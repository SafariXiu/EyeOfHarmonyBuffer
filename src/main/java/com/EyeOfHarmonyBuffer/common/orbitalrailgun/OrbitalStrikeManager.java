package com.EyeOfHarmonyBuffer.common.orbitalrailgun;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.common.item.itemadders.ItemOrbitalRailgun;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轨道打击服务端逻辑（移植自 orbital-railgun，MIT License，见 LICENSE-orbital-railgun.txt）。
 * 时间轴：0-400t 蓄力；400-700t 吸引实体；700t 伤害 + 逐 tick 预算清空方块。
 */
public class OrbitalStrikeManager {

    public static final int SUCK_START_TICKS = 400;
    public static final int DETONATE_TICKS = 700;
    public static final int MAX_STRIKE_TICKS = 2400;

    public static final DamageSource ORBITAL_RAILGUN = new DamageSource("orbitalRailgun");

    private static final Map<StrikeKey, ActiveStrike> ACTIVE_STRIKES = new ConcurrentHashMap<>();
    private static final java.util.ArrayDeque<ClearTask> CLEAR_QUEUE = new java.util.ArrayDeque<>();
    /** 清空完成后待重发的区块（每 tick 1 个，避免一次性数据包/重渲染卡死）。每个元素：{dimId, cx, cz} */
    private static final java.util.ArrayDeque<long[]> CHUNK_SYNC_QUEUE = new java.util.ArrayDeque<>();
    /** 冷却表：值 = 墙钟毫秒（跨世界一致，避免世界时间戳回退导致新世界永久冷却）。 */
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    public OrbitalStrikeManager() {}

