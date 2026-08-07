package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverEdgeData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverRelation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.CoastClipper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 河网级剖面（图一致版）：把河网看成有向水流树，统一解决三个问题：
 *
 * 1. 纵向深度倍率（depthScale）：
 *    - ROOT 主河：points[0] 是真正源头，points[-1] 是真正入海口；
 *    - INTO_PARENT 支流：末端汇入父河，「到海距离」沿父河继续延伸；
 *    - FROM_PARENT 支流：起点从父河分出，自带入海口。
 *    接点处父子倍率由同一条水流路径决定 → 天然一致，没有深度突变。
 *
 * 2. 接点宽度一致（effectiveWidthStart/End）：
 *    - INTO_PARENT 支流在汇入点宽度 = 父河在接点的宽度；
 *    - FROM_PARENT 支流在分叉点宽度 = 父河在接点的宽度；
 *    这样「最近河段独占」的查询在接点两侧切换时河道宽度不会跳变。
 *
 * 3. 影响半径（influenceAt）：由「河谷半径 + 洪泛平原宽度」反推，
 *    洪泛平原 = 最小宽度（约 20 格）+ 河宽 × 系数，随下游变大，
 *    呈三角洲式扩张；平坦带始终明显宽于河谷，不会被越来越宽的河道挤没。
 *
 * 入海口抬升 ramp 以「海岸线」为终点（截断缓冲在海里，不参与雕刻），
 * 因此河床在海岸处就已经抬到接近近岸海床的深度。
 * 计算纯几何、不采样地形，构建成本极低。
 */
public final class RiverNetworkProfile {

    /** 入海口目标深度倍率：河床抬到海平面下约 2~4 格。 */
    private static final double MOUTH_SCALE = 0.15;

    /** 入海口抬升过渡长度（沿水流的路径距离，blocks）。 */
    private static final double MOUTH_RAMP_BLOCKS = 48.0;

    /** 源头起步深度倍率：避免源头直接全深。 */
    private static final double SOURCE_SCALE = 0.35;

    /** 源头加深过渡长度（blocks）。 */
    private static final double SOURCE_RAMP_BLOCKS = 64.0;

    /** 分叉支流从「父河接点深度」恢复到「自身深度」的过渡长度（blocks）。 */
    private static final double JUNCTION_RAMP_BLOCKS = 40.0;

    /** 洪泛平原平坦带的最小宽度（blocks）：保证小河也有可见的河岸平地。 */
    private static final double FLOODPLAIN_MIN_RING = 20.0;

    /** 洪泛平原随河宽额外增加的宽度系数（三角洲式扩张）。 */
    private static final double FLOODPLAIN_RING_PER_WIDTH = 1.0;

    /**
     * 河道最小宽度（blocks）。
     *
     * 模板里最小支流的源头宽度可能只有 1~2 格，经过大陆缩放后更窄；
     * 1 格宽的斜向河道在逐列雕刻时会变成不连续的“楼梯”断点，
     * 穿过源头湖干岸环时尤其明显。这里统一把宽度钳到 4 格以上，
     * 保证最细的支流源头也能保持连续（视觉上仍然是小河）。
     */
    private static final double MIN_RIVER_WIDTH_BLOCKS = 4.0;

    /** 单条边的剖面信息。 */
    private static final class EdgeEntry {
        final int edgeId;
        final double length;
        /** 到入海口的链式距离尾部：len*(1-p) + tail 即该边在 progress p 的到海距离。 */
        final double tailMouth;
        /** 从源头出发的链式距离头部：len*p + head 即该边在 progress p 的源距离。 */
        final double headSource;
        /** FROM_PARENT 支流专用：父河在接点处的完整深度倍率。 */
        final double parentScaleAtJunction;
        /** 是否为 FROM_PARENT（下游分流入海支流）。 */
        final boolean fromParent;
        /** 整条水流链最终是否真正入海；内流河（无入海口）为 false。 */
        final boolean mouthChain;
        /** 接点一致后的有效宽度端点（查询时按 smoothstep(progress) 插值）。 */
        final double widthStart;
        final double widthEnd;
        EdgeEntry(int edgeId, double length, double tailMouth, double headSource,
                  double parentScaleAtJunction, boolean fromParent, boolean mouthChain,
                  double widthStart, double widthEnd) {
            this.edgeId = edgeId;
            this.length = length;
            this.tailMouth = tailMouth;
            this.headSource = headSource;
            this.parentScaleAtJunction = parentScaleAtJunction;
            this.fromParent = fromParent;
            this.mouthChain = mouthChain;
            this.widthStart = widthStart;
            this.widthEnd = widthEnd;
        }
    }

    private final Map<Integer, EdgeEntry> entries;

    private RiverNetworkProfile(Map<Integer, EdgeEntry> entries) {
        this.entries = entries;
    }

