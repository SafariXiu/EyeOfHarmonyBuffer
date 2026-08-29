package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import java.math.BigInteger;

import net.minecraft.world.World;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;
import com.EyeOfHarmonyBuffer.space.talos.station.WorldProviderTalos2Station;

/**
 * 戴森球机器系统的集中常量。
 * <p>
 * 数值已全部定稿并硬编码为 final 常量，刻意不开放配置文件，防止玩家修改破坏平衡。
 */
public final class DysonMachineConfig {

    private DysonMachineConfig() {}

    /** 每框架容纳的贴片数。 */
    public static final int PASTE_PER_FRAME = 4;

    /** 每贴片消耗的云数。 */
    public static final int CLOUDS_PER_PASTE = 128;

    /** 每日掉落范围（含上下限）。 */
    public static final int DAILY_DROP_MIN = 10;
    public static final int DAILY_DROP_MAX = 64;

    /** 点亮“掉落减免”节点后的每日掉落范围。 */
    public static final int DAILY_DROP_MIN_REDUCED = 8;
    public static final int DAILY_DROP_MAX_REDUCED = 48;

    /** 发射模块：每个组件的发射成本（Orundum）。 */
    public static final long launchCostOrundum = 10_000L;

    /** 发射模块：基础单轮批量（未升级 16）。 */
    public static final int launchBatch = 16;

    /** 发射模块：基础无线周期（10 秒 = 200 tick）。 */
    public static final int launchTimeTicks = 200;

    /** 全量发射模块：固定一轮 60 秒（1200 tick）。 */
    public static final int massLaunchTimeTicks = 1200;

    /** 制造模块：基础无线周期（30 秒 = 600 tick）与基础并行（64）。 */
    public static final int manufacturingTimeTicks = 600;
    public static final int manufacturingMaxParallel = 64;

    /** 制造效率 I/II/III 对应的一轮时间（tick）。 */
    public static final int manufacturingEfficiencyTicksI = 300;
    public static final int manufacturingEfficiencyTicksII = 200;
    public static final int manufacturingEfficiencyTicksIII = 100;

    /** 制造并行 II/III 对应的基础并行。 */
    public static final int manufacturingParallelII = 128;
    public static final int manufacturingParallelIII = 512;

    /** 制造并行 III 每轮额外产出的概率与倍率范围（100~200 × 配方单份产出）。 */
    public static final float manufacturingExtraChance = 0.20F;
    public static final int manufacturingExtraMin = 100;
    public static final int manufacturingExtraMax = 200;

    /** 组件制造 Orundum 成本基准（每 tick）：云组件 10 亿，框架组件 50 亿。 */
    public static final BigInteger CLOUD_COMPONENT_ORUNDUM_PER_TICK = BigInteger.valueOf(1_000_000_000L);
    public static final BigInteger FRAME_COMPONENT_ORUNDUM_PER_TICK = BigInteger.valueOf(5_000_000_000L);

    /** 配方单份产出：64 源石 → 64 云组件 / 64 息壤 → 512 框架组件。 */
    public static final int CLOUD_RECIPE_OUTPUT = 64;
    public static final int FRAME_RECIPE_OUTPUT = 512;

    /** 奇异物质产出：每个核心无线周期（1 秒）产出 = 本队云 / cloudDivisor + 本队贴片 / pasteDivisor。 */
    public static final long strangeMatterCloudDivisor = 10_000L;
    public static final long strangeMatterPasteDivisor = 200_000L;

    /** 发射效率 I/II 对应的一轮时间（tick）。 */
    public static final int launchEfficiencyTicksI = 100;
    public static final int launchEfficiencyTicksII = 40;

    /** 发射批量 I/II 对应的单轮批量。 */
    public static final int launchBatchI = 64;
    public static final int launchBatchII = 128;

    /** 算力需求（核心 / 制造模块 / 发射模块）。 */
    public static final int coreCompute = 1_000_000;
    public static final int manufacturingCompute = 10_000;
    public static final int launchCompute = 100_000;

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

    /** 每台核心最多可链接的模块数（与激活曲线完工后的 32 槽一致）。 */
    public static final int MAX_LINKED_MODULES = 32;

    /** 戴森核心与模块只能在塔罗斯 2 维度运行。 */
    public static final int REQUIRED_DIMENSION = RegisterDimensions.ID_TALOS2_DIM;

    /** 塔罗斯-2 空间站维度（固定 ID，戴森模块特殊判定用）。 */
    public static final int STATION_DIMENSION = RegisterDimensions.ID_TALOS2_STATION_DIM;

    /** 接收模块在塔罗斯-2 地面时的功率倍率（空间站为全功率 1.0，地面 60%）。 */
    public static final double RECEIVER_POWER_MULTIPLIER_ON_SURFACE = 0.6;

    public static boolean isInTalos(World world) {
        return world != null && world.provider.dimensionId == REQUIRED_DIMENSION;
    }

    /**
     * 是否位于塔罗斯-1 空间站。
     *
     * <p>注意：GC 创建空间站时实际维度为动态分配（DimensionManager.getNextFreeDimId()，
     * 如 -1000 之类），-52（STATION_DIMENSION）只是 provider 注册 ID，玩家所在空间的
     * dimensionId 并不等于 -52，因此必须按 provider 类型判定；维度号检查仅作兜底。
     */
    public static boolean isInTalosStation(World world) {
        return world != null
            && (world.provider instanceof WorldProviderTalos2Station
                || world.provider.dimensionId == STATION_DIMENSION);
    }

    /** 戴森机器允许运行的维度：塔罗斯-2 或塔罗斯-2 空间站。 */
    public static boolean isInTalosOrStation(World world) {
        return isInTalos(world) || isInTalosStation(world);
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
