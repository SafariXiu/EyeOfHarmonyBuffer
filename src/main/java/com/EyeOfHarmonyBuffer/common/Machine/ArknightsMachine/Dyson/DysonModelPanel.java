package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import com.EyeOfHarmonyBuffer.client.holo.HoloModel3D;
import com.EyeOfHarmonyBuffer.space.talos.client.render.DysonSphereRenderer;
import org.lwjgl.opengl.GL11;

/**
 * 世界 3D 戴森球模型展示（机器绑定的真 3D 模型，与屏面板同一锚点/朝向/数据体系）。
 * <p>
 * - 绑定/调整：由 {@code HoloPanelConfig}（screenId = "model:dyson"）在结构检查时
 *   算锚点与朝向，renderTESR 分派到 {@code HoloRender.renderModel3D}，与屏完全同款；
 * - 渲染：真 3D —— 深度测试/写入由世界管线提供，可绕行观察背面（整球渲染，无前半球裁剪），
 *   被方块/墙正常遮挡；球心在锚点，半径 29 单位 × {@link #WORLD_SCALE}；
 * - 数据：与机载屏同源（core.holoCloud/Frame/Paste），动画时钟为世界 tick，附带缓慢自转。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public class DysonModelPanel implements HoloModel3D {

    /** 基准世界缩放：戴森球半径 29 单位 → ≈1.16 格；面板配置 modelScale 为倍率（1 = 此尺寸）。 */
    public static final float WORLD_SCALE = 0.04F;
    /** 自转速度（度/tick）：≈0.5°/tick = 10°/秒，一圈 36 秒。 */
    private static final double SPIN_DEG_PER_TICK = 0.5D;
    /** 是否显示戴森云环（世界 3D 模型默认显示）。 */
    private static final boolean SHOW_CLOUDS = false;

    private final DysonCore core;

    public DysonModelPanel(DysonCore core) {
        this.core = core;
    }

    @Override
    public void draw(double ticks, float vx, float vy, float vz, float scale, float opacity) {
        float rotY = (float) ((ticks * SPIN_DEG_PER_TICK) % 360.0D);
        GL11.glPushMatrix();
        try {
            // 基准世界缩放 × 面板配置倍率
            float total = WORLD_SCALE * Math.max(0.0001F, scale);
            GL11.glScalef(total, total, total);
            // 自转必须真正施加到模型矩阵（仅补偿视线方向会导致模型静止不动）
            GL11.glRotatef(0.0F, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(rotY, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(0.0F, 0.0F, 0.0F, 1.0F);
            DysonSphereRenderer.renderAsModel(ticks, 0.0F, rotY, 0.0F, SHOW_CLOUDS,
                core.holoCloud, core.holoFrame, core.holoPaste,
                vx, vy, vz, opacity);
        } finally {
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GL11.glPopMatrix();
        }
    }
}
