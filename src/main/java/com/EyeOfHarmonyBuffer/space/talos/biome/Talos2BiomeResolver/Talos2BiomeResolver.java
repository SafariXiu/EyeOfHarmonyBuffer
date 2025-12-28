package com.EyeOfHarmonyBuffer.space.talos.biome.Talos2BiomeResolver;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import com.EyeOfHarmonyBuffer.space.talos.biome.Talos2ClimateSampler;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.World;

import java.util.*;

import static com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField.clamp01;

public final class Talos2BiomeResolver {

    private static final boolean DEBUG_LOG_MACRO =
        Boolean.parseBoolean(System.getProperty("talos.debug.macro", "false"));
    private static final int DEBUG_LOG_CHUNK_INTERVAL = 16;

    private static final boolean DEBUG_LOG_FINAL =
        Boolean.parseBoolean(System.getProperty("talos.debug.final", "false"));
    private static final int DEBUG_LOG_FINAL_INTERVAL = 4;

    private static final Optional<MacroBiome> DEBUG_FORCE_MACRO =
        Optional.ofNullable(System.getProperty("talos.forceMacro"))
            .flatMap(name -> {
                try {
                    return Optional.of(MacroBiome.valueOf(name));
                } catch (IllegalArgumentException ex) {
                    return Optional.empty();
                }
            });

    private static final boolean DEBUG_MICRO =
        Boolean.parseBoolean(System.getProperty("talos.debug.micro", "true"));
    private static final int DEBUG_MICRO_MAX = 200;

    private static final boolean DEBUG_TIMING =
        Boolean.parseBoolean(System.getProperty("talos.debug.timing", "false"));

    private static final double MICRO_SCALE_NOISE_FREQ = 1.0 / 40_000.0;
    private static final int MICRO_SIZE_MIN = 2_000;
    private static final int MICRO_SIZE_MAX = 3_000;

    private static final long VARIANT_SALT = 0xC6BC279692B5CC83L;
    private static final double HUMID_BAND_SCALE = 1.0 / 4_000.0;

    private static final int PLATE_OFF_TEMPERATE_DRY    = 10_000;
    private static final int PLATE_OFF_TEMPERATE_NORMAL = 11_000;
    private static final int PLATE_OFF_TEMPERATE_HUMID  = 12_000;

    private static final SimplexNoiseOctave MICRO_SCALE_NOISE;
    static {
        long seed = VARIANT_SALT ^ 0x1234ABCD1234ABCDL;
        MICRO_SCALE_NOISE = new SimplexNoiseOctave(seed, 1);

        long warpSeed = VARIANT_SALT ^ 0x0F0F0F0F0F0F0F0FL;
        MICRO_WARP_X_COARSE = new SimplexNoiseOctave(warpSeed ^ 0x13579BDF2468ACE0L, 2);
        MICRO_WARP_Z_COARSE = new SimplexNoiseOctave(warpSeed ^ 0x2468ACE013579BDFL, 2);
        MICRO_WARP_X_FINE   = new SimplexNoiseOctave(warpSeed ^ 0x89ABCDEFFEDCBA98L, 3);
        MICRO_WARP_Z_FINE   = new SimplexNoiseOctave(warpSeed ^ 0x76543210ABCDEF01L, 3);

        System.out.println("[Talos2BiomeResolver]:1");
    }

    private static final double MICRO_WARP_FREQ_COARSE = 1.0 / 8_000.0;
    private static final double MICRO_WARP_FREQ_FINE   = 1.0 / 2_000.0;
    private static final double MICRO_WARP_AMPLITUDE   = 0.45D;
    private static final SimplexNoiseOctave MICRO_PLATE_NOISE =
        new SimplexNoiseOctave(VARIANT_SALT ^ 0xBEEFDEADL, 4);
    private static final double MICRO_PLATE_SCALE = 1.0D / 2048.0D;
    private static final int    MICRO_PLATE_OCTAVES = 1;


    private static final SimplexNoiseOctave MICRO_WARP_X_COARSE;
    private static final SimplexNoiseOctave MICRO_WARP_Z_COARSE;
    private static final SimplexNoiseOctave MICRO_WARP_X_FINE;
    private static final SimplexNoiseOctave MICRO_WARP_Z_FINE;

    private final Talos2ClimateSampler climateSampler;
    private final MacroBiomeField macroField;
    private final SimplexNoiseOctave humidBandNoise;
    private final MicroPlateSelector microSelector;

