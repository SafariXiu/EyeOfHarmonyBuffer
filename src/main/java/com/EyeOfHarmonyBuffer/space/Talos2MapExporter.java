package com.EyeOfHarmonyBuffer.space;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import com.EyeOfHarmonyBuffer.space.talos.Talos2Continent;
import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import net.minecraft.world.World;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import java.util.Locale;

public class Talos2MapExporter {

    private Talos2MapExporter() {}

    public static final class ExportConfig {
        private static boolean boolProp(String key, boolean def) {
            String v = System.getProperty(key);
            return (v == null) ? def : Boolean.parseBoolean(v);
        }
        private static int intProp(String key, int def) {
            String v = System.getProperty(key);
            if (v == null) return def;
            try { return Integer.parseInt(v.trim()); }
            catch (Exception e) { return def; }
        }
        private static String strProp(String key, String def) {
            String v = System.getProperty(key);
            return (v == null || v.isEmpty()) ? def : v;
        }

        public static boolean enabled() {
            return boolProp("talos.export", false);
        }

        public static String mode() {
            return strProp("talos.export.mode", "distZone");
        }

        public static int sizeBlocks() {
            return intProp("talos.export.size", 32768);
        }

        public static int stepBlocks() {
            return intProp("talos.export.step", 16);
        }

        public static String fileName() {
            return strProp("talos.export.file", "talos_export.png");
        }

        public static int coastRadiusBlocks() {
            return intProp("talos.export.coastRadius", 192);
        }

        public static int visRadiusBlocks() {
            return intProp("talos.export.visRadius", 160);
        }

        public static int tilePixels() {
            return intProp("talos.export.tile", 24);
        }

        public static boolean progressLog() {
            return boolProp("talos.export.log", true);
        }

        public static String outDirName() {
            return strProp("talos.export.dir", "debug_talos");
        }
    }

    public static void maybeExportFromSystemProps(
        World world,
        SimplexNoiseOctave continentNoise,
        long seed
    ) throws IOException {

        if (!ExportConfig.enabled()) return;

        String mode = ExportConfig.mode();
        String file = ExportConfig.fileName();

        int size = ExportConfig.sizeBlocks();
        int step = ExportConfig.stepBlocks();

        if ("distZone".equalsIgnoreCase(mode)) {
            exportDistZoneMap(world, continentNoise, seed, file, size, step,
                ExportConfig.coastRadiusBlocks(),
                ExportConfig.visRadiusBlocks(),
                ExportConfig.tilePixels(),
                ExportConfig.progressLog()
            );
            return;
        }

        if ("isLand".equalsIgnoreCase(mode)) {
            exportIsLandMap(world, continentNoise, seed, file, size, step,
                ExportConfig.coastRadiusBlocks(),
                ExportConfig.tilePixels(),
                ExportConfig.progressLog()
            );
            return;
        }

        if ("c01".equalsIgnoreCase(mode)) {
            exportContinentC01Map(world, continentNoise, seed, file, size, step,
                ExportConfig.tilePixels(),
                ExportConfig.progressLog()
            );
            return;
        }

        throw new IllegalArgumentException("Unknown talos.export.mode=" + mode);
    }

