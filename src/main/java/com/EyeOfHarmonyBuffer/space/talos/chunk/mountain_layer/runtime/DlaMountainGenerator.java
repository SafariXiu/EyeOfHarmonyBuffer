package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime;

import java.util.ArrayDeque;

/**
 * 涌现式 DLA 山脊生成器（矩形网格版，纯 Java，无 MC 依赖）。
 *
 * 与 Python 原型 / DlaMountainGen 同源：
 *   - MT19937 随机数（与 Python random.Random 逐位一致）；
 *   - 粒子雨 DLA：从聚集体上下两端释放粒子，碰到就粘附；
 *   - 厚度场 -> 高度：树干（主脊）最厚最高，支脊自然递减；
 *   - 纵向峰谷剖面 + 高斯模糊。
 *
 * 网格宽 w（横向/横截面）高 h（纵向/山脉走向），支持矩形条带。
 * 纯函数：相同 (w, h, seed, 参数) 必得相同结果，可安全并行。
 */
public final class DlaMountainGenerator {

    /** 默认参数。 */
    public static final double DEFAULT_TARGET_FILL = 0.12;
    public static final double DEFAULT_DRIFT = 0.55;
    public static final double DEFAULT_LATERAL = 0.30;
    public static final int DEFAULT_MAX_STEPS = 3000;
    public static final double DEFAULT_BLUR = 2.5;
    public static final double DEFAULT_PROFILE_AMP = 0.18;

    private static final int[][] DIRS4 = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    /** 生成结果：连续高程场（行主序，row = 沿山脉走向）。 */
    public static final class Result {
        public final int width;
        public final int height;
        public final float[] elevation01;

        Result(int width, int height, float[] elevation01) {
            this.width = width;
            this.height = height;
            this.elevation01 = elevation01;
        }
    }

    private DlaMountainGenerator() {}

    public static Result generate(int width, int height, long seed) {
        return generate(
            width, height, seed,
            DEFAULT_TARGET_FILL, DEFAULT_DRIFT, DEFAULT_LATERAL,
            DEFAULT_MAX_STEPS, DEFAULT_BLUR, DEFAULT_PROFILE_AMP, 3
        );
    }

