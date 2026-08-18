package com.EyeOfHarmonyBuffer.common.orbitalrailgun;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.utils.UnitParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 轨道打击对外接口（仿 DysonSphereState/DysonSphereSystem 的静态门面模式）。
 * <p>
 * 供戴森球模块/多方块机器/命令调用，统一完成：
 * 校验（启用/世界/坐标/冷却/去重）→ ORU 费用扣除（队伍制）→ 启动打击。
 * 三个入口：
 * <ul>
 *   <li>{@link #requestStrikeForPlayer}：玩家入口（物品开火内部也走这里），扣玩家所在队伍 ORU，受个人冷却与射程限制</li>
 *   <li>{@link #requestStrikeForTeam}：机器/模块入口，按队伍 UUID 扣费，不受个人冷却与射程限制</li>
 *   <li>{@link #fireStrikeAt}：纯坐标直接打击（不扣费，由调用方自行管理费用），仅校验世界/坐标/去重</li>
 * </ul>
 * 监听器：{@link StrikeListener} 在打击成功启动时回调，供机器 GUI/统计使用。
 */
public final class OrbitalRailgunApi {

    /** 打击结果。 */
    public enum StrikeResult {
        SUCCESS,
        NOT_ENABLED,
        INSUFFICIENT_ORU,
        STRIKE_ALREADY_ACTIVE,
        INVALID_POSITION,
        INVALID_TARGET,
        WORLD_UNAVAILABLE,
        COOLDOWN,
        NO_TEAM
    }

    /** 打击事件监听器（服务端主线程回调）。 */
    public interface StrikeListener {
        void onStrikeStarted(int dimensionId, int x, int y, int z, float radius, UUID teamId);
    }

    private static final List<StrikeListener> LISTENERS = new CopyOnWriteArrayList<StrikeListener>();

    private OrbitalRailgunApi() {}

    // ---------- 费用 ----------

    /** 单次打击 ORU 消耗（解析配置，失败回退 100 亿）。 */
    public static BigInteger getStrikeCost() {
        try {
            return BigInteger.valueOf(UnitParser.parseQuantityWithUnits(MainConfig.OrbitalRailgunOruCost));
        } catch (Exception e) {
            return BigInteger.valueOf(10_000_000_000L);
        }
    }

    /** 队伍当前 ORU 余额（供机器 GUI 显示）。 */
    public static BigInteger getTeamBalance(UUID teamId) {
        return OrundumEnergyService.getOrundumForTeam(teamId);
    }

    // ---------- 监听器 ----------

    public static void addStrikeListener(StrikeListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeStrikeListener(StrikeListener listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyStrikeStarted(int dimensionId, int x, int y, int z, float radius, UUID teamId) {
        for (StrikeListener l : LISTENERS) {
            try {
                l.onStrikeStarted(dimensionId, x, y, z, radius, teamId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ---------- 玩家入口 ----------

    /**
     * 玩家发起打击：扣玩家所在队伍 ORU，受个人冷却与射程限制。
     * 物品开火（OrbitalStrikeManager.handleFireRequest）内部也走这里。
     */
    public static StrikeResult requestStrikeForPlayer(EntityPlayerMP player, int x, int y, int z) {
        if (!MainConfig.OrbitalRailgunEnable) {
            return StrikeResult.NOT_ENABLED;
        }
        World world = player.worldObj;
        if (world == null || world.isRemote) {
            return StrikeResult.WORLD_UNAVAILABLE;
        }
        if (!world.blockExists(x, y, z)) {
            return StrikeResult.INVALID_POSITION;
        }
        double dist = player.getDistance(x + 0.5, y + 0.5, z + 0.5);
        if (dist > MainConfig.OrbitalRailgunRange * 1.5) {
            return StrikeResult.INVALID_TARGET;
        }
        if (OrbitalStrikeManager.isOnCooldown(player)) {
            return StrikeResult.COOLDOWN;
        }
        if (OrbitalStrikeManager.hasActiveStrike(world.provider.dimensionId, x, y, z)) {
            return StrikeResult.STRIKE_ALREADY_ACTIVE;
        }
        UUID teamId = OrundumEnergyService.getTeamIdForUser(player.getUniqueID());
        if (teamId == null) {
            return StrikeResult.NO_TEAM;
        }
        if (!OrundumEnergyService.changeOrundumForTeam(teamId, getStrikeCost().negate())) {
            return StrikeResult.INSUFFICIENT_ORU;
        }
        OrbitalStrikeManager.applyCooldown(player);
        OrbitalStrikeManager.startStrike(world, x, y, z, (float) MainConfig.OrbitalRailgunRadius,
            player, player.getUniqueID(), teamId);
        return StrikeResult.SUCCESS;
    }

    // ---------- 队伍/机器入口 ----------

    /**
     * 机器/戴森球模块入口：按队伍 UUID 扣费，不受个人冷却与射程限制。
     */
    public static StrikeResult requestStrikeForTeam(UUID teamId, int dimensionId, int x, int y, int z) {
        if (!MainConfig.OrbitalRailgunEnable) {
            return StrikeResult.NOT_ENABLED;
        }
        if (teamId == null) {
            return StrikeResult.NO_TEAM;
        }
        WorldServer world = DimensionManager.getWorld(dimensionId);
        if (world == null) {
            return StrikeResult.WORLD_UNAVAILABLE;
        }
        if (!world.blockExists(x, y, z)) {
            return StrikeResult.INVALID_POSITION;
        }
        if (OrbitalStrikeManager.hasActiveStrike(dimensionId, x, y, z)) {
            return StrikeResult.STRIKE_ALREADY_ACTIVE;
        }
        if (!OrundumEnergyService.changeOrundumForTeam(teamId, getStrikeCost().negate())) {
            return StrikeResult.INSUFFICIENT_ORU;
        }
        OrbitalStrikeManager.startStrike(world, x, y, z, (float) MainConfig.OrbitalRailgunRadius,
            null, null, teamId);
        return StrikeResult.SUCCESS;
    }

    // ---------- 纯坐标入口 ----------

    /**
     * 纯坐标直接打击：不扣费、不校验队伍/冷却（调用方自行管理费用与频次），
     * 仅校验世界/坐标/同位置去重。适合调试命令或需要完全控制的系统。
     */
    public static StrikeResult fireStrikeAt(int dimensionId, int x, int y, int z, float radius) {
        if (!MainConfig.OrbitalRailgunEnable) {
            return StrikeResult.NOT_ENABLED;
        }
        WorldServer world = DimensionManager.getWorld(dimensionId);
        if (world == null) {
            return StrikeResult.WORLD_UNAVAILABLE;
        }
        if (!world.blockExists(x, y, z)) {
            return StrikeResult.INVALID_POSITION;
        }
        if (OrbitalStrikeManager.hasActiveStrike(dimensionId, x, y, z)) {
            return StrikeResult.STRIKE_ALREADY_ACTIVE;
        }
        OrbitalStrikeManager.startStrike(world, x, y, z, radius, null, null, null);
        return StrikeResult.SUCCESS;
    }

    // 内部：通知监听器（startStrike 成功后由 OrbitalStrikeManager 调用）
    static void fireListeners(int dimensionId, int x, int y, int z, float radius, UUID teamId) {
        notifyStrikeStarted(dimensionId, x, y, z, radius, teamId);
    }
}
