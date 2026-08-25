package com.EyeOfHarmonyBuffer.space.talos.client.render;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 专用戴森球预览渲染器：与天空盒（{@link DysonSphereRenderer}）完全解耦。
 * <p>
 * 与天空盒\"GL 世界矩阵 + 深度缓冲 + 前半球裁剪\"不同，本渲染器在帧内仅使用屏面
 * （z=0）绘制，**全部几何变换与前后遮挡都在 CPU 侧完成**：
 * <ul>
 *   <li>旋转：所有顶点按 (rotX, rotY, rotZ) 在 CPU 旋转，得到当前视角下的 (x, y, z')；</li>
 *   <li>遮挡：把壳面板/梁/节点收集成图元列表，按旋转后深度 z' 全局排序（远→近绘制）。
 *       球面流形图元互不交错，画家算法即精确遮挡——这是\"真正的前后关系\"；</li>
 *   <li>整球渲染：前面（z' ≥ 0）亮色实心，背面从壳缺口透出（暗一档）——
 *       转一圈整球壳面完整可见，无任何剔除感；</li>
 *   <li>装饰（面板高亮线/核心点、梁辉光/能量点、节点辉光）随所属图元绘制。</li>
 * </ul>
 * 调用方（HoloCanvas.modelDyson）负责 translate/scale 到预览位置、push/pop 矩阵与
 * 属性恢复；本类只负责 GL 混合/贴图清理与图元绘制。圆盘底座、空进度骨架、完工光晕均在此实现。
 */
public final class DysonPreviewRenderer {

    private static final Logger LOGGER = LogManager.getLogger("DysonPreview");

    private DysonPreviewRenderer() {}

    // ---- 结构与视觉常量（风格与天空盒一致，颜色按 GUI 提亮） ----

    /** 节点正多边形外接半径推导：与节点边长 = FRAME_THICKNESS 一致。 */
    private static final double FRAME_THICKNESS = 2.5D;
    /** 节点板外移量：略大于面板外表面。 */
    private static final double NODE_OUTER_OFFSET = 0.44D;
    /** 棱上能量光点速度（每 tick 沿棱前进比例）。 */
    private static final double BEAM_ENERGY_SPEED = 0.02D;

    /** 前面（朝向观察者）与背面（从缺口透出）的分界：旋转后深度 z' = 0（赤道面）。 */
    private static final float FRONT_CLIP = 0.0F;

    // 颜色（GUI 提亮冷色）
    private static final float C_PANEL_FRONT_R = 0.28F, C_PANEL_FRONT_G = 0.37F, C_PANEL_FRONT_B = 0.60F;
    private static final float C_PANEL_BACK_R = 0.15F, C_PANEL_BACK_G = 0.20F, C_PANEL_BACK_B = 0.33F;
    private static final float C_BEAM_FRONT_R = 0.35F, C_BEAM_FRONT_G = 0.46F, C_BEAM_FRONT_B = 0.74F;
    private static final float C_BEAM_BACK_R = 0.20F, C_BEAM_BACK_G = 0.26F, C_BEAM_BACK_B = 0.42F;
    private static final float C_NODE_FRONT_R = 0.42F, C_NODE_FRONT_G = 0.55F, C_NODE_FRONT_B = 0.85F;
    private static final float C_NODE_BACK_R = 0.23F, C_NODE_BACK_G = 0.30F, C_NODE_BACK_B = 0.47F;

    /** 图元：depth() = 旋转后真实深度 z'（同一壳面同一层级内远→近排）；layer() = 模型几何层级
     *  （0 框架/梁 < 1 贴片/面板 < 2 节点）。draw() 以 z=0 平贴绘制。
     *  <p>
     *  排序键 = (壳面 side, 层级 layer, 深度 z') 的三元字典序：
     *  <ul>
     *   <li>壳面 side：z' ≥ 0 为前面壳（观察者一侧），恒在背面壳（z' < 0）之后绘制 → 近盖远，
     *       正面框架梁也一定盖住背面贴片（前后遮挡正确）；</li>
     *   <li>层级 layer：同一壳面附近按模型几何层级——框架最里、贴片中间、节点最外 → 同组恒定；</li>
     *   <li>深度 z'：同壳面同层级内远→近 → 层内遮挡也正确。</li>
     *  </ul>
     *  三元字典序严格传递 → 无 TimSort 契约异常，无桶边界错位。 */
    private interface Prim {
        float depth();

        int layer();

        void draw();
    }

    /** 旋转矩阵（行主序 3×3，与调用方视角约定 rotX→rotY→rotZ）。 */
    private static float[] rotMatrix(float rotX, float rotY, float rotZ) {
        double ax = Math.toRadians(rotX), ay = Math.toRadians(rotY), az = Math.toRadians(rotZ);
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double cy = Math.cos(ay), sy = Math.sin(ay);
        double cz = Math.cos(az), sz = Math.sin(az);
        float[] r = new float[9];
        // 行0 = ROTY x ROTZ（x 分量）
        r[0] = (float) (cy * cz);
        r[1] = (float) (-cy * sz);
        r[2] = (float) sy;
        // 行1
        r[3] = (float) (cx * sz + sx * sy * cz);
        r[4] = (float) (cx * cz - sx * sy * sz);
        r[5] = (float) (-sx * cy);
        // 行2
        r[6] = (float) (sx * sz - cx * sy * cz);
        r[7] = (float) (sx * cz + cx * sy * sz);
        r[8] = (float) (cx * cy);
        return r;
    }

    /** 旋转一个向量 → 当前视角局部坐标。 */
    private static float[] rotate(float[] m, double x, double y, double z) {
        return new float[] {
            (float) (m[0] * x + m[1] * y + m[2] * z),
            (float) (m[3] * x + m[4] * y + m[5] * z),
            (float) (m[6] * x + m[7] * y + m[8] * z)
        };
    }

    /** 节点（正五/六边形）角点数。 */
    private static int nodeSides(int index) {
        return index < 12 ? 5 : 6;
    }

    /** 节点切平面角点（单位坐标），radiusScale=1 为标准节点半径。 */
    private static double[][] nodeCorners(int index, double radiusScale) {
        double[] n = DysonSphereRenderer.vertices()[index];
        double ux, uy = 0.0, uz;
        if (Math.abs(n[1]) < 0.9D) {
            ux = n[2];
            uz = -n[0];
        } else {
            ux = 1.0;
            uz = 0.0;
        }
        double ul = Math.sqrt(ux * ux + uz * uz);
        ux /= ul;
        uz /= ul;
        double vx = n[1] * uz - n[2] * uy;
        double vy = n[2] * ux - n[0] * uz;
        double vz = n[0] * uy - n[1] * ux;
        int sides = nodeSides(index);
        double radius = FRAME_THICKNESS / (2.0D * Math.sin(Math.PI / sides)) * radiusScale;
        double outer = DysonSphereRenderer.getSphereRadius() + NODE_OUTER_OFFSET;
        double[][] out = new double[sides][3];
        for (int k = 0; k < sides; k++) {
            double a = Math.PI * 2.0D * k / sides;
            double ca = Math.cos(a), sa = Math.sin(a);
            out[k][0] = n[0] * outer + radius * (ca * ux + sa * vx);
            out[k][1] = n[1] * outer + radius * (ca * uy + sa * vy);
            out[k][2] = n[2] * outer + radius * (ca * uz + sa * vz);
        }
        return out;
    }

    // ==================== 图元实现 ====================

    /** 圆盘底座（最远）：深蓝半透明盘 + 亮环。 */
    private static final class DiscPrim implements Prim {
        @Override
        public float depth() {
            return -1000.0F;
        }

        @Override
        public int layer() {
            return 0;
        }

        @Override
        public void draw() {
            double r = DysonSphereRenderer.getSphereRadius() * 1.06D;
            int seg = 64;
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glColor4f(0.07F, 0.16F, 0.32F, 0.85F);
            GL11.glVertex3f(0, 0, 0);
            for (int i = 0; i <= seg; i++) {
                double a = Math.PI * 2.0D * i / seg;
                GL11.glColor4f(0.07F, 0.16F, 0.32F, 0.78F);
                GL11.glVertex3d(Math.cos(a) * r, Math.sin(a) * r, 0);
            }
            GL11.glEnd();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glColor4f(0.35F, 0.68F, 1.0F, 0.85F);
            for (int i = 0; i < seg; i++) {
                double a = Math.PI * 2.0D * i / seg;
                GL11.glVertex3d(Math.cos(a) * r, Math.sin(a) * r, 0);
            }
            GL11.glEnd();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /** 空进度骨架线。 */
    private static final class SkeletonPrim implements Prim {
        private final float x0, y0, x1, y1;
        private final float depth;

        SkeletonPrim(float[] m, double[] a, double[] b) {
            float[] ra = rotate(m, a[0], a[1], a[2]);
            float[] rb = rotate(m, b[0], b[1], b[2]);
            this.x0 = ra[0];
            this.y0 = ra[1];
            this.x1 = rb[0];
            this.y1 = rb[1];
            this.depth = Math.min(ra[2], rb[2]) - 20.0F;
        }

        @Override
        public float depth() {
            return depth;
        }

        @Override
        public int layer() {
            return 0;
        }

        @Override
        public void draw() {
            GL11.glLineWidth(1.0F);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(0.32F, 0.52F, 0.92F, 0.22F);
            GL11.glVertex3f(x0, y0, 0);
            GL11.glVertex3f(x1, y1, 0);
            GL11.glEnd();
        }
    }

    /** 完工光晕（最远，加法混合）：壳外圈渐隐幽蓝光。 */
    private static final class GlowPrim implements Prim {
        private final double pulse;

        GlowPrim(double animTime) {
            this.pulse = 0.5D + 0.5D * Math.sin(animTime * 0.01D);
        }

        @Override
        public float depth() {
            return -900.0F;
        }

        @Override
        public int layer() {
            return 0;
        }

        @Override
        public void draw() {
            float breathe = (float) (0.75D + 0.25D * pulse);
            double r0 = DysonSphereRenderer.getSphereRadius() * 1.04D;
            double r1 = DysonSphereRenderer.getSphereRadius() * 1.6D;
            int layers = 14;
            int seg = 64;
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            for (int i = layers - 1; i >= 0; i--) {
                double t = i / (double) (layers - 1);
                double ra = r0 + (r1 - r0) * t;
                double rb = r0 + (r1 - r0) * (t + 1.0D / layers);
                float alpha = (float) (0.09D * (1.0D - t) * (1.0D - t) * breathe);
                if (alpha <= 0.001F) {
                    continue;
                }
                GL11.glColor4f(0.15F, 0.45F, 0.95F, alpha);
                GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
                for (int k = 0; k <= seg; k++) {
                    double a = Math.PI * 2.0D * k / seg;
                    double c = Math.cos(a), s = Math.sin(a);
                    GL11.glVertex3d(c * ra, s * ra, 0);
                    GL11.glVertex3d(c * rb, s * rb, 0);
                }
                GL11.glEnd();
            }
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /** 三角面板（壳片）：实心 + 高亮中线 + 核心点。 */
    private static final class PanelPrim implements Prim {
        private final float[] x = new float[3];
        private final float[] y = new float[3];
        private final float depth;
        private final boolean back;

        PanelPrim(float[] m, double[][] verts, int[] face) {
            double r = DysonSphereRenderer.getSphereRadius();
            double mx = 0, my = 0, mz = 0;
            for (int k = 0; k < 3; k++) {
                double[] v = verts[face[k]];
                float[] p = rotate(m, v[0] * r, v[1] * r, v[2] * r);
                x[k] = p[0];
                y[k] = p[1];
                mx += p[0];
                my += p[1];
                mz += p[2];
            }
            float centerZ = (float) (mz / 3.0D);
            this.depth = centerZ;
            this.back = centerZ < FRONT_CLIP;
        }

        @Override
        public float depth() {
            return depth;
        }

        @Override
        public int layer() {
            return 1;
        }

        @Override
        public void draw() {
            float r = back ? C_PANEL_BACK_R : C_PANEL_FRONT_R;
            float g = back ? C_PANEL_BACK_G : C_PANEL_FRONT_G;
            float b = back ? C_PANEL_BACK_B : C_PANEL_FRONT_B;
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glColor4f(r, g, b, 1.0F);
            GL11.glVertex3f(x[0], y[0], 0);
            GL11.glColor4f(r, g, b, 1.0F);
            GL11.glVertex3f(x[1], y[1], 0);
            GL11.glColor4f(r, g, b, 1.0F);
            GL11.glVertex3f(x[2], y[2], 0);
            GL11.glEnd();
            // 高亮中线（顶点到对边中点）+ 核心点
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(0.55F, 0.75F, 1.0F, 0.55F);
            for (int k = 0; k < 3; k++) {
                float ax = x[k], ay = y[k];
                float bx = x[(k + 1) % 3], by = y[(k + 1) % 3];
                float cx = x[(k + 2) % 3], cy = y[(k + 2) % 3];
                GL11.glVertex3f(ax, ay, 0);
                GL11.glVertex3f((bx + cx) * 0.5F, (by + cy) * 0.5F, 0);
            }
            GL11.glEnd();
            float centerX = (x[0] + x[1] + x[2]) / 3.0F;
            float centerY = (y[0] + y[1] + y[2]) / 3.0F;
            float s = 0.45F;
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(0.70F, 0.85F, 1.0F, 0.90F);
            GL11.glVertex3f(centerX + s, centerY, 0);
            GL11.glVertex3f(centerX, centerY + s, 0);
            GL11.glVertex3f(centerX - s, centerY, 0);
            GL11.glVertex3f(centerX, centerY - s, 0);
            GL11.glEnd();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /** 棱（梁）：外圈辉光 + 实体梁 + 中心亮线 + 能量流动光点。 */
    private static final class BeamPrim implements Prim {
        private final float x0, y0, x1, y1;
        private final float depth;
        private final boolean back;
        private final float ex, ey;
        private final float pulseAlpha;

        BeamPrim(float[] m, double[][] verts, int[] ev, double animTime, double energyPhase) {
            double r = DysonSphereRenderer.getSphereRadius();
            float[] pa = rotate(m, verts[ev[0]][0] * r, verts[ev[0]][1] * r, verts[ev[0]][2] * r);
            float[] pb = rotate(m, verts[ev[1]][0] * r, verts[ev[1]][1] * r, verts[ev[1]][2] * r);
            this.x0 = pa[0];
            this.y0 = pa[1];
            this.x1 = pb[0];
            this.y1 = pb[1];
            float midZ = (pa[2] + pb[2]) * 0.5F;
            this.depth = midZ;
            this.back = midZ < FRONT_CLIP;
            if (energyPhase >= 0.0D) {
                double t = (animTime * BEAM_ENERGY_SPEED + energyPhase) % 1.0D;
                this.ex = (float) (x0 + (x1 - x0) * t);
                this.ey = (float) (y0 + (y1 - y0) * t);
                this.pulseAlpha = Math.min(1.0F, 0.5F + (float) Math.abs(Math.sin(t * Math.PI * 0.5D)) * 0.5F);
            } else {
                this.ex = -1;
                this.ey = -1;
                this.pulseAlpha = 0.0F;
            }
        }

        /** 沿线段绘矩形条带（2D 平贴）：t0w/t1w 为两端总宽（世界单位）。 */
        private void quadStrip(float t0w, float t1w, float r, float g, float b, float a) {
            float dx = x1 - x0, dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1.0e-4F) {
                return;
            }
            dx /= len;
            dy /= len;
            float nx = -dy, ny = dx;
            float h0 = t0w * 0.5F, h1 = t1w * 0.5F;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex3f(x0 + nx * h0, y0 + ny * h0, 0);
            GL11.glVertex3f(x0 - nx * h0, y0 - ny * h0, 0);
            GL11.glVertex3f(x1 - nx * h1, y1 - ny * h1, 0);
            GL11.glVertex3f(x1 + nx * h1, y1 + ny * h1, 0);
            GL11.glEnd();
        }

        @Override
        public float depth() {
            return depth;
        }

        @Override
        public int layer() {
            return 0;
        }

        @Override
        public void draw() {
            float r = back ? C_BEAM_BACK_R : C_BEAM_FRONT_R;
            float g = back ? C_BEAM_BACK_G : C_BEAM_FRONT_G;
            float b = back ? C_BEAM_BACK_B : C_BEAM_FRONT_B;
            // 外圈辉光（加法）
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            quadStrip(2.0F, 2.0F, 0.30F, 0.55F, 1.0F, 0.14F);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // 实体梁（厚 0.9 世界单位，sc≈1.724 → 约 1.6px）
            quadStrip(0.9F, 0.9F, r, g, b, 1.0F);
            // 中心亮线
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(0.72F, 0.86F, 1.0F, 0.85F);
            GL11.glVertex3f(x0, y0, 0);
            GL11.glVertex3f(x1, y1, 0);
            GL11.glEnd();
            // 能量流动光点
            if (ex >= -0.5F) {
                float s = 1.1F;
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glColor4f(0.30F, 0.60F, 1.0F, 0.25F * pulseAlpha);
                GL11.glVertex3f(ex + s * 1.6F, ey, 0);
                GL11.glVertex3f(ex, ey + s * 1.6F, 0);
                GL11.glVertex3f(ex - s * 1.6F, ey, 0);
                GL11.glVertex3f(ex, ey - s * 1.6F, 0);
                GL11.glColor4f(0.70F, 0.90F, 1.0F, 0.95F * pulseAlpha);
                GL11.glVertex3f(ex + s, ey, 0);
                GL11.glVertex3f(ex, ey + s, 0);
                GL11.glVertex3f(ex - s, ey, 0);
                GL11.glVertex3f(ex, ey - s, 0);
                GL11.glEnd();
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
        }
    }

    /** 节点：实心多边形 + 辉光 + 亮边。 */
    private static final class NodePrim implements Prim {
        private final float cx, cy;
        private final float[] xs, ys;
        private final float depth;
        private final boolean back;

        NodePrim(float[] m, int index) {
            double[][] corners = nodeCorners(index, 1.0D);
            double mx = 0, my = 0, mz = 0;
            int sides = corners.length;
            this.xs = new float[sides];
            this.ys = new float[sides];
            float[] center = rotate(m,
                DysonSphereRenderer.vertices()[index][0] * DysonSphereRenderer.getSphereRadius(),
                DysonSphereRenderer.vertices()[index][1] * DysonSphereRenderer.getSphereRadius(),
                DysonSphereRenderer.vertices()[index][2] * DysonSphereRenderer.getSphereRadius());
            this.cx = center[0];
            this.cy = center[1];
            for (int k = 0; k < sides; k++) {
                float[] p = rotate(m, corners[k][0], corners[k][1], corners[k][2]);
                xs[k] = p[0];
                ys[k] = p[1];
                mx += p[0];
                my += p[1];
                mz += p[2];
            }
            float centerZ = (float) (mz / sides);
            this.depth = centerZ;
            this.back = centerZ < FRONT_CLIP;
        }

        @Override
        public float depth() {
            return depth;
        }

        @Override
        public int layer() {
            return 2;
        }

        @Override
        public void draw() {
            int sides = xs.length;
            float r = back ? C_NODE_BACK_R : C_NODE_FRONT_R;
            float g = back ? C_NODE_BACK_G : C_NODE_FRONT_G;
            float b = back ? C_NODE_BACK_B : C_NODE_FRONT_B;
            // 实心面
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glColor4f(r, g, b, 1.0F);
            GL11.glVertex3f(cx, cy, 0);
            for (int k = 0; k < sides; k++) {
                GL11.glVertex3f(xs[k], ys[k], 0);
            }
            GL11.glVertex3f(xs[0], ys[0], 0);
            GL11.glEnd();
            // 辉光（加法，外扩 1.5 倍）
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glColor4f(0.30F, 0.55F, 1.0F, 0.16F);
            GL11.glVertex3f(cx, cy, 0);
            for (int k = 0; k < sides; k++) {
                GL11.glVertex3f(cx + (xs[k] - cx) * 1.5F, cy + (ys[k] - cy) * 1.5F, 0);
            }
            GL11.glVertex3f(cx + (xs[0] - cx) * 1.5F, cy + (ys[0] - cy) * 1.5F, 0);
            GL11.glEnd();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // 亮边
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(0.62F, 0.72F, 0.92F, 0.9F);
            for (int k = 0; k < sides; k++) {
                GL11.glVertex3f(xs[k], ys[k], 0);
                GL11.glVertex3f(xs[(k + 1) % sides], ys[(k + 1) % sides], 0);
            }
            GL11.glEnd();
        }
    }

    // ==================== 入口 ====================

    /**
     * GUI 预览主入口：调用方已设置好 translate(cx,cy)+scale（球半径 → size/2 像素），
     * 并在 pushAttrib 保护中。本方法绘制圆盘底座与整个戴森球（CPU 旋转 + 深度排序）。
     *
     * @param animTime 动画时钟；rotX/rotY/rotZ 自转角（度，X→Y→Z）
     * @param showClouds 预留（当前不画云，恒 false）
     * @param cloud/frame/paste 实时进度（与屏上进度条同源）
     */
    public static void render(double animTime, float rotX, float rotY, float rotZ, boolean showClouds,
                              int cloud, int frame, int paste) {
        try {
            renderInner(animTime, rotX, rotY, rotZ, showClouds, cloud, frame, paste);
        } catch (Throwable t) {
            // 渲染异常只跳过本帧并留档 —— 绝不能让它炸掉渲染线程/游戏
            LOGGER.error("[DysonPreview] render failed, frame skipped", t);
        }
    }

    private static void renderInner(double animTime, float rotX, float rotY, float rotZ, boolean showClouds,
                                    int cloud, int frame, int paste) {
        // 平贴预览：纯色几何需显式关闭贴图/光照/雾（屏的 2D 管线会残留 GL_TEXTURE_2D 与旧贴图）
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        // 关键：屏绘制管线开着 polygonOffset(-2)，其偏移量随图元深度斜率变化，
        // 会把常量层带 z 打乱（大斜面板被偏移进别的层）→ 闪烁/乱层。平贴预览自带层带，禁用它。
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        // 不写深度：图元平贴于屏面 z=0，与屏内容等深（LEQUAL 深度测试由外部保持，被墙正常遮挡）
        GL11.glDepthMask(false);

        float[] m = rotMatrix(rotX, rotY, rotZ);

        double[][] verts = DysonSphereRenderer.vertices();
        int[][] faces = DysonSphereRenderer.faces();
        int[][] edges = DysonSphereRenderer.edges();

        float pasteCoverage = paste <= 0 ? 0.0F : Math.min(1.0F, paste / (float) DysonSphereState.PASTE_COMPLETE);
        boolean completed = paste >= DysonSphereState.PASTE_COMPLETE;
        boolean empty = frame <= 0 && paste <= 0 && cloud <= 0;

        // 全部图元（含底座），按 (深度桶, 层级) 字典序统一排序
        List<Prim> prims = new ArrayList<>(700);

        prims.add(new DiscPrim());
        if (completed) {
            prims.add(new GlowPrim(animTime));
        }
        // 空进度骨架（打底层）
        if (empty) {
            for (int[] e : DysonSphereRenderer.edges()) {
                prims.add(new SkeletonPrim(m, verts[e[0]], verts[e[1]]));
            }
        }

        // 面板（环带式顺序铺满 180 面的一部分）
        int faceCount = frame > 0 ? (int) Math.round(faces.length * pasteCoverage) : 0;
        if (faceCount > 0) {
            int[] panelOrder = DysonSphereRenderer.panelOrder();
            for (int i = 0; i < faceCount; i++) {
                int f = panelOrder[i];
                prims.add(new PanelPrim(m, verts, faces[f]));
            }
        }

        // 梁（BFS 生长顺序）
        int edgeCount = frame >= DysonSphereState.FRAME_MIN
            ? edges.length
            : (int) Math.round(edges.length * (double) frame / DysonSphereState.FRAME_MIN);
        boolean showEnergy = pasteCoverage >= 0.5F;
        if (edgeCount > 0) {
            int[] edgeOrder = DysonSphereRenderer.edgeOrder();
            for (int i = 0; i < edgeCount; i++) {
                int e = edgeOrder[i];
                double phase = showEnergy ? (e * 0.618033988749895D) % 1.0D : -1.0D;
                prims.add(new BeamPrim(m, verts, edges[e], animTime, phase));
            }
        }

        // 节点（梁端点 + 面板端点）
        boolean[] nodeShown = new boolean[verts.length];
        if (edgeCount > 0) {
            int[] edgeOrder = DysonSphereRenderer.edgeOrder();
            for (int i = 0; i < edgeCount; i++) {
                int[] ev = edges[edgeOrder[i]];
                nodeShown[ev[0]] = true;
                nodeShown[ev[1]] = true;
            }
        }
        if (faceCount > 0) {
            int[] panelOrder = DysonSphereRenderer.panelOrder();
            for (int i = 0; i < faceCount; i++) {
                int[] face = faces[panelOrder[i]];
                nodeShown[face[0]] = true;
                nodeShown[face[1]] = true;
                nodeShown[face[2]] = true;
            }
        }
        for (int i = 0; i < nodeShown.length; i++) {
            if (nodeShown[i]) {
                prims.add(new NodePrim(m, i));
            }
        }

        // 排序：三元字典序 (壳面 side, 层级 layer, 深度 z')，远→近（升序）。
        // - side：前面壳（z' ≥ 0）恒在背面壳后绘制 → 近盖远（前后遮挡正确）；
        // - layer：同壳面内框架(0) < 贴片(1) < 节点(2) → 同组层级恒定不互抢；
        // - depth：同壳面同层级内远→近 → 层内遮挡正确。
        prims.sort((a, b) -> {
            int sa = a.depth() >= 0 ? 1 : 0;
            int sb = b.depth() >= 0 ? 1 : 0;
            if (sa != sb) {
                return Integer.compare(sa, sb);
            }
            int la = a.layer();
            int lb = b.layer();
            if (la != lb) {
                return Integer.compare(la, lb);
            }
            return Float.compare(a.depth(), b.depth());
        });

        for (Prim p : prims) {
            p.draw();
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
    }
}
