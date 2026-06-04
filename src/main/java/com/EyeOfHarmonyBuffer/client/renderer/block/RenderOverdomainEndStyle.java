package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.example.tile.TileEntityOverdomainErosion;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.Random;


@SideOnly(Side.CLIENT)
public class RenderOverdomainEndStyle extends TileEntitySpecialRenderer {

    private static final ResourceLocation SKY_TEXTURE =
        new ResourceLocation("textures/environment/end_sky.png");

    private static final ResourceLocation PORTAL_TEXTURE =
        new ResourceLocation("textures/entity/end_portal.png");

    private static final Random RANDOM = new Random(31100L);
    private final FloatBuffer floatBuffer = GLAllocation.createDirectFloatBuffer(16);

    public void renderTileEntityAt(TileEntityOverdomainErosion te,
                                   double x, double y, double z,
                                   float partialTicks) {

        float playerX = (float) this.field_147501_a.field_147560_j;
        float playerY = (float) this.field_147501_a.field_147561_k;
        float playerZ = (float) this.field_147501_a.field_147558_l;

        GL11.glDisable(GL11.GL_LIGHTING);
        RANDOM.setSeed(31100L);

        float surfaceY = 0.875F;

        for (int i = 0; i < 16; ++i) {

            GL11.glPushMatrix();

            float depthFactor = (float) (16 - i);
            float texScale = 0.0625F;
            float brightnessFactor = 1.0F / (depthFactor + 1.0F);

            if (i == 0) {
                this.bindTexture(SKY_TEXTURE);
                brightnessFactor = 0.10F;
                depthFactor = 65.0F;
                texScale = 0.125F;

                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            if (i == 1) {
                this.bindTexture(PORTAL_TEXTURE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                texScale = 0.5F;
            }

            float baseY = (float) (-(y + (double) surfaceY));
            float y1 = baseY + ActiveRenderInfo.objectY;
            float y2 = baseY + depthFactor + ActiveRenderInfo.objectY;
            float layerOffset = y1 / y2;
            layerOffset += surfaceY;

            GL11.glTranslatef(playerX, layerOffset, playerZ);

            GL11.glTexGeni(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GL11.glTexGeni(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GL11.glTexGeni(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GL11.glTexGeni(GL11.GL_Q, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_EYE_LINEAR);

            GL11.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, makePlane(1.0F, 0.0F, 0.0F, 0.0F));
            GL11.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, makePlane(0.0F, 0.0F, 1.0F, 0.0F));
            GL11.glTexGen(GL11.GL_R, GL11.GL_OBJECT_PLANE, makePlane(0.0F, 0.0F, 0.0F, 1.0F));
            GL11.glTexGen(GL11.GL_Q, GL11.GL_EYE_PLANE,    makePlane(0.0F, 1.0F, 0.0F, 0.0F));

            GL11.glEnable(GL11.GL_TEXTURE_GEN_S);
            GL11.glEnable(GL11.GL_TEXTURE_GEN_T);
            GL11.glEnable(GL11.GL_TEXTURE_GEN_R);
            GL11.glEnable(GL11.GL_TEXTURE_GEN_Q);

            GL11.glPopMatrix();

            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            GL11.glTranslatef(
                0.0F,
                (float) (Minecraft.getSystemTime() % 700000L) / 700000.0F,
                0.0F
            );

            GL11.glScalef(texScale, texScale, texScale);
            GL11.glTranslatef(0.5F, 0.5F, 0.0F);
            GL11.glRotatef((float) (i * i * 4321 + i * 9) * 2.0F,
                0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-0.5F, -0.5F, 0.0F);

            GL11.glTranslatef(-playerX, -playerZ, -playerY);
            y1 = baseY + ActiveRenderInfo.objectY;
            GL11.glTranslatef(
                ActiveRenderInfo.objectX * depthFactor / y1,
                ActiveRenderInfo.objectZ * depthFactor / y1,
                -playerY
            );

            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();

            float baseR = RANDOM.nextFloat() * 0.5F + 0.1F;
            float baseG = RANDOM.nextFloat() * 0.3F + 0.0F;
            float baseB = RANDOM.nextFloat() * 0.5F + 0.3F;

            if (i == 0) {
                baseR = 1.0F;
                baseG = 0.8F;
                baseB = 0.8F;
            }

            float r = baseR * 1.4F;
            float g = baseG * 0.4F;
            float b = baseB * 0.4F;

            tess.setColorRGBA_F(r * brightnessFactor,
                g * brightnessFactor,
                b * brightnessFactor,
                1.0F);

            double x0 = x;
            double x1 = x + 1.0D;
            double z0 = z;
            double z1 = z + 1.0D;
            double ySurf = y + (double) surfaceY;

            tess.addVertex(x0, ySurf, z0);
            tess.addVertex(x0, ySurf, z1);
            tess.addVertex(x1, ySurf, z1);
            tess.addVertex(x1, ySurf, z0);

            tess.draw();

            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_S);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_T);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_R);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_Q);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private FloatBuffer makePlane(float a, float b, float c, float d) {
        this.floatBuffer.clear();
        this.floatBuffer.put(a).put(b).put(c).put(d);
        this.floatBuffer.flip();
        return this.floatBuffer;
    }

    @Override
    public void renderTileEntityAt(TileEntity te,
                                   double x, double y, double z,
                                   float partialTicks) {
        if (te instanceof TileEntityOverdomainErosion) {
            renderTileEntityAt((TileEntityOverdomainErosion) te, x, y, z, partialTicks);
        }
    }
}