    /**
     * 为某个河网构建图一致剖面。
     * 注意：CoastClipper 可能丢弃个别边，父边查找必须按 edgeId 而非列表下标。
     */
    public static RiverNetworkProfile build(RiverNetwork network) {
        Map<Integer, RiverEdgeData> edgeById = new HashMap<Integer, RiverEdgeData>();
        for (RiverEdgeData e : network.getEdges()) {
            edgeById.put(e.getId(), e);
        }

        Map<Integer, double[]> cumByEdge = new HashMap<Integer, double[]>();
        Map<Integer, Double> lenByEdge = new HashMap<Integer, Double>();

        for (RiverEdgeData e : network.getEdges()) {
            List<RiverPoint> pts = e.getPoints();
            int n = pts.size();
            double[] cum = new double[n];
            cum[0] = 0.0;
            for (int i = 1; i < n; i++) {
                RiverPoint a = pts.get(i - 1);
                RiverPoint b = pts.get(i);
                cum[i] = cum[i - 1]
                    + Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
            }
            double len = (n > 0) ? cum[n - 1] : 0.0;
            cumByEdge.put(e.getId(), cum);
            lenByEdge.put(e.getId(), len);
        }

        Map<Integer, EdgeEntry> entries = new HashMap<Integer, EdgeEntry>();

        // 父边 id 恒小于子边 id，按 id 升序处理即可保证父条目已就绪
        for (RiverEdgeData e : network.getEdges()) {
            int id = e.getId();
            double len = lenByEdge.getOrDefault(id, 0.0);

            double tail = 0.0;
            double head = 0.0;
            double parentScaleAtJunction = 1.0;
            boolean fromParent = false;
            boolean mouthChain = e.hasMouth();
            double widthStart = Math.max(e.getWidthStart(), MIN_RIVER_WIDTH_BLOCKS);
            double widthEnd = Math.max(e.getWidthEnd(), MIN_RIVER_WIDTH_BLOCKS);

            RiverRelation rel = e.getRelation();
            if (rel == RiverRelation.INTO_PARENT || rel == RiverRelation.FROM_PARENT) {
                RiverEdgeData parent = edgeById.get(e.getParentId());
                if (parent != null && !e.getPoints().isEmpty()) {
                    List<RiverPoint> pts = e.getPoints();
                    RiverPoint conn = (rel == RiverRelation.INTO_PARENT)
                        ? pts.get(pts.size() - 1)
                        : pts.get(0);

                    double[] pcum = cumByEdge.get(parent.getId());
                    double plen = lenByEdge.getOrDefault(parent.getId(), 0.0);
                    double jp = junctionProgress(parent, pcum, plen, conn.getX(), conn.getZ());

                    EdgeEntry pe = entries.get(parent.getId());
                    if (pe != null && plen > 1.0e-6) {
                        double parentDistMouth = plen * (1.0 - jp) + pe.tailMouth;
                        double parentDistSource = plen * jp + pe.headSource;
                        double parentWidthAtJp =
                            pe.widthStart + (pe.widthEnd - pe.widthStart) * smoothstep01(jp);

                        if (rel == RiverRelation.INTO_PARENT) {
                            tail = parentDistMouth;
                            // 汇入点宽度与父河一致
                            widthEnd = parentWidthAtJp;
                            // 汇入支流继承父河链的「是否真正入海」
                            mouthChain = pe.mouthChain;
                        } else {
                            head = parentDistSource;
                            parentScaleAtJunction =
                                mouthFactor(parentDistMouth) * sourceFactor(parentDistSource);
                            fromParent = true;
                            mouthChain = e.hasMouth();
                            // 分叉点宽度与父河一致
                            widthStart = parentWidthAtJp;
                        }
                    }
                }
            }

            entries.put(id, new EdgeEntry(
                id, len, tail, head, parentScaleAtJunction, fromParent, mouthChain,
                widthStart, widthEnd
            ));
        }

        return new RiverNetworkProfile(entries);
    }

    /** 查询某条边在指定 progress 处的深度倍率；无剖面时返回 1.0。 */
    public double scaleAt(int edgeId, double progress) {
        EdgeEntry en = entries.get(edgeId);
        if (en == null) {
            return 1.0;
        }

        if (en.fromParent) {
            // 下游分流入海支流：起点与父河接点深度一致，中段恢复自身深度，
            // 末端按自己的入海口抬升
            double ownMouth = mouthFactor(en.length * (1.0 - progress) + en.tailMouth);
            double blend = smoothstep01(en.length * progress / JUNCTION_RAMP_BLOCKS);
            double junctionBlend = en.parentScaleAtJunction
                + (1.0 - en.parentScaleAtJunction) * blend;
            return Math.min(ownMouth, junctionBlend);
        }

        double dMouth = en.length * (1.0 - progress) + en.tailMouth;
        double dSource = en.length * progress + en.headSource;
        if (!en.mouthChain) {
            // 内流河：终点在终端湖 / 湿地，不做入海口抬升，
            // 保持河深直到汇入水体，由水体雕刻接管。
            return sourceFactor(dSource);
        }
        return mouthFactor(dMouth) * sourceFactor(dSource);
    }

