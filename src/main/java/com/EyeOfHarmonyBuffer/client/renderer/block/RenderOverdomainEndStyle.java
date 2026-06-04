package com.EyeOfHarmonyBuffer.client.renderer.block;

import com.EyeOfHarmonyBuffer.example.tile.TileEntityOverdomainErosion;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
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

    private final Block selfBlock;

    public RenderOverdomainEndStyle(Block selfBlock) {
        this.selfBlock = selfBlock;
    }

    private enum Face {
        UP, DOWN, NORTH, SOUTH, WEST, EAST
    }

    public void renderTileEntityAt(TileEntityOverdomainErosion te,
                                   double x, double y, double z,
                                   float partialTicks) {

        World world = te.getWorldObj();
        int bx = te.xCoord;
        int by = te.yCoord;
        int bz = te.zCoord;

        float playerX = (float) this.field_147501_a.field_147560_j;
        float playerY = (float) this.field_147501_a.field_147561_k;
        float playerZ = (float) this.field_147501_a.field_147558_l;

        boolean inside = isPlayerInsideOverdomainVolume(world);

        GL11.glDisable(GL11.GL_LIGHTING);
        RANDOM.setSeed(31100L);

        if (inside) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        float minY = 0.0F;
        float maxY = 1.0F;

        if (shouldRenderSide(world, bx, by, bz, Face.UP)) {
            renderFaceLayered(Face.UP, x, y, z,
                minY, maxY, playerX, playerY, playerZ, partialTicks, inside);
        }

        if (shouldRenderSide(world, bx, by, bz, Face.DOWN)) {
            renderFaceLayered(Face.DOWN, x, y, z,
                minY, maxY, playerX, playerY, playerZ, partialTicks, inside);
        }
        if (shouldRenderSide(world, bx, by, bz, Face.NORTH)) {
            renderFaceLayered(Face.NORTH, x, y, z,
                minY, maxY, playerX, playerY, playerZ, partialTicks, inside);
        }
        if (shouldRenderSide(world, bx, by, bz, Face.SOUTH)) {
            renderFaceLayered(Face.SOUTH, x, y, z,
                minY, maxY, playerX, playerY, playerZ, partialTicks, inside);
        }
        if (shouldRenderSide(world, bx, by, bz, Face.WEST)) {
            renderFaceLayered(Face.WEST, x, y, z,
                minY, maxY, playerX, playerY, playerZ, partialTicks, inside);
        }
        if (shouldRenderSide(world, bx, by, bz, Face.EAST)) {
            renderFaceLayered(Face.EAST, x, y, z,
                minY, maxY, playerX, playerY, playerZ, partialTicks, inside);
        }

        if (inside) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }

        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private boolean shouldRenderSide(World world, int x, int y, int z, Face face) {
        int nx = x, ny = y, nz = z;

        switch (face) {
            case UP:    ny += 1; break;
            case DOWN:  ny -= 1; break;
            case NORTH: nz -= 1; break;
            case SOUTH: nz += 1; break;
            case WEST:  nx -= 1; break;
            case EAST:  nx += 1; break;
        }

        Block neighbor = world.getBlock(nx, ny, nz);

        return neighbor != selfBlock;
    }

    private void renderFaceLayered(Face face,
                                   double x, double y, double z,
                                   float minY, float maxY,
                                   float playerX, float playerY, float playerZ,
                                   float partialTicks,
                                   boolean inside) {

        for (int i = 0; i < 16; ++i) {

            if (i == 0) {
                GL11.glDepthMask(true);
            } else {
                GL11.glDepthMask(false);
            }

            GL11.glPushMatrix();

            float layer = (float) i;

            float texScale = 0.04F + layer * 0.01F;

            float brightnessFactor = 0.25F + layer * 0.03F;
            if (brightnessFactor > 1.0F) brightnessFactor = 1.0F;

            if (i == 0) {
                this.bindTexture(SKY_TEXTURE);
                brightnessFactor = 0.15F;
                texScale = 0.12F;
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            if (i == 1) {
                this.bindTexture(PORTAL_TEXTURE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
                texScale *= 1.4F;
            }

            float layerOffset = i * 0.05F;
            GL11.glTranslatef(0.0F, 0.0F, layerOffset);

            GL11.glTexGeni(GL11.GL_S, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GL11.glTexGeni(GL11.GL_T, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);
            GL11.glTexGeni(GL11.GL_R, GL11.GL_TEXTURE_GEN_MODE, GL11.GL_OBJECT_LINEAR);

            switch (face) {
                case UP:
                case DOWN:
                    GL11.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, makePlane(1.0F, 0.0F, 0.0F, 0.0F));
                    GL11.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, makePlane(0.0F, 0.0F, 1.0F, 0.0F));
                    break;
                case NORTH:
                case SOUTH:
                    GL11.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, makePlane(1.0F, 0.0F, 0.0F, 0.0F));
                    GL11.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, makePlane(0.0F, 1.0F, 0.0F, 0.0F));
                    break;
                case WEST:
                case EAST:
                    GL11.glTexGen(GL11.GL_S, GL11.GL_OBJECT_PLANE, makePlane(0.0F, 0.0F, 1.0F, 0.0F));
                    GL11.glTexGen(GL11.GL_T, GL11.GL_OBJECT_PLANE, makePlane(0.0F, 1.0F, 0.0F, 0.0F));
                    break;
            }

            GL11.glEnable(GL11.GL_TEXTURE_GEN_S);
            GL11.glEnable(GL11.GL_TEXTURE_GEN_T);
            GL11.glEnable(GL11.GL_TEXTURE_GEN_R);

            GL11.glPopMatrix();

            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();

            float t = (Minecraft.getSystemTime() % 360000L) / 360000.0F;

            GL11.glTranslatef(0.0F, t * (0.4F + layer * 0.05F), 0.0F);

            GL11.glScalef(texScale, texScale, texScale);
            GL11.glTranslatef(0.5F, 0.5F, 0.0F);
            GL11.glRotatef(layer * 10.0F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-0.5F, -0.5F, 0.0F);

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

            if (RANDOM.nextFloat() < 0.7F) {
                baseR *= 0.3F;
                baseG *= 0.3F;
                baseB *= 0.3F;
            } else {
                baseR *= 1.4F;
                baseG *= 1.4F;
                baseB *= 1.4F;
            }

            float depthLerp = layer / 15.0F;
            baseR += 0.08F * depthLerp;
            baseB -= 0.06F * depthLerp;
            if (baseR > 1.0F) baseR = 1.0F;
            if (baseB < 0.0F) baseB = 0.0F;

            float r = baseR * 1.4F;
            float g = baseG * 0.4F;
            float b = baseB * 0.4F;

            tess.setColorRGBA_F(
                r * brightnessFactor,
                g * brightnessFactor,
                b * brightnessFactor,
                1.0F
            );

            addFaceVertices(tess, face, x, y, z, minY, maxY);

            tess.draw();

            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }

        if (inside) {
            Tessellator tess = Tessellator.instance;

            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);

            this.bindTexture(PORTAL_TEXTURE);

            tess.startDrawingQuads();

            float r = 0.15F;
            float g = 0.02F;
            float b = 0.05F;
            float a = 1.0F;

            tess.setColorRGBA_F(r, g, b, a);

            addFaceVertices(tess, face, x, y, z, minY, maxY);

            tess.draw();

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);

            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_S);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_T);
        GL11.glDisable(GL11.GL_TEXTURE_GEN_R);

        GL11.glDepthMask(true);
    }

    private void addFaceVertices(Tessellator tess, Face face,
                                 double x, double y, double z,
                                 float minY, float maxY) {

        double x0 = x;
        double x1 = x + 1.0D;
        double y0 = y + minY;
        double y1 = y + maxY;
        double z0 = z;
        double z1 = z + 1.0D;

        switch (face) {
            case UP:
                tess.addVertex(x0, y1, z0);
                tess.addVertex(x0, y1, z1);
                tess.addVertex(x1, y1, z1);
                tess.addVertex(x1, y1, z0);
                break;

            case DOWN:
                tess.addVertex(x0, y0, z0);
                tess.addVertex(x1, y0, z0);
                tess.addVertex(x1, y0, z1);
                tess.addVertex(x0, y0, z1);
                break;

            case NORTH:
                tess.addVertex(x0, y0, z0);
                tess.addVertex(x0, y1, z0);
                tess.addVertex(x1, y1, z0);
                tess.addVertex(x1, y0, z0);
                break;

            case SOUTH:
                tess.addVertex(x0, y0, z1);
                tess.addVertex(x1, y0, z1);
                tess.addVertex(x1, y1, z1);
                tess.addVertex(x0, y1, z1);
                break;

            case WEST:
                tess.addVertex(x0, y0, z0);
                tess.addVertex(x0, y0, z1);
                tess.addVertex(x0, y1, z1);
                tess.addVertex(x0, y1, z0);
                break;

            case EAST:
                tess.addVertex(x1, y0, z0);
                tess.addVertex(x1, y1, z0);
                tess.addVertex(x1, y1, z1);
                tess.addVertex(x1, y0, z1);
                break;
        }
    }

    private FloatBuffer makePlane(float a, float b, float c, float d) {
        this.floatBuffer.clear();
        this.floatBuffer.put(a).put(b).put(c).put(d);
        this.floatBuffer.flip();
        return this.floatBuffer;
    }

    private boolean isPlayerInsideOverdomainVolume(World world) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) return false;

        int px = (int)Math.floor(player.posX);
        int py = (int)Math.floor(player.posY + player.getEyeHeight());
        int pz = (int)Math.floor(player.posZ);

        Block inBlock = world.getBlock(px, py, pz);

        return inBlock == selfBlock;
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
