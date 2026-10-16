package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

/**
 * 群系气候坐标（L1c 的输入层，B1）：把环流层（M1–M6）的物理量收口成
 * **两个有物理含义的坐标 + 内陆度**，供 V2BiomeSelect 做气候带分类。
 *
 *   tempEff  = 纬度带 + 气团温度 + 气团签名 − 海拔直减
 *   moistEff = 空气湿度 + 海洋性 + 动力干湿 + 迎风抬升雨 − 背风焚风
 *              + 寒/暖流海岸 + 气团签名 − 大陆性干燥
 *   continent= 内陆度（海岸距离归一）
 *
 * 与 GlobalClimate 的 P1b **共用同一份算子常量**（{@link GlobalClimate#UPLIFT_SCALE} /
 * {@link GlobalClimate#ELEV_STEP}），因此群系与 /talosmap、降水场不会口径漂移 ——
 * 这不是靠注释声称一致，而是**同一份静态常量**（P220 的源码扫描器盯着这里不许再出现字面量）。
 * **不读最终高度** → 无循环依赖。
 *
 * 第 3 段（2026-09-18 顶死一套）之后，本类只剩「一个分派 + 一个 Coords 结构」：
 *   气候的唯一实现是 {@link com.EyeOfHarmonyBuffer.sim.runtime.SimClimate}。旧栈实现已删除。
 */
public final class ClimateCoords {

    private ClimateCoords() {}

    // ===== 开关：已随第 3 段（顶死一套）退役 =====
    //   ENABLE_ORO / ENABLE_SST / ENABLE_AIRMASS 只被已删除的旧栈实现使用；
    //   新气候链（SimClimate）有自己的一套，所以这三个字段没有任何读者。
    //   ⚠ 不要再把它们加回来：同一件事两份开关正是口径漂移的温床。

    // ===== 系数（探针可扫参） =====
    /** 温度 = W_LAT·纬度带 + (1−W_LAT)·气团温度。 */
    public static double W_LAT = 0.70;

    // ★ §537（P2-12）退役：以下 8 个常数【全工程零读者】，已删除。
    //   实测（2026-09-19）：src/main/java 与 tools/talos-probe/probe 下，
    //     每个名字除声明行外【零命中】。
    //     LAPSE / ORO_UP_GAIN / ORO_LEE_GAIN / MOIST_OFFSET /
    //     SST_GAIN / SST_OFFSET / INLAND_SCALE / INLAND_DRY
    //   ⚠ 它们与上面 :28-31 那块墓碑【同类】：都是旧栈退役后留下的、没有任何读者的字段。
    //     上一轮墓碑只清了三个开关、漏了这 8 个系数 —— 现在一起清掉。
    //   ⚠ 不要再加回来：同一件事两份系数/开关正是口径漂移的温床。

    /** 气团签名：Δ温度 / Δ湿度（0=mT 1=cT 2=mP 3=cP）。 */
    public static double[] AIR_DT = {0.05, 0.08, -0.05, -0.08};
    public static double[] AIR_DQ = {0.08, -0.06, 0.05, -0.05};



    /** 一次采样的结果。 */
    public static final class Coords {
        public double temp, moist, continent;
        // ---- 诊断 / 出图 ----
        public double windX, windZ;
        public double up, lee, sstAnom;
        public double dry, q, mar, airT, bandD;
        /** 0=mT 1=cT 2=mP 3=cP。 */
        public int airMass;
        public boolean onshore;
    }

    /** 单点采样（陆地；海上只需 temp/moist 的话也可调用）。 */
    // ================= ★★★★★★★★ 采样记忆化 =================
    //
    // 【为什么必须有】
    //   sample() -> SimClimate.sample() = 完整气候链（PrecipField/Atmosphere/OceanField/kappa）
    //   V2BiomeField.solve 对【每个格点】调 accumulateWeights 【两次】
    //     （一次 asLand=true，一次 asLand=false），而两次都传【同一个 oro】
    //     ⟹ 完全重复的气候求值。
    //   实测：BiomeField = 22~38 秒（80,000 点 × 2 次 = 160,000 次气候求值）。
    //
    // 【为什么可以记忆化】
    //   入参 (x, z, worldSeedInt, oro) 中 oro 只用于传递 isLand/elevation 等，
    //   而同一 (x,z,seed) 的 oro 必然相同 ⟹ 键 (x, z, seed) 足够。
    //   ⚠ 返回的是【共享对象】⟹ 调用方不得修改（现有调用方只读）。
    private static final ThreadLocal<java.util.HashMap<Long, Coords>> SAMPLE_MEMO =
        ThreadLocal.withInitial(java.util.HashMap::new);
    public static boolean SAMPLE_MEMO_ON = true;
    public static void clearSampleMemo() { SAMPLE_MEMO.get().clear(); }

    public static Coords sample(int x, int z, int worldSeedInt, OrographyField.OroSample oro) {
        if (!SAMPLE_MEMO_ON) return sampleRaw(x, z, worldSeedInt, oro);
        java.util.HashMap<Long, Coords> m = SAMPLE_MEMO.get();
        long key = ((((long) x) << 32) ^ (z & 0xFFFFFFFFL)) * 31L + worldSeedInt;
        Coords hit = m.get(key);
        if (hit != null) return hit;
        Coords r = sampleRaw(x, z, worldSeedInt, oro);
        if (m.size() > 262144) m.clear();
        m.put(key, r);
        return r; }

    private static Coords sampleRaw(int x, int z, int worldSeedInt, OrographyField.OroSample oro) {
        // ---- 新模拟器的运行时分派（纵向切片第 2 步：气候 -> 群系，§97）----------------
        // 这是本文件里**唯一**为接线而加的东西：一行分派。SimClimate.ENABLED=false 时，
        // 下面每一行都与接线前**逐位相同**（实测校验和 17ead228bdf6f150 两侧一致）；
        // 旧实现一个方法、一个常量都没有改动或删除。
        // ★★ 2026-09-18 顶死一套（第 3 段）★★
        //   原来这里有一个 `if (SimClimate.ENABLED)` 分派 + 一整套旧栈实现（RelaxedClimate /
        //   GlobalCirculation / ThermalForcing，LAT_CYCLE = 1M、赤道→极点 500 km）。
        //   `ENABLED` 默认就是 true ⇒ 旧实现**一行都不走**，是不可达代码；
        //   留着它只会再长出口径分歧（D17 雪线 20 倍错摆、D72 二十条等距冰带都是这么来的）。
        //   ⇒ 旧实现已删除，本方法**只剩这一条路**。
        //   回退开关（SimClimate.ENABLED）一并退役：新气候链已被 D1 / 口径统一 /
        //   地球掩膜验证这一整套依赖，回退已无实际意义。
        return com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.sample(x, z, worldSeedInt, oro);
    }

    // sstExpectation(bandD) 已于第 3 段（顶死一套）删除：它只被已删除的旧栈实现使用，
    // 而且是本文件对旧栈 ThermalForcing 的**最后一个依赖**。新气候链用它自己的一致性口径。

    private static double elev01(int x, int z, int worldSeedInt) {
        return OrographyField.elevation01(NoiseContinentGrid.landResidual(x, z, worldSeedInt), worldSeedInt);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
