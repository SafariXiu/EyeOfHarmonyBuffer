package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.PlanetMetric;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * F5 静态气候解的基础网格：WIDTH_LAPS x 40,000 km（1 圈 = 40,000）、72 x 72 x 15。
 * x 周期、y 墙、LandV3 mask；不依赖 MITgcm。
 */
public final class OceanGrid {

    /**
     * ★ 2026-11-03（阶段 0.5）：144 -> 72。宽度从 2 圈（80,000 km）改回 1 圈（40,000 km）后，
     * 保持格距 555.6 km（5°）不变，必须同时把列数减半——否则 dx 会变成 277.8 km，
     * 而 {@code BarotropicSolver} 的 Laplacian/beta 离散假定 dx=dy，会引入各向异性误差。
     * <p>分辨率与 v7 相同（5°），变的只有【域宽】与【旋度配对】两项，便于 A/B。
     */
    public static final int NX = 72;
    public static final int NY = 72;
    public static final int NZ = 15;
    /**
     * 口径：x 方向跨几圈赤道。
     * <p>★ 2026-11-03（阶段 0.5）：2.0 -> 1.0。旧值来自 MITgcm 80k 的
     * {@code delX=144*555555.5556}，使海洋箱宽 = 2 圈赤道（80,000 km），
     * 与大气/陆地/降水（40,000 km）不一致；同一块大陆会被复制两遍。
     * <p>1.0 = 行星周长 40,000 km。格距随之变成 dx=277.8 km（2.5°）、dy=555.6 km（5°）——
     * <b>各向异性是过渡态</b>，区域 1° 架构（3D海洋-无限世界架构.md §3）会重新定义。
     */
    public static final double WIDTH_LAPS = 1.0;
    public static final double WIDTH_M = WIDTH_LAPS * PlanetMetric.CIRCUMFERENCE_M;   // = 80,000,000（逐位不变）
    public static final double HEIGHT_M = WorldContract.Z_CYCLE;                      // = 40,000,000（一整条经圈）
    public static final double[] DZ = {
        50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190
    };

    public final int nx, ny, nz;
    public final double dx, dy;
    public final double[] dz;
    /** 各层【格心】深度（m，从海面算起）；供分层扩散率等使用。 */
    public final double[] zCenter;
    public final boolean[] wet;
    public final int x0, z0;

    private OceanGrid(int nx, int ny, int nz, double dx, double dy,
                      double[] dz, boolean[] wet, int x0, int z0) {
        this.nx = nx; this.ny = ny; this.nz = nz;
        this.dx = dx; this.dy = dy; this.dz = dz;
        this.wet = wet; this.x0 = x0; this.z0 = z0;
        this.zCenter = new double[nz];
        double zc = 0.0;
        for (int k = 0; k < nz; k++) { zCenter[k] = zc + 0.5 * dz[k]; zc += dz[k]; }
    }

    public static OceanGrid landV3(int worldSeed, int cell, int x0, int z0) {
        long seed = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(worldSeed);
        double dx = WIDTH_M / NX;
        double dy = HEIGHT_M / NY;
        boolean[] wet = new boolean[NX * NY];
        for (int j = 0; j < NY; j++) {
            double z = z0 + (j + 0.5) * dy;
            for (int i = 0; i < NX; i++) {
                double x = x0 + (i + 0.5) * dx;
                x = x % WIDTH_M;
                if (x < 0) x += WIDTH_M;
                boolean wall = (j == 0 || j == NY - 1);
                boolean land = PlateField.isLandWithCell(
                    (int) Math.round(x), (int) Math.round(z), seed, cell);
                wet[j * NX + i] = !wall && !land;
            }
        }
        return new OceanGrid(NX, NY, NZ, dx, dy, DZ, wet, x0, z0);
    }

    /** 测试用：y 墙，其余全湿。 */
    public static OceanGrid allWet(int x0, int z0) {
        boolean[] wet = new boolean[NX * NY];
        for (int j = 1; j < NY - 1; j++) {
            for (int i = 0; i < NX; i++) wet[j * NX + i] = true;
        }
        return new OceanGrid(NX, NY, NZ, WIDTH_M / NX, HEIGHT_M / NY, DZ, wet, x0, z0);
    }

    public int idx(int i, int j) {
        int ii = i % nx;
        if (ii < 0) ii += nx;
        return j * nx + ii;
    }

    public boolean isWet(int i, int j) {
        if (j < 0 || j >= ny) return false;
        return wet[idx(i, j)];
    }

    public double xOf(int i) { return x0 + (i + 0.5) * dx; }
    public double zOf(int j) { return z0 + (j + 0.5) * dy; }

    public int wetCount() {
        int n = 0;
        for (boolean b : wet) if (b) n++;
        return n;
    }

    @Override public String toString() {
        return "OceanGrid(" + nx + "x" + ny + "x" + nz
            + ", " + (WIDTH_M / 1000) + "x" + (HEIGHT_M / 1000) + " km"
            + ", wet=" + wetCount() + ")";
    }
}
