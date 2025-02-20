package com.EyeOfHarmonyBuffer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.IModelCustom;
import org.lwjgl.opengl.GL11;

public class ItemWindmillRenderer implements IItemRenderer {

    private IModelCustom model;
    private ResourceLocation texture;

    public ItemWindmillRenderer(IModelCustom model, ResourceLocation texture) {
        this.model = model;
        this.texture = texture;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();

        // 根据渲染类型调整位置和缩放
        switch(type) {
            case ENTITY:
                GL11.glScaled(0.5, 0.5, 0.5);
                break;
            case EQUIPPED:
                GL11.glScaled(0.4, 0.4, 0.4);
                GL11.glRotatef(90, 0, 1, 0);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                break;
            case EQUIPPED_FIRST_PERSON:
                GL11.glScaled(0.4, 0.4, 0.4);
                GL11.glRotatef(90, 0, 1, 0);
                GL11.glTranslatef(0.5F, 0.5F, 0.5F);
                break;
            case INVENTORY:
                GL11.glScaled(0.6, 0.6, 0.6);
                GL11.glRotatef(180, 0, 1, 0);
                break;
            default:
                break;
        }

        Minecraft.getMinecraft().renderEngine.bindTexture(texture);
        model.renderAll();

        GL11.glPopMatrix();
    }
}
