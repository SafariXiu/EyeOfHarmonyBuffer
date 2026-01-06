package com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager;

import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.BiomeDecisionStrategy;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.FullStrategy;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.SimplifiedStrategy;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.NoiseCoastlineProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.ClimateProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.HydroProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.TerrainProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise.NoiseClimateProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise.NoiseHydroProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise.NoiseMacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.CachingMacroCellBuilder;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise.NoiseTerrainProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.TalosMacroCellBuilder;
import net.minecraft.world.World;

import java.util.Objects;

public final class FieldManagerFactory {

    private FieldManagerFactory() {}

    public static FieldContext create(World world) {
        return create(world, FieldManagerConfig.defaults());
    }

    public static FieldContext create(World world, FieldManagerConfig config) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(config, "config");

        long seed = world.getSeed();

        MacroFieldProvider macroProvider =
            new NoiseMacroFieldProvider(seed, config.getMacroSettings());
        CoastlineProvider coastlineProvider =
            new NoiseCoastlineProvider(seed, config.getCoastlineSettings());

        TalosMacroCellBuilder rawBuilder = new TalosMacroCellBuilder(macroProvider, coastlineProvider);
        CachingMacroCellBuilder cachedBuilder = new CachingMacroCellBuilder(rawBuilder, config.getMacroCacheSize());

        FieldDiagnostics diagnostics = new FieldDiagnostics();

        TerrainProvider terrainProvider = new NoiseTerrainProvider(
            seed,
            config.getTerrainSettings()
        );
        ClimateProvider climateProvider = new NoiseClimateProvider(
            seed,
            config.getClimateSettings()
        );
        HydroProvider hydroProvider = new NoiseHydroProvider(
            seed,
            config.getHydroSettings()
        );

        FieldManager fieldManager = new DefaultFieldManager(
            cachedBuilder,
            cachedBuilder,
            terrainProvider,
            climateProvider,
            hydroProvider,
            diagnostics
        );

        BiomeDecisionStrategy strategy = createStrategy(config, fieldManager, world);

        return new FieldContext(
            world,
            seed,
            macroProvider,
            coastlineProvider,
            rawBuilder,
            cachedBuilder,
            fieldManager,
            strategy,
            diagnostics
        );
    }

    private static BiomeDecisionStrategy createStrategy(FieldManagerConfig config,
                                                        FieldManager fieldManager,
                                                        World world) {

        return switch (config.getStrategyMode()) {
            case FULL -> new FullStrategy(config.getStrategyVersion(), fieldManager, world);
            case SIMPLIFIED -> new SimplifiedStrategy(config.getStrategyVersion(), fieldManager, world);
        };
    }
}
