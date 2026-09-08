package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

/**
 * M4 洋流 v2（风场 v2 第四模块）。
 *
 * 权威链：洋流 ≈ 风驱动 × 埃克曼转向 × 大陆折射；海温 = 沿流场的平衡温输运。
 *   - 驱动风：M2 PressureField 地表风（含摩擦辐合结构）；
 *   - 埃克曼转向：表层流相对风向右偏（N）/左偏（S）约 EKMAN_TURN；
 *   - **岸墙折射**：近岸把流的向陆分量抹掉 → 流沿海岸等深线"滑行"，绕大陆闭合 → 环流圈；
 *   - **海温输运**：SST = 逆流回溯 SST_STEPS 步（吃 M1 seaTeq 骨架平衡）+ 逐步弛豫
 *     → 暖舌沿西边界流北上、寒舌沿东边界流南下等结构由流场自己"画"出。
 *
 * 输出：flowX/flowZ（单位方向）、speed（风强×系数）、temperature（输运后海温 [-1,1]）。
 * 纯函数 O(1)，但单点偏贵（SST 回溯每步一次风场）——原型验证用；M6 查表化后作为离线算子。
 */
public final class OceanCurrentField {

    private OceanCurrentField() {}

    /** 埃克曼转向角（弧度 ≈ 20°）。 */
    private static final double EKMAN_TURN = 0.35;
    /** 岸墙作用距离（block）：d &lt; 此值开始折射。 */
    private static final double WALL_REACH = 26_000.0;
    /** 全切向距离（block）：d &lt; 此值完全沿海岸滑行（边界流贴岸段）。 */
    private static final double WALL_FULL = 4_000.0;
    /** 岸墙梯度采样步长（block）。 */
    private static final int WALL_STEP = 4000;
    /** 流速系数（风强 → 洋流速度，显示用）。 */
    private static final double SPEED_K = 0.5;
    /** 海温回溯步数。 */
    private static final int SST_STEPS = 5;
    /** 海温回溯步长（block）。 */
    private static final double SST_DT = 26_000.0;
    /** 海温弛豫特征长度（block）。 */
    private static final double L_SST = 55_000.0;

    /** 洋流采样结果。 */
    public static final class CurrentSample {
        public final double flowX;       // 洋流方向（单位）
        public final double flowZ;
        public final double temperature; // [-1,1] 海温（沿流输运后）
        public final double speed;       // [0,1] 流速

        CurrentSample(double fx, double fz, double temp, double speed) {
            this.flowX = fx;
            this.flowZ = fz;
            this.temperature = temp;
            this.speed = speed;
        }

        @Override
        public String toString() {
            return String.format("Current[(%.2f,%.2f) t=%.2f v=%.2f]", flowX, flowZ, temperature, speed);
        }
    }

    private static boolean isSea(int x, int z, int worldSeedInt) {
        return NoiseContinentGrid.landResidual(x, z, worldSeedInt) < 0.0;
    }

    /** 表层流方向（单位向量）与风强：风 → 埃克曼转向 → 岸墙折射。返回 null 表示风太弱/陆上。 */
    private static double[] flowDir(int x, int z, int worldSeedInt) {
        if (!isSea(x, z, worldSeedInt)) {
            return null;
        }
        double[] w = PressureField.windDir(x, z, worldSeedInt);
        double sp = Math.sqrt(w[0] * w[0] + w[1] * w[1]);
        if (sp < 1.0e-6) {
            return null;
        }
        double ux = w[0] / sp, uz = w[1] / sp;
        // 埃克曼转向：北半右偏（顺）、南半左偏（逆）——按半球符号
        double s = (GlobalCirculation.foldZ(z) <= 100_000) ? 1.0 : -1.0;
        double ca = Math.cos(EKMAN_TURN), sa = Math.sin(EKMAN_TURN);
        double fx = ux * ca + s * uz * sa;
        double fz = -s * ux * sa + uz * ca;
        // 岸墙折射：近岸抹去向陆分量 → 沿海岸滑行
        double d = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);   // 海 >0
        if (d < WALL_REACH) {
            int k = WALL_STEP;
            double dp = NoiseContinentGrid.coastDistBlocks(x + k, z, worldSeedInt)
                      - NoiseContinentGrid.coastDistBlocks(x - k, z, worldSeedInt);
            double dzp = NoiseContinentGrid.coastDistBlocks(x, z + k, worldSeedInt)
                       - NoiseContinentGrid.coastDistBlocks(x, z - k, worldSeedInt);
            double gl = Math.sqrt(dp * dp + dzp * dzp);
            if (gl > 1.0e-9) {
                double nx = dp / gl, nz = dzp / gl;   // 指向外海
                double vn = fx * nx + fz * nz;          // &gt;0 朝外海
                if (vn < 0.0) {
                    // 强度：d<WALL_FULL 全切向（贴岸边界流），向外衰减
                    double inf = clamp01((WALL_REACH - d) / (WALL_REACH - WALL_FULL));
                    fx -= vn * nx * inf;               // 抹去向陆分量
                    fz -= vn * nz * inf;
                    double l2 = Math.sqrt(fx * fx + fz * fz);
                    if (l2 > 1.0e-9) {
                        fx /= l2;
                        fz /= l2;
                    }
                }
            }
        }
        return new double[] { fx, fz };
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** 采样某点洋流（仅海上点调用；陆上返回 isSea=false 语义由调用方处理）。 */
    public static CurrentSample sample(int x, int z, int worldSeedInt) {
        double[] flow = flowDir(x, z, worldSeedInt);
        if (flow == null) {
            return new CurrentSample(0.0, 0.0, Double.NaN, 0.0);
        }
        double[] w = PressureField.windDir(x, z, worldSeedInt);
        double sp = Math.sqrt(w[0] * w[0] + w[1] * w[1]);
        double speed = Math.min(1.0, sp * SPEED_K);
        double sst = advectedSst(x, z, worldSeedInt);
        return new CurrentSample(flow[0], flow[1], sst, speed);
    }

    /** 海温：沿流场逆流回溯（源地 seaTeq）+ 逐步弛豫。 */
    private static double advectedSst(int x, int z, int worldSeedInt) {
        double px = x, pz = z;
        // 记录路径（从近到远）
        double[][] path = new double[SST_STEPS][2];
        path[0][0] = x;
        path[0][1] = z;
        for (int i = 1; i < SST_STEPS; i++) {
            double[] f = flowDir((int) px, (int) pz, worldSeedInt);
            if (f == null) {
                // 上游撞陆/无风：就地停住（记忆冻结在此）
                path[i][0] = px;
                path[i][1] = pz;
                continue;
            }
            px -= f[0] * SST_DT;
            pz -= f[1] * SST_DT;
            path[i][0] = px;
            path[i][1] = pz;
        }
        // 源地初始化
        int sx = (int) path[SST_STEPS - 1][0];
        int sz = (int) path[SST_STEPS - 1][1];
        double t = ThermalForcing.seaTeq(sx, sz, worldSeedInt);
        // 顺流弛豫回查询点
        for (int i = SST_STEPS - 2; i >= 0; i--) {
            int cx = (int) path[i][0];
            int cz = (int) path[i][1];
            double eq = ThermalForcing.seaTeq(cx, cz, worldSeedInt);
            double f = 1.0 - Math.exp(-SST_DT / L_SST);
            t += (eq - t) * f;
        }
        if (t < -1.0) t = -1.0;
        if (t > 1.0) t = 1.0;
        return t;
    }
}
