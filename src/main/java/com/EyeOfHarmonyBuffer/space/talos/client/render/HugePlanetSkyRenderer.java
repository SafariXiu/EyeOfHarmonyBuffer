package com.EyeOfHarmonyBuffer.space.talos.client.render;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.space.talos.WorldProviderTalos2;
import micdoodle8.mods.galacticraft.api.vector.Vector3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;

public class HugePlanetSkyRenderer extends IRenderHandler {

    private static final ResourceLocation SUN_TEXTURE =
        new ResourceLocation("textures/environment/sun.png");

    private static final ResourceLocation TALOS_TEXTURE =
        new ResourceLocation(EyeOfHarmonyBuffer.MODID,
            "textures/gui/celestialbodies/talos/Talos_Sky.png");

    @Override
    public void render(float partialTicks, WorldClient world, Minecraft mc) {
        if (world == null || mc.thePlayer == null) return;

        Tessellator tess = Tessellator.instance;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();

        //GL11.glDisable(GL11.GL_FOG);
        //GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        double sunSize = 9.0D;
        double sunDist = -100.0D;

        {
            GL11.glPushMatrix();
            mc.getTextureManager().bindTexture(SUN_TEXTURE);

            float cel = world.getCelestialAngle(partialTicks);
            float angle = cel * 360.0F + 180.0F - 10.0F;
            GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);

            double minU = 0.38D;
            double maxU = 0.62D;
            double minV = 0.38D;
            double maxV = 0.62D;

            tess.startDrawingQuads();
            tess.addVertexWithUV(-sunSize,  sunDist,  sunSize, minU, minV);
            tess.addVertexWithUV( sunSize,  sunDist,  sunSize, maxU, minV);
            tess.addVertexWithUV( sunSize,  sunDist, -sunSize, maxU, maxV);
            tess.addVertexWithUV(-sunSize,  sunDist, -sunSize, minU, maxV);
            tess.draw();

            GL11.glPopMatrix();
        }

        {
            GL11.glPushMatrix();

            float cel = world.getCelestialAngle(partialTicks);
            float angle = cel * 360.0F + 180.0F - 10.0F;
            GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);

            double haloDist = sunDist - 0.1D;

            GL11.glDisable(GL11.GL_TEXTURE_2D);

            int layers = 4;
            double startScale = 1.4D;
            double endScale   = 2.4D;

            float innerAlpha = 0.22F;
            float outerAlpha = 0.03F;

            for (int i = 0; i < layers; i++) {
                float t = i / (float)(layers - 1);

                double scale = startScale + (endScale - startScale) * t;
                double size  = sunSize * scale;

                float alpha = innerAlpha + (outerAlpha - innerAlpha) * t;

                float r = 1.0F;
                float g = 1.0F;
                float b = 0.85F;

                GL11.glColor4f(r, g, b, alpha);

                tess.startDrawingQuads();
                tess.addVertex(-size,  haloDist,  size);
                tess.addVertex( size,  haloDist,  size);
                tess.addVertex( size,  haloDist, -size);
                tess.addVertex(-size,  haloDist, -size);
                tess.draw();
            }

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1F, 1F, 1F, 1F);

            GL11.glPopMatrix();
        }

        // 戴森球：叠加在太阳/光晕之上，随进度遮蔽恒星
        DysonSphereRenderer.render(world, partialTicks);

        float planetYaw = 180.0F;
        float planetPitch = 0F;
        float spin = (world.getWorldTime() % 24000L) / 24000.0F * 360.0F;

        // 球体网格渲染：平面贴图四角容易超出天空盒远裁剪面，换成球体后，
        // 可见前半球的最远点（地平线 √(D²-R²)）始终远小于远裁剪面。
        double planetRadius = 210.0D;
        double planetDist = -475.0D;

        {
            GL11.glPushMatrix();
            mc.getTextureManager().bindTexture(TALOS_TEXTURE);

            GL11.glRotatef(planetYaw,   0.0F, 1.0F, 0.0F);
            GL11.glRotatef(planetPitch, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(spin,        0.0F, 0.0F, 1.0F);

            Vector3 sky = ((WorldProviderTalos2)world.provider).getSkyColor();
            float sr = (float) sky.x;
            float sg = (float) sky.y;
            float sb = (float) sky.z;

            float planetBaseR = 1.0F;
            float planetBaseG = 1.0F;
            float planetBaseB = 1.0F;

            float fogStrength = 0.5F;

            float finalR = planetBaseR * (1.0F - fogStrength) + sr * fogStrength;
            float finalG = planetBaseG * (1.0F - fogStrength) + sg * fogStrength;
            float finalB = planetBaseB * (1.0F - fogStrength) + sb * fogStrength;

            GL11.glColor4f(finalR, finalG, finalB, 1.0F);

            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glCullFace(GL11.GL_BACK);
            drawPlanetSphere(planetRadius, planetDist);
            GL11.glCullFace(GL11.GL_BACK);
            GL11.glDisable(GL11.GL_CULL_FACE);

            GL11.glPopMatrix();
        }

        GL11.glPopMatrix();
        GL11.glPopAttrib();;
    }

    /** 绘制行星球体：贴图是圆盘图，按正交投影映射（u=x/r, v=-y/r）避免两极拉伸。 */
    private static void drawPlanetSphere(double radius, double dist) {
        int lonSeg = 32;
        int latSeg = 16;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        for (int j = 0; j < latSeg; j++) {
            double phi0 = Math.PI * (j / (double) latSeg) - Math.PI / 2.0D;
            double phi1 = Math.PI * ((j + 1) / (double) latSeg) - Math.PI / 2.0D;
            for (int i = 0; i < lonSeg; i++) {
                double theta0 = Math.PI * 2.0D * i / lonSeg;
                double theta1 = Math.PI * 2.0D * (i + 1) / lonSeg;
                addPlanetVertex(tess, radius, dist, phi0, theta0);
                addPlanetVertex(tess, radius, dist, phi0, theta1);
                addPlanetVertex(tess, radius, dist, phi1, theta1);
                addPlanetVertex(tess, radius, dist, phi1, theta0);
            }
        }
        tess.draw();
    }

    private static void addPlanetVertex(Tessellator tess, double radius, double dist, double phi, double theta) {
        double x = radius * Math.cos(phi) * Math.cos(theta);
        double y = radius * Math.cos(phi) * Math.sin(theta);
        double z = dist + radius * Math.sin(phi);
        double u = x / radius * 0.5D + 0.5D;
        double v = -y / radius * 0.5D + 0.5D;
        tess.addVertexWithUV(x, y, z, u, v);
    }
}
