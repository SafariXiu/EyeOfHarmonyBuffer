package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import java.math.BigInteger;

import net.minecraft.world.World;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;

/**
 * 戴森球机器系统的集中常量与配置（当前为占位值，后续接入 MainConfig）。
 */
public final class DysonMachineConfig {

    private DysonMachineConfig() {}

    /** 发射模块：每个组件的发射成本（Orundum）。 */
    public static long launchCostOrundum = 10_000L;

    /** 发射模块：基础单轮批量（未升级 16）。 */
    public static int launchBatch = 16;

    /** 发射模块：基础无线周期（10 秒 = 200 tick）。 */
    public static int launchTimeTicks = 200;

    /** 全量发射模块：固定一轮 60 秒（1200 tick）。 */
    public static int massLaunchTimeTicks = 1200;

    /** 制造模块：基础无线周期（30 秒 = 600 tick）与基础并行（64）。 */
    public static int manufacturingTimeTicks = 600;
    public static int manufacturingMaxParallel = 64;

    /** 制造效率 I/II/III 对应的一轮时间（tick）。 */
    public static int manufacturingEfficiencyTicksI = 300;
    public static int manufacturingEfficiencyTicksII = 200;
    public static int manufacturingEfficiencyTicksIII = 100;

    /** 制造并行 II/III 对应的基础并行。 */
    public static int manufacturingParallelII = 128;
    public static int manufacturingParallelIII = 512;

    /** 制造并行 III 每轮额外产出的概率与倍率范围（100~200 × 配方单份产出）。 */
    public static float manufacturingExtraChance = 0.20F;
    public static int manufacturingExtraMin = 100;
    public static int manufacturingExtraMax = 200;

    /** 组件制造 Orundum 成本基准（每 tick）：云组件 10 亿，框架组件 50 亿。 */
    public static final BigInteger CLOUD_COMPONENT_ORUNDUM_PER_TICK = BigInteger.valueOf(1_000_000_000L);
    public static final BigInteger FRAME_COMPONENT_ORUNDUM_PER_TICK = BigInteger.valueOf(5_000_000_000L);

    /** 配方单份产出：64 源石 → 64 云组件 / 64 息壤 → 512 框架组件。 */
    public static final int CLOUD_RECIPE_OUTPUT = 64;
    public static final int FRAME_RECIPE_OUTPUT = 512;

    /** 发射效率 I/II 对应的一轮时间（tick）。 */
    public static int launchEfficiencyTicksI = 100;
    public static int launchEfficiencyTicksII = 40;

    /** 发射批量 I/II 对应的单轮批量。 */
    public static int launchBatchI = 64;
    public static int launchBatchII = 128;

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
