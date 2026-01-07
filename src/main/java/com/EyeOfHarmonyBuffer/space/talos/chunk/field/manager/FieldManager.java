package com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.ClimateProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.HydroProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.TerrainProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldDomain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldSampleRequest;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldSnapshot;

import java.util.Objects;

public interface FieldManager extends AutoCloseable {

    IMacroCellProvider getMacroProvider();

    FieldDiagnostics getDiagnostics();

    void invalidateMacroCache();

    TerrainProvider getTerrainProvider();

    ClimateProvider getClimateProvider();

    HydroProvider getHydroProvider();

    FieldSnapshot sample(FieldSampleRequest request);

    void invalidateCaches(FieldDomain... domains);

    TerrainSample sampleTerrain(int blockX, int blockZ);

    ClimateSample sampleClimate(int blockX, int blockZ);

    HydroSample sampleHydro(int blockX, int blockZ);

    FieldSnapshot sampleAll(int blockX, int blockZ);

    void dispose();

    @Override
    default void close() {
        dispose();
    }

    default <T> T requireProvider(T provider, String name) {
        return Objects.requireNonNull(provider, name);
    }
}
