package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;

/**
 * F5 静态气候解的基础网格：80,000 x 40,000 km、144 x 72 x 15。
 * x 周期、y 墙、LandV3 mask；不依赖 MITgcm。
 */
public final class OceanGrid {

    public static final int NX = 144;
    public static final int NY = 72;
    public static final int NZ = 15;
    public static final double WIDTH_M = 80_000_000.0;
    public static final double HEIGHT_M = 40_000_000.0;
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
