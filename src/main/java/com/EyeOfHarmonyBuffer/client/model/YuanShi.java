package com.EyeOfHarmonyBuffer.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.Tessellator;
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

    private static final int[][] FACES_CORE = new int[][]{
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

    private static final int[][] FACES_SHELL = FACES_CORE;

    public YuanShi() {
        this.textureWidth = 64;
        this.textureHeight = 32;
    }

    @Override
    public void render(Entity entity, float f, float f1, float f2,
                       float f3, float f4, float scale) {
        this.setRotationAngles(f, f1, f2, f3, f4, scale, entity);
    }

    private static float hash11(int n) {
        n = (n ^ 61) ^ (n >> 16);
        n = n + (n << 3);
        n = n ^ (n >> 4);
        n = n * 0x27d4eb2d;
        n = n ^ (n >> 15);
        return (n & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static float hash12(int n, int m) {
        int x = n * 73856093 ^ m * 19349663;
        x = (x ^ 61) ^ (x >> 16);
        x = x + (x << 3);
        x = x ^ (x >> 4);
        x = x * 0x27d4eb2d;
        x = x ^ (x >> 15);
        return (x & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static float crackNoise(double x, double y, double z, float time) {
        double sx = x * 2.8;
        double sy = y * 3.1;
        double sz = z * 2.4;

        double n  = Math.sin(sx + time * 0.9);
        n += Math.sin(sy * 1.7 - time * 1.1);
        n += Math.sin(sz * 2.3 + time * 0.6);
        n += Math.sin((sx + sy + sz) * 0.7 - time * 0.4);

        float t = (float) (n * 0.12 + 0.5);
        t = Math.max(0.0F, Math.min(1.0F, t));

        return 0.8F + 0.5F * t;
    }

    private void tessellate(int[][] faces,
                            float baseR, float baseG, float baseB, float baseA,
                            boolean useHighlight,
                            boolean useScatter,
                            boolean useVertexCrack,
                            float time) {

        Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_TRIANGLES);

        final double lx = 0.25;
        final double ly = 0.9;
        final double lz = 0.35;
        final double lenL = Math.sqrt(lx * lx + ly * ly + lz * lz);
        final double Lx = lx / lenL;
        final double Ly = ly / lenL;
        final double Lz = lz / lenL;

        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            int[] face = faces[faceIndex];

            int i0 = face[0];
            int i1 = face[1];
            int i2 = face[2];

            double[] v0 = VERTICES[i0];
            double[] v1 = VERTICES[i1];
            double[] v2 = VERTICES[i2];

            double ux = v1[0] - v0[0];
            double uy = v1[1] - v0[1];
            double uz = v1[2] - v0[2];

            double vx = v2[0] - v0[0];
            double vy = v2[1] - v0[1];
            double vz = v2[2] - v0[2];

            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;

            double lenN = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (lenN != 0.0) {
                nx /= lenN;
                ny /= lenN;
                nz /= lenN;
            }

            tess.setNormal((float) nx, (float) ny, (float) nz);

            double dot = nx * Lx + ny * Ly + nz * Lz;
            double intensity = Math.max(0.0, dot);
            float lightFactor = (float) (0.35 + 0.65 * intensity);

            boolean isCore = useScatter || useVertexCrack;

            float scatter = 1.0F;
            if (useScatter) {
                float h = hash11(faceIndex * 7347 + 13);
                float wobble = 0.06F * (float) Math.sin(time * 0.9F + faceIndex * 0.7F);
                scatter = 0.9F + 0.2F * h + wobble;
            }

            float r, g, b;

            if (isCore) {
                r = baseR;
                g = baseG;
                b = baseB;
            } else {
                r = baseR;
                g = baseG;
                b = baseB;

                if (useHighlight) {
                    float spec = (float) Math.pow(intensity, 2.3);
                    float specStrength = 0.45F;

                    float targetR = 1.0F;
                    float targetG = 0.97F;
                    float targetB = 0.92F;

                    r = baseR * lightFactor * (1.0F - specStrength * spec)
                        + targetR * specStrength * spec;
                    g = baseG * lightFactor * (1.0F - specStrength * spec)
                        + targetG * specStrength * spec;
                    b = baseB * lightFactor * (1.0F - specStrength * spec)
                        + targetB * specStrength * spec;
                } else {
                    r = baseR * lightFactor;
                    g = baseG * lightFactor;
                    b = baseB * lightFactor;
                }

                r *= scatter;
                g *= scatter;
                b *= scatter;
            }

            double u0, v0u;
            double u1, v1u;
            double u2, v2u;

            if (faceIndex == 0) {
                float uMin = 142F / 1024F;
                float uMax = 450F / 1024F;
                float vMin = 375F / 1024F;
                float vMax = 690F / 1024F;

                u0 = uMin;      v0u = vMin;
                u1 = uMax;      v1u = vMin;
                u2 = (uMin + uMax) * 0.5F;
                v2u = vMax;

            } else {

                float safeUMin = 450F / 1024F;
                float safeUMax = 1024F / 1024F;
                float safeVMin = 0F   / 1024F;
                float safeVMax = 1024F / 1024F;

                float randU = hash11(faceIndex * 92821 + 1);
                float randV = hash11(faceIndex * 92821 + 2);

                float sizeU = (safeUMax - safeUMin) * 0.4F;
                float sizeV = (safeVMax - safeVMin) * 0.4F;

                float baseU = safeUMin + (safeUMax - safeUMin - sizeU) * randU;
                float baseV = safeVMin + (safeVMax - safeVMin - sizeV) * randV;

                u0 = baseU;
                v0u = baseV;

                u1 = baseU + sizeU;
                v1u = baseV;

                u2 = baseU + sizeU * 0.5F;
                v2u = baseV + sizeV;
            }

            addCrackedVertex(tess, faceIndex, 0, v0,
                r, g, b, baseA,
                useVertexCrack, isCore, time,
                u0, v0u);

            addCrackedVertex(tess, faceIndex, 1, v1,
                r, g, b, baseA,
                useVertexCrack, isCore, time,
                u1, v1u);

            addCrackedVertex(tess, faceIndex, 2, v2,
                r, g, b, baseA,
                useVertexCrack, isCore, time,
                u2, v2u);
        }

        tess.draw();
    }

    private void addCrackedVertex(Tessellator tess,
                                  int faceIndex, int localIndex,
                                  double[] pos,
                                  float r, float g, float b, float a,
                                  boolean useVertexCrack,
                                  boolean isCore,
                                  float time,
                                  double u, double v) {

        float jitter = 1.0F;

        if (!isCore) {
            float hv = hash12(faceIndex, localIndex);
            jitter *= (0.9F + hv * 0.3F);

            if (useVertexCrack) {
                float crack = crackNoise(pos[0], pos[1], pos[2], time);
                jitter *= crack;
            }
        }

        float vr = Math.min(r * jitter, 1.0F);
        float vg = Math.min(g * jitter, 1.0F);
        float vb = Math.min(b * jitter, 1.0F);

        tess.setColorRGBA_F(vr, vg, vb, a);
        tess.addVertexWithUV(pos[0], pos[1], pos[2], u, v);
    }

    public void renderShell(float r, float g, float b, float a) {
        float time = 0.0F;
        tessellate(FACES_SHELL, r * 0.9F, g * 0.9F, b * 0.9F, a,
            true,
            false,
            false,
            time);
    }

    public void renderCoreSolid(float r, float g, float b, float a) {
        float time = (Minecraft.getSystemTime() % 8000L) / 1000.0F;
        tessellate(FACES_CORE, r, g, b, a,
            true,
            true,
            true,
            time);
    }

    public void renderCoreAdd(float r, float g, float b, float a) {
        float time = (Minecraft.getSystemTime() % 8000L) / 1000.0F;
        tessellate(FACES_CORE, r, g, b, a,
            true,
            false,
            true,
            time);
    }

    public void renderMiddleLayer(float r, float g, float b, float a) {
        float time = (Minecraft.getSystemTime() % 8000L) / 1000.0F;

        tessellate(FACES_CORE,
            r, g, b, a,
            true,
            false,
            false,
            time);
    }
}
