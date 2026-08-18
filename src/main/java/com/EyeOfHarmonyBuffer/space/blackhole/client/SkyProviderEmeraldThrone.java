package com.EyeOfHarmonyBuffer.space.blackhole.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.IRenderHandler;

import org.lwjgl.opengl.GL11;

/**
 * 翡翠王座过渡天空盒：仅保留暗紫黑渐变背景（星空 / 黑洞均移除），
 * 后续将接入一套完整的自定义天空盒。
 */
public class SkyProviderEmeraldThrone extends IRenderHandler {

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc) {
        if (world == null || mc.thePlayer == null) {
            return;
        }

        Tessellator tess = Tessellator.instance;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 背景：暗紫黑渐变（不透明，完全接管天空底色）
        drawBackgroundGradient();

        GL11.glColor4f(1F, 1F, 1F, 1F);

        // 清掉天空写入的深度，避免遮挡之后的地形渲染
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDepthMask(false);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    /** NDC 全屏渐变：上深（近黑）下略浅（暗紫）。 */
    private static void drawBackgroundGradient() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_F(0.02F, 0.005F, 0.02F, 1.0F);
        tess.addVertex(-1.0F, -1.0F, 0.9F);
        tess.addVertex(1.0F, -1.0F, 0.9F);
        tess.setColorRGBA_F(0.06F, 0.015F, 0.08F, 1.0F);
        tess.addVertex(1.0F, 1.0F, 0.9F);
        tess.addVertex(-1.0F, 1.0F, 0.9F);
        tess.draw();

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

}
