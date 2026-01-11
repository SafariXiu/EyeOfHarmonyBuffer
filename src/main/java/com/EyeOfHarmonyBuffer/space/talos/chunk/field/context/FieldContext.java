package com.EyeOfHarmonyBuffer.space.talos.chunk.field.context;

import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.BiomeDecisionStrategy;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroBiomeSelector;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectorConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import net.minecraft.world.World;

import java.util.Objects;

public final class FieldContext {

    private final int dimensionId;
    private final World world;
    private final long seed;

    private final MacroFieldProvider macroFieldProvider;
    private final CoastlineProvider coastlineProvider;

    private final FieldManager fieldManager;
    private final BiomeDecisionStrategy strategy;
    private final FieldDiagnostics diagnostics;

    private final MacroBiomeSelector macroSelector;

    public FieldContext(World world,
                        long seed,
                        MacroFieldProvider macroFieldProvider,
                        CoastlineProvider coastlineProvider,
                        FieldManager fieldManager,
                        BiomeDecisionStrategy strategy,
                        FieldDiagnostics diagnostics) {

        this.world = Objects.requireNonNull(world, "world");
        this.dimensionId = world.provider.dimensionId;
        this.seed = seed;

        this.macroFieldProvider = Objects.requireNonNull(macroFieldProvider, "macroFieldProvider");
        this.coastlineProvider = Objects.requireNonNull(coastlineProvider, "coastlineProvider");
        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");

        this.macroSelector = new MacroBiomeSelector(
            this.fieldManager,
            this.seed,
            this.macroFieldProvider,
            MacroSelectorConfig.fromSpec()
        );
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

    public FieldManager getFieldManager() {
        return fieldManager;
    }

    public BiomeDecisionStrategy getStrategy() {
        return strategy;
    }

    public FieldDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public MacroBiomeSelector getMacroSelector() {
        return macroSelector;
    }

    public MacroSelectorConfig.HeightProfile heightProfile() {
        return macroSelector.heightProfile();
    }

    public void dispose() {
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
