package com.EyeOfHarmonyBuffer.space;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import com.EyeOfHarmonyBuffer.space.talos.Talos2Continent;
import net.minecraft.world.World;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Talos2MapExporter {

    /**
     * 导出一个非常大的Talos大陆调试地图
     * @param world Minecraft世界
     * @param continentNoise 大陆噪声对象
     * @param fileName 输出文件名
     * @param size 区域尺寸(例如50000)
     * @param step 采样像素步进(例如16)
     */
    public static void exportHuge(World world, SimplexNoiseOctave continentNoise,
                                  String fileName, int size, int step) throws IOException {

        int pixels = size / step;
        final double SCALE = 0.0025D;

        File outDir = new File(world.getSaveHandler().getWorldDirectory(), "debug_talos");
        if (!outDir.exists()) outDir.mkdirs();

        File outFile = new File(outDir, fileName);
        System.out.println("[Talos2][DEBUG] Exporting huge map " + pixels + "x" + pixels + " to " + outFile.getAbsolutePath());

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        for (int z = 0; z < pixels; z++) {
            for (int x = 0; x < pixels; x++) {
                int gx = -size/2 + x * step;
                int gz = -size/2 + z * step;

                double c = Talos2Continent.sampleC01(continentNoise, gx, gz);
                Color col = colorizeC(c);
                img.setRGB(x, z, col.getRGB());
            }

            if (z % 500 == 0) {
                double percent = (100.0 * z / pixels);
                System.out.printf("[Talos2] %.1f%% done (%d/%d rows)%n", percent, z, pixels);
            }
        }

        ImageIO.write(img, "png", outFile);
        System.out.println("[Talos2][DEBUG] Export finished successfully! File: " + outFile.getAbsolutePath());
    }

    private static Color colorizeC(double c) {
        double SHELF_START = Talos2Continent.C_SHELF_START;
        double SHELF_END   = Talos2Continent.C_SHELF_END;
        double BEACH_END   = Talos2Continent.C_BEACH_END;

        if (c < SHELF_START)
            return new Color(12, 40, 90);
        else if (c < SHELF_END)
            return new Color(25, 90, 160);
        else if (c < BEACH_END)
            return new Color(235, 225, 140);
        else
            return new Color(50, 200, 80);
    }
}
