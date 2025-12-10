package com.EyeOfHarmonyBuffer.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public class YuanShi extends ModelBase {

    private static final double PHI = (1.0 + Math.sqrt(5.0)) / 2.0;

    private static final double[][] VERTICES = new double[][]{
        {-1,  PHI,  0},
        { 1,  PHI,  0},
        {-1, -PHI,  0},
        { 1, -PHI,  0},
        { 0, -1,  PHI},
        { 0,  1,  PHI},
        { 0, -1, -PHI},
        { 0,  1, -PHI},
        { PHI,  0, -1},
        { PHI,  0,  1},
        {-PHI,  0, -1},
        {-PHI,  0,  1}
    };

    private static final int[][] FACES = new int[][]{
        {0, 11, 5},
        {0, 5, 1},
        {0, 1, 7},
        {0, 7, 10},
        {0, 10, 11},

        {1, 5, 9},
        {5, 11, 4},
        {11, 10, 2},
        {10, 7, 6},
        {7, 1, 8},

        {3, 9, 4},
        {3, 4, 2},
        {3, 2, 6},
        {3, 6, 8},
        {3, 8, 9},

        {4, 9, 5},
        {2, 4, 11},
        {6, 2, 10},
        {8, 6, 7},
        {9, 8, 1}
    };

    public float modelScale = 2.5F;

    public YuanShi() {
        this.textureWidth = 64;
        this.textureHeight = 32;
    }

    @Override
    public void render(Entity entity, float f, float f1, float f2,
                       float f3, float f4, float scale) {

        this.setRotationAngles(f, f1, f2, f3, f4, scale, entity);

        renderIcosahedron();
    }

    private void renderIcosahedron() {
        GL11.glBegin(GL11.GL_TRIANGLES);

        for (int[] face : FACES) {
            int i0 = face[0];
            int i1 = face[1];
            int i2 = face[2];

            double[] v0 = VERTICES[i0];
            double[] v1 = VERTICES[i1];
            double[] v2 = VERTICES[i2];

            double nx, ny, nz;
            {
                double ux = v1[0] - v0[0];
                double uy = v1[1] - v0[1];
                double uz = v1[2] - v0[2];

                double vx = v2[0] - v0[0];
                double vy = v2[1] - v0[1];
                double vz = v2[2] - v0[2];

                nx = uy * vz - uz * vy;
                ny = uz * vx - ux * vz;
                nz = ux * vy - uy * vx;

                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len != 0.0) {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                }
            }

            GL11.glNormal3d(nx, ny, nz);

            GL11.glVertex3d(v0[0], v0[1], v0[2]);
            GL11.glVertex3d(v1[0], v1[1], v1[2]);
            GL11.glVertex3d(v2[0], v2[1], v2[2]);
        }

        GL11.glEnd();
    }
}
