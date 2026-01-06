package com.EyeOfHarmonyBuffer.space.talos.chunk.field.context;

import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.BiomeDecisionStrategy;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.CachingMacroCellBuilder;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.TalosMacroCellBuilder;
import net.minecraft.world.World;

import java.util.Objects;

public final class FieldContext {

    private final int dimensionId;
    private final World world;
    private final long seed;

    private final MacroFieldProvider macroFieldProvider;
    private final CoastlineProvider coastlineProvider;
    private final TalosMacroCellBuilder macroCellBuilder;
    private final CachingMacroCellBuilder cachedMacroCellBuilder;

    private final FieldManager fieldManager;
    private final BiomeDecisionStrategy strategy;
    private final FieldDiagnostics diagnostics;

    public FieldContext(World world,
                        long seed,
                        MacroFieldProvider macroFieldProvider,
                        CoastlineProvider coastlineProvider,
                        TalosMacroCellBuilder macroCellBuilder,
                        CachingMacroCellBuilder cachedMacroCellBuilder,
                        FieldManager fieldManager,
                        BiomeDecisionStrategy strategy,
                        FieldDiagnostics diagnostics) {

        this.world = Objects.requireNonNull(world, "world");
        this.dimensionId = world.provider.dimensionId;
        this.seed = seed;

        this.macroFieldProvider = Objects.requireNonNull(macroFieldProvider, "macroFieldProvider");
        this.coastlineProvider = Objects.requireNonNull(coastlineProvider, "coastlineProvider");
        this.macroCellBuilder = Objects.requireNonNull(macroCellBuilder, "macroCellBuilder");
        this.cachedMacroCellBuilder = Objects.requireNonNull(cachedMacroCellBuilder, "cachedMacroCellBuilder");
        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public World getWorld() {
        return world;
    }

    public long getSeed() {
        return seed;
    }

    public MacroFieldProvider getMacroFieldProvider() {
        return macroFieldProvider;
    }

    public CoastlineProvider getCoastlineProvider() {
        return coastlineProvider;
    }

    public TalosMacroCellBuilder getMacroCellBuilder() {
        return macroCellBuilder;
    }

    public CachingMacroCellBuilder getCachedMacroCellBuilder() {
        return cachedMacroCellBuilder;
    }

    public FieldManager getFieldManager() {
        return fieldManager;
    }

    public BiomeDecisionStrategy getStrategy() {
        return strategy;
    }

    public FieldDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public void dispose() {
        try {
            cachedMacroCellBuilder.invalidateAll();
        } catch (Exception ignored) {
        }

        try {
            fieldManager.dispose();
        } catch (Exception ignored) {
        }

        try {
            strategy.dispose();
        } catch (Exception ignored) {
        }
    }
}