    private final Map<MacroBiome, TalosMacroPicker> pickerRegistry;

    public Talos2BiomeResolver(World world, MacroBiomeField macroField) {
        this.macroField = macroField;
        this.climateSampler = new Talos2ClimateSampler(world, macroField);

        long seed = world.getSeed() ^ 0xA1B2C3D4E5F60718L;
        this.humidBandNoise = new SimplexNoiseOctave(seed ^ 0x55AA55AA55AA55AAL, 2);
        this.microSelector = new MicroPlateSelector();

        this.pickerRegistry = buildPickerRegistry();

        System.out.println("[Talos2BiomeResolver] build 2024-12-27 micro=voronoi rng=hash -1");
        System.out.println("DEBUG_LOG_MACRO is :" + DEBUG_LOG_MACRO);
    }

    public BiomeGenBase resolve(int x, int z) {
        final long t0 = now();

        TalosBiomeContext ctx = new TalosBiomeContext(x, z);

        long a;

        a = now();
        TalosClimatePipeline.sampleMacroAndClimate(ctx, macroField, climateSampler, DEBUG_FORCE_MACRO);
        long tClimate = now() - a;

        a = now();
        MacroHumidityPipeline.apply(ctx, humidBandNoise);
        long tHumid = now() - a;

        a = now();
        PickerResult pickerResult = TalosPickerPipeline.pick(ctx, pickerRegistry);
        long tPicker = now() - a;

        a = now();
        BiomeGenBase biome = finalizeBiome(ctx, pickerResult);
        long tFinal = now() - a;

        long tTotal = now() - t0;

        TalosDebugLogger.logTiming(ctx, tTotal, tClimate, tHumid, tPicker, tFinal);

        return biome;
    }

    private Map<MacroBiome, TalosMacroPicker> buildPickerRegistry() {
        Map<MacroBiome, TalosMacroPicker> map = new EnumMap<>(MacroBiome.class);

        map.put(MacroBiome.SUBPOLAR, new TalosMacroPicker() {
            @Override public String name() { return "pickSubpolar"; }

            @Override
            public BiomeGenBase pick(TalosBiomeContext ctx) {
                double temp = ctx.temperature();
                double humid = ctx.humidity();
                float rough = ctx.roughness();

                if (temp < 0.18D && humid < 0.35D) return TalosBiomes.TALOS_POLAR_DESERT;
                if (temp < 0.30D && rough > 0.6F) return TalosBiomes.TALOS_ALPINE;
                if (humid < 0.45D) return TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
                return TalosBiomes.TALOS_COOL_FOREST;
            }
        });

        map.put(MacroBiome.COOL_FORESTED, new TalosMacroPicker() {
            @Override public String name() { return "pickCoolForested"; }

            @Override
            public BiomeGenBase pick(TalosBiomeContext ctx) {
                double temp = ctx.temperature();
                double humid = ctx.humidity();
                float rough = ctx.roughness();

                if (rough > 0.65F) return TalosBiomes.TALOS_ALPINE;
                if (temp < 0.40D) return TalosBiomes.TALOS_COOL_FOREST;
                if (humid < 0.45D) return TalosBiomes.TALOS_TEMPERATE_STEPPE;
                return TalosBiomes.TALOS_TEMPERATE_FOREST;
            }
        });

        map.put(MacroBiome.PLAINS_TEMPERATE, new PlainsTemperatePicker());

        map.put(MacroBiome.WARM_DRY, new WarmDryPicker());

        map.put(MacroBiome.TROPICAL_HUMID, new TalosMacroPicker() {
            @Override public String name() { return "pickTropical"; }

            @Override
            public BiomeGenBase pick(TalosBiomeContext ctx) {
                double humid = ctx.humidity();
                float rough = ctx.roughness();

                if (humid < 0.55D) return TalosBiomes.TALOS_SAVANNA;
                if (rough > 0.60F) return TalosBiomes.TALOS_PLATEAU;
                return TalosBiomes.TALOS_TROPICAL_RAIN;
            }
        });

        map.put(MacroBiome.MOUNTAINOUS, new TalosMacroPicker() {
            @Override public String name() { return "pickMountain"; }

            @Override
            public BiomeGenBase pick(TalosBiomeContext ctx) {
                double temp = ctx.temperature();
                double humid = ctx.humidity();
                float rough = ctx.roughness();

                if (rough > 0.7F || temp < 0.30D) return TalosBiomes.TALOS_ALPINE;
                if (humid > 0.55D) return TalosBiomes.TALOS_COOL_FOREST;
                return TalosBiomes.TALOS_MOUNTAINS;
            }
        });

        map.put(MacroBiome.LOWLAND_WET, new TalosMacroPicker() {
            @Override public String name() { return "pickWetlands"; }

            @Override
            public BiomeGenBase pick(TalosBiomeContext ctx) {
                if (ctx.humidity() > 0.80D) return TalosBiomes.TALOS_BASIN;
                return TalosBiomes.TALOS_TROPICAL_RAIN;
            }
        });

        map.put(MacroBiome.COASTAL, new TalosMacroPicker() {
            @Override public String name() { return "pickCoastal"; }

            @Override
            public BiomeGenBase pick(TalosBiomeContext ctx) {
                double humid = ctx.humidity();
                float rough = ctx.roughness();

                if (rough < 0.35F) return TalosBiomes.TALOS_BEACH;
                if (humid > 0.65D) return TalosBiomes.TALOS_BASIN;
                return TalosBiomes.TALOS_PLAINS;
            }
        });

        map.put(MacroBiome.OCEANIC, new TalosMacroPicker() {
            @Override public String name() { return "pickOceanic"; }

            @Override
            public BiomeGenBase pick(TalosBiomeContext ctx) {
                if (ctx.roughness() > 0.45F) return TalosBiomes.TALOS_SHELF;
                return TalosBiomes.TALOS_OCEAN;
            }
        });

        return map;
    }

