package com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldDomain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldSampleRequest;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldSnapshot;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.ClimateProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.FieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.HydroProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.TerrainProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.MacroCacheInvalidator;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.cleanroommc.modularui.ModularUI.LOGGER;

public final class DefaultFieldManager implements FieldManager {

    private final IMacroCellProvider macroProvider;
    private final MacroCacheInvalidator macroCache;
    private final TerrainProvider terrainProvider;
    private final ClimateProvider climateProvider;
    private final HydroProvider hydroProvider;
    private final FieldDiagnostics diagnostics;

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public DefaultFieldManager(IMacroCellProvider macroProvider,
                               @Nullable MacroCacheInvalidator macroCache,
                               TerrainProvider terrainProvider,
                               ClimateProvider climateProvider,
                               HydroProvider hydroProvider,
                               FieldDiagnostics diagnostics) {

        this.macroProvider = Objects.requireNonNull(macroProvider, "macroProvider");
        this.macroCache = macroCache;
        this.terrainProvider = terrainProvider;
        this.climateProvider = climateProvider;
        this.hydroProvider = hydroProvider;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public IMacroCellProvider getMacroProvider() {
        ensureActive();
        return macroProvider;
    }

    @Override
    public FieldDiagnostics getDiagnostics() {
        ensureActive();
        return diagnostics;
    }

    @Override
    public void invalidateMacroCache() {
        ensureActive();
        if (macroCache != null) {
            macroCache.invalidateAll();
        }
    }

    @Override
    public TerrainProvider getTerrainProvider() {
        ensureActive();
        return terrainProvider;
    }

    @Override
    public ClimateProvider getClimateProvider() {
        ensureActive();
        return climateProvider;
    }

    @Override
    public HydroProvider getHydroProvider() {
        ensureActive();
        return hydroProvider;
    }

    @Override
    public FieldSnapshot sample(FieldSampleRequest request) {
        ensureActive();
        Objects.requireNonNull(request, "request");

        diagnostics.recordSample(request.getDomains());
        FieldSnapshot.Builder builder = FieldSnapshot.builder(request);

        if (request.includes(FieldDomain.MACRO)) {
            FieldDiagnostics.SampleToken token = diagnostics.begin(FieldDomain.MACRO);
            try {
                builder.macro(macroProvider.build(request.getChunkX(), request.getChunkZ()));
            } catch (RuntimeException ex) {
                diagnostics.recordError(FieldDomain.MACRO, ex);
                throw ex;
            } finally {
                diagnostics.end(token);
            }
        }

        if (request.includes(FieldDomain.TERRAIN) && terrainProvider != null) {
            FieldDiagnostics.SampleToken token = diagnostics.begin(FieldDomain.TERRAIN);
            try {
                builder.terrain(terrainProvider.sample(request.getBlockX(), request.getBlockZ()));
            } catch (RuntimeException ex) {
                diagnostics.recordError(FieldDomain.TERRAIN, ex);
                throw ex;
            } finally {
                diagnostics.end(token);
            }
        }

        if (request.includes(FieldDomain.CLIMATE) && climateProvider != null) {
            FieldDiagnostics.SampleToken token = diagnostics.begin(FieldDomain.CLIMATE);
            try {
                builder.climate(climateProvider.sample(request.getBlockX(), request.getBlockZ()));
            } catch (RuntimeException ex) {
                diagnostics.recordError(FieldDomain.CLIMATE, ex);
                throw ex;
            } finally {
                diagnostics.end(token);
            }
        }

        if (request.includes(FieldDomain.HYDRO) && hydroProvider != null) {
            FieldDiagnostics.SampleToken token = diagnostics.begin(FieldDomain.HYDRO);
            try {
                builder.hydro(hydroProvider.sample(request.getBlockX(), request.getBlockZ()));
            } catch (RuntimeException ex) {
                diagnostics.recordError(FieldDomain.HYDRO, ex);
                throw ex;
            } finally {
                diagnostics.end(token);
            }
        }

        return builder.build();
    }

    @Override
    public void invalidateCaches(FieldDomain... domains) {
        ensureActive();

        EnumSet<FieldDomain> targets = (domains == null || domains.length == 0)
            ? EnumSet.allOf(FieldDomain.class)
            : EnumSet.copyOf(Arrays.asList(domains));

        LOGGER.info("[TalosFieldManager] invalidateCaches called. targets={}", targets);

        if (targets.contains(FieldDomain.MACRO)) {
            invalidateMacroCache();
        }
        if (targets.contains(FieldDomain.TERRAIN) && terrainProvider != null) {
            terrainProvider.invalidateCaches();
        }
        if (targets.contains(FieldDomain.CLIMATE) && climateProvider != null) {
            climateProvider.invalidateCaches();
        }
        if (targets.contains(FieldDomain.HYDRO) && hydroProvider != null) {
            hydroProvider.invalidateCaches();
        }
    }

    @Override
    public TerrainSample sampleTerrain(int blockX, int blockZ) {
        FieldSampleRequest req = FieldSampleRequest.builder()
            .block(blockX, blockZ)
            .domains(FieldDomain.TERRAIN)
            .build();
        FieldSnapshot snapshot = sample(req);
        return snapshot.getTerrain();
    }

    @Override
    public ClimateSample sampleClimate(int blockX, int blockZ) {
        FieldSampleRequest req = FieldSampleRequest.builder()
            .block(blockX, blockZ)
            .domains(FieldDomain.TERRAIN)
            .build();
        FieldSnapshot snapshot = sample(req);
        return snapshot.getClimate();
    }

    @Override
    public HydroSample sampleHydro(int blockX, int blockZ) {
        FieldSampleRequest req = FieldSampleRequest.builder()
            .block(blockX, blockZ)
            .domains(FieldDomain.TERRAIN)
            .build();
        FieldSnapshot snapshot = sample(req);
        return snapshot.getHydro();
    }

    @Override
    public FieldSnapshot sampleAll(int blockX, int blockZ) {
        FieldSampleRequest req = FieldSampleRequest.builder()
            .block(blockX, blockZ)
            .domains(FieldDomain.values())
            .build();
        return sample(req);
    }

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        invalidateCaches(FieldDomain.values());
        safeDispose(terrainProvider);
        safeDispose(climateProvider);
        safeDispose(hydroProvider);
    }

    private void ensureActive() {
        if (disposed.get()) {
            throw new IllegalStateException("FieldManager already disposed");
        }
    }

    private void safeDispose(FieldProvider<?> provider) {
        if (provider == null) {
            return;
        }
        try {
            provider.dispose();
        } catch (Exception ignored) {
        }
    }
}
