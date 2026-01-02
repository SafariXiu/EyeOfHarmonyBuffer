package com.EyeOfHarmonyBuffer.space;

import com.EyeOfHarmonyBuffer.space.talos.*;
import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.biome.Talos2BiomeResolver.Talos2BiomeResolver;
import com.EyeOfHarmonyBuffer.space.talos.Talos2Hooks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import java.util.*;

public class Talos2MapExporter {

    private Talos2MapExporter() {}

    public static final class ExportConfig {

        private static String strProp(String key, String def) {
            String v = System.getProperty(key);
            return (v == null || v.isEmpty()) ? def : v;
        }

        public static String outDirName() {
            return strProp("talos.export.dir", "debug_talos");
        }
    }

    private static MacroBiomeField resolveMacroField(
        Talos2Hooks.HookData hook,
        long seed,
        MacroBiomeField.MacroBiomeConfig macroCfg
    ) {
        if (hook != null && hook.macroField != null) return hook.macroField;
        return new MacroBiomeField(seed, macroCfg);
    }

    private static CoastlineAtlas resolveCoastlineAtlas(
        Talos2Hooks.HookData hook,
        long seed,
        MacroBiomeField macroField,
        MacroBiomeField.MacroBiomeConfig macroCfg
    ) {
        if (hook != null) {
            if (hook.coastlineAtlas != null) return hook.coastlineAtlas;
            if (hook.macroField != null) return new DefaultCoastlineAtlas(hook.macroField, seed);
        }
        MacroBiomeField source = (macroField != null)
            ? macroField
            : new MacroBiomeField(seed,
            macroCfg != null ? macroCfg : Talos2NoiseConfig.currentMacroConfig());
        return new DefaultCoastlineAtlas(source, seed);
    }

    private static MacroBiomeField.MacroBiomeConfig resolveMacroConfig(
        Talos2Hooks.HookData hook,
        MacroBiomeField.MacroBiomeConfig override
    ) {
        if (override != null) return override;
        if (hook != null && hook.macroConfig != null) return hook.macroConfig;
        return Talos2NoiseConfig.currentMacroConfig();
    }

    public static void exportDistZoneMap(
        Talos2Hooks.HookData hook,
        World world,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int coastRadiusBlocks,
        int visRadiusBlocks,
        int tilePixels,
        boolean progressLog,
        int centerX,
        int centerZ,
        MacroBiomeField.MacroBiomeConfig macroConfig
    ) throws IOException {

        final MacroBiomeField.MacroBiomeConfig macroCfg = resolveMacroConfig(hook, macroConfig);
        Objects.requireNonNull(macroCfg, "macroConfig");

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");
        requirePositive(coastRadiusBlocks, "coastRadiusBlocks");
        requirePositive(visRadiusBlocks, "visRadiusBlocks");
        requirePositive(tilePixels, "tilePixels");

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) {
            throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");
        }

