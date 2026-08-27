package com.EyeOfHarmonyBuffer.common.transition;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/**
 * 维度转场服务端编排（忠实移植 Nostalgia EchoRitualManager 的相位时序，CC0-1.0，见 LICENSE-nostalgia.txt）。
 * <p>
 * 相位由服务端定时器驱动（源库等客户端全息缓存 ready，我们无全息系统 → 固定时长）：
 * phase 1（发 PacketTransitionStart）→ +Phase1Ms → phase 2（白幕扩张）→ +Phase2Ms →
 * phase 3（白幕揭开）→ +Phase3Ms → 传送（NoPortalTeleporter，落目标世界出生点）。
 * <p>
 * 触发：指令 /eohbwarp（当前）或任意机器调用 {@link #startTransition}（预留接口）。
 * 仅允许从塔罗斯2（14001）触发，目标维度暂定主世界（0）。
 */
public class DimensionTransitionManager {

    public static final DimensionTransitionManager INSTANCE = new DimensionTransitionManager();

    /** 传送超时（tick）：相位推进异常时兜底直接传送。 */
    private static final long TOTAL_TIMEOUT_TICKS = 20 * 60;

    private final Map<UUID, PendingTransition> pending = new HashMap<UUID, PendingTransition>();

    private DimensionTransitionManager() {}

    // ================= 对外接口（指令 / 机器共用） =================

    /**
     * 发起一次维度转场。机器调用入口：任意多方块/物品在塔罗斯2上调用即可。
     *
     * @param player    发起玩家（服务端）
     * @param centerX/centerY/centerZ 转场中心（特效以该位置为中心扩张）
     * @param targetDim 目标维度 ID（服务端校验；默认 0 = 主世界）
     */
    public static void startTransition(EntityPlayerMP player, int centerX, int centerY, int centerZ, int targetDim) {
        if (player == null) {
            return;
        }
        if (!MainConfig.DimensionTransitionEnable) {
            player.addChatMessage(new ChatComponentText("[EOHB] 维度转场功能已在配置中关闭"));
            return;
        }
        int source = MainConfig.DimensionTransitionSourceDimension;
        if (player.dimension != source) {
            player.addChatMessage(new ChatComponentText("[EOHB] 维度转场只能在塔罗斯2（维度 " + source + "）使用"));
            return;
        }
        if (targetDim == player.dimension) {
            player.addChatMessage(new ChatComponentText("[EOHB] 目标维度不能是当前维度"));
            return;
        }
        INSTANCE.begin(player, centerX, centerY, centerZ, targetDim);
    }

    // ================= 内部状态机 =================

    private void begin(EntityPlayerMP player, int centerX, int centerY, int centerZ, int targetDim) {
        UUID id = player.getUniqueID();
        if (pending.containsKey(id)) {
            player.addChatMessage(new ChatComponentText("[EOHB] 转场正在进行中，请稍候"));
            return;
        }
        long now = player.worldObj.getTotalWorldTime();
        pending.put(id, new PendingTransition(centerX, centerY, centerZ, targetDim, now));
        player.addChatMessage(new ChatComponentText("[EOHB] 转场开始…"));
        TransitionNetwork.INSTANCE.sendTo(new PacketTransitionStart(centerX, centerY, centerZ, targetDim), player);
        EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Transition started for {} (phase 1)", player.getDisplayName());
    }

    /** 发送相位包并返回下阶段是否已触发。 */
    private void sendPhase(EntityPlayerMP player, int phase) {
        TransitionNetwork.INSTANCE.sendTo(new PacketTransitionPhase(phase), player);
        EyeOfHarmonyBuffer.LOGGER.info("[EOHB] Sent phase {} to {}", phase, player.getDisplayName());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        long worldTime = server.worldServerForDimension(0) != null
            ? server.worldServerForDimension(0).getTotalWorldTime() : 0;
        Iterator<Map.Entry<UUID, PendingTransition>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PendingTransition> entry = it.next();
            EntityPlayerMP player = findPlayerByUUID(server, entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            PendingTransition t = entry.getValue();
            long elapsed = worldTime - t.startTick;

            if (t.phase == 1 && elapsed >= phase1Ticks()) {
                t.phase = 2;
                sendPhase(player, 2);
            } else if (t.phase == 2 && elapsed >= phase1Ticks() + phase2Ticks()) {
                t.phase = 3;
                sendPhase(player, 3);
            } else if (t.phase == 3 && elapsed >= phase1Ticks() + phase2Ticks() + phase3Ticks()) {
                it.remove();
                doTeleport(player, t);
            } else if (elapsed > TOTAL_TIMEOUT_TICKS) {
                // 兜底：任何异常都强制传送，避免玩家被卡在转场状态
                EyeOfHarmonyBuffer.LOGGER.warn("[EOHB] Transition timeout for {}, force teleport", player.getDisplayName());
                it.remove();
                doTeleport(player, t);
            }
        }
    }