    public static Result generate(int width, int height, long seed,
                                  double targetFill, double drift,
                                  double lateral, int maxSteps,
                                  double blurRadius, double profileAmp,
                                  int anchorCount) {
        int w = Math.max(8, width);
        int h = Math.max(8, height);
        Mt19937 rng = new Mt19937((int) (seed ^ (seed >>> 32)));

        boolean[][] occupied = new boolean[h][w];
        int[][] birth = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                birth[y][x] = -1;
            }
        }

        // 多锚点播种：沿山脉走向放 2~4 个小种子团（左右轻微错开），
        // 各自随机生长、自然连成并行脊系——仍是涌现式，不画曲线。
        int anchors = Math.max(1, anchorCount);
        for (int a = 0; a < anchors; a++) {
            int ay = (int) Math.round(
                (a + 1) * (h / (double) (anchors + 1))
            );
            int ax = w / 2;
            if (a % 2 == 0) {
                ax -= Math.max(1, (int) Math.round(w * 0.07));
            } else {
                ax += Math.max(1, (int) Math.round(w * 0.07));
            }
            ax += rng.randint(-1, 1);

            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int y = ay + dy;
                    int x = ax + dx;
                    if (y >= 0 && y < h && x >= 0 && x < w
                        && !occupied[y][x]) {
                        occupied[y][x] = true;
                        birth[y][x] = 0;
                    }
                }
            }
        }

        int count = 0;
        int ymin = Integer.MAX_VALUE;
        int ymax = Integer.MIN_VALUE;
        int xmin = Integer.MAX_VALUE;
        int xmax = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (occupied[y][x]) {
                    count++;
                    if (y < ymin) ymin = y;
                    if (y > ymax) ymax = y;
                    if (x < xmin) xmin = x;
                    if (x > xmax) xmax = x;
                }
            }
        }

        int target = (int) (w * h * targetFill);
        int step = 0;
        int failedSinceStick = 0;
        int maxFailed = Math.max(2000, w * 8);
        int maxWalkers = target * 30 + 10000;

        while (count < target) {
            step++;
            if (step > maxWalkers || failedSinceStick > maxFailed) {
                // 看门狗：聚集体两端被占满后可能无法继续生长，
                // 此时退出并接受略低于 target 的填充率。
                break;
            }

            // 四向释放：上下为主（沿走向伸长），左右为辅（加宽山体）
            int roll = rng.randint(0, 99);
            boolean fromTop;
            boolean fromLeft = false;
            boolean fromRight = false;
            if (roll < 40) {
                fromTop = true;
            } else if (roll < 80) {
                fromTop = false;
            } else if (roll < 90) {
                fromTop = true;
                fromLeft = true;
            } else {
                fromTop = true;
                fromRight = true;
            }

            int x;
            int y;
            if (fromLeft) {
                x = Math.max(0, xmin - 1);
                y = rng.randint(Math.max(0, ymin - 3), Math.min(h - 1, ymax + 3));
            } else if (fromRight) {
                x = Math.min(w - 1, xmax + 1);
                y = rng.randint(Math.max(0, ymin - 3), Math.min(h - 1, ymax + 3));
            } else if (fromTop) {
                x = rng.randint(Math.max(0, xmin - 3), Math.min(w - 1, xmax + 3));
                y = Math.max(0, ymin - 1);
            } else {
                x = rng.randint(Math.max(0, xmin - 3), Math.min(w - 1, xmax + 3));
                y = Math.min(h - 1, ymax + 1);
            }

            boolean stuck = false;
            for (int s = 0; s < maxSteps; s++) {
                double r = rng.random();
                int dx;
                int dy;
                if (fromLeft || fromRight) {
                    // 左右释放：主方向横向（朝聚集体），纵向为横向游走
                    if (r < drift) {
                        dx = fromLeft ? 1 : -1;
                        dy = 0;
                    } else if (r < drift + lateral) {
                        dx = 0;
                        dy = rng.choicePM();
                    } else {
                        dx = fromLeft ? -1 : 1;
                        dy = 0;
                    }
                } else {
                    if (r < drift) {
                        dy = fromTop ? 1 : -1;
                        dx = 0;
                    } else if (r < drift + lateral) {
                        dy = 0;
                        dx = rng.choicePM();
                    } else {
                        dy = fromTop ? -1 : 1;
                        dx = 0;
                    }
                }

                int nx = x + dx;
                int ny = y + dy;
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                    break;
                }
                if (occupied[ny][nx]) {
                    if (!occupied[y][x]) {
                        occupied[y][x] = true;
                        birth[y][x] = step;
                        count++;
                        failedSinceStick = 0;
                        stuck = true;
                        if (y < ymin) ymin = y;
                        else if (y > ymax) ymax = y;
                        if (x < xmin) xmin = x;
                        else if (x > xmax) xmax = x;
                    }
                    break;
                }
                x = nx;
                y = ny;
            }
            if (!stuck) {
                failedSinceStick++;
            }
        }

        // 厚度场
        int[][] thickness = thicknessField(occupied, w, h);

        // 纵向峰谷剖面（沿 y = 山脉走向）
        double[] profile = buildProfile(h, seed, profileAmp);

        // 脊线强度：厚度（主干最厚）-> 主脊更高
        int maxDepth = 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (occupied[y][x]) {
                    maxDepth = Math.max(maxDepth, thickness[y][x]);
                }
            }
        }
        // 到最近脊线的距离场（全网格；脊线格距离 0）
        int[][] distToSkeleton = distanceToOccupied(occupied, w, h);

        // 连续高程：脊线上 1，向两侧按距离衰减到 0（谷底）。
        // 衰减半径必须远小于山带半宽，否则整条带是一个大缓坡穹顶，
        // 脊/谷对比被抹平（实测 elev 只有 0.3~0.5，地形看不出起伏）。
        // 取 2.5~3.5 格：坡宽 ~160-224 blocks；线性锥形剖面带尖点。
        double falloff = Math.max(2.5, Math.min(w * 0.06, 3.5));
        double[][] elevField = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = distToSkeleton[y][x];
                double t = Math.min(d / falloff, 1.0);
                // 线性锥形剖面：山脊是尖顶三角棱，坡面全程陡峭。
                double falloffElev = 1.0 - t;

                double ridgeI = 0.0;
                if (occupied[y][x]) {
                    ridgeI = Math.pow(
                        (double) thickness[y][x] / maxDepth, 0.6
                    );
                }

                double elev = falloffElev
                    * (0.35 + 0.65 * ridgeI)
                    * profile[y];
                elevField[y][x] = elev;
            }
        }
        // 微峰噪声：沿山脊线制造独立尖峰/鞍部（波长 ~5 格 ≈ 320 block），
        // 让山脊不再是连续平台，而是"一个接一个的山峰"。
        double[] microPeaks = buildProfile(h, seed + 54321, 0.22, 5.0);
        for (int y = 0; y < h; y++) {
            double mp = microPeaks[y];
            for (int x = 0; x < w; x++) {
                elevField[y][x] *= mp;
            }
        }
        elevField = gaussianBlur(elevField, Math.min(blurRadius, 0.2));

        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // 增益：把"最厚主干与剖面峰不重合"导致的峰值压低拉回来
                double v = elevField[y][x] * 1.18;
                if (v < 0.0) v = 0.0;
                else if (v > 1.0) v = 1.0;
                out[y * w + x] = (float) v;
            }
        }

        return new Result(w, h, out);
    }

    /** 网格上每格到最近"聚集体格"的距离（脊线距离场）。 */
    private static int[][] distanceToOccupied(boolean[][] occupied,
                                              int w, int h) {
        int[][] dist = new int[h][w];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (occupied[y][x]) {
                    dist[y][x] = 0;
                    queue.add(y * w + x);
                } else {
                    dist[y][x] = Integer.MAX_VALUE;
                }
            }
        }
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int x = cur % w;
            int y = cur / w;
            int d = dist[y][x] + 1;
            for (int[] dir : DIRS4) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h
                    && dist[ny][nx] > d) {
                    dist[ny][nx] = d;
                    queue.add(ny * w + nx);
                }
            }
        }
        return dist;
    }

    /** 对 0~1 浮点网格做高斯模糊（供山带蒙版拉宽过渡带用）。 */
    public static float[] blur01(float[] src, int w, int h, double radius) {
        double[][] a = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                a[y][x] = src[y * w + x];
            }
        }
        double[][] b = gaussianBlur(a, radius);
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = (float) b[y][x];
            }
        }
        return out;
    }

    // ------------------------------------------------------------
    // 厚度场 / 剖面 / 模糊
    // ------------------------------------------------------------

    private static int[][] thicknessField(boolean[][] occupied, int w, int h) {
        int[][] dist = new int[h][w];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!occupied[y][x]) {
                    dist[y][x] = 0;
                    queue.add(y * w + x);
                } else {
                    dist[y][x] = Integer.MAX_VALUE;
                }
            }
        }
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int x = cur % w;
            int y = cur / w;
            int d = dist[y][x] + 1;
            for (int[] dir : DIRS4) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h
                    && dist[ny][nx] > d) {
                    dist[ny][nx] = d;
                    queue.add(ny * w + nx);
                }
            }
        }
        return dist;
    }

    /** 沿 y 的 1D 平滑剖面：确定性哈希梯度 + smoothstep 插值。 */
    private static double[] buildProfile(int len, long seed, double amp) {
        return buildProfile(len, seed, amp, Math.max(8.0, len * 0.30));
    }

    private static double[] buildProfile(int len, long seed, double amp,
                                         double wavelength) {
        double[] raw = new double[len];
        for (int i = 0; i < len; i++) {
            double x = i / wavelength;
            int i0 = (int) Math.floor(x);
            double f = x - i0;
            double u = f * f * (3.0 - 2.0 * f);
            double g0 = gradValue(i0, seed + 91);
            double g1 = gradValue(i0 + 1, seed + 91);
            raw[i] = g0 + (g1 - g0) * u;
        }
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (double v : raw) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        double[] profile = new double[len];
        for (int i = 0; i < len; i++) {
            double norm = (hi - lo > 1e-9) ? (raw[i] - lo) / (hi - lo) : 0.5;
            profile[i] = 1.0 - amp + amp * norm;
        }
        return profile;
    }

    private static double gradValue(long i, long seed) {
        long h = seed;
        h ^= i * 0x9E3779B97F4A7C15L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h & 0xFFFFFL) / (double) (1L << 20)) * 2.0 - 1.0;
    }

    private static double[][] gaussianBlur(double[][] a, double radius) {
        if (radius <= 0.0) {
            return a;
        }
        double sigma = Math.max(0.6, radius / 2.0);
        int k = Math.max(1, (int) Math.ceil(radius * 2.0));
        int taps = 2 * k + 1;
        double[] kernel = new double[taps];
        double sum = 0.0;
        for (int i = -k; i <= k; i++) {
            double x = i;
            kernel[i + k] = Math.exp(-(x * x) / (2.0 * sigma * sigma));
            sum += kernel[i + k];
        }
        for (int i = 0; i < taps; i++) {
            kernel[i] /= sum;
        }
        double[][] out = convRows(a, kernel);
        out = transpose(convRows(transpose(out), kernel));
        return out;
    }

    private static double[][] convRows(double[][] a, double[] kernel) {
        int h = a.length;
        int w = a[0].length;
        int taps = kernel.length;
        int k = taps / 2;
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double acc = 0.0;
                for (int t = 0; t < taps; t++) {
                    int idx = x - k + t;
                    if (idx >= 0 && idx < w) {
                        acc += kernel[t] * a[y][idx];
                    }
                }
                out[y][x] = acc;
            }
        }
        return out;
    }

    private static double[][] transpose(double[][] a) {
        int h = a.length;
        int w = a[0].length;
        double[][] out = new double[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[x][y] = a[y][x];
            }
        }
        return out;
    }

    // ------------------------------------------------------------
    // MT19937（与 Python random.Random 逐位一致）
    // ------------------------------------------------------------
    private static final class Mt19937 {
        private static final int N = 624;
        private final int[] mt = new int[N];
        private int mti = N + 1;

        Mt19937(int seed) {
            initByArray(new int[] {seed});
        }

        private void initGenrand(int s) {
            mt[0] = s;
            for (int i = 1; i < N; i++) {
                mt[i] = 1812433253 * (mt[i - 1] ^ (mt[i - 1] >>> 30)) + i;
            }
            mti = N;
        }

        private void initByArray(int[] key) {
            initGenrand(19650218);
            int i = 1;
            int j = 0;
            int k = Math.max(N, key.length);
            for (; k > 0; k--) {
                mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525))
                    + key[j] + j;
                i++;
                j++;
                if (i >= N) {
                    mt[0] = mt[N - 1];
                    i = 1;
                }
                if (j >= key.length) {
                    j = 0;
                }
            }
            for (k = N - 1; k > 0; k--) {
                mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941))
                    - i;
                i++;
                if (i >= N) {
                    mt[0] = mt[N - 1];
                    i = 1;
                }
            }
            mt[0] = 0x80000000;
            mti = N;
        }

        private int genrandInt32() {
            int y;
            if (mti >= N) {
                int kk;
                for (kk = 0; kk < N - 397; kk++) {
                    y = (mt[kk] & 0x80000000) | (mt[kk + 1] & 0x7fffffff);
                    mt[kk] = mt[kk + 397]
                        ^ (y >>> 1)
                        ^ ((y & 1) != 0 ? 0x9908b0df : 0);
                }
                for (; kk < N - 1; kk++) {
                    y = (mt[kk] & 0x80000000) | (mt[kk + 1] & 0x7fffffff);
                    mt[kk] = mt[kk + (397 - N)]
                        ^ (y >>> 1)
                        ^ ((y & 1) != 0 ? 0x9908b0df : 0);
                }
                y = (mt[N - 1] & 0x80000000) | (mt[0] & 0x7fffffff);
                mt[N - 1] = mt[396] ^ (y >>> 1)
                    ^ ((y & 1) != 0 ? 0x9908b0df : 0);
                mti = 0;
            }
            y = mt[mti++];
            y ^= y >>> 11;
            y ^= (y << 7) & 0x9d2c5680;
            y ^= (y << 15) & 0xefc60000;
            y ^= y >>> 18;
            return y;
        }

        double random() {
            return ((genrandInt32() >>> 5) * 67108864.0
                + (genrandInt32() >>> 6)) / 9007199254740992.0;
        }

        int randbelow(int n) {
            if (n <= 0) {
                throw new IllegalArgumentException("randbelow: n <= 0");
            }
            if (n == 1) {
                return 0;
            }
            int k = 32 - Integer.numberOfLeadingZeros(n);
            int r;
            do {
                r = getrandbits(k);
            } while (r >= n);
            return r;
        }

        int getrandbits(int k) {
            if (k <= 0) {
                return 0;
            }
            return genrandInt32() >>> (32 - k);
        }

        int randint(int a, int b) {
            return a + randbelow(b - a + 1);
        }

        int choicePM() {
            return randbelow(2) == 0 ? -1 : 1;
        }
    }
}
