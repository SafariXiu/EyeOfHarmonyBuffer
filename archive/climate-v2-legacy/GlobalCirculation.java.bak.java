package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath;

/**
 * L0 全球环流 · 半固定气压系统（天气系统）驱动的风场（S2 阶段）。
 *
 * 真实风场不是平行纬向带，而是由**少数巨大、沿纬度带分布、带类型标签**的
 * 半固定气压系统（大气行动中心）驱动。本模型：
 *
 *   每年（Z 周期 200k）沿纬度带放置若干系统：
 *     - ITCZ 辐合带（赤道，低压，极湿，随机 0~1 段）
 *     - 副热带高压 × 随机数量（副热带带，顺时针辐散，干）
 *     - 副极地低压 × 随机数量（副极地带，逆时针辐合，湿）
 *     - 极地高压 × 随机数量（寒带，顺时针辐散，干）
 *   数量随机、X 位置哈希、不强制南北对称（冷涡可更强更多、热涡更少）。
 *
 *   风 = 三圈环流纬向基底（主导，权重 1.0）+ 15° 斜向（信风朝赤道 / 西风朝极地 /
 *        极地东风朝副极地）+ 气压系统弱扰动（切向 0.18 / 径向 0.08）；
 *   干湿 = 以系统为主（高压系统大干区、低压大湿区、ITCZ 湿带），少量纬度基准混合；
 *   CirculationSample.pressureSystem 输出当前点主导系统类型（供 /talosmap 标注标签）。
 *
 * 环面几何：X 周期 400k，Z 纬度循环 200k（热带中线 0/±200k，寒带 ±100k）。
 * 查询点先折叠进主域再采样；系统影响距离一律按周期最小镜像回卷（wrapDelta），
 * 保证接缝两侧连续——例如 ITCZ 只放在 z≈0，通过回卷同时正确覆盖南赤道 z≈200k 一侧。大陆影响留 S3。
 */
public final class GlobalCirculation {

    /** X 方向周期（blocks）。 */
    public static final int X_CYCLE = 400_000;
    /** Z 方向纬度循环（blocks）。 */
    public static final int Z_CYCLE = ClimateLatitudes.LAT_CYCLE;

    /** 系统半径范围（风场转向用；体量大、影响远）。 */
    private static final double W_R_MIN = 60_000.0;
    private static final double W_R_MAX = 110_000.0;

    /**
     * 干湿带核半宽（block）。干湿权重用锐化四次核：1/(1+(d/R)^4)。
     * 旧 Lorentz 核 r2/(d2+r2) 的系统半径(60~110k)远大于带间距(ITCZ~副高 26k)，
     * 相邻系统全部饱和 → 干湿被平均成 ≈0.5，沙漠带信号(D19)出不来。
     * R 取带间距一半量级：赤道湿/副热干/副极湿/极干各自成带，且场仍连续。
     */
    private static final double DRY_KERNEL_R = 12_000.0;
    private static final double DRY_KERNEL_R4 = DRY_KERNEL_R * DRY_KERNEL_R * DRY_KERNEL_R * DRY_KERNEL_R;

    private GlobalCirculation() {}

    public static int foldX(int x) {
        int m = x % X_CYCLE;
        return m < 0 ? m + X_CYCLE : m;
    }

    public static int foldZ(int z) {
        int m = z % Z_CYCLE;
        return m < 0 ? m + Z_CYCLE : m;
    }

    /** 纬度带 0=赤道 1=极地。 */
    public static double bandD(int worldZ) {
        int d = ClimateLatitudes.getDistanceToCenter(worldZ);
        return d / (double) ClimateLatitudes.MAX_D;
    }

    /** 南北侧符号（科里奥利镜像）。 */
    private static double hemisphereSign(int worldZ) {
        int zMod = foldZ(worldZ);
        return zMod <= ClimateLatitudes.MAX_D ? 1.0 : -1.0;
    }

    private static double[] foldPoint(double wx, double wz) {
        double fx = wx % X_CYCLE; if (fx < 0) fx += X_CYCLE;
        double fz = wz % Z_CYCLE; if (fz < 0) fz += Z_CYCLE;
        return new double[] { fx, fz };
    }

    /** 环面最小镜像差：把 d 折叠到 [-cycle/2, cycle/2)，作为环面上的真实距离。 */
    private static double wrapDelta(double d, int cycle) {
        double dd = d % cycle;
        if (dd > cycle * 0.5) dd -= cycle;
        else if (dd < -cycle * 0.5) dd += cycle;
        return dd;
    }

