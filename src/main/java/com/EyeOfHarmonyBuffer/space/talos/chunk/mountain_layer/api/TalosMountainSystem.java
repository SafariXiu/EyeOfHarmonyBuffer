package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.integration.MountainHeightProfile;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.integration.MountainTerrainModifier;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime.MountainBelt;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime.MountainPrebuildService;
import com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime.MountainWorldState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.World;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 山地层统一入口（与河流层的 TalosRiverSystem 同构）。
 *
 * 地形生成只通过这里查询：
 *   - sampleMountain(worldX, worldZ, worldSeedInt)：连续高程 + 蒙版 + 类型；
 *   - 世界加载时启动后台预构建，卸载时停止并释放缓存。
 */
public final class TalosMountainSystem {

    private TalosMountainSystem() {}

    private static final ConcurrentHashMap<Integer, MountainWorldState> STATES =
        new ConcurrentHashMap<Integer, MountainWorldState>();

    private static final ConcurrentHashMap<Integer, MountainPrebuildService> SERVICES =
        new ConcurrentHashMap<Integer, MountainPrebuildService>();

    /** 已激活世界（生命周期幂等保护，双总线注册时避免重复启停）。 */
    private static final ConcurrentHashMap<Integer, Boolean> ACTIVE =
        new ConcurrentHashMap<Integer, Boolean>();

    /** 调试开关：-Dtalos.mountain.enabled=false 可整体关闭山地系统。 */
    public static boolean isEnabled() {
        return System.getProperty(
            "talos.mountain.enabled", "true"
        ).equalsIgnoreCase("true");
    }

    /** 一次取回连续高程 + 山带蒙版（地形修饰器需要分开使用）。 */
    public static final class MountainSample {
        public final double elevation01;
        public final double mask01;
        public final int kind;

        public MountainSample(double elevation01, double mask01, int kind) {
            this.elevation01 = elevation01;
            this.mask01 = mask01;
            this.kind = kind;
        }
    }

    /** 一次取回连续高程 + 山带蒙版 + 山带类型（地形修饰器使用）。 */
    public static MountainSample sampleMountain(int worldX, int worldZ,
                                                int worldSeedInt) {
        if (!isEnabled()) {
            return new MountainSample(0.0, 0.0, 0);
        }
        MountainWorldState state = STATES.get(worldSeedInt);
        if (state == null) {
            return new MountainSample(0.0, 0.0, 0);
        }
        MountainBelt belt = state.beltAt(worldX, worldZ);
        if (belt == null) {
            return new MountainSample(0.0, 0.0, 0);
        }
        return new MountainSample(
            belt.sampleElevation01(worldX, worldZ),
            belt.sampleMask01(worldX, worldZ),
            belt.kind
        );
    }

    /**
     * 地形链统一入口：山脉抬升（最终高度场使用）。
     * worldHeight 用于峰顶随世界高度解耦（见 MountainHeightProfile）；
     * 山地系统禁用 / 未构建时 elevation/mask 为 0，本方法等效于不抬升。
     */
    public static double applyMountainUplift(double baseHeight,
                                             int seaLevel,
                                             double elevation01,
                                             double mask01,
                                             int beltKind,
                                             int worldHeight) {
        return MountainTerrainModifier.applyMountainUplift(
            baseHeight, seaLevel, elevation01, mask01, beltKind,
            MountainHeightProfile.ofWorldHeight(worldHeight)
        );
    }

    /**
     * 带峰顶上限的抬升（V2 轨 D33）：peakCap = 峰核场目标高度 T（峰核外传 +∞）。
     * DLA 脊顶被压到 T 以下，峰核中心因此保持为最高点。
     */
    public static double applyMountainUplift(double baseHeight,
                                             int seaLevel,
                                             double elevation01,
                                             double mask01,
                                             int beltKind,
                                             int worldHeight,
                                             double peakCap) {
        return MountainTerrainModifier.applyMountainUplift(
            baseHeight, seaLevel, elevation01, mask01, beltKind,
            MountainHeightProfile.ofWorldHeight(worldHeight), peakCap
        );
    }

    /** 调试：获取某世界的山地状态。 */
    public static MountainWorldState getState(int worldSeedInt) {
        return STATES.get(worldSeedInt);
    }

    /** 调试汇总（/talmountain 用，只经 api 暴露，指令不摸 runtime）。 */
    public static java.util.List<String> debugSummary(int worldX, int worldZ,
                                                      int worldSeedInt) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<String>();
        if (!isEnabled()) {
            lines.add("[TALMOUNTAIN] 山地系统已禁用 (talos.mountain.enabled=false)");
            return lines;
        }
        MountainWorldState state = STATES.get(worldSeedInt);
        if (state == null) {
            lines.add("[TALMOUNTAIN] 山地状态不存在：WorldEvent.Load 未触发，"
                + "或当前维度不是 Talos（seed=" + worldSeedInt + "）");
            return lines;
        }

        int tier = state.debugStyleTier(worldX, worldZ);
        MountainBelt belt = state.beltAt(worldX, worldZ);
        lines.add(String.format(
            "[TALMOUNTAIN] pos=(%d,%d) seed=%d styleTier=%d",
            worldX, worldZ, worldSeedInt, tier
        ));