    private final class PlainsTemperatePicker implements TalosMacroPicker {
        @Override public String name() { return "pickTemperate"; }

        @Override
        public BiomeGenBase pick(TalosBiomeContext ctx) {
            double humidMacro = ctx.macroHumidity();

            BiomeGenBase[] set;
            MicroPlateSelector.Request request;

            if (humidMacro < 0.40D) {
                set = new BiomeGenBase[] {
                    TalosBiomes.TALOS_TEMPERATE_STEPPE,
                    TalosBiomes.TALOS_PLAINS
                };
                request = MicroPlateSelector.Request.dry(ctx, PLATE_OFF_TEMPERATE_DRY, set.length);
                ctx.setBucketLabel("temperate-dry");
            } else if (humidMacro > 0.70D) {
                set = new BiomeGenBase[] {
                    TalosBiomes.TALOS_TEMPERATE_FOREST,
                    TalosBiomes.TALOS_COOL_FOREST
                };
                request = MicroPlateSelector.Request.humid(ctx, PLATE_OFF_TEMPERATE_HUMID, set.length);
                ctx.setBucketLabel("temperate-humid");
            } else {
                set = new BiomeGenBase[] {
                    TalosBiomes.TALOS_PLAINS,
                    TalosBiomes.TALOS_TEMPERATE_FOREST
                };
                request = MicroPlateSelector.Request.normal(ctx, PLATE_OFF_TEMPERATE_NORMAL, set.length);
                ctx.setBucketLabel("temperate-normal");
            }

            int idx = microSelector.select(request, ctx);
            return set[idx];
        }
    }

    private final class WarmDryPicker implements TalosMacroPicker {
        @Override public String name() { return "pickWarmDry"; }

        @Override
        public BiomeGenBase pick(TalosBiomeContext ctx) {
            List<WeightedBiome> entries = new ArrayList<>();
            double humid = ctx.humidity();
            float rough = ctx.roughness();

            entries.add(new WeightedBiome(TalosBiomes.TALOS_DESERT, humid < 0.25D ? 4 : 2));
            entries.add(new WeightedBiome(TalosBiomes.TALOS_WARM_STEPPE, rough > 0.55F ? 3 : 1));
            entries.add(new WeightedBiome(TalosBiomes.TALOS_SAVANNA, 2));

            WeightedBiome picked = microSelector.selectWeighted(
                ctx,
                entries,
                MacroBiome.WARM_DRY,
                "warm-dry-weighted"
            );

            return picked == null ? TalosBiomes.TALOS_DESERT : picked.biome;
        }
    }