    public static void exportDistZoneMap(
        World world,
        SimplexNoiseOctave continentNoise,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int coastRadiusBlocks,
        int visRadiusBlocks,
        int tilePixels,
        boolean progressLog
    ) throws IOException {

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");
        requirePositive(coastRadiusBlocks, "coastRadiusBlocks");
        requirePositive(visRadiusBlocks, "visRadiusBlocks");
        requirePositive(tilePixels, "tilePixels");

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");

        final MacroBiomeField macroField = new MacroBiomeField(seed);
        final CoastWidthField coastWidthField = new CoastWidthField(seed);

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] distZone " + pixels + "x" + pixels
                + " -> " + outFile.getAbsolutePath()
                + " (size=" + sizeBlocks + ", step=" + stepBlocks
                + ", coastRadius=" + coastRadiusBlocks + ", tilePixels=" + tilePixels + ")");
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final int minGx = -sizeBlocks / 2;
        final int minGz = -sizeBlocks / 2;
        final int minChunkX = floorDiv(minGx, 16);
        final int minChunkZ = floorDiv(minGz, 16);

        final int chunkStride = Math.max(1, stepBlocks / 16);

        long t0 = System.currentTimeMillis();
        long last = t0;

        long samples = 0;
        long builds = 0;

        for (int tilePz = 0; tilePz < pixels; tilePz += tilePixels) {
            for (int tilePx = 0; tilePx < pixels; tilePx += tilePixels) {

                int pz0 = tilePz, px0 = tilePx;
                int pz1 = Math.min(pixels, tilePz + tilePixels);
                int px1 = Math.min(pixels, tilePx + tilePixels);

                int centerPx = (px0 + px1 - 1) >> 1;
                int centerPz = (pz0 + pz1 - 1) >> 1;

                int centerChunkX = minChunkX + centerPx * chunkStride;
                int centerChunkZ = minChunkZ + centerPz * chunkStride;

                ChunkCoastField coast = ChunkCoastField.build(continentNoise, centerChunkX, centerChunkZ, coastRadiusBlocks);
                builds++;

                for (int pz = pz0; pz < pz1; pz++) {
                    int chunkZ = minChunkZ + pz * chunkStride;
                    int gz = (chunkZ << 4) + 8;
                    for (int px = px0; px < px1; px++) {
                        int chunkX = minChunkX + px * chunkStride;
                        int gx = (chunkX << 4) + 8;

                        boolean isLand = coast.isLandAt(continentNoise, gx, gz);
                        int dist = coast.distToCoastAt(continentNoise, gx, gz);

                        MacroBiome macro = macroField.pick(gx, gz);
                        CoastProfile profile = CoastProfiles.forMacro(macro);

                        int shelfW = coastWidthField.shelfWidthBlocks(gx, gz, profile);
                        int beachW = coastWidthField.beachWidthBlocks(gx, gz, profile);

                        Zone zone;
                        if (!isLand) zone = (dist <= shelfW) ? Zone.SHELF : Zone.DEEP;
                        else         zone = (dist <= beachW) ? Zone.BEACH : Zone.PLAINS;

                        Color base = zoneColor(zone);

                        double t = 1.0 - clamp01(dist / (double) visRadiusBlocks);
                        t = smooth01(t);
                        base = overlay(base, WHITE, 0.35 * t);

                        img.setRGB(px, pz, base.getRGB());
                        samples++;
                    }
                }
            }

            if (progressLog) {
                long now = System.currentTimeMillis();
                double percent = 100.0 * tilePz / pixels;
                System.out.printf(Locale.ROOT,
                    "[Talos2][EXPORT] %.2f%% row=%d/%d dt=%.3fs total=%.1fs builds=%d samples=%d%n",
                    percent, tilePz, pixels,
                    (now - last) / 1000.0,
                    (now - t0) / 1000.0,
                    builds, samples
                );
                last = now;
            }
        }

        ImageIO.write(img, "png", outFile);

        if (progressLog) {
            long t1 = System.currentTimeMillis();
            System.out.println("[Talos2][EXPORT] finished: " + outFile.getAbsolutePath()
                + " time=" + ((t1 - t0) / 1000.0) + "s builds=" + builds + " samples=" + samples);
        }
    }

    public static void exportIsLandMap(
        World world,
        SimplexNoiseOctave continentNoise,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int coastRadiusBlocks,
        int tilePixels,
        boolean progressLog
    ) throws IOException {

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] isLand " + pixels + "x" + pixels + " -> " + outFile.getAbsolutePath());
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final int minGx = -sizeBlocks / 2;
        final int minGz = -sizeBlocks / 2;
        final int minChunkX = floorDiv(minGx, 16);
        final int minChunkZ = floorDiv(minGz, 16);
        final int chunkStride = Math.max(1, stepBlocks / 16);

        long t0 = System.currentTimeMillis();
        long last = t0;

        long samples = 0;
        long builds = 0;

        for (int tilePz = 0; tilePz < pixels; tilePz += tilePixels) {
            for (int tilePx = 0; tilePx < pixels; tilePx += tilePixels) {

                int pz0 = tilePz, px0 = tilePx;
                int pz1 = Math.min(pixels, tilePz + tilePixels);
                int px1 = Math.min(pixels, tilePx + tilePixels);

                int centerPx = (px0 + px1 - 1) >> 1;
                int centerPz = (pz0 + pz1 - 1) >> 1;

                int centerChunkX = minChunkX + centerPx * chunkStride;
                int centerChunkZ = minChunkZ + centerPz * chunkStride;

                ChunkCoastField coast = ChunkCoastField.build(continentNoise, centerChunkX, centerChunkZ, coastRadiusBlocks);
                builds++;

                for (int pz = pz0; pz < pz1; pz++) {
                    int chunkZ = minChunkZ + pz * chunkStride;
                    int gz = (chunkZ << 4) + 8;

                    for (int px = px0; px < px1; px++) {
                        int chunkX = minChunkX + px * chunkStride;
                        int gx = (chunkX << 4) + 8;

                        boolean isLand = coast.isLandAt(continentNoise, gx, gz);
                        img.setRGB(px, pz, isLand ? 0xFFFFFF : 0x000000);
                        samples++;
                    }
                }
            }

            if (progressLog) {
                long now = System.currentTimeMillis();
                System.out.printf(Locale.ROOT,
                    "[Talos2][EXPORT] row=%d/%d dt=%.3fs total=%.1fs builds=%d samples=%d%n",
                    tilePz, pixels,
                    (now - last) / 1000.0,
                    (now - t0) / 1000.0,
                    builds, samples
                );
                last = now;
            }
        }

        ImageIO.write(img, "png", outFile);
    }

    public static void exportContinentC01Map(
        World world,
        SimplexNoiseOctave continentNoise,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int tilePixels,
        boolean progressLog
    ) throws IOException {

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] c01 " + pixels + "x" + pixels + " -> " + outFile.getAbsolutePath());
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final int minGx = -sizeBlocks / 2;
        final int minGz = -sizeBlocks / 2;

        long t0 = System.currentTimeMillis();
        long last = t0;

        long samples = 0;

        for (int pz = 0; pz < pixels; pz++) {
            int gz = minGz + pz * stepBlocks + (stepBlocks >> 1);
            for (int px = 0; px < pixels; px++) {
                int gx = minGx + px * stepBlocks + (stepBlocks >> 1);

                double c01 = Talos2Continent.sampleC01(continentNoise, gx, gz);
                c01 = clamp01(c01);

                int g = (int) Math.round(c01 * 255.0);
                int rgb = (g << 16) | (g << 8) | g;
                img.setRGB(px, pz, rgb);
                samples++;
            }

            if (progressLog && (pz % tilePixels == 0)) {
                long now = System.currentTimeMillis();
                System.out.printf(Locale.ROOT,
                    "[Talos2][EXPORT] row=%d/%d dt=%.3fs total=%.1fs samples=%d%n",
                    pz, pixels,
                    (now - last) / 1000.0,
                    (now - t0) / 1000.0,
                    samples
                );
                last = now;
            }
        }

        ImageIO.write(img, "png", outFile);
    }

    private enum Zone { DEEP, SHELF, BEACH, PLAINS }

    private static final Color WHITE = new Color(255, 255, 255);

    private static Color zoneColor(Zone z) {
        return switch (z) {
            case DEEP   -> new Color(12, 40, 90);
            case SHELF  -> new Color(25, 90, 160);
            case BEACH  -> new Color(235, 225, 140);
            case PLAINS -> new Color(50, 200, 80);
        };
    }

    private static Color overlay(Color dst, Color src, double alpha) {
        alpha = clamp01(alpha);
        int r = (int) Math.round(dst.getRed()   + (src.getRed()   - dst.getRed())   * alpha);
        int g = (int) Math.round(dst.getGreen() + (src.getGreen() - dst.getGreen()) * alpha);
        int b = (int) Math.round(dst.getBlue()  + (src.getBlue()  - dst.getBlue())  * alpha);
        return new Color(clamp255(r), clamp255(g), clamp255(b));
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double smooth01(double x) {
        x = clamp01(x);
        return x * x * (3.0D - 2.0D * x);
    }

    private static void requirePositive(int v, String name) {
        if (v <= 0) throw new IllegalArgumentException(name + " must be > 0");
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        int m = a % b;
        if ((m != 0) && ((a ^ b) < 0)) r--;
        return r;
    }

    private static File resolveOutFile(World world, String fileName) throws IOException {
        File outDir = new File(world.getSaveHandler().getWorldDirectory(), ExportConfig.outDirName());
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outDir.getAbsolutePath());
        }

        String safe = sanitizeFileName(fileName);
        safe = ensurePngExt(safe);

        File outFile = uniqueFile(outDir, safe);
        return outFile;
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return "talos_export";

        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);

        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (c <= 0x1F || c == 0x7F) {
                sb.append('_');
                continue;
            }

            if (c == '\\' || c == '/' || c == ':' || c == '*' || c == '?' ||
                c == '"'  || c == '<' || c == '>' || c == '|') {
                sb.append('_');
                continue;
            }

            sb.append(c);
        }

        String out = sb.toString().trim();

        while (!out.isEmpty() && (out.endsWith(".") || out.endsWith(" "))) {
            out = out.substring(0, out.length() - 1);
        }

        return out.isEmpty() ? "talos_export" : out;
    }

    private static String ensurePngExt(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") ? name : (name + ".png");
    }

    private static File uniqueFile(File dir, String fileName) {
        File f = new File(dir, fileName);
        if (!f.exists()) return f;

        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf(".png");
        String base = (dot >= 0) ? fileName.substring(0, dot) : fileName;
        String ext  = (dot >= 0) ? fileName.substring(dot) : "";

        for (int i = 1; i < 10_000; i++) {
            File candidate = new File(dir, base + "-" + i + ext);
            if (!candidate.exists()) return candidate;
        }

        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }
}
