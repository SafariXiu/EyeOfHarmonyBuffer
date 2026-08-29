package com.EyeOfHarmonyBuffer.space.talos.station;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import org.lwjgl.opengl.GL11;

import com.EyeOfHarmonyBuffer.space.talos.client.resources.ResourcesDimensions;

import galaxyspace.core.render.sky.SkyProviderBaseSS;

/**
 * 塔罗斯-1 空间站天空盒：星空/太阳/星系由 GS 基类绘制，
 * 这里补画环绕的母行星——塔罗斯-1（气态巨行星），参考 GS 土星空间站天空的绘制方式。
 *
 * <p>注意：GS 的 {@code drawTexture} 只绑定纹理并画 quad，不处理 alpha（GS 自家行星贴图是
 * 深色不透明底，在黑色太空里看不出问题）；我们的图标是透明背景 PNG，必须先启用
 * GL_BLEND + GL_ALPHA_TEST，否则透明区会显示为白框、边缘线性过滤出脏色。
 */
public class SkyProviderTalos2Station extends SkyProviderBaseSS {

    @Override
    protected void setup() {
        // 使用基类默认星空/太阳配置
    }

    @Override
    protected void renderCelestialBodies(float partialTicks, WorldClient world, Minecraft mc) {
        // 塔罗斯-1（气态巨行星）：空间站环绕的母行星
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.25F);

        GL11.glPushMatrix();
        GL11.glScalef(0.6F, 0.6F, 0.6F);
        this.spin();
        this.drawTexture(ResourcesDimensions.TalosSky, 100.0D);
        GL11.glPopMatrix();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
    }
}
