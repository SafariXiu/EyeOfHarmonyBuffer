package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.client.model.ForgeOfTheSkyCore;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TileEntityForgeOfTheSkyCoreRenderer extends TileEntitySpecialRenderer {

    private final ResourceLocation texture =
        new ResourceLocation("eyeofharmonybuffer:textures/models/ForgeOfTheSky_Ceore.png");
    private final ForgeOfTheSkyCore model = new ForgeOfTheSkyCore();

    @Override
    public void renderTileEntityAt(TileEntity te,
                                   double x,
                                   double y,
                                   double z,
                                   float partialTicks) {
        GL11.glPushMatrix();

        boolean lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean lightmapEnabled;
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GL11.glTranslated(x + 0.80D, y - 0.25D, z + 0.88D);
        GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(0.5F, 0.6F, 0.5F);

        this.bindTexture(texture);
        model.render(null, 0, 0, 0, 0, 0, 0.0625F);

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        if (lightmapEnabled) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        } else {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);

        if (lightingEnabled) {
            GL11.glEnable(GL11.GL_LIGHTING);
        } else {
            GL11.glDisable(GL11.GL_LIGHTING);
        }

        GL11.glPopMatrix();
    }
}