    private BiomeGenBase finalizeBiome(TalosBiomeContext ctx, PickerResult result) {
        String pickerName = result.pickerName;
        BiomeGenBase biome = result.biome;

        if (biome == null) {
            biome = resolveVariant(ctx);
            pickerName = pickerName + "+variant";
        }

        if (biome == null) {
            biome = TalosBiomes.TALOS_PLAINS;
            pickerName = pickerName + "+fallback";
        }

        TalosDebugLogger.logFinal(ctx, pickerName, biome);
        return biome;
    }

    private BiomeGenBase resolveVariant(TalosBiomeContext ctx) {
        MacroBiome macro = ctx.macro();
        List<MacroBiome.MacroBiomeVariant> variants = macro.variants;
        if (variants == null || variants.isEmpty()) return null;

        int idx = microSelector.select(
            MicroPlateSelector.Request.variant(ctx, variants.size()),
            ctx
        );
        return variants.get(idx).biome;
    }

    private interface TalosMacroPicker {
        String name();
        BiomeGenBase pick(TalosBiomeContext ctx);
    }

    private static final class PickerResult {
        final String pickerName;
        final BiomeGenBase biome;

        PickerResult(String pickerName, BiomeGenBase biome) {
            this.pickerName = pickerName;
            this.biome = biome;
        }
    }

    private static final class WeightedBiome {
        final BiomeGenBase biome;
        final int weight;
        WeightedBiome(BiomeGenBase biome, int weight) {
            this.biome = biome;
            this.weight = Math.max(1, weight);
        }
    }

    private static final class TalosBiomeContext {
        private final int x;
        private final int z;

        private MacroBiomeField.SampleDual macroSample;
        private Talos2ClimateSampler.ClimateSample climate;
        private MacroBiome macro;
        private double macroHumidity;
        private String bucketLabel = "-";

        private double macroWarmth;
        private double macroWetness;
        private MacroBlend macroBlend;

        double macroWarmth() { return macroWarmth; }
        double macroWetness() { return macroWetness; }
        MacroBlend macroBlend() { return macroBlend; }

        void setMacroWarmth(double value) { this.macroWarmth = value; }
        void setMacroWetness(double value) { this.macroWetness = value; }
        void setMacroBlend(MacroBlend blend) { this.macroBlend = blend == null ? new MacroBlend() : blend; }

        TalosBiomeContext(int x, int z) {
            this.x = x;
            this.z = z;
        }

        int x() { return x; }
        int z() { return z; }

        void setMacroSample(MacroBiomeField.SampleDual sample) { this.macroSample = sample; }
        void setClimate(Talos2ClimateSampler.ClimateSample sample) { this.climate = sample; }
        void setMacro(MacroBiome macro) { this.macro = macro; }
        void setMacroHumidity(double value) { this.macroHumidity = value; }
        void setBucketLabel(String label) { this.bucketLabel = label == null ? "-" : label; }

        MacroBiome macro() { return macro; }
        double macroHumidity() { return macroHumidity; }
        String bucketLabel() { return bucketLabel; }
        Talos2ClimateSampler.ClimateSample climate() { return climate; }

        double temperature() { return climate.temperature; }
        double humidity() { return climate.humidity; }
        float roughness() { return climate.roughness; }
    }

    private static final class TalosClimatePipeline {

        static void sampleMacroAndClimate(
            TalosBiomeContext ctx,
            MacroBiomeField macroField,
            Talos2ClimateSampler sampler,
            Optional<MacroBiome> forcedMacro
        ) {
            MacroBiomeField.SampleDual macroSample = macroField.sampleDual(ctx.x(), ctx.z());
            Talos2ClimateSampler.ClimateSample climate = sampler.sample(ctx.x(), ctx.z());

            ctx.setMacroSample(macroSample);
            ctx.setClimate(climate);

            double warmth = clamp01(0.5D + climate.temperature * 0.5D);
            double wetness = clamp01(0.5D + climate.humidity * 0.5D);

            ctx.setMacroWarmth(warmth);
            ctx.setMacroWetness(wetness);

            MacroBlend blend = buildMacroBlend(macroSample, warmth, wetness, climate.roughness);
            ctx.setMacroBlend(blend);

            MacroBiome chosen = forcedMacro.orElse(blend.pickDominant());
            ctx.setMacro(chosen);

            if (DEBUG_LOG_MACRO) {
                int chunkX = ctx.x() >> 4;
                int chunkZ = ctx.z() >> 4;
                if (Math.floorMod(chunkX, DEBUG_LOG_CHUNK_INTERVAL) == 0
                    && Math.floorMod(chunkZ, DEBUG_LOG_CHUNK_INTERVAL) == 0) {
                    logMacroSample(ctx.x(), ctx.z(), macroSample, climate);
                }
            }
        }

