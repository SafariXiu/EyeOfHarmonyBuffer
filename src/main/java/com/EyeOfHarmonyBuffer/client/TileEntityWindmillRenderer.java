package com.EyeOfHarmonyBuffer.client;

import com.EyeOfHarmonyBuffer.client.model.Trubine;
import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TileEntityWindmillRenderer extends TileEntitySpecialRenderer {

    int brightness = 15728880;

    private final ResourceLocation texture = new ResourceLocation("eyeofharmonybuffer:textures/models/textureTrubine.png");
    private final Trubine model = new Trubine();

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityWindmill)) return;
        TileEntityWindmill tile = (TileEntityWindmill) te;

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y, z + 0.5);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightness % 65536, brightness / 65536);
        this.bindTexture(texture);

        //GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glScalef(12.0F, 12.0F, 12.0F);
        GL11.glRotatef(tile.getRotation(), 0, 0, 1);

        model.render(null, 0, 0, 0, 0, 0, 0.0625F);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
