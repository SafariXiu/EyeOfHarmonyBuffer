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
 *   风 = 绕各系统的旋转环流（科里奥利：北顺南逆）+ 径向辐散/辐合 + 弱纬向基底；
 *   干湿 = **以系统为主**（高压系统大干区、低压大湿区、ITCZ 湿带），少量纬度基准混合；
 *   CirculationSample.pressureSystem 输出当前点主导系统类型（供 /talosmap 标注标签）。
 *
 * 环面几何：X 周期 400k，Z 纬度循环 200k。大陆影响留 S3。
 */
public final class GlobalCirculation {

    /** X 方向周期（blocks）。 */
    public static final int X_CYCLE = 400_000;
    /** Z 方向纬度循环（blocks）。 */
    public static final int Z_CYCLE = ClimateLatitudes.LAT_CYCLE;

    /** 系统半径范围。 */
    private static final double W_R_MIN = 60_000.0;
    private static final double W_R_MAX = 110_000.0;

    /** 纬向风带基底强度（弱背景）。 */
    private static final double ZONAL_BASE = 0.25;

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
            // ITCZ（bandY=0）是赤道单一的辐合带，不分南北——只放一次，避免折叠后重复
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

    /** 风场向量 [windX, windZ]：绕各系统旋转（科里奥利）+ 径向 + 弱纬向基底。 */
    public static double[] windDir(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        double fx = p[0], fz = p[1];
        double sign = hemisphereSign(worldZ);
        double vx = 0.0, vz = 0.0;

        double b = bandD(worldZ);
        double zonalX;
        if (b < 0.32) zonalX = -1.0;
        else if (b < 0.84) zonalX = 1.0;
        else zonalX = -1.0;
        vx += zonalX * ZONAL_BASE;

        java.util.ArrayList<double[]> systems = systemsNear(worldX, worldZ, worldSeedInt);
        for (double[] c : systems) {
            double dx = fx - c[0], dz = fz - c[1];
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
                double mag = w * 1.0;
                vx += (tx / tl) * mag;
                vz += (tz / tl) * mag;
            }
            double rmag = w * 0.4 * spin;
            vx += (dx / d) * rmag;
            vz += (dz / d) * rmag;
        }
        double len = Math.sqrt(vx * vx + vz * vz);
        if (len < 1.0e-4) return new double[] { zonalX, 0.0 };
        return new double[] { vx, vz };
    }

    /** 气压干湿 [0,1]：以系统为主。 */
    public static double pressureDry(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        double fx = p[0], fz = p[1];
        double acc = 0.0, wsum = 0.0;
        java.util.ArrayList<double[]> systems = systemsNear(worldX, worldZ, worldSeedInt);
        for (double[] c : systems) {
            double dx = fx - c[0], dz = fz - c[1];
            double r2 = c[2] * c[2];
            double w = r2 / (dx * dx + dz * dz + r2);
            PressureSystemType type = PressureSystemType.values()[(int) c[3]];
            acc += type.baseDry * w;
            wsum += w;
        }
        // 系统主导（wsum 覆盖），少量纬度基准混合
        double b = bandD(worldZ);
        double latBase = (b < 0.2) ? 0.1 : (b < 0.55) ? 0.85 : (b < 0.85) ? 0.2 : 0.8;
        double dry = (wsum > 1.0e-9) ? ((acc / wsum) * 0.9 + latBase * 0.1) : latBase;
        return dry < 0 ? 0 : (dry > 1 ? 1 : dry);
    }

    /** 当前点主导系统类型（供 /talosmap 标注）。 */
    public static PressureSystemType dominantSystem(int worldX, int worldZ, int worldSeedInt) {
        double[] p = foldPoint(worldX, worldZ);
        double fx = p[0], fz = p[1];
        double bestW = 0.0;
        PressureSystemType best = null;
        java.util.ArrayList<double[]> systems = systemsNear(worldX, worldZ, worldSeedInt);
        for (double[] c : systems) {
            double dx = fx - c[0], dz = fz - c[1];
            double r2 = c[2] * c[2];
            double w = r2 / (dx * dx + dz * dz + r2);
            if (w > bestW) {
                bestW = w;
                best = PressureSystemType.values()[(int) c[3]];
            }
        }
        return bestW > 0.2 ? best : null;   // 阈值以下视为无主导系统
    }

    /** 潜在降水 [0,1]。 */
    public static double rainfallBase(int worldX, int worldZ, int worldSeedInt) {
        double dry = pressureDry(worldX, worldZ, worldSeedInt);
        double b = bandD(worldZ);
        double latRain = 1.0 - 0.6 * b;
        double r = latRain * (1.0 - 0.75 * dry);
        return r < 0 ? 0 : (r > 1 ? 1 : r);
    }

    /** 组合采样。 */
    public static CirculationSample sample(int worldX, int worldZ, int worldSeedInt) {
        double b = bandD(worldZ);
        double[] wind = windDir(worldX, worldZ, worldSeedInt);
        double dry = pressureDry(worldX, worldZ, worldSeedInt);
        double rain = rainfallBase(worldX, worldZ, worldSeedInt);
        PressureSystemType sys = dominantSystem(worldX, worldZ, worldSeedInt);
        double gyreBase = 0.5 - b;
        double gyre = gyreBase < -1 ? -1 : (gyreBase > 1 ? 1 : gyreBase);
        return new CirculationSample(b, wind[0], wind[1], gyre, dry, rain, sys);
    }
}