        private static double computeCoastalMask(MacroBiomeField.SampleDual sample) {
            if (sample.primary == null) return 0.0D;

            boolean primaryOcean = sample.primary.isOceanic();
            boolean secondaryOcean = sample.secondary != null && sample.secondary.isOceanic();
            boolean primaryLand = !primaryOcean;
            boolean secondaryLand = sample.secondary != null && !sample.secondary.isOceanic();

            double weight = sample.primaryWeight;

            if ((primaryOcean && secondaryLand) || (primaryLand && secondaryOcean)) {
                double distanceFromHalf = Math.abs(weight - 0.5D);
                if (distanceFromHalf <= 0.25D) {
                    return 1.0D - (distanceFromHalf / 0.25D);
                }
            }

            if (sample.primary == MacroBiome.COASTAL
                || sample.secondary == MacroBiome.COASTAL) {
                return 0.7D;
            }

            return 0.0D;
        }

        private static MacroBlend buildMacroBlend(
            MacroBiomeField.SampleDual macroSample,
            double warmth,
            double wetness,
            float roughness
        ) {
            MacroBlend blend = new MacroBlend();

            boolean primaryOcean = macroSample.primary != null && macroSample.primary.isOceanic();
            boolean secondaryOcean = macroSample.secondary != null && macroSample.secondary.isOceanic();
            double oceanHint = primaryOcean ? macroSample.primaryWeight
                : secondaryOcean ? (1.0D - macroSample.primaryWeight) : 0.0D;

            double oceanMask = MathHelper.clamp_double(oceanHint, 0.0D, 1.0D);
            double landMask = 1.0D - oceanMask;
            double coastalMask = computeCoastalMask(macroSample);
            blend.set(MacroBiome.COASTAL, coastalMask * weightCoastal(wetness, roughness));

            blend.set(MacroBiome.SUBPOLAR, landMask * weightSubpolar(warmth));
            blend.set(MacroBiome.COOL_FORESTED, landMask * weightCoolForested(warmth, wetness));
            blend.set(MacroBiome.PLAINS_TEMPERATE, landMask * weightTemperate(warmth, wetness));
            blend.set(MacroBiome.WARM_DRY, landMask * weightWarmDry(warmth, wetness));
            blend.set(MacroBiome.TROPICAL_HUMID, landMask * weightTropical(warmth, wetness));
            blend.set(MacroBiome.MOUNTAINOUS, landMask * weightMountainous(roughness));
            blend.set(MacroBiome.LOWLAND_WET, landMask * weightLowlandWet(wetness));
            blend.set(MacroBiome.COASTAL, coastalMask * weightCoastal(wetness, roughness));
            blend.set(MacroBiome.OCEANIC, oceanMask * weightOceanic(roughness));

            blend.normalize();
            return blend;
        }

        private static double weightSubpolar(double warmth) {
            return smoothstep(0.35D - warmth, 0.0D, 0.25D);
        }

        private static double weightCoolForested(double warmth, double wetness) {
            return bellCurve(warmth, 0.35D, 0.12D) * clamp01(wetness + 0.2D);
        }

        private static double weightTemperate(double warmth, double wetness) {
            return bellCurve(warmth, 0.55D, 0.18D) * bellCurve(wetness, 0.55D, 0.20D);
        }

        private static double weightWarmDry(double warmth, double wetness) {
            return smoothstep(warmth, 0.60D, 0.80D) * smoothstep(0.45D - wetness, 0.0D, 0.30D);
        }

        private static double weightTropical(double warmth, double wetness) {
            return smoothstep(warmth, 0.70D, 0.90D) * smoothstep(wetness, 0.65D, 0.85D);
        }

        private static double weightMountainous(float roughness) {
            return smoothstep(roughness, 0.55F, 0.80F);
        }

        private static double weightLowlandWet(double wetness) {
            return smoothstep(wetness, 0.75D, 0.90D);
        }

        private static double weightCoastal(double wetness, float roughness) {
            double flatness = smoothstep(0.35F - roughness, 0.0F, 0.30F);
            return flatness * bellCurve(wetness, 0.60D, 0.25D);
        }