        final MacroBiomeField macroField = resolveMacroField(hook, seed, macroCfg);
        final CoastlineAtlas coastlineAtlas = resolveCoastlineAtlas(hook, seed, macroField, macroCfg);

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] distZone " + pixels + "x" + pixels
                + " -> " + outFile.getAbsolutePath()
                + " (size=" + sizeBlocks + ", step=" + stepBlocks
                + ", center=" + centerX + "," + centerZ
                + ", coastRadius=" + coastRadiusBlocks + ", tilePixels=" + tilePixels + ")");
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final int half = sizeBlocks / 2;
        final int minGx = centerX - half;
        final int minGz = centerZ - half;
        final int minChunkX = floorDiv(minGx, 16);
        final int minChunkZ = floorDiv(minGz, 16);

        final int chunkStride = Math.max(1, stepBlocks / 16);

        long t0 = System.currentTimeMillis();
        long last = t0;
        long samples = 0;

        for (int tilePz = 0; tilePz < pixels; tilePz += tilePixels) {
            for (int tilePx = 0; tilePx < pixels; tilePx += tilePixels) {

                int pz0 = tilePz, px0 = tilePx;
                int pz1 = Math.min(pixels, tilePz + tilePixels);
                int px1 = Math.min(pixels, tilePx + tilePixels);

                for (int pz = pz0; pz < pz1; pz++) {
                    int gz = minGz + pz * stepBlocks + (stepBlocks >> 1);
                    for (int px = px0; px < px1; px++) {
                        int gx = minGx + px * stepBlocks + (stepBlocks >> 1);

                        boolean isLand = coastlineAtlas.isLand(gx, gz);
                        int dist = coastlineAtlas.distanceToCoast(gx, gz);

                        MacroBiomeField.MacroSample sample = macroField.sampleMacro(gx, gz);
                        MacroBiome primary = (sample != null && sample.dominant != null)
                            ? sample.dominant
                            : MacroBiome.PLAINS_TEMPERATE;

                        int shelfW = coastlineAtlas.shelfWidth(gx, gz, primary);
                        int beachW = coastlineAtlas.beachWidth(gx, gz, primary);

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
                    "[Talos2][EXPORT] distZone %.2f%% row=%d/%d dt=%.3fs total=%.1fs samples=%d%n",
                    percent, tilePz, pixels,
                    (now - last) / 1000.0,
                    (now - t0) / 1000.0,
                    samples
                );
                last = now;
            }
        }

        ImageIO.write(img, "png", outFile);

        if (progressLog) {
            long t1 = System.currentTimeMillis();
            System.out.println("[Talos2][EXPORT] finished: " + outFile.getAbsolutePath()
                + " time=" + ((t1 - t0) / 1000.0) + "s samples=" + samples);
        }
    }

    public static void exportIsLandMap(
        Talos2Hooks.HookData hook,
        World world,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int coastRadiusBlocks,
        int tilePixels,
        boolean progressLog,
        int centerX,
        int centerZ
    ) throws IOException {

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");

        MacroBiomeField.MacroBiomeConfig hookMacroCfg =
            (hook != null && hook.macroConfig != null) ? hook.macroConfig : null;
        MacroBiomeField hookMacroField = (hook != null) ? hook.macroField : null;
        final CoastlineAtlas coastlineAtlas = resolveCoastlineAtlas(
            hook, seed, hookMacroField, hookMacroCfg
        );

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] isLand " + pixels + "x" + pixels
                + " -> " + outFile.getAbsolutePath()
                + " (size=" + sizeBlocks + ", step=" + stepBlocks
                + ", center=" + centerX + "," + centerZ
                + ", coastRadius=" + coastRadiusBlocks + ", tilePixels=" + tilePixels + ")");
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final int half = sizeBlocks / 2;
        final int minGx = centerX - half;
        final int minGz = centerZ - half;
        final int minChunkX = floorDiv(minGx, 16);
        final int minChunkZ = floorDiv(minGz, 16);
        final int chunkStride = Math.max(1, stepBlocks / 16);

        long t0 = System.currentTimeMillis();
        long last = t0;
        long samples = 0;

        for (int tilePz = 0; tilePz < pixels; tilePz += tilePixels) {
            for (int tilePx = 0; tilePx < pixels; tilePx += tilePixels) {

                int pz0 = tilePz, px0 = tilePx;
                int pz1 = Math.min(pixels, tilePz + tilePixels);
                int px1 = Math.min(pixels, tilePx + tilePixels);

                for (int pz = pz0; pz < pz1; pz++) {
                    int gz = minGz + pz * stepBlocks + (stepBlocks >> 1);

                    for (int px = px0; px < px1; px++) {
                        int gx = minGx + px * stepBlocks + (stepBlocks >> 1);

                        boolean isLand = coastlineAtlas.isLand(gx, gz);
                        img.setRGB(px, pz, isLand ? 0xFFFFFF : 0x000000);
                        samples++;
                    }
                }
            }

            if (progressLog) {
                long now = System.currentTimeMillis();
                System.out.printf(Locale.ROOT,
                    "[Talos2][EXPORT] isLand row=%d/%d dt=%.3fs total=%.1fs samples=%d%n",
                    tilePz, pixels,
                    (now - last) / 1000.0,
                    (now - t0) / 1000.0,
                    samples
                );
                last = now;
            }
        }

        ImageIO.write(img, "png", outFile);
    }

    public static void exportContinentC01Map(
        World world,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int tilePixels,
        boolean progressLog,
        int centerX,
        int centerZ
    ) throws IOException {

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] c01 " + pixels + "x" + pixels
                + " -> " + outFile.getAbsolutePath()
                + " (size=" + sizeBlocks + ", step=" + stepBlocks
                + ", center=" + centerX + "," + centerZ + ")");
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final int half = sizeBlocks / 2;
        final int minGx = centerX - half;
        final int minGz = centerZ - half;

        long t0 = System.currentTimeMillis();
        long last = t0;
        long samples = 0;

        SimplexNoiseOctave continentNoise = new SimplexNoiseOctave(
            seed ^ Talos2Continent.CONTINENT_SALT,
            Talos2Continent.CONTINENT_OCTAVES
        );

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
                    "[Talos2][EXPORT] c01 row=%d/%d dt=%.3fs total=%.1fs samples=%d%n",
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

    public static void exportMacroBiomeMap(
        Talos2Hooks.HookData hook,
        World world,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int tilePixels,
        boolean progressLog,
        int centerX,
        int centerZ,
        MacroBiomeField.MacroBiomeConfig macroConfig
    ) throws IOException {

        final MacroBiomeField.MacroBiomeConfig macroCfg = resolveMacroConfig(hook, macroConfig);
        Objects.requireNonNull(macroCfg, "macroConfig");

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");
        requirePositive(tilePixels, "tilePixels");

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) {
            throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");
        }

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] macroBiome " + pixels + "x" + pixels
                + " -> " + outFile.getAbsolutePath()
                + " (size=" + sizeBlocks + ", step=" + stepBlocks
                + ", center=" + centerX + "," + centerZ + ")");
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final MacroBiomeField macroField = resolveMacroField(hook, seed, macroCfg);
        final CoastlineAtlas coastlineAtlas = resolveCoastlineAtlas(hook, seed, macroField, macroCfg);
        final Talos2BiomeResolver resolver = new Talos2BiomeResolver(world, macroField);
        final MacroCellSampler sampler = new MacroCellSampler(macroField, coastlineAtlas, seed);

        final int half = sizeBlocks / 2;
        final int minGx = centerX - half;
        final int minGz = centerZ - half;

        long t0 = System.currentTimeMillis();
        long last = t0;
        long samples = 0;

        for (int tilePz = 0; tilePz < pixels; tilePz += tilePixels) {
            int pz1 = Math.min(pixels, tilePz + tilePixels);

            for (int tilePx = 0; tilePx < pixels; tilePx += tilePixels) {
                int px1 = Math.min(pixels, tilePx + tilePixels);

                for (int pz = tilePz; pz < pz1; pz++) {
                    int gz = minGz + pz * stepBlocks + (stepBlocks >> 1);

                    for (int px = tilePx; px < px1; px++) {
                        int gx = minGx + px * stepBlocks + (stepBlocks >> 1);

                        ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = sampler.sampleCell(gx, gz);
                        if (cell == null) {
                            img.setRGB(px, pz, macroColor(MacroBiome.PLAINS_TEMPERATE));
                            samples++;
                            continue;
                        }

                        if (!cell.isLand) {
                            int dist = coastlineAtlas.distanceToCoast(gx, gz);
                            int shelf = coastlineAtlas.shelfWidth(gx, gz, MacroBiome.OCEANIC);
                            MacroBiome waterMacro = (dist <= shelf) ? MacroBiome.COASTAL : MacroBiome.OCEANIC;
                            img.setRGB(px, pz, macroColor(waterMacro));
                            samples++;
                            continue;
                        }

                        MacroBiome macro = cell.primary != null ? cell.primary : MacroBiome.PLAINS_TEMPERATE;
                        img.setRGB(px, pz, macroColor(macro));
                        samples++;
                    }
                }
            }

            if (progressLog) {
                long now = System.currentTimeMillis();
                double percent = 100.0 * tilePz / pixels;
                System.out.printf(Locale.ROOT,
                    "[Talos2][EXPORT] macroBiome %.2f%% rows=%d/%d dt=%.3fs total=%.1fs samples=%d%n",
                    percent, tilePz, pixels,
                    (now - last) / 1000.0,
                    (now - t0) / 1000.0,
                    samples
                );
                last = now;
            }
        }

        ImageIO.write(img, "png", outFile);
    }


    public static void exportFinalBiomeMap(
        Talos2Hooks.HookData hook,
        World world,
        long seed,
        String fileName,
        int sizeBlocks,
        int stepBlocks,
        int tilePixels,
        boolean progressLog,
        int centerX,
        int centerZ,
        MacroBiomeField.MacroBiomeConfig macroConfig
    ) throws IOException {

        final MacroBiomeField.MacroBiomeConfig macroCfg = resolveMacroConfig(hook, macroConfig);
        Objects.requireNonNull(macroCfg, "macroConfig");

        requirePositive(sizeBlocks, "sizeBlocks");
        requirePositive(stepBlocks, "stepBlocks");
        requirePositive(tilePixels, "tilePixels");

        final int pixels = sizeBlocks / stepBlocks;
        if (pixels <= 0) {
            throw new IllegalArgumentException("pixels=sizeBlocks/stepBlocks must be > 0");
        }

        File outFile = resolveOutFile(world, fileName);
        if (progressLog) {
            System.out.println("[Talos2][EXPORT] finalBiome " + pixels + "x" + pixels
                + " -> " + outFile.getAbsolutePath()
                + " (size=" + sizeBlocks + ", step=" + stepBlocks
                + ", center=" + centerX + "," + centerZ + ")");
        }

        BufferedImage img = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);

        final MacroBiomeField macroField = resolveMacroField(hook, seed, macroCfg);
        final CoastlineAtlas coastlineAtlas = resolveCoastlineAtlas(hook, seed, macroField, macroCfg);
        final Talos2BiomeResolver resolver = new Talos2BiomeResolver(world, macroField);
        final MacroCellSampler sampler = new MacroCellSampler(macroField, coastlineAtlas, seed);

        final int half = sizeBlocks / 2;
        final int minGx = centerX - half;
        final int minGz = centerZ - half;

        long t0 = System.currentTimeMillis();
        long last = t0;
        long samples = 0;

        for (int tilePz = 0; tilePz < pixels; tilePz += tilePixels) {
            int pz1 = Math.min(pixels, tilePz + tilePixels);

            for (int tilePx = 0; tilePx < pixels; tilePx += tilePixels) {
                int px1 = Math.min(pixels, tilePx + tilePixels);

                for (int pz = tilePz; pz < pz1; pz++) {
                    int gz = minGz + pz * stepBlocks + (stepBlocks >> 1);

                    for (int px = tilePx; px < px1; px++) {
                        int gx = minGx + px * stepBlocks + (stepBlocks >> 1);

                        ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = sampler.sampleCell(gx, gz);
                        if (cell == null) {
                            img.setRGB(px, pz, biomeColor(TalosBiomes.TALOS_PLAINS, MacroBiome.PLAINS_TEMPERATE));
                            samples++;
                            continue;
                        }

                        if (!cell.isLand) {
                            int dist = coastlineAtlas.distanceToCoast(gx, gz);
                            int shelf = coastlineAtlas.shelfWidth(gx, gz, MacroBiome.OCEANIC);
                            MacroBiome waterMacro = (dist <= shelf) ? MacroBiome.COASTAL : MacroBiome.OCEANIC;
                            BiomeGenBase waterBiome = (dist <= shelf)
                                ? TalosBiomes.TALOS_SHELF
                                : TalosBiomes.TALOS_OCEAN;

                            img.setRGB(px, pz, biomeColor(waterBiome, waterMacro));
                            samples++;
                            continue;
                        }

                        MacroBiome macro = cell.primary != null ? cell.primary : MacroBiome.PLAINS_TEMPERATE;
                        BiomeGenBase biome = resolver.resolve(gx, gz, cell);
                        img.setRGB(px, pz, biomeColor(biome, macro));
                        samples++;
                    }
                }
            }

            if (progressLog) {
                long now = System.currentTimeMillis();
                double percent = 100.0 * tilePz / pixels;
                System.out.printf(Locale.ROOT,
                    "[Talos2][EXPORT] finalBiome %.2f%% rows=%d/%d dt=%.3fs total=%.1fs samples=%d%n",
                    percent, tilePz, pixels,
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

    private static int biomeColor(BiomeGenBase biome, MacroBiome macro) {
        if (macro == null) macro = MacroBiome.PLAINS_TEMPERATE;
        int base = macroColor(macro);

        if (biome == null) return base;

        int hash = (biome.biomeID * 31) & 0x0F;
        float delta = (hash - 7) * 0.008f;
        return adjustBrightness(base, delta);
    }

    private static int adjustBrightness(int rgb, float delta) {
        float factor = 1.0f + delta;
        int r = clamp255(Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = clamp255(Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = clamp255(Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
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

        return uniqueFile(outDir, safe);
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

    private static final EnumMap<MacroBiome, Integer> MACRO_COLORS = new EnumMap<>(MacroBiome.class);
    static {
        MACRO_COLORS.put(MacroBiome.OCEANIC, 0x0C285A);
        MACRO_COLORS.put(MacroBiome.COASTAL, 0xE7D38B);
        MACRO_COLORS.put(MacroBiome.LOWLAND_WET, 0x2A6FCC);
        MACRO_COLORS.put(MacroBiome.PLAINS_TEMPERATE,0x55B44D);
        MACRO_COLORS.put(MacroBiome.WARM_DRY, 0xD7B26A);
        MACRO_COLORS.put(MacroBiome.TROPICAL_HUMID, 0x1F8B45);
        MACRO_COLORS.put(MacroBiome.COOL_FORESTED, 0x3E5C4D);
        MACRO_COLORS.put(MacroBiome.SUBPOLAR, 0x95B6C7);
        MACRO_COLORS.put(MacroBiome.MOUNTAINOUS, 0x9DA4A8);
    }

    private static int macroColor(MacroBiome biome) {
        return MACRO_COLORS.getOrDefault(biome, 0xFF00FF);
    }

    private static final class MacroCellSampler {
        private static final MacroBiome[] MACRO_VALUES = MacroBiome.values();

        private final MacroBiomeField macroField;
        private final CoastlineAtlas coastline;
        private final SimplexNoiseOctave terrainNoise;
        private final Map<Long, ChunkProviderTalos2.ChunkShoreCache> chunkCache = new HashMap<>();

        MacroCellSampler(MacroBiomeField field, CoastlineAtlas coastline, long worldSeed) {
            this.macroField = Objects.requireNonNull(field, "macroField");
            this.coastline = Objects.requireNonNull(coastline, "coastline");
            this.terrainNoise = new SimplexNoiseOctave(worldSeed ^ 0x1234ABCDL, 4);
        }

        ChunkProviderTalos2.ChunkShoreCache.MacroCell sampleCell(int gx, int gz) {
            int chunkX = floorDiv(gx, 16);
            int chunkZ = floorDiv(gz, 16);
            long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);

            ChunkProviderTalos2.ChunkShoreCache chunk = chunkCache.computeIfAbsent(
                key, k -> buildChunk(chunkX, chunkZ)
            );

            int lx = gx - chunkX * 16;
            int lz = gz - chunkZ * 16;
            return chunk.macroContext[lx][lz];
        }

        private ChunkProviderTalos2.ChunkShoreCache buildChunk(int chunkX, int chunkZ) {
            ChunkProviderTalos2.ChunkShoreCache cache = new ChunkProviderTalos2.ChunkShoreCache();
            macroField.sample(chunkX, chunkZ, cache);
            populateCoastlineAndHeights(cache, chunkX, chunkZ);
            return cache;
        }

        private void populateCoastlineAndHeights(ChunkProviderTalos2.ChunkShoreCache out,
                                                 int chunkX, int chunkZ) {
            for (int lx = 0; lx < 17; lx++) {
                for (int lz = 0; lz < 17; lz++) {
                    int gx = chunkX * 16 + lx;
                    int gz = chunkZ * 16 + lz;

                    MacroBiome primary = safeMacro(out.macroPrimary[lx][lz]);
                    MacroBiome secondary = safeMacro(out.macroSecondary[lx][lz]);
                    double blend = (out.macroBlend[lx][lz] & 0xFF) / 255.0;

                    boolean isLand = coastline.isLand(gx, gz);
                    int dist = clampToU16(coastline.distanceToCoast(gx, gz));
                    int beach = clampToU16(coastline.beachWidth(gx, gz, primary));
                    int shelf = clampToU16(coastline.shelfWidth(gx, gz, primary));

                    ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = out.macroContext[lx][lz];
                    cell.primary = primary;
                    cell.secondary = secondary;
                    cell.blendPrimary = blend;
                    cell.tier = out.macroTier[lx][lz];
                    cell.plateId = out.macroPlateId[lx][lz];
                    cell.plateauAnchor = out.macroPlateau[lx][lz];
                    cell.isLand = isLand;
                    cell.distToCoast = (short) dist;
                    cell.beachWidth = (short) beach;
                    cell.shelfWidth = (short) shelf;

                    cell.patchVariant = out.macroPatchVariant[lx][lz];
                    cell.patchSingleBiome = (out.macroPatchFlags[lx][lz] & 0x1) != 0;
                    cell.patchEdgeBlend = (out.macroPatchEdge[lx][lz] & 0xFF) / 255.0D;

                    short macroBase = (short) Math.round(
                        sampleBlendedMacroHeight(gx, gz, primary, secondary, blend)
                    );
                    cell.macroBaseHeight = macroBase;
                }
            }
        }

        private MacroBiome safeMacro(byte id) {
            MacroBiome m = MACRO_VALUES[id & 0xFF];
            return (m != null) ? m : MacroBiome.PLAINS_TEMPERATE;
        }

        private short clampToU16(int v) {
            if (v < 0) return 0;
            if (v > 65535) return (short) 0xFFFF;
            return (short) v;
        }

        private double sampleBlendedMacroHeight(int gx, int gz,
                                                MacroBiome primary,
                                                MacroBiome secondary,
                                                double blendPrimary) {
            MacroHeightSample sample = blendHeightProfiles(primary, secondary, blendPrimary);
            double base = lerp(sample.min, sample.max, sample.blend);
            double plateau = sample.offset * 32.0D;
            double micro = terrainNoise.noise(gx * 0.0008D, gz * 0.0008D) * sample.variation * 3.0D;
            return clamp(base + plateau + micro, sample.min, sample.max);
        }

        private MacroHeightSample blendHeightProfiles(MacroBiome primary,
                                                      MacroBiome secondary,
                                                      double blendPrimary) {
            MacroBiome.MacroHeightProfile hpA = (primary != null) ? primary.height : MacroBiome.PLAINS_TEMPERATE.height;
            MacroBiome.MacroHeightProfile hpB = (secondary != null) ? secondary.height : hpA;

            double t = clamp01(blendPrimary);
            double invT = 1.0D - t;

            double min = hpA.absoluteMin * t + hpB.absoluteMin * invT;
            double max = hpA.absoluteMax * t + hpB.absoluteMax * invT;
            double offset = hpA.baseHeightOffset * t + hpB.baseHeightOffset * invT;
            double variation = hpA.heightVariation * t + hpB.heightVariation * invT;

            return new MacroHeightSample(min, max, offset, variation, t);
        }

        private static final class MacroHeightSample {
            final double min, max, offset, variation, blend;
            MacroHeightSample(double min, double max, double offset, double variation, double blend) {
                this.min = min; this.max = max; this.offset = offset;
                this.variation = variation; this.blend = blend;
            }
        }

        private static double clamp(double v, double min, double max) {
            return v < min ? min : (v > max ? max : v);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }
}
