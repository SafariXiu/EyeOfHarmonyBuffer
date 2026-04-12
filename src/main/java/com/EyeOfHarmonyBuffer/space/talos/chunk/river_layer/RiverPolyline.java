package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

/**
 * 一条河（主河或支流）的折线表示。
 */

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * 一条河（主河或支流）的折线表示。
 *
 * 统一设计：
 *   - nodes：双精度世界坐标骨架，用于生长、源头湖、分析等高层逻辑；
 *   - xs/zs：将 nodes 四舍五入到整格后的坐标缓存，用于距离场采样等需要 int 坐标的场景；
 *   - 弧长缓存：按 xs/zs 计算，提供 0..1 的 tAlong 参数。
 */
public final class RiverPolyline {

    /** 折线节点（世界坐标，blocks，double 精度） */
    public static final class Node {
        public final double x;
        public final double z;

        public Node(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    /** 全局唯一河 ID（主河 + 支流共用一个 ID 空间） */
    public final int id;

    /** 所属板块 ID */
    public final int plateId;

    /** 河级别：0 = 主河，1 = 一级支流，2 = 二级支流 ... */
    public final int level;

    /** 原始节点（double）列表，表示生成时的折线骨架 */
    public final java.util.List<Node> nodes;

    /** 采样用整数坐标缓存（四舍五入自 nodes） */
    public final IntList xs;
    public final IntList zs;

    private double[] segmentLengths;
    private double[] prefixLengths;
    private double totalLength;
    private boolean lengthReady = false;

    /**
     * 标准构造器：
     *   - id:     河 ID
     *   - plateId: 所属板块
     *   - level:  级别（0 主河）
     *   - nodes:  世界坐标骨架（mouth -> source，至少 2 个点）
     */
    public RiverPolyline(int id,
                         int plateId,
                         int level,
                         java.util.List<Node> nodes) {
        this.id      = id;
        this.plateId = plateId;
        this.level   = level;
        this.nodes   = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(nodes));

        IntArrayList xsList = new IntArrayList(nodes.size());
        IntArrayList zsList = new IntArrayList(nodes.size());
        for (Node n : nodes) {
            xsList.add((int) Math.round(n.x));
            zsList.add((int) Math.round(n.z));
        }
        this.xs = xsList;
        this.zs = zsList;
    }

    /**
     * 弧长缓存初始化：
     *   - 以 xs/zs 作为离散折线；
     *   - segmentLengths[i] = 第 i 段长度（点 i -> i+1）；
     *   - prefixLengths[i]  = 到第 i 段起点为止的弧长；
     *   - totalLength       = 总弧长（>0，否则置为 1 防止除零）。
     */
    public void ensureLengthCache() {
        if (lengthReady) return;

        int n = xs.size();
        if (n < 2) {
            segmentLengths = new double[0];
            prefixLengths  = new double[0];
            totalLength    = 0.0;
            lengthReady    = true;
            return;
        }

        segmentLengths = new double[n - 1];
        prefixLengths  = new double[n - 1];

        double acc = 0.0;
        for (int i = 0; i < n - 1; i++) {
            int x0 = xs.getInt(i);
            int z0 = zs.getInt(i);
            int x1 = xs.getInt(i + 1);
            int z1 = zs.getInt(i + 1);

            double dx = x1 - x0;
            double dz = z1 - z0;
            double len = Math.sqrt(dx * dx + dz * dz);
            segmentLengths[i] = len;
            prefixLengths[i]  = acc;
            acc += len;
        }

        totalLength = acc <= 0.0 ? 1.0 : acc; // 避免除零
        lengthReady = true;
    }

    /**
     * 沿整条河的 0..1 参数：段 i，段内参数 u∈[0,1]。
     *
     * - i：段索引（0..n-2）
     * - u：该段内插值参数，0 在段起点、1 在段终点
     *
     * 返回：
     *   - t ∈ [0,1]，0 表示整个 polyline 的首点附近，1 表示尾点附近。
     */
    public double computeTAlong(int segmentIndex, double uOnSegment) {
        ensureLengthCache();
        if (segmentIndex < 0 || segmentIndex >= segmentLengths.length) return 0.0;
        double segLen = segmentLengths[segmentIndex];
        double prefix = prefixLengths[segmentIndex];
        double u = uOnSegment;
        if (u < 0.0) u = 0.0;
        else if (u > 1.0) u = 1.0;
        double pos = prefix + u * segLen;
        return pos / totalLength;
    }

    /**
     * 源头点（世界坐标，整格），这里约定：
     *   - nodes[0] 为河口（mouth）；
     *   - nodes[n-1] 为源头（source）；
     *   - 源头点坐标直接取整数缓存中的最后一个点。
     */
    public int getSourceX() {
        int n = xs.size();
        if (n == 0) return 0;
        return xs.getInt(n - 1);
    }

    public int getSourceZ() {
        int n = zs.size();
        if (n == 0) return 0;
        return zs.getInt(n - 1);
    }
}