        private static double weightOceanic(float roughness) {
            return smoothstep(0.40F - roughness, 0.0F, 0.35F);
        }

        private static double smoothstep(double value, double edge0, double edge1) {
            double t = clamp01((value - edge0) / (edge1 - edge0));
            return t * t * (3.0D - 2.0D * t);
        }

        private static double bellCurve(double value, double center, double width) {
            double t = (value - center) / width;
            return Math.exp(-t * t);
        }
    }

    private static final class MacroHumidityPipeline {

        static void apply(TalosBiomeContext ctx, SimplexNoiseOctave bandNoise) {
            double band = bandNoise.noise(ctx.x() * HUMID_BAND_SCALE, ctx.z() * HUMID_BAND_SCALE);
            double band01 = MathHelper.clamp_double(0.5D + band * 0.5D, 0.0D, 1.0D);
            double mixed = MathHelper.clamp_double(
                band01 * 0.9D + ctx.humidity() * 0.1D,
                0.0D, 1.0D
            );
            ctx.setMacroHumidity(mixed);
        }
    }

    private static final class TalosPickerPipeline {

        static PickerResult pick(
            TalosBiomeContext ctx,
            Map<MacroBiome, TalosMacroPicker> registry
        ) {
            TalosMacroPicker picker = registry.get(ctx.macro());
            if (picker == null) {
                return new PickerResult("pickUnknown", null);
            }

            BiomeGenBase biome = picker.pick(ctx);
            return new PickerResult(picker.name(), biome);
        }
    }

    private static final class MicroPlateSelector {
        private static final int SUPER_CELL_SIZE = 4_096;
        private static final double JITTER_AMPLITUDE = 0.45D;
        private int debugPrints = 0;

        int select(Request request, TalosBiomeContext ctx) {
            if (request.count <= 1) return 0;

            int baseX = request.xOffset + ctx.x();
            int baseZ = request.zOffset + ctx.z();

            int localSize = sampleLocalMicroSize(baseX, baseZ);

            double warpedX = warpCoordinate(baseX, baseZ, localSize, true);
            double warpedZ = warpCoordinate(baseX, baseZ, localSize, false);

            CellSelection cell = locateVoronoiCell(warpedX, warpedZ, localSize);

            int hash = hashCoordsWithLocalSize(cell.cellX, cell.cellZ, request.count, localSize);
            if (hash < 0) hash = Math.abs(hash);

            int idx = pickMicroPlate(baseX, baseZ, request.count);

            logMicro(ctx, request, cell, hash, idx, localSize);
            return idx;
        }

        private static double warpCoordinate(double x, double z, int localSize, boolean axisX) {
            SimplexNoiseOctave coarse = axisX ? MICRO_WARP_X_COARSE : MICRO_WARP_Z_COARSE;
            SimplexNoiseOctave fine   = axisX ? MICRO_WARP_X_FINE   : MICRO_WARP_Z_FINE;

            double coarseShift = coarse.noise(x * MICRO_WARP_FREQ_COARSE, z * MICRO_WARP_FREQ_COARSE);
            double fineShift   = fine.noise(
                (x + (axisX ? 1_000 : -1_000)) * MICRO_WARP_FREQ_FINE,
                (z + (axisX ? -1_000 : 1_000)) * MICRO_WARP_FREQ_FINE
            );

            double blend = coarseShift * 0.7D + fineShift * 0.3D;
            double amplitude = localSize * MICRO_WARP_AMPLITUDE;

            return x + blend * amplitude;
        }

        private static CellSelection locateVoronoiCell(double worldX, double worldZ, int localSize) {
            double inv = 1.0D / localSize;
            int baseX = MathHelper.floor_double(worldX * inv);
            int baseZ = MathHelper.floor_double(worldZ * inv);

            double bestDist = Double.MAX_VALUE;
            int bestCellX = baseX;
            int bestCellZ = baseZ;

            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cellX = baseX + dx;
                    int cellZ = baseZ + dz;

                    long jitterSeed = hashJitterSeed(cellX, cellZ);
                    double jitterX = jitterFromSeed(jitterSeed, 0);
                    double jitterZ = jitterFromSeed(jitterSeed, 1);

                    double centerX = (cellX + 0.5D + jitterX) * localSize;
                    double centerZ = (cellZ + 0.5D + jitterZ) * localSize;

                    double dX = worldX - centerX;
                    double dZ = worldZ - centerZ;
                    double distSq = dX * dX + dZ * dZ;

                    if (distSq < bestDist) {
                        bestDist = distSq;
                        bestCellX = cellX;
                        bestCellZ = cellZ;
                    }
                }
            }