        if (belt != null) {
            lines.add(String.format(
                "  山带 id=%d kind=%d grid=%dx%d mask=%.3f elev=%.3f "
                    + "center=(%.0f,%.0f) len=%.0f wid=%.0f",
                belt.beltId,
                belt.kind,
                belt.gridW,
                belt.gridH,
                belt.sampleMask01(worldX, worldZ),
                belt.sampleElevation01(worldX, worldZ),
                belt.centerX,
                belt.centerZ,
                belt.halfLength * 2.0,
                belt.halfWidth * 2.0
            ));
        } else {
            lines.add("  当前位置不在任何已构建山带内");
        }

        lines.add(String.format(
            "  缓存: belts=%d indexedCells=%d styleCache=%d scannedTiles=%d",
            state.debugBeltCount(),
            state.debugIndexedCellCount(),
            state.debugStyleCacheSize(),
            state.debugScannedTileCount()
        ));

        java.util.List<MountainBelt> belts = state.debugBelts();
        if (!belts.isEmpty()) {
            lines.add("  已构建山带 " + belts.size() + " 条:");
            int shown = 0;
            for (MountainBelt b : belts) {
                if (shown >= 6) {
                    lines.add("    ...");
                    break;
                }
                lines.add(String.format(
                    "    id=%d kind=%d grid=%dx%d center=(%.0f,%.0f) len=%.0f wid=%.0f",
                    b.beltId, b.kind, b.gridW, b.gridH,
                    b.centerX, b.centerZ,
                    b.halfLength * 2.0, b.halfWidth * 2.0
                ));
                shown++;
            }
        } else {
            lines.add("  尚未构建任何山带（后台预构建可能在工作中）");
        }
        return lines;
    }

    /**
     * 群系归属跟山带走：山带核心（mask ≥ 0.5）内返回对应山地群系，
     * 带外返回 null（沿用原群系链）。
     * PEAK -> Alpine，MOUNTAINS/HIGHLAND -> Mountains。
     */
    public static BiomeGenBase getMountainBiomeOverride(int worldX, int worldZ,
                                                        int worldSeedInt) {
        if (!isEnabled()) {
            return null;
        }
        MountainWorldState state = STATES.get(worldSeedInt);
        if (state == null) {
            return null;
        }
        MountainBelt belt = state.beltAt(worldX, worldZ);
        // 阈值 0.65：群系切换点更靠近山带核心，山脚保留一圈原群系，
        // 避免"材质/雪线"与地形过渡错位造成硬切。
        if (belt == null || belt.sampleMask01(worldX, worldZ) < 0.65) {
            return null;
        }
        if (belt.kind >= 2) {
            return TalosBiomes.TALOS_ALPINE;
        }
        return TalosBiomes.TALOS_MOUNTAINS;
    }

    /** 世界加载：创建状态并启动后台预构建。 */
    public static void onWorldLoad(World world) {
        if (!isEnabled()) {
            return;
        }
        int seed = TalosLandMask.getWorldSeedInt(world);
        if (ACTIVE.putIfAbsent(seed, Boolean.TRUE) != null) {
            return;
        }

        MountainWorldState state = STATES.get(seed);
        if (state == null) {
            MountainWorldState created = new MountainWorldState(seed);
            MountainWorldState prev = STATES.putIfAbsent(seed, created);
            state = prev != null ? prev : created;
        }

        MountainPrebuildService svc = new MountainPrebuildService(
            state,
            world.getSpawnPoint().posX,
            world.getSpawnPoint().posZ
        );
        MountainPrebuildService old = SERVICES.put(seed, svc);
        if (old != null) {
            old.stop();
        }
        // 出生点附近同步预构建：出生区块生成前先把山带索引建好，
        // 避免主线程在"下载地形"阶段做整条带发现。
        state.prebuildAroundSpawn(
            (int) world.getSpawnPoint().posX,
            (int) world.getSpawnPoint().posZ
        );
        svc.start();
    }

    /** 世界卸载：停止后台线程并释放缓存（下次进入重新计算）。 */
    public static void onWorldUnload(World world) {
        int seed = TalosLandMask.getWorldSeedInt(world);
        ACTIVE.remove(seed);
        MountainPrebuildService svc = SERVICES.remove(seed);
        if (svc != null) {
            svc.stop();
        }
        STATES.remove(seed);
    }

    /** 世界 tick：预构建中心跟随最近玩家（无玩家时保持出生点）。 */
    public static void onWorldTick(World world) {
        if (!isEnabled()) {
            return;
        }
        int seed = TalosLandMask.getWorldSeedInt(world);
        MountainPrebuildService svc = SERVICES.get(seed);
        if (svc == null) {
            return;
        }

        // 跟随任意在线玩家（列表通常 1~2 人，够用）；无人时保持出生点
        for (Object obj : world.playerEntities) {
            if (obj instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) obj;
                svc.updateCenter(p.posX, p.posZ);
                break;
            }
        }
    }
}
