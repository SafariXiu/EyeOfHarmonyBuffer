package com.EyeOfHarmonyBuffer.client.renderer.item;

import com.EyeOfHarmonyBuffer.client.model.YuanShi;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public class ItemYuanShiRenderer implements IItemRenderer {

    private final YuanShi model = new YuanShi();
    private static final ResourceLocation TEX_YUANSHI =
        new ResourceLocation("eyeofharmonybuffer:textures/models/YuanShiCaiZhi.png");

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item,
                                         ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.4F, 1.4F, 1.4F, 1.0F);

        float time = (Minecraft.getSystemTime() % 4000L) / 4000.0F * 360.0F;

        switch (type) {
            case EQUIPPED: {
                GL11.glTranslatef(0.7F, 0.7F, 0.7F);
                GL11.glScalef(0.12F, 0.12F, 0.12F);
                GL11.glRotatef(180, 0, 0, 1);
                break;
            }
            case EQUIPPED_FIRST_PERSON: {
                GL11.glTranslatef(0.9F, 0.7F, 0.7F);
                GL11.glScalef(0.12F, 0.12F, 0.12F);
                break;
            }
            case INVENTORY: {
                GL11.glTranslatef(0.25F - 4F / 16F, 0.25F - 2F / 16F, 0F);
                GL11.glScalef(0.18F, 0.18F, 0.18F);
                GL11.glRotatef(180, 1, 0, 0);
                GL11.glRotatef(210, 1, 0, 0);
                GL11.glRotatef(45, 0, 1, 0);
                break;
            }
            case ENTITY: {
                GL11.glTranslatef(0F, 0.25F, 0F);
                GL11.glScalef(0.09F, 0.09F, -0.09F);
                break;
            }
            default:
                break;
        }

        float baseScale = 2.5F;
        GL11.glScalef(baseScale, baseScale, baseScale);
        GL11.glRotatef(time, 0F, 1F, 0F);

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(true);

        GL11.glPushMatrix();
        GL11.glScalef(0.45F, 0.45F, 0.45F);
        GL11.glTranslatef(0.0F, 0.08F, 0.0F);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        model.renderCoreSolid(1.00F, 0.85F, 0.40F, 0.98F);
        GL11.glPopMatrix();

        GL11.glDepthMask(false);
        GL11.glPushMatrix();
        GL11.glScalef(0.9F, 0.9F, 0.9F);
        GL11.glTranslatef(0.0F, 0.08F, 0.0F);

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        model.renderCoreAdd(1.00F, 0.95F, 0.65F, 0.80F);
        GL11.glPopMatrix();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEX_YUANSHI);

        GL11.glDepthMask(true);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.08F, 0.0F);

        model.renderMiddleLayer(0.52F, 0.46F, 0.35F, 0.94F);
        GL11.glPopMatrix();

        GL11.glDepthMask(false);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        model.renderShell(0.12F, 0.10F, 0.10F, 0.34F);

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