    /** 客户端开火请求的服务端入口（已在主线程执行）：物品/启用校验后走统一 API（ORU 扣费 + 启动）。 */
    public static void handleFireRequest(EntityPlayerMP player, int x, int y, int z) {
        if (player.worldObj == null || player.worldObj.isRemote) {
            return;
        }
        if (!MainConfig.OrbitalRailgunEnable) {
            return;
        }
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemOrbitalRailgun)) {
            return;
        }
        OrbitalRailgunApi.StrikeResult result = OrbitalRailgunApi.requestStrikeForPlayer(player, x, y, z);
        if (result == OrbitalRailgunApi.StrikeResult.INSUFFICIENT_ORU) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_OrbitalRailgun_InsufficientOru));
        } else if (result == OrbitalRailgunApi.StrikeResult.STRIKE_ALREADY_ACTIVE) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_OrbitalRailgun_AlreadyActive));
        }
    }

    /** 指定位置当前是否有进行中的打击（API 扣费前先去重，避免扣费后无法启动）。 */
    public static boolean hasActiveStrike(int dimensionId, int x, int y, int z) {
        return ACTIVE_STRIKES.containsKey(new StrikeKey(dimensionId, x, y, z));
    }

    /**
     * 启动一次轨道打击（服务端主线程调用）。
     *
     * @return true 表示成功启动；false 表示同位置已有打击
     */
    public static boolean startStrike(World world, int x, int y, int z, float radius,
                                      EntityPlayer shooter, UUID shooterUuid, UUID teamId) {
        if (world == null || world.isRemote) {
            return false;
        }
        StrikeKey key = new StrikeKey(world.provider.dimensionId, x, y, z);
        if (ACTIVE_STRIKES.containsKey(key)) {
            return false;
        }

        double clampedRadius = Math.max(0.5, Math.min(128.0, radius));
        long startTick = world.getTotalWorldTime();
        double extent = Math.max(clampedRadius * 4.0, 128.0);

        List<Entity> tracked = world.getEntitiesWithinAABB(
            Entity.class,
            AxisAlignedBB.getBoundingBox(
                x - extent, y - extent, z - extent,
                x + extent, y + extent, z + extent));

        ACTIVE_STRIKES.put(key, new ActiveStrike(world.provider.dimensionId, x, y, z,
            (float) clampedRadius, startTick, tracked,
            shooterUuid != null ? shooterUuid : (shooter != null ? shooter.getUniqueID() : null),
            teamId));

        // 开火音效（占位，后续替换为自定义音效）
        world.playSoundEffect(shooter == null ? x : shooter.posX,
            shooter == null ? y : shooter.posY,
            shooter == null ? z : shooter.posZ,
            "fireworks.launch", 2.0F, 0.9F);

        // 通知附近客户端播放特效（携带归属，供客户端多打击分流）
        OrbitalRailgunNetwork.INSTANCE.sendToAllAround(
            new PacketOrbitalStrikeStart(x, y, z, (float) clampedRadius,
                shooterUuid != null ? shooterUuid : (shooter != null ? shooter.getUniqueID() : null),
                teamId),
            new NetworkRegistry.TargetPoint(world.provider.dimensionId, x, y, z, 512.0));

        // 打击监听器（机器 GUI/统计）
        OrbitalRailgunApi.fireListeners(world.provider.dimensionId, x, y, z, (float) clampedRadius, teamId);
        return true;
    }

    // ---------- 冷却 ----------

    public static boolean isOnCooldown(EntityPlayer player) {
        Long until = COOLDOWN_UNTIL.get(player.getUniqueID());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            COOLDOWN_UNTIL.remove(player.getUniqueID());
            return false;
        }
        return true;
    }

    /** 施加玩家个人冷却（Api 玩家入口调用）。 */
    public static void applyCooldown(EntityPlayer player) {
        COOLDOWN_UNTIL.put(player.getUniqueID(),
            System.currentTimeMillis() + Math.max(0, MainConfig.OrbitalRailgunCooldownTicks) * 50L);
    }

    // ---------- tick ----------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        processClears();

        if (ACTIVE_STRIKES.isEmpty()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        for (Map.Entry<StrikeKey, ActiveStrike> entry : ACTIVE_STRIKES.entrySet()) {
            ActiveStrike strike = entry.getValue();
            WorldServer world = server.worldServerForDimension(strike.dimensionId);
            if (world == null) {
                // 世界已卸载，丢弃
                ACTIVE_STRIKES.remove(entry.getKey());
                continue;
            }
            long age = world.getTotalWorldTime() - strike.startTick;
            if (age >= DETONATE_TICKS) {
                ACTIVE_STRIKES.remove(entry.getKey());
                detonate(world, strike);
            } else if (age >= SUCK_START_TICKS && MainConfig.OrbitalRailgunSuckEnabled) {
                suckEntities(world, strike, age);
            } else if (age > MAX_STRIKE_TICKS) {
                ACTIVE_STRIKES.remove(entry.getKey());
            }
        }
    }

    private void suckEntities(World world, ActiveStrike strike, long age) {
        double cx = strike.x + 0.5;
        double cy = strike.y + 0.5;
        double cz = strike.z + 0.5;
        for (Entity entity : strike.tracked) {
            if (entity.isDead || entity.worldObj != world) {
                continue;
            }
            double dx = cx - entity.posX;
            double dy = cy - entity.posY;
            double dz = cz - entity.posZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double mag = Math.min(1.0 / Math.abs(len - 20.0) * 4.0 * (age - SUCK_START_TICKS) / 300.0, 5.0);
            if (mag <= 0 || len < 1.0E-4) {
                continue;
            }
            entity.addVelocity(dx / len * mag, dy / len * mag, dz / len * mag);
            entity.velocityChanged = true;
        }
    }

    private void detonate(World world, ActiveStrike strike) {
        double cx = strike.x + 0.5;
        double cy = strike.y + 0.5;
        double cz = strike.z + 0.5;
        double r = strike.radius;
        double rSq = r * r;

        // 伤害快照内的实体
        for (Entity entity : strike.tracked) {
            if (entity.isDead || entity.worldObj != world) {
                continue;
            }
            if (entity.getDistanceSq(cx, cy, cz) <= rSq) {
                entity.attackEntityFrom(ORBITAL_RAILGUN, (float) MainConfig.OrbitalRailgunDamage);
            }
        }

        // 音效 + 粒子
        world.playSoundEffect(cx, cy, cz, "random.explode", 4.0F,
            (1.0F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F) * 0.7F);
        world.spawnParticle("hugeexplosion", cx, cy, cz, 0.0, 0.0, 0.0);

        // 列队清空方块（每 tick 预算处理，避免一帧删除几万方块卡服）
        int radius = (int) Math.ceil(r);
        List<Long> columns = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= rSq) {
                    columns.add(((long) (strike.x + dx) << 32) | (strike.z + dz) & 0xFFFFFFFFL);
                }
            }
        }
        long[] columnArray = new long[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            columnArray[i] = columns.get(i);
        }
        if (columnArray.length > 0) {
            CLEAR_QUEUE.addLast(new ClearTask((WorldServer) world, columnArray, strike.x, strike.z));
        }
    }

    private void processClears() {
        int budget = Math.max(1, MainConfig.OrbitalRailgunBlocksPerTick);
        while (budget > 0 && !CLEAR_QUEUE.isEmpty()) {
            ClearTask task = CLEAR_QUEUE.peekFirst();
            int consumed = task.process(budget);
            budget -= consumed;
            if (task.isDone()) {
                task.notifyClientRefresh();
                CLEAR_QUEUE.removeFirst();
            }
        }
        // 每 tick 重发 1 个区块（清空后客户端数据同步 + 重渲染）
        if (!CHUNK_SYNC_QUEUE.isEmpty()) {
            long[] entry = CHUNK_SYNC_QUEUE.pollFirst();
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server != null) {
                WorldServer ws = server.worldServerForDimension((int) entry[0]);
                if (ws != null) {
                    Chunk chunk = ws.getChunkFromChunkCoords((int) entry[1], (int) entry[2]);
                    if (chunk != null) {
                        List<Chunk> chunks = new ArrayList<>();
                        chunks.add(chunk);
                        S26PacketMapChunkBulk packet = new S26PacketMapChunkBulk(chunks);
                        double cx = ((int) entry[1]) * 16 + 8;
                        double cz = ((int) entry[2]) * 16 + 8;
                        for (Object o : ws.playerEntities) {
                            EntityPlayerMP player = (EntityPlayerMP) o;
                            if (player.getDistanceSq(cx, 128.0, cz) <= 512.0 * 512.0) {
                                player.playerNetServerHandler.sendPacket(packet);
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 内部结构 ----------

    private static final class StrikeKey {
        final int dimensionId;
        final int x;
        final int y;
        final int z;

        StrikeKey(int dimensionId, int x, int y, int z) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StrikeKey)) {
                return false;
            }
            StrikeKey k = (StrikeKey) o;
            return dimensionId == k.dimensionId && x == k.x && y == k.y && z == k.z;
        }

        @Override
        public int hashCode() {
            int hash = dimensionId;
            hash = hash * 31 + x;
            hash = hash * 31 + y;
            hash = hash * 31 + z;
            return hash;
        }
    }

    private static final class ActiveStrike {
        final int dimensionId;
        final int x;
        final int y;
        final int z;
        final float radius;
        final long startTick;
        final List<Entity> tracked;
        final UUID shooterUuid;
        final UUID teamId;

        ActiveStrike(int dimensionId, int x, int y, int z, float radius, long startTick,
                     List<Entity> tracked, UUID shooterUuid, UUID teamId) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.startTick = startTick;
            this.tracked = tracked;
            this.shooterUuid = shooterUuid;
            this.teamId = teamId;
        }
    }

    /** 清空任务：逐层（从世界顶部向下）删除半径内整列方块。 */
    private static final class ClearTask {
        final WorldServer world;
        final long[] columns; // 每个元素：x << 32 | z
        final int centerX;
        final int centerZ;
        int idx = 0;
        int y;
        boolean notified;

        ClearTask(WorldServer world, long[] columns, int centerX, int centerZ) {
            this.world = world;
            this.columns = columns;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.y = world.getActualHeight() - 1;
        }

        boolean isDone() {
            return idx >= columns.length;
        }

        /** 处理至多 budget 个方块，返回实际消耗的预算。 */
        int process(int budget) {
            int consumed = 0;
            while (consumed < budget && !isDone()) {
                long xz = columns[idx];
                int x = (int) (xz >> 32);
                int z = (int) (xz & 0xFFFFFFFFL);
                if (y >= 0) {
                    if (world.blockExists(x, y, z)) {
                        Block block = world.getBlock(x, y, z);
                        if (block != Blocks.air) {
                            boolean breakable = MainConfig.OrbitalRailgunAllowUnbreakable
                                || block.getBlockHardness(world, x, y, z) >= 0.0F;
                            if (breakable) {
                                if (MainConfig.OrbitalRailgunDropBlocks) {
                                    world.func_147480_a(x, y, z, true);
                                } else {
                                    // flag 0：只改服务端，不逐块通知客户端。
                                    // 全部清空完成后由 notifyClientRefresh() 一次性刷新区域，
                                    // 避免 60 万次方块更新包拖死客户端渲染线程。
                                    world.setBlock(x, y, z, Blocks.air, 0, 0);
                                }
                            }
                        }
                    }
                    y--;
                } else {
                    idx++;
                    y = world.getActualHeight() - 1;
                    // 列翻转不消耗预算
                    continue;
                }
                consumed++;
            }
            return consumed;
        }

        /** 清空完成且未入队过时，把覆盖区块排入重发队列（幂等）。 */
        void notifyClientRefresh() {
            if (notified || !isDone()) {
                return;
            }
            notified = true;
            // 覆盖区域（半径 24 的清空圆柱）所跨的所有区块入队，每 tick 重发一个
            int cX1 = (centerX - 24) >> 4;
            int cX2 = (centerX + 24) >> 4;
            int cZ1 = (centerZ - 24) >> 4;
            int cZ2 = (centerZ + 24) >> 4;
            for (int cx = cX1; cx <= cX2; cx++) {
                for (int cz = cZ1; cz <= cZ2; cz++) {
                    CHUNK_SYNC_QUEUE.addLast(new long[] { world.provider.dimensionId, cx, cz });
                }
            }
        }
    }
}
