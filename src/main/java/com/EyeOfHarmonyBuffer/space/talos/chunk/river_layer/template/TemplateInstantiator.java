package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverEdgeData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把模板（u,v ∈ [0,1]）实例化到某个超级大陆上。
 *
 * 新版模板是「源头在中心、向四周发散」的放射水系，
 * 因此实例化统一采用中心锚定：
 *   - 模板中心 (0.5, 0.5) 对齐超级大陆中心；
 *   - 整体按 info.angleRad 旋转（放射网旋转不改变源头分布，只做确定性朝向）；
 *   - 缩放 = info.radius * scaleFactor（模板 100000 格映射到 2×scaleFactor×半径）。
 *
 * 河道与水体的变换完全一致：先绕模板中心缩放，再旋转，最后平移到大陆中心。
 * 水体深度 / 水位偏移是 block 单位，不随缩放变化。
 */
public final class TemplateInstantiator {

    private TemplateInstantiator() {}

    public static RiverNetwork buildNetworkForSupercontinent(
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
        List<RiverBodyData> bodies = new ArrayList<RiverBodyData>();

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (TemplateEdge te : tpl.edges) {
            List<RiverPoint> pts = new ArrayList<RiverPoint>(te.pointsUV.size());

            for (TemplatePoint p : te.pointsUV) {
                double wx = transformX(p.u, p.v, cos, sin, baseScale, info.centerX);
                double wz = transformZ(p.u, p.v, cos, sin, baseScale, info.centerZ);

                pts.add(new RiverPoint(wx, wz));

                if (wx < minX) minX = wx;
                if (wx > maxX) maxX = wx;
                if (wz < minZ) minZ = wz;
                if (wz > maxZ) maxZ = wz;
            }

            float widthStart = (float) (te.widthStart * s);
            float widthEnd   = (float) (te.widthEnd   * s);
            float influence  = (float) (te.influenceRadius * s);

            edges.add(new RiverEdgeData(
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
            ));
        }

        for (TemplateBody tb : tpl.bodies) {
            double cx = transformX(tb.centerU, tb.centerV, cos, sin, baseScale, info.centerX);
            double cz = transformZ(tb.centerU, tb.centerV, cos, sin, baseScale, info.centerZ);

            double rx = tb.radiusU * baseScale;
            double rz = tb.radiusV * baseScale;

            List<RiverPoint> outline = new ArrayList<RiverPoint>(tb.outlineUV.size());
            for (TemplatePoint p : tb.outlineUV) {
                double ox = transformX(p.u, p.v, cos, sin, baseScale, info.centerX);
                double oz = transformZ(p.u, p.v, cos, sin, baseScale, info.centerZ);
                outline.add(new RiverPoint(ox, oz));

                if (ox < minX) minX = ox;
                if (ox > maxX) maxX = ox;
                if (oz < minZ) minZ = oz;
                if (oz > maxZ) maxZ = oz;
            }

            bodies.add(new RiverBodyData(
                tb.id,
                tb.type,
                tb.parentEdgeId,
                tb.tStart,
                tb.tEnd,
                cx,
                cz,
                rx,
                rz,
                tb.rotation + (float) angle,
                tb.maxDepthBlocks,
                tb.waterLevelOffset,
                outline
            ));
        }

        if (edges.isEmpty() && bodies.isEmpty()) {
            minX = maxX = info.centerX;
            minZ = maxZ = info.centerZ;
        }

        return new RiverNetwork(
            3,
            16,
            minX,
            minZ,
            maxX,
            maxZ,
            tpl.seed,
            Collections.unmodifiableList(edges),
            Collections.unmodifiableList(bodies)
        );
    }

    private static double transformX(double u, double v,
                                     double cos, double sin,
                                     double baseScale, double centerX) {
        double du = u - 0.5;
        double dv = v - 0.5;
        double lx = du * baseScale;
        double lz = dv * baseScale;
        return centerX + lx * cos - lz * sin;
    }

    private static double transformZ(double u, double v,
                                     double cos, double sin,
                                     double baseScale, double centerZ) {
        double du = u - 0.5;
        double dv = v - 0.5;
        double lx = du * baseScale;
        double lz = dv * baseScale;
        return centerZ + lx * sin + lz * cos;
    }
}
