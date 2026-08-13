package com.EyeOfHarmonyBuffer.common.dyson;

import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.DysonCore;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.DysonMachineConfig;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.DysonReceiverModule;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgradeStorage;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
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
     * 制造模块入口：把组件计入**机主个人**虚拟库存（发射前组件是个人资产）。
     */
    public static boolean addComponentsToPlayer(World world, UUID playerUUID, String playerName,
                                                long cloudAmount, long frameAmount) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || playerUUID == null) {
            return false;
        }
        DysonTeamProgress player = data.getOrCreateTeam(playerUUID, playerName);
        if (cloudAmount > 0) {
            player.cloudComponents += cloudAmount;
        }
        if (frameAmount > 0) {
            player.frameComponents += frameAmount;
        }
        data.markDirty();
        return true;
    }

    /**
     * 发射模块入口：从**机主个人**虚拟库存扣组件。库存不足时一笔不扣。
     */
    public static boolean consumeComponentsOfPlayer(World world, UUID playerUUID, long cloudAmount, long frameAmount) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || playerUUID == null) {
            return false;
        }
        DysonTeamProgress player = data.getTeam(playerUUID);
        if (player == null) {
            return false;
        }
        if (player.cloudComponents < cloudAmount || player.frameComponents < frameAmount) {
            return false;
        }
        player.cloudComponents -= cloudAmount;
        player.frameComponents -= frameAmount;
        data.markDirty();
        return true;
    }

    // region 对外数据接口

    public static int getTeamCloudCount(World world, UUID teamId) {
        DysonTeamProgress team = getTeamForQuery(world, teamId);
        return team == null ? 0 : team.cloudCount;
    }

    public static int getTeamFrameCount(World world, UUID teamId) {
        DysonTeamProgress team = getTeamForQuery(world, teamId);
        return team == null ? 0 : team.frameCount;
    }

    public static int getTeamPasteCount(World world, UUID teamId) {
        DysonTeamProgress team = getTeamForQuery(world, teamId);
        return team == null ? 0 : team.pasteCount;
    }

    public static long getPlayerCloudComponents(World world, UUID playerUUID) {
        DysonTeamProgress player = getTeamForQuery(world, playerUUID);
        return player == null ? 0 : player.cloudComponents;
    }

    public static long getPlayerFrameComponents(World world, UUID playerUUID) {
        DysonTeamProgress player = getTeamForQuery(world, playerUUID);
        return player == null ? 0 : player.frameComponents;
    }

    /** 队伍级升级树存储；队伍不存在返回 null。 */
    public static DysonUpgradeStorage getTeamUpgrades(World world, UUID teamId) {
        DysonTeamProgress team = getTeamForQuery(world, teamId);
        return team == null ? null : team.upgrades;
    }

    public static boolean isTeamUpgradeActive(World world, UUID teamId, DysonUpgrade upgrade) {
        DysonUpgradeStorage storage = getTeamUpgrades(world, teamId);
        return storage != null && storage.isUpgradeActive(upgrade);
    }

    /**
     * 核心每 tick 上报 owner 的队伍归属；检测到队伍变化时处理升级树继承。
     * 队伍计数与组件库存是队伍资产，不随玩家离队转移。
     */
    public static void trackPlayerTeam(World world, UUID playerUUID, String playerName, UUID teamId) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || playerUUID == null || teamId == null) {
            return;
        }
        UUID oldTeam = data.getPlayerTeam(playerUUID);
        data.setPlayerTeam(playerUUID, teamId);
        if (oldTeam != null && !oldTeam.equals(teamId)) {
            handleTeamChange(data, playerUUID, playerName, oldTeam, teamId);
        }
    }

    /** 离队/被踢（新队伍 = 玩家自己）时，把旧队伍的升级树继承一份到个人。 */
    private static void handleTeamChange(DysonSphereWorldData data, UUID playerUUID, String playerName,
                                         UUID oldTeamId, UUID newTeamId) {
        // 加入他人队伍：使用队伍树，不把个人树注入队伍
        if (!newTeamId.equals(playerUUID)) {
            return;
        }
        DysonTeamProgress oldTeam = data.getTeam(oldTeamId);
        if (oldTeam == null) {
            return;
        }
        DysonTeamProgress personal = data.getOrCreateTeam(playerUUID, playerName);
        personal.upgrades.copyFrom(oldTeam.upgrades);
        data.markDirty();
    }

    private static DysonTeamProgress getTeamForQuery(World world, UUID teamId) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || teamId == null) {
            return null;
        }
        return data.getTeam(teamId);
    }

    // endregion

    /** 每日 0:00 结算：先贴片、后掉落、再完工判定。 */
    public static void settleDaily(World world) {
        settleDaily(world, false);
    }

    /**
     * 结算入口（按队独立）。
     *
     * @param halfDay true = 正午 12:00，只做贴片转化（需点亮贴片转化节点），不做掉落；
     *                 false = 每日 0:00 完整结算。
     */
    public static void settleDaily(World world, boolean halfDay) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null) {
            return;
        }

        if (data.isCompleted()) {
            if (halfDay) {
                return;
            }
            // 完工后：占领者剩余轨道云继续掉落，直到 0；贴片不再变化
            DysonTeamProgress winner = data.getTeam(data.getCompletedTeamId());
            if (winner != null && winner.cloudCount > 0) {
                int dropped = Math.min(winner.cloudCount, randomDrop(world));
                winner.cloudCount -= dropped;
                recoverDroppedClouds(world, data.getCompletedTeamId(), dropped);
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
            // 1) 贴片优先：需队伍核心开机；正午额外要求已点亮贴片转化节点
            boolean coreOnline = DysonCore.isTeamCoreOnline(teamId);
            boolean allowConvert = coreOnline
                && (!halfDay || isTeamUpgradeActive(world, teamId, DysonUpgrade.PASTE_CONVERSION));
            if (allowConvert) {
                int room = Math.max(0, DysonMachineConfig.PASTE_PER_FRAME * team.frameCount - team.pasteCount);
                int batches = Math.min(team.cloudCount / DysonMachineConfig.CLOUDS_PER_PASTE, room);
                if (batches > 0) {
                    team.pasteCount += batches;
                    team.cloudCount -= batches * DysonMachineConfig.CLOUDS_PER_PASTE;
                    changed = true;
                }
            }

            // 2) 掉落：10~64 云，仅每日 0:00
            if (!halfDay && team.cloudCount > 0) {
                int dropped = Math.min(team.cloudCount, randomDrop(world));
                team.cloudCount -= dropped;
                recoverDroppedClouds(world, teamId, dropped);
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

    /** 接收强化：掉落的云按 50% 回收为云组件，归正在工作的那台接收模块的机主。 */
    private static void recoverDroppedClouds(World world, UUID teamId, int dropped) {
        if (dropped <= 0 || !isTeamUpgradeActive(world, teamId, DysonUpgrade.RECEIVER_BOOST)) {
            return;
        }
        UUID owner = DysonReceiverModule.getTeamReceiverOwner(teamId);
        if (owner == null) {
            return;
        }
        addComponentsToPlayer(world, owner, null, dropped / 2, 0);
    }

    private static int randomDrop(World world) {
        return DysonMachineConfig.DAILY_DROP_MIN
            + world.rand.nextInt(DysonMachineConfig.DAILY_DROP_MAX - DysonMachineConfig.DAILY_DROP_MIN + 1);
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
        String name = teamName == null || teamName.isEmpty()
            ? TextLocalization.Dyson_Broadcast_UnknownTeam
            : teamName;
        server.getConfigurationManager().sendChatMsg(new ChatComponentText(
            EnumChatFormatting.GOLD + TextLocalization.Dyson_Broadcast_00 + EnumChatFormatting.RESET
                + EnumChatFormatting.AQUA + name + EnumChatFormatting.RESET
                + TextLocalization.Dyson_Broadcast_01));
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

    /**
     * 调试入口：把某队贴片/框架推满并触发完整完工流程（广播、败者清零、永久锁死）。
     * 轨道云保持不变，便于继续测试完工后的每日掉落。
     */
    public static void debugComplete(World world, UUID teamId, String teamName) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null || teamId == null || data.isCompleted()) {
            return;
        }
        DysonTeamProgress team = data.getTeam(teamId);
        int cloud = team == null ? 0 : team.cloudCount;
        setTeamCounters(
            world,
            teamId,
            teamName,
            cloud,
            DysonSphereState.FRAME_COMPLETE,
            DysonSphereState.PASTE_COMPLETE);
        complete(world, teamId, teamName);
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