    /** 接点一致后的边宽度端点（查询时按 smoothstep(progress) 插值）。 */
    public double widthStartAt(int edgeId) {
        EdgeEntry en = entries.get(edgeId);
        return (en != null) ? en.widthStart : 0.0;
    }

    public double widthEndAt(int edgeId) {
        EdgeEntry en = entries.get(edgeId);
        return (en != null) ? en.widthEnd : 0.0;
    }

    /** 与 RiverQuery 相同的宽度插值：lerp(start, end, smoothstep(progress))。 */
    public double widthAt(int edgeId, double progress) {
        EdgeEntry en = entries.get(edgeId);
        if (en == null) {
            return 0.0;
        }
        return en.widthStart + (en.widthEnd - en.widthStart) * smoothstep01(progress);
    }

    /**
     * 某线段的影响半径：由「河谷半径 + 洪泛平原宽度」反推。
     *   - 河谷半径 = 1.5 × 河宽；
     *   - 洪泛平原宽度 = 最小宽度（FLOODPLAIN_MIN_RING）+ 河宽 × 系数；
     *   - 平坦带约为 0.32~0.39 × 影响半径（随宏群系 bankIntensity 变化），
     *     这里用最保守的 0.32 反推，保证任何河宽下平坦带都明显宽于河谷，
     *     且随下游变宽形成三角洲式扩张。
     */
    public double influenceAt(int edgeId, double progressStart, double progressEnd) {
        EdgeEntry en = entries.get(edgeId);
        if (en == null) {
            return 0.0;
        }
        double w = Math.max(
            widthAt(edgeId, progressStart),
            widthAt(edgeId, progressEnd)
        );
        double valleyRadius = 1.5 * w;
        double ring = FLOODPLAIN_MIN_RING + FLOODPLAIN_RING_PER_WIDTH * w;
        return (valleyRadius + ring) / 0.32;
    }

    /** 把子河的接点坐标投影到（截断后的）父河折线上，返回弧长 progress。 */
    private static double junctionProgress(RiverEdgeData parent, double[] cum, double parentLen,
                                           double cx, double cz) {
        List<RiverPoint> pts = parent.getPoints();
        int n = pts.size();
        if (n < 2 || parentLen <= 1.0e-6) {
            return 0.0;
        }

        double bestDistSq = Double.POSITIVE_INFINITY;
        double bestProgress = 0.0;

        for (int i = 0; i < n - 1; i++) {
            RiverPoint a = pts.get(i);
            RiverPoint b = pts.get(i + 1);
            double abx = b.getX() - a.getX();
            double abz = b.getZ() - a.getZ();
            double lenSq = abx * abx + abz * abz;

            double t;
            if (lenSq <= 1.0e-12) {
                t = 0.0;
            } else {
                t = ((cx - a.getX()) * abx + (cz - a.getZ()) * abz) / lenSq;
                if (t < 0.0) {
                    t = 0.0;
                } else if (t > 1.0) {
                    t = 1.0;
                }
            }

            double px = a.getX() + abx * t;
            double pz = a.getZ() + abz * t;
            double dx = cx - px;
            double dz = cz - pz;
            double d2 = dx * dx + dz * dz;

            if (d2 < bestDistSq) {
                bestDistSq = d2;
                double segLen = Math.sqrt(lenSq);
                bestProgress = (cum[i] + segLen * t) / parentLen;
            }
        }

        return bestProgress;
    }

    /**
     * 入海口抬升因子：以「海岸线」为终点。
     * 截断缓冲（DEFAULT_BUFFER_BLOCKS）在海里，不参与雕刻，
     * 因此到海岸线时抬升已经完成，河床与近岸海床齐平。
     */
    private static double mouthFactor(double distToMouth) {
        double d = distToMouth - CoastClipper.DEFAULT_BUFFER_BLOCKS;
        if (d < 0.0) {
            d = 0.0;
        }
        return MOUTH_SCALE + (1.0 - MOUTH_SCALE)
            * smoothstep01(d / MOUTH_RAMP_BLOCKS);
    }

    private static double sourceFactor(double distFromSource) {
        return SOURCE_SCALE + (1.0 - SOURCE_SCALE)
            * smoothstep01(distFromSource / SOURCE_RAMP_BLOCKS);
    }

    private static double smoothstep01(double t) {
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }
}
