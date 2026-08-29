package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.client.transition.TransitionClientState;
import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界加载界面（"纯泥土背景"，换维时由 mc.loadWorld 触发）落地揭幕适配（方案 A）：
 * 维度转场白幕激活时（coverWhite &gt; 0），用全屏白替代泥土背景 + 进度条。
 * 与 GuiDownloadTerrain mixin 互补：GuiDownloadTerrain 是"Downloading terrain..."，
 * 这个是世界加载时的 dirt 界面（setLoadingProgress 绘制）。
 * 用 Tessellator（与原方法同路径，实测可用）画白；cancel 跳过原 dirt 绘制。
 * 注意：不写 FBO 收尾（field_146588_g 只在原方法内 bind/unbind，cancel 后保持主 FBO 绑定自洽）。
 */
@Mixin(value = LoadingScreenRenderer.class, remap = false)
public class MixinLoadingScreenRenderer {

    @Inject(method = "setLoadingProgress", at = @At("HEAD"), cancellable = true)
    private void eohb$coverLoadingProgress(int progress, CallbackInfo ci) {
        float cover = TransitionClientState.coverWhite();
        if (cover <= 0.001F) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int scale = res.getScaleFactor();
        int w = res.getScaledWidth() * scale;
        int h = res.getScaledHeight() * scale;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, w, h, 0.0D, 100.0D, 300.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -200.0F);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_F(1.0F, 1.0F, 1.0F, Math.min(1.0F, cover));
        tess.addVertex(0.0D, 0.0D, 0.0D);
        tess.addVertex(w, 0.0D, 0.0D);
        tess.addVertex(w, h, 0.0D);
        tess.addVertex(0.0D, h, 0.0D);
        tess.draw();

        GL11.glDepthMask(true);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopAttrib();

        ci.cancel();
        EyeOfHarmonyBuffer.LOGGER.info("[EOHB] LoadingScreenRenderer covered (progress={})", progress);
    }
}
