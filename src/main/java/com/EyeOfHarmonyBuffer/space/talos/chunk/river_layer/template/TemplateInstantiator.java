package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.*;

import java.util.ArrayList;
import java.util.List;

public final class TemplateInstantiator {

    private TemplateInstantiator() {}

    private static final class SourceInfo {
        final TemplateEdge edge;
        final TemplatePoint sourcePoint;
        final TemplatePoint flowTargetPoint; // 用来估计主流方向

        SourceInfo(TemplateEdge edge,
                   TemplatePoint sourcePoint,
                   TemplatePoint flowTargetPoint) {
            this.edge = edge;
            this.sourcePoint = sourcePoint;
            this.flowTargetPoint = flowTargetPoint;
        }
    }

    /**
     * 尝试在模板中找到“主源头”和主流大致方向。
     *
     * 约定：
     *   - pointsUV 是从上游到下游排序；
     *   - hasSource=true 且 parentId<0 的边优先作为主干；
     *   - mouth 点暂时取同一条边的最后一个点。
     */
    private static SourceInfo findMainSource(RiverTemplate tpl) {
        TemplateEdge candidate = null;

        for (TemplateEdge e : tpl.edges) {
            if (e.type == RiverType.MAIN
                && e.relation == RiverRelation.ROOT
                && e.hasSource
                && e.hasMouth
                && e.parentId < 0) {
                candidate = e;
                break;
            }
        }

        if (candidate == null) {
            for (TemplateEdge e : tpl.edges) {
                if (e.hasSource) {
                    candidate = e;
                    break;
                }
            }
        }

        if (candidate == null || candidate.pointsUV.isEmpty()) {
            return null;
        }

        TemplatePoint src = candidate.pointsUV.get(0);
        TemplatePoint target = candidate.pointsUV.get(candidate.pointsUV.size() - 1);

        return new SourceInfo(candidate, src, target);
    }

    /**
     * 给定一个 RiverTemplate 和一块超级大陆的 SupercontinentInfo，
     * 构造一份“以超级大陆中心为主源头”的 RiverNetwork。
     *
     * @param tpl         模板（u,v ∈ [0,1] 空间）
     * @param info        超级大陆中心 / 半径 / 朝向
     * @param scaleFactor 控制河网占用大陆半径比例（例如 0.8 表示河网整体半径约为 0.8 * radius）
     */
    public static RiverNetwork buildNetworkForSupercontinent(
        RiverTemplate tpl,
        SupercontinentInfo info,
        double scaleFactor
    ) {
        SourceInfo srcInfo = findMainSource(tpl);
        if (srcInfo == null) {
            return buildNetworkCentered(tpl, info, scaleFactor);
        }

        double su = srcInfo.sourcePoint.u;
        double sv = srcInfo.sourcePoint.v;

        double dxT = srcInfo.flowTargetPoint.u - su;
        double dzT = srcInfo.flowTargetPoint.v - sv;
        if (dxT == 0.0 && dzT == 0.0) {
            dxT = 0.0;
            dzT = 1.0;
        }

        double templateAngle = Math.atan2(dzT, dxT);
        double desiredAngle  = info.angleRad;
        double rotateAngle   = desiredAngle - templateAngle;

        double cos = Math.cos(rotateAngle);
        double sin = Math.sin(rotateAngle);

        double baseScale = info.radius * scaleFactor;
        double s = baseScale / RiverTemplate.WORLD_SIZE;

        List<RiverEdgeData> edges = new ArrayList<RiverEdgeData>();

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (TemplateEdge te : tpl.edges) {
            List<RiverPoint> pts = new ArrayList<RiverPoint>(te.pointsUV.size());

            for (TemplatePoint p : te.pointsUV) {
                double du = p.u - su;
                double dv = p.v - sv;

                double lx = du * baseScale;
                double lz = dv * baseScale;

                double rx = lx * cos - lz * sin;
                double rz = lx * sin + lz * cos;

                double wx = info.centerX + rx;
                double wz = info.centerZ + rz;

                pts.add(new RiverPoint(wx, wz));

                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wz < minZ) minZ = wz;
                if (wz > maxZ) maxZ = wz;
            }

            float widthStart = (float) (te.widthStart * s);
            float widthEnd   = (float) (te.widthEnd   * s);
            float influence  = (float) (te.influenceRadius * s);

            RiverEdgeData edgeData = new RiverEdgeData(
                te.id,
                te.parentId,
                te.parentSegment,
                te.type,
                te.relation,
                te.hasSource,
                te.hasMouth,
                te.visualWidthScale,
                widthStart,
                widthEnd,
                influence,
                te.parentT,
                pts
            );
            edges.add(edgeData);
        }

        if (edges.isEmpty()) {
            minX = maxX = info.centerX;
            minZ = maxZ = info.centerZ;
        }

        return new RiverNetwork(
            2,
            16,
            minX,
            minZ,
            maxX,
            maxZ,
            tpl.seed,
            java.util.Collections.unmodifiableList(edges)
        );
    }

    private static RiverNetwork buildNetworkCentered(
        RiverTemplate tpl,
        SupercontinentInfo info,
        double scaleFactor
    ) {
        double angle = info.angleRad;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double baseScale = info.radius * scaleFactor;
        double s = baseScale / RiverTemplate.WORLD_SIZE;

        List<RiverEdgeData> edges = new ArrayList<RiverEdgeData>();

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (TemplateEdge te : tpl.edges) {
            List<RiverPoint> pts = new ArrayList<RiverPoint>(te.pointsUV.size());

            for (TemplatePoint p : te.pointsUV) {
                double du = p.u - 0.5;
                double dv = p.v - 0.5;

                double lx = du * baseScale;
                double lz = dv * baseScale;

                double rx = lx * cos - lz * sin;
                double rz = lx * sin + lz * cos;

                double wx = info.centerX + rx;
                double wz = info.centerZ + rz;

                pts.add(new RiverPoint(wx, wz));

                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wz < minZ) minZ = wz;
                if (wz > maxZ) maxZ = wz;
            }

            float widthStart = (float) (te.widthStart * s);
            float widthEnd   = (float) (te.widthEnd   * s);
            float influence  = (float) (te.influenceRadius * s);

            RiverEdgeData edgeData = new RiverEdgeData(
                te.id,
                te.parentId,
                te.parentSegment,
                te.type,
                te.relation,
                te.hasSource,
                te.hasMouth,
                te.visualWidthScale,
                widthStart,
                widthEnd,
                influence,
                te.parentT,
                pts
            );
            edges.add(edgeData);
        }

        if (edges.isEmpty()) {
            minX = maxX = info.centerX;
            minZ = maxZ = info.centerZ;
        }

        return new RiverNetwork(
            2,
            16,
            minX,
            minZ,
            maxX,
            maxZ,
            tpl.seed,
            java.util.Collections.unmodifiableList(edges)
        );
    }
}