            return new CellSelection(bestCellX, bestCellZ);
        }

        private static long hashJitterSeed(int cellX, int cellZ) {
            long h = VARIANT_SALT ^ 0x5EEDFACECAFEBEEFL;
            h ^= (long) cellX * 0x632BE59BD9B4E019L;
            h ^= (long) cellZ * 0x9E3779B185EBCA87L;
            h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
            return h ^ (h >>> 31);
        }

        private static double jitterFromSeed(long seed, int lane) {
            long rotated = Long.rotateLeft(seed, lane * 21);
            double unit = ((rotated & 0xFFFFFFL) / (double) 0xFFFFFFL) - 0.5D;
            return unit * JITTER_AMPLITUDE;
        }

        private static int sampleLocalMicroSize(int x, int z) {
            double noise = MICRO_SCALE_NOISE.noise(
                x * MICRO_SCALE_NOISE_FREQ,
                z * MICRO_SCALE_NOISE_FREQ
            );
            double t = 0.5D + 0.5D * noise;

            int size = (int) Math.round(
                MICRO_SIZE_MIN + t * (MICRO_SIZE_MAX - MICRO_SIZE_MIN)
            );

            int step = 200;
            size = (size / step) * step;

            return MathHelper.clamp_int(size, MICRO_SIZE_MIN, MICRO_SIZE_MAX);
        }

        WeightedBiome selectWeighted(
            TalosBiomeContext ctx,
            List<WeightedBiome> entries,
            MacroBiome macro,
            String bucketLabel
        ) {
            if (entries.isEmpty()) return null;

            List<WeightedBiome> expanded = new ArrayList<>();
            for (WeightedBiome entry : entries) {
                for (int i = 0; i < entry.weight; i++) {
                    expanded.add(entry);
                }
            }

            if (expanded.isEmpty()) return null;

            Request req = new Request(ctx, 0, 0, expanded.size(), macro, bucketLabel);
            int expandedIdx = select(req, ctx);
            return expanded.get(expandedIdx);
        }

        private void logMicro(
            TalosBiomeContext ctx,
            Request req,
            CellSelection cell,
            int hash,
            int idx,
            int localSize
        ) {
            if (!DEBUG_MICRO) return;
            if (debugPrints >= DEBUG_MICRO_MAX) return;

            int chunkX = ctx.x() >> 4;
            int chunkZ = ctx.z() >> 4;
            if (Math.floorMod(chunkX, DEBUG_LOG_CHUNK_INTERVAL) != 0
                || Math.floorMod(chunkZ, DEBUG_LOG_CHUNK_INTERVAL) != 0) {
                return;
            }

            debugPrints++;

            String macroLabel = req.macro == null ? "-" : req.macro.name();
            String bucket = req.bucketLabel == null ? "-" : req.bucketLabel;
            String humidLabel = String.format(Locale.ROOT, "%.3f", ctx.macroHumidity());

            System.out.printf(
                Locale.ROOT,
                "[MICRO %03d] macro=%s bucket=%s humid=%s x=%d z=%d cell=(%d,%d) size=%d count=%d hash=%d idx=%d%n",
                debugPrints,
                macroLabel,
                bucket,
                humidLabel,
                ctx.x(), ctx.z(),
                cell.cellX,
                cell.cellZ,
                localSize,
                req.count,
                hash,
                idx
            );
        }

        static final class Request {
            final int xOffset;
            final int zOffset;
            final int count;
            final MacroBiome macro;
            final String bucketLabel;

            Request(TalosBiomeContext ctx, int xOffset, int zOffset, int count,
                    MacroBiome macro, String bucketLabel) {
                this.xOffset = xOffset;
                this.zOffset = zOffset;
                this.count = count;
                this.macro = macro;
                this.bucketLabel = bucketLabel;
            }

            static Request dry(TalosBiomeContext ctx, int offset, int count) {
                return new Request(ctx, offset, offset, count, MacroBiome.PLAINS_TEMPERATE, "temperate-dry");
            }

            static Request normal(TalosBiomeContext ctx, int offset, int count) {
                return new Request(ctx, offset, offset, count, MacroBiome.PLAINS_TEMPERATE, "temperate-normal");
            }

            static Request humid(TalosBiomeContext ctx, int offset, int count) {
                return new Request(ctx, offset, offset, count, MacroBiome.PLAINS_TEMPERATE, "temperate-humid");
            }

            static Request variant(TalosBiomeContext ctx, int count) {
                return new Request(ctx, 0, 0, count, ctx.macro(), "macro-variant");
            }
        }

        private static final class CellSelection {
            final int cellX;
            final int cellZ;
            CellSelection(int cellX, int cellZ) {
                this.cellX = cellX;
                this.cellZ = cellZ;
            }
        }

        private int pickMicroPlate(int x, int z, int count) {
            if (count <= 1) return 0;

            double n = MICRO_PLATE_NOISE.noise(
                x * MICRO_PLATE_SCALE,
                z * MICRO_PLATE_SCALE
            );
            double selector = MathHelper.clamp_double(0.5D + n * 0.5D, 0.0D, 1.0D);

            int idx = (int) Math.floor(selector * count);
            return MathHelper.clamp_int(idx, 0, count - 1);
        }
    }

    private static final class TalosDebugLogger {

        static void logFinal(TalosBiomeContext ctx, String pickerName, BiomeGenBase biome) {
            if (!DEBUG_LOG_FINAL) return;

            int chunkX = ctx.x() >> 4;
            int chunkZ = ctx.z() >> 4;
            if (Math.floorMod(chunkX, DEBUG_LOG_FINAL_INTERVAL) != 0
                || Math.floorMod(chunkZ, DEBUG_LOG_FINAL_INTERVAL) != 0) {
                return;
            }

            System.out.printf(
                Locale.ROOT,
                "[FINAL] x=%d z=%d macro=%s picker=%s biome=%s temp=%.3f humid=%.3f rough=%.3f%n",
                ctx.x(), ctx.z(),
                ctx.macro(),
                pickerName,
                biome.biomeName,
                ctx.temperature(),
                ctx.humidity(),
                ctx.roughness()
            );
        }

        static void logTiming(
            TalosBiomeContext ctx,
            long total,
            long tClimate,
            long tHumid,
            long tPicker,
            long tFinal
        ) {
            if (!DEBUG_TIMING) return;
            System.out.printf(
                Locale.ROOT,
                "[BIOME-TIME] x=%d z=%d total=%dµs climate=%d humid=%d picker=%d final=%d%n",
                ctx.x(), ctx.z(),
                total, tClimate, tHumid, tPicker, tFinal
            );
        }
    }

    static final class MacroBlend {
        final EnumMap<MacroBiome, Double> weights = new EnumMap<>(MacroBiome.class);

        void set(MacroBiome biome, double weight) {
            weights.put(biome, weight);
        }

        double weightOf(MacroBiome biome) {
            return weights.getOrDefault(biome, 0.0D);
        }

        MacroBiome pickDominant() {
            return weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(MacroBiome.PLAINS_TEMPERATE);
        }

        void normalize() {
            double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (sum < 1e-6D) {
                double fallback = 1.0D / Math.max(weights.size(), 1);
                weights.replaceAll((k, v) -> fallback);
                return;
            }
            weights.replaceAll((k, v) -> v / sum);
        }
    }

    private static int hashCoordsWithLocalSize(int x, int z, int salt, int localSize) {
        long h = VARIANT_SALT;
        h ^= (long) x * 0x632BE59BD9B4E019L;
        h ^= (long) z * 0x9E3779B185EBCA87L;
        h ^= (long) salt * 0xBF58476D1CE4E5B9L;
        h ^= (long) localSize * 0xDEADBEEFCAFEBABEL;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return (int) h;
    }

    private static void logMacroSample(
        int x,
        int z,
        MacroBiomeField.SampleDual macroSample,
        Talos2ClimateSampler.ClimateSample climate
    ) {
        System.out.printf(
            Locale.ROOT,
            "[MACRO] x=%d z=%d primary=%s secondary=%s weight=%.2f temp=%.3f humid=%.3f rough=%.3f%n",
            x, z,
            macroSample.primary,
            macroSample.secondary,
            macroSample.primaryWeight,
            climate.temperature,
            climate.humidity,
            climate.roughness
        );
    }

    private static long now() {
        return System.nanoTime() / 1_000L;
    }
}