    /**
     * 沿一条纬度带放置的系统列表（一维）：每条带一个 seed，返回若干 [cx, cy, rad, type]。
     * 数量随机（1~3 个），X 位置哈希，不强制对称。
     */
    private static java.util.ArrayList<double[]> bandSystems(int worldSeedInt, double bandY, PressureSystemType type) {
        long seed = TectonicMath.hashLongs(worldSeedInt & 0xFFFFFFFFL, Double.doubleToLongBits(bandY), 0x55L);
        int count = 1 + (int) Math.floor(TectonicMath.randUnitDouble(seed ^ 0x1L) * 3.0);  // 1~3
        java.util.ArrayList<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            long s = TectonicMath.hashLongs(seed, i ^ 0x2L);
            double cx = TectonicMath.randRange(TectonicMath.hashLongs(s, 0x61L), 15_000.0, X_CYCLE - 15_000.0);
            double rad = TectonicMath.randRange(TectonicMath.hashLongs(s, 0x62L), W_R_MIN, W_R_MAX);
            double cy = bandY + TectonicMath.randRange(TectonicMath.hashLongs(s, 0x63L), -6_000.0, 6_000.0);
            int cyf = foldZ((int) Math.round(cy));
            out.add(new double[] { cx, cyf, rad, type.ordinal() });
        }
        return out;
    }

    /** 获取该点附近所有系统 [cx, cy, rad, typeOrdinal]（覆盖所有纬度带）。 */
    private static java.util.ArrayList<double[]> systemsNear(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        double fx = p[0], fz = p[1];
        java.util.ArrayList<double[]> all = new java.util.ArrayList<>();
        double[] bandYs = { 0.0, 26_000.0, 74_000.0, 90_000.0 };
        PressureSystemType[] types = {
            PressureSystemType.ITCZ, PressureSystemType.SUBTROPICAL_HIGH,
            PressureSystemType.SUBPOLAR_LOW, PressureSystemType.POLAR_HIGH
        };
        for (int bi = 0; bi < bandYs.length; bi++) {
            double bandY = bandYs[bi];
            // ITCZ（bandY=0）是赤道辐合带：只放一次；配合 wrapDelta 最小镜像距离，
            // 它对 z≈0 与 z≈200k（同一条赤道线）两侧的作用是一致的
            if (bi == 0) {
                all.addAll(bandSystems(worldSeedInt, 0.0, types[bi]));
                continue;
            }
            // 其余带：南北两个半带（bandY 与 Z_CYCLE-bandY）
            all.addAll(bandSystems(worldSeedInt, bandY, types[bi]));
            all.addAll(bandSystems(worldSeedInt, Z_CYCLE - bandY, types[bi]));
        }
        return all;
    }

    /** 纬向基底 + 15° 斜向（抽出，供 windDir/sample 共用）。返回 {zonalX, zSlope}。 */
    private static double[] zonalBaseSlope(double b, double sign) {
        double zonalX;
        if (b < 0.32) {
            zonalX = -1.0;
        } else if (b < 0.84) {
            zonalX = 1.0;
        } else {
            zonalX = -1.0;
        }
        double slope = 0.27;
        double zSlope;
        if (b < 0.32) {
            zSlope = -sign * slope * (1.0 - b / 0.32);
        } else if (b < 0.84) {
            zSlope =  sign * slope * (1.0 - Math.abs(b - 0.58) / 0.26);
        } else {
            zSlope = -sign * slope * (1.0 - (b - 0.84) / 0.16);
        }
        return new double[] { zonalX, zSlope };
    }

    /** 系统弱扰动（切向 0.18 / 径向 0.08）叠加到基底上。 */
    private static double[] systemWind(double fx, double fz, double sign,
                                       double vx0, double vz0,
                                       java.util.ArrayList<double[]> systems) {
        double vx = vx0, vz = vz0;
        for (double[] c : systems) {
            double dx = wrapDelta(fx - c[0], X_CYCLE), dz = wrapDelta(fz - c[1], Z_CYCLE);
            double d2 = dx * dx + dz * dz;
            double r2 = c[2] * c[2];
            if (d2 > r2 * 4.0) continue;
            double w = r2 / (d2 + r2);
            double d = Math.sqrt(d2) < 1.0 ? 1.0 : Math.sqrt(d2);
            PressureSystemType type = PressureSystemType.values()[(int) c[3]];
            double spin = type.sign;
            double tx = spin * sign * dz;
            double tz = spin * sign * (-dx);
            double tl = Math.sqrt(tx * tx + tz * tz);
            if (tl > 1.0e-9) {
                double mag = w * 0.18;
                vx += (tx / tl) * mag;
                vz += (tz / tl) * mag;
            }
            double rmag = w * 0.08 * spin;
            vx += (dx / d) * rmag;
            vz += (dz / d) * rmag;
        }
        return new double[] { vx, vz };
    }

    /** 干湿核心（四次核锐化；纬度基准混 10%）。 */
    private static double pressureDryCore(double fx, double fz, double b,
                                          java.util.ArrayList<double[]> systems) {
        double acc = 0.0, wsum = 0.0;
        for (double[] c : systems) {
            double dx = wrapDelta(fx - c[0], X_CYCLE), dz = wrapDelta(fz - c[1], Z_CYCLE);
            double d2 = dx * dx + dz * dz;
            double w = 1.0 / (1.0 + d2 * d2 / DRY_KERNEL_R4);
            PressureSystemType type = PressureSystemType.values()[(int) c[3]];
            acc += type.baseDry * w;
            wsum += w;
        }
        double latBase = (b < 0.2) ? 0.1 : (b < 0.55) ? 0.85 : (b < 0.85) ? 0.2 : 0.8;
        double dry = (wsum > 1.0e-9) ? ((acc / wsum) * 0.9 + latBase * 0.1) : latBase;
        return dry < 0 ? 0 : (dry > 1 ? 1 : dry);
    }

    /** 主导系统核心。 */
    private static PressureSystemType dominantCore(double fx, double fz,
                                                    java.util.ArrayList<double[]> systems) {
        double bestW = 0.0;
        PressureSystemType best = null;
        for (double[] c : systems) {
            double dx = wrapDelta(fx - c[0], X_CYCLE), dz = wrapDelta(fz - c[1], Z_CYCLE);
            double r2 = c[2] * c[2];
            double w = r2 / (dx * dx + dz * dz + r2);
            if (w > bestW) {
                bestW = w;
                best = PressureSystemType.values()[(int) c[3]];
            }
        }
        return bestW > 0.2 ? best : null;
    }

    /** 风场向量 [windX, windZ]：三圈环流基底主导（方向稳定）+ 气压系统弱扰动 + 15° 斜向。 */
    public static double[] windDir(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        double fx = p[0], fz = p[1];
        double sign = hemisphereSign(worldZ);
        double vx = 0.0, vz = 0.0;

        double b = bandD(worldZ);
        double[] bs = zonalBaseSlope(b, sign);
        double[] wv = systemWind(fx, fz, sign, bs[0], bs[1],
            systemsNear(worldX, worldZ, worldSeedInt));
        double len = Math.sqrt(wv[0] * wv[0] + wv[1] * wv[1]);
        if (len < 1.0e-4) return new double[] { bs[0], 0.0 };
        return wv;
    }

    /** 气压干湿 [0,1]：以系统为主（锐化四次核，带间不串扰）。 */
    public static double pressureDry(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        double b = bandD(worldZ);
        return pressureDryCore(p[0], p[1], b, systemsNear(worldX, worldZ, worldSeedInt));
    }

    /** 当前点主导系统类型（供 /talosmap 标注）。 */
    public static PressureSystemType dominantSystem(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        return dominantCore(p[0], p[1], systemsNear(worldX, worldZ, worldSeedInt));
    }

    /** 潜在降水 [0,1]。 */
    public static double rainfallBase(int worldX, int worldZ, int worldSeedInt) {
        double dry = pressureDry(worldX, worldZ, worldSeedInt);
        double b = bandD(worldZ);
        double latRain = 1.0 - 0.6 * b;
        double r = latRain * (1.0 - 0.75 * dry);
        return r < 0 ? 0 : (r > 1 ? 1 : r);
    }

    /** 组合采样（单次构建系统列表，各量共享；比逐量调用省 3/4 的列表重建）。 */
    public static CirculationSample sample(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        double fx = p[0], fz = p[1];
        double sign = hemisphereSign(worldZ);
        double b = bandD(worldZ);
        java.util.ArrayList<double[]> systems = systemsNear(worldX, worldZ, worldSeedInt);
        double[] bs = zonalBaseSlope(b, sign);
        double[] wv = systemWind(fx, fz, sign, bs[0], bs[1], systems);
        double dry = pressureDryCore(fx, fz, b, systems);
        double latRain = 1.0 - 0.6 * b;
        double rain = latRain * (1.0 - 0.75 * dry);
        if (rain < 0) rain = 0;
        if (rain > 1) rain = 1;
        PressureSystemType sys = dominantCore(fx, fz, systems);
        double gyreBase = 0.5 - b;
        double gyre = gyreBase < -1 ? -1 : (gyreBase > 1 ? 1 : gyreBase);
        return new CirculationSample(b, wv[0], wv[1], gyre, dry, rain, sys);
    }
}