    private static long phase1Ticks() {
        return Math.max(1, Math.round(MainConfig.DimensionTransitionPhase1Ms / 50.0D));
    }

    private static long phase2Ticks() {
        return Math.max(1, Math.round(MainConfig.DimensionTransitionPhase2Ms / 50.0D));
    }

    private static long phase3Ticks() {
        return Math.max(1, Math.round(MainConfig.DimensionTransitionPhase3Ms / 50.0D));
    }

    private void doTeleport(EntityPlayerMP player, PendingTransition t) {
        try {
            MinecraftServer server = MinecraftServer.getServer();
            WorldServer targetWorld = server.worldServerForDimension(t.targetDim);
            if (targetWorld == null) {
                EyeOfHarmonyBuffer.LOGGER.error("[EOHB] Transition target dimension {} not found", t.targetDim);
                player.addChatMessage(new ChatComponentText("[EOHB] 目标维度不存在，转场取消"));
                return;
            }
            // 先摆到目标世界出生点，再切换维度（自定义无门 Teleporter 不会生成/寻找传送门）
            ChunkCoordinates spawn = targetWorld.getSpawnPoint();
            player.setPositionAndUpdate(spawn.posX + 0.5, spawn.posY, spawn.posZ + 0.5);
            player.addChatMessage(new ChatComponentText("[EOHB] 正在传送…"));
            EyeOfHarmonyBuffer.LOGGER.info("[EOHB] teleporting {} to dim {} spawn ({},{},{})",
                player.getDisplayName(), t.targetDim, spawn.posX, spawn.posY, spawn.posZ);
            server.getConfigurationManager().transferPlayerToDimension(
                player, t.targetDim, new NoPortalTeleporter(targetWorld));
            EyeOfHarmonyBuffer.LOGGER.info("[EOHB] {} transitioned to dim {}", player.getDisplayName(), t.targetDim);
        } catch (Exception e) {
            EyeOfHarmonyBuffer.LOGGER.error("[EOHB] Transition teleport failed", e);
            player.addChatMessage(new ChatComponentText("[EOHB] 传送失败：" + e.getMessage()));
        }
    }

    /** 1.7.10 ServerConfigurationManager 无 getPlayerByUUID，遍历玩家列表查找。 */
    private static EntityPlayerMP findPlayerByUUID(MinecraftServer server, UUID uuid) {
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (o instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) o;
                if (p.getUniqueID().equals(uuid)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * 不找门、不建门的传送器。
     * vanilla {@link Teleporter} 会在目标维度找不到传送门时现场生成一个下界传送门
     * （makePortal），导致"传送到主世界却出现地狱门、随后被送进下界"。
     */
    private static final class NoPortalTeleporter extends Teleporter {

        NoPortalTeleporter(WorldServer world) {
            super(world);
        }

        @Override
        public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {
        }

        @Override
        public boolean placeInExistingPortal(Entity entity, double x, double y, double z, float yaw) {
            return true;
        }

        @Override
        public boolean makePortal(Entity entity) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long time) {
        }
    }

    private static final class PendingTransition {
        final int centerX;
        final int centerY;
        final int centerZ;
        final int targetDim;
        final long startTick;
        int phase = 1;

        PendingTransition(int centerX, int centerY, int centerZ, int targetDim, long startTick) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.targetDim = targetDim;
            this.startTick = startTick;
        }
    }
}
