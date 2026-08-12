package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import java.math.BigInteger;

import net.minecraft.world.World;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;

/**
 * 戴森球机器系统的集中常量与配置（当前为占位值，后续接入 MainConfig）。
 */
public final class DysonMachineConfig {

    private DysonMachineConfig() {}

    /** 发射模块：每个组件的发射成本（EU 等价）。 */
    public static long launchCostEU = 10_000L;

    /** 发射模块：每个无线周期处理的组件数。 */
    public static int launchBatch = 64;

    /** 制造模块：默认无线周期（tick）与最大并行（占位）。 */
    public static int manufacturingTimeTicks = 100;
    public static int manufacturingMaxParallel = 16;

    /** 算力需求（核心 / 制造模块 / 发射模块）。 */
    public static int coreCompute = 1_000_000;
    public static int manufacturingCompute = 10_000;
    public static int launchCompute = 100_000;

    /** 1 云功率 = 1024 × MAX（EU/t）。 */
    public static final BigInteger CLOUD_POWER = BigInteger.valueOf(2_147_483_647L)
        .multiply(BigInteger.valueOf(1024L));

    /** 1 贴片功率 = 128 × 1024 × MAX² = 2^79（EU/t）。 */
    public static final BigInteger PASTE_POWER = BigInteger.valueOf(2_147_483_647L)
        .multiply(BigInteger.valueOf(2_147_483_647L))
        .multiply(BigInteger.valueOf(1024L))
        .multiply(BigInteger.valueOf(128L));

    /** 完工球壳功率 = 10^200（EU/t）。 */
    public static final BigInteger COMPLETED_POWER = BigInteger.TEN.pow(200);

    /** 接收模块每 1 秒（20 tick）批量结算一次。 */
    public static final int TICKS_PER_SETTLEMENT = 20;

    /** 核心心跳窗口：超过该 tick 数未收到核心刷新，模块自动停机。 */
    public static final int CORE_HEARTBEAT_TICKS = 40;

    /** 戴森核心与模块只能在塔罗斯 2 维度运行。 */
    public static final int REQUIRED_DIMENSION = RegisterDimensions.ID_TALOS2_DIM;

    public static boolean isInTalos(World world) {
        return world != null && world.provider.dimensionId == REQUIRED_DIMENSION;
    }

    /** 模块槽位激活曲线：0/50万/100万/150万贴片 → 8/12/16/20 槽，完工 → 32 槽。 */
    public static int activeSlotsForPaste(int pasteCount) {
        if (pasteCount >= 2_000_000) {
            return 32;
        }
        if (pasteCount >= 1_500_000) {
            return 20;
        }
        if (pasteCount >= 1_000_000) {
            return 16;
        }
        if (pasteCount >= 500_000) {
            return 12;
        }
        return 8;
    }
}
