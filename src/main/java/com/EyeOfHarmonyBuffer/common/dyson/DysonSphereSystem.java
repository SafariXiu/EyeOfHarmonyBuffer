package com.EyeOfHarmonyBuffer.common.dyson;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 戴森球服务端统一入口：发射、每日结算、完工、同步都走这里。
 * <p>
 * 队伍语义沿用 Orundum 电网（调用方把玩家 UUID 解析为队伍 ID 后传入）。
 */
public final class DysonSphereSystem {

    /** 每框架容纳的贴片数。 */
    public static final int PASTE_PER_FRAME = 4;
    /** 每贴片消耗的云数。 */
    public static final int CLOUDS_PER_PASTE = 128;
    /** 每日掉落范围（含上下限）。 */
    public static final int DAILY_DROP_MIN = 10;
    public static final int DAILY_DROP_MAX = 64;

    private DysonSphereSystem() {}

    /**
     * 发射机入口：为本队增加云/框架计数（1 个组件 = 1 计数）。
     *
     * @return 是否接受本次发射；完工锁死后返回 false。
     */
    public static boolean addModules(World world, UUID teamId, String teamName, int cloudDelta, int frameDelta) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || teamId == null) {
            return false;
        }
        // 完工后永久锁死（含占领者自己）
        if (data.isCompleted()) {
            return false;
        }

        DysonTeamProgress team = data.getOrCreateTeam(teamId, teamName);
        if (team.firstLaunchTick <= 0L) {
            team.firstLaunchTick = world.getTotalWorldTime();
        }

        int newCloud = clamp(team.cloudCount + cloudDelta, 0, DysonSphereState.CLOUD_CAP);
        int newFrame = clamp(team.frameCount + frameDelta, 0, DysonSphereState.FRAME_COMPLETE);
        if (newCloud == team.cloudCount && newFrame == team.frameCount) {
            return true;
        }

        team.cloudCount = newCloud;
        team.frameCount = newFrame;
        data.markDirty();
        syncToAll(world);
        return true;
    }

    /**
     * 调试入口：直接设置某队三计数器。不触发完工锁死，供 /dyson 预览渲染状态。
     */
    public static void setTeamCounters(World world, UUID teamId, String teamName,
                                       int cloudCount, int frameCount, int pasteCount) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || teamId == null) {
            return;
        }
        DysonTeamProgress team = data.getOrCreateTeam(teamId, teamName);
        team.cloudCount = clamp(cloudCount, 0, DysonSphereState.CLOUD_CAP);
        team.frameCount = clamp(frameCount, 0, DysonSphereState.FRAME_COMPLETE);
        team.pasteCount = clamp(pasteCount, 0, DysonSphereState.PASTE_COMPLETE);
        data.markDirty();
        syncToAll(world);
    }

    /**
     * 每日结算（每 MC 天一次，按队独立）：先贴片、后掉落、再完工判定。
     */
    public static void settleDaily(World world) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null) {
            return;
        }

        if (data.isCompleted()) {
            // 完工后：占领者剩余轨道云继续掉落，直到 0；贴片不再变化
            DysonTeamProgress winner = data.getTeam(data.getCompletedTeamId());
            if (winner != null && winner.cloudCount > 0) {
                winner.cloudCount = Math.max(0, winner.cloudCount - randomDrop(world));
                data.markDirty();
                syncToAll(world);
            }
            return;
        }

        boolean changed = false;
        for (UUID teamId : new ArrayList<>(data.getTeamIds())) {
            DysonTeamProgress team = data.getTeam(teamId);
            if (team == null) {
                continue;
            }

            // 1) 贴片优先：128 云 = 1 贴片，容量上限 = 4 × 框架
            int room = Math.max(0, PASTE_PER_FRAME * team.frameCount - team.pasteCount);
            int batches = Math.min(team.cloudCount / CLOUDS_PER_PASTE, room);
            if (batches > 0) {
                team.pasteCount += batches;
                team.cloudCount -= batches * CLOUDS_PER_PASTE;
                changed = true;
            }

            // 2) 掉落：10~64 云
            if (team.cloudCount > 0) {
                team.cloudCount = Math.max(0, team.cloudCount - randomDrop(world));
                changed = true;
            }

            // 3) 完工判定：贴片打满即自动完工
            if (team.pasteCount >= DysonSphereState.PASTE_COMPLETE) {
                complete(world, teamId, team.teamName);
                return;
            }
        }

        if (changed) {
            data.markDirty();
            syncToAll(world);
        }
    }

    private static int randomDrop(World world) {
        return DAILY_DROP_MIN + world.rand.nextInt(DAILY_DROP_MAX - DAILY_DROP_MIN + 1);
    }

    /**
     * 完工：广播、败者清零、永久锁死。
     */
    public static void complete(World world, UUID teamId, String teamName) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || data.isCompleted()) {
            return;
        }

        data.markCompleted(teamId, teamName);
        for (UUID other : new ArrayList<>(data.getTeamIds())) {
            if (!other.equals(teamId)) {
                data.removeTeam(other);
            }
        }
        data.markDirty();

        broadcastCompletion(teamName);
        syncToAll(world);
    }

    private static void broadcastCompletion(String teamName) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        String name = teamName == null || teamName.isEmpty() ? "未知队伍" : teamName;
        server.getConfigurationManager().sendChatMsg(new ChatComponentText(
            EnumChatFormatting.GOLD + "[戴森球] " + EnumChatFormatting.RESET
                + EnumChatFormatting.AQUA + name + EnumChatFormatting.RESET
                + " 的队伍完成了塔罗斯戴森球！恒星已永久归属他们。"));
    }

    /** 调试重置：清空全部队伍与完工状态。 */
    public static void resetAll(World world) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null) {
            return;
        }
        data.clearAllTeams();
        data.clearCompletion();
        syncToAll(world);
    }

    /** 把当前状态广播给所有在线玩家（服务端）。 */
    public static void syncToAll(World world) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null) {
            return;
        }
        DysonSphereNetwork.INSTANCE.sendToAll(toPacket(data));
    }

    /** 把当前状态单独发给某个玩家（登录/进入维度时同步）。 */
    public static void syncToPlayer(EntityPlayerMP player) {
        DysonSphereWorldData data = DysonSphereWorldData.get(player.worldObj);
        if (data == null) {
            return;
        }
        DysonSphereNetwork.INSTANCE.sendTo(toPacket(data), player);
    }

    /** 阶段由云/框架/贴片三参数统一推导，存档/指令/机器共用同一套规则。 */
    public static int computeStage(int cloud, int frame, int paste) {
        if (paste >= DysonSphereState.PASTE_COMPLETE) {
            return DysonSphereState.STAGE_COMPLETE;
        }
        if (frame >= DysonSphereState.FRAME_STAGE_3) {
            return DysonSphereState.STAGE_FRAME_80;
        }
        if (frame >= DysonSphereState.FRAME_STAGE_2) {
            return DysonSphereState.STAGE_HALF;
        }
        if (frame >= DysonSphereState.FRAME_MIN) {
            return DysonSphereState.STAGE_FRAME_25;
        }
        return cloud > 0 ? DysonSphereState.STAGE_CLOUD : DysonSphereState.STAGE_NONE;
    }

    private static PacketDysonSphereState toPacket(DysonSphereWorldData data) {
        return new PacketDysonSphereState(
            data.getStage(), data.getProgress(), data.getCloudCount(),
            data.getFrameCount(), data.getPasteCount(), data.getOwnerName());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
