package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

/**
 * M2 气压诊断场（风场 v2 第二模块；M5 离线松弛的初值来源）。
 *
 * 把"带+点系统"的旧模型替换成**场模型**——气压异常是若干平滑场的叠加：
 *
 *   p(x,z) = p0(b)              动力剖面（高斯带合成，无硬切边界）：
 *                                赤道槽(0) / 副热带高压(~30°) / 副极地低压(~60°) / 极地高压
 *                               —— 哈德利下沉(副高)与极锋(副极低压)的"气候平均"表达
 *          + wave               纬向摆动（罗斯贝式蜿蜒，中纬最强、赤道/极地消失）
 *          + thermal            −γ·(Teq − zonalMeanSeaTeq)：暖池→低压、热大陆夏→热低压、
 *                               冷舌/冷陆→高压（吃 M1；含高原的 Teq 冷却）
 *
 * 风（地表）：
 *   v_geo = 地转（-∇p 旋转 90°，科里奥利随纬向符号）；赤道 f→0 处加阻尼；
 *   v_surf = 地转方向向低压侧旋转 α（摩擦：陆 ~25°）并减速 —— 摩擦辐合自动产生
 *   辐散辐合场，供 M3 降水/干湿诊断。
 *
 * 单位：p 为归一化异常（正=高压），wind 输出沿用全项目"幅 ~1"的约定。
 * 纯函数 O(1)：每点 p ×5（中心+4 邻域差分）。
 */
public final class PressureField {

    private PressureField() {}

    /** 地转速度标尺（把 p 梯度换算到 ~1 的风速幅；出图后标定）。 */
    private static final double GEO_SCALE = 14_000.0;
    /** 摩擦旋转角（弧度 ≈ 25°）：地表风偏向低压侧。 */
    private static final double FRICTION_ANGLE = 0.42;
    /** 摩擦减速系数。 */
    private static final double FRICTION_SPEED = 0.88;
    /** 赤道阻尼：|f| 低于此量级风强被压低（地转在赤道失效）。 */
    private static final double EQUATOR_DAMP = 0.30;
    /** 有限差分步长（block）。 */
    private static final int GRAD_STEP = 3000;

    // ---- 动力剖面 p0(b)（b=bandD∈[0,1]：0=赤道 1=极地）高斯带 ----
    private static double g(double b, double center, double sigma) {
        double d = (b - center) / sigma;
        return Math.exp(-0.5 * d * d);
    }

    /** 纬向平均动力剖面（相对异常）：赤道槽 / 副高 / 副极低压 / 极地高压。 */
    static double profileP0(double b) {
        return -0.95 * g(b, 0.00, 0.17)
             + 1.00 * g(b, 0.30, 0.085)
             - 0.80 * g(b, 0.62, 0.075)
             + 0.55 * g(b, 0.95, 0.09);
    }

    // ---- 纬向摆动（罗斯贝蜿蜒；纬度包络 sin(πb) 在中纬最强） ----
    private static final double WAVE_AMP = 0.55;
    private static final double WAVE_WAV = 90_000.0;
    private static final long WAVE_SALT = 0xABC_1234L;

    private static double waveTerm(int x, int z, int worldSeedInt, double b) {
        double n = NoiseContinentGrid.bandNoise(x, z, worldSeedInt, WAVE_SALT, 1.0 / WAVE_WAV, 2);
        double envelope = Math.sin(Math.PI * b);
        return (n * 2.0 - 1.0) * WAVE_AMP * envelope;
    }

    // ---- 热力项：暖→低压（暖池/夏季热大陆），冷→高压（冷舌/冷陆/高原 Teq） ----
    private static final double THERMAL_K = 1.5;

    private static double thermalTerm(int x, int z, int worldSeedInt, double b) {
        double teq = ThermalForcing.teq(x, z, worldSeedInt);
        double anomaly = teq - ThermalForcing.zonalMeanSeaTeq(b);
        return -THERMAL_K * anomaly;
    }

    /** 气压异常（正=高压）。供 pressure 出图 / M3 干湿诊断。 */
    public static double pressure01(int x, int z, int worldSeedInt) {
        double b = GlobalCirculation.bandD(z);
        double p = profileP0(b)
                 + waveTerm(x, z, worldSeedInt, b)
                 + thermalTerm(x, z, worldSeedInt, b);
        return p < -2.0 ? -2.0 : (p > 2.0 ? 2.0 : p);
    }

    /** 有符号纬向（bandD 是到赤道的距离，这里还原半球符号；±90°=极点）。 */
    private static double signedLatitude(int worldZ) {
        int zMod = GlobalCirculation.foldZ(worldZ);
        double d = GlobalCirculation.bandD(worldZ);
        double latFrac = (zMod <= ClimateLatitudes.MAX_D) ? d : -d;   // +北 -南
        return latFrac * (Math.PI / 2.0);                              // 弧度
    }

    /** 地表风 [windX, windZ]（地转 + 摩擦偏向低压）。 */
    public static double[] windDir(int x, int z, int worldSeedInt) {
        int s = GRAD_STEP;
        double pxp = pressure01(x + s, z, worldSeedInt);
        double pxm = pressure01(x - s, z, worldSeedInt);
        double pzp = pressure01(x, z + s, worldSeedInt);
        double pzm = pressure01(x, z - s, worldSeedInt);
        double dpdx = (pxp - pxm) / (2.0 * s);
        double dpdz = (pzp - pzm) / (2.0 * s);
        double gradLen = Math.sqrt(dpdx * dpdx + dpdz * dpdz);
        if (gradLen < 1.0e-9) {
            return new double[] { 0.0, 0.0 };
        }
        double lat = signedLatitude(z);
        double f = Math.sin(lat);
        double fAbs = Math.abs(f);
        if (fAbs < 1.0e-4) {
            return new double[] { 0.0, 0.0 };   // 赤道无风带（doldrums）：f=0 地转失效
        }
        // 赤道阻尼：f→0 时地转失效，风强压平
        double damp = fAbs / (fAbs + EQUATOR_DAMP);
        // 地转：u = -(1/f)∂p/∂z、v = +(1/f)∂p/∂x（f 带符号 → 南北半球自动镜像）
        double u = -(dpdz / f) * GEO_SCALE * damp;
        double v = (dpdx / f) * GEO_SCALE * damp;
        double speed = Math.sqrt(u * u + v * v);
        if (speed < 1.0e-9) {
            return new double[] { 0.0, 0.0 };
        }
        // 摩擦：向低压侧（-∇p 方向）旋转 FRICTION_ANGLE 并减速
        double ix = -dpdx / gradLen;
        double iz = -dpdz / gradLen;
        double gx = u / speed, gz = v / speed;
        double ca = Math.cos(FRICTION_ANGLE), sa = Math.sin(FRICTION_ANGLE);
        // (gx,gz) 与 (ix,iz) 正交（地转 ⟂ 梯度），旋转 α 向低压侧
        double sx = gx * ca + ix * sa;
        double sz = gz * ca + iz * sa;
        double outSpeed = speed * FRICTION_SPEED;
        if (outSpeed > 0) {
            double sxLen = Math.sqrt(sx * sx + sz * sz);
            if (sxLen > 1.0e-9) {
                sx /= sxLen;
                sz /= sxLen;
            }
        }
        return new double[] { sx * outSpeed, sz * outSpeed };
    }

    /** 中心点平均气压（供辐散/辐合或降水诊断复用，M3）。 */
    public static double pressureCenter(int x, int z, int worldSeedInt) {
        return pressure01(x, z, worldSeedInt);
    }
}
