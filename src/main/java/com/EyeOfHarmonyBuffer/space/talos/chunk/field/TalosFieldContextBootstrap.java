package com.EyeOfHarmonyBuffer.space.talos.chunk.field;

import com.EyeOfHarmonyBuffer.Config.FieldManagerConfigSpec;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.config.MacroCacheConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManagerConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManagerFactory;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.ClimateProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.TerrainProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.ClimateProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.HydroProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.MacroFieldSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.TerrainProviderSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TalosFieldContextBootstrap {

    private static final Logger LOGGER = LogManager.getLogger(TalosFieldContextBootstrap.class);

    private TalosFieldContextBootstrap() {}

    public static FieldContext create(World world) {
        WorldProvider provider = world.provider;
        int dimensionId = provider != null ? provider.dimensionId : -999;
        String dimensionName = provider != null ? provider.getDimensionName() : "unknown";

        LOGGER.info(
            "[TalosFieldContext][Bootstrap] Building FieldContext for dim={} ({}) macroCacheEnabled={}, macroCacheSize={}, terrainFreq={}, climateTempBase={}, hydroSeaLevel={}",
            dimensionId,
            dimensionName,
            FieldManagerConfigSpec.macroCacheEnabled,
            FieldManagerConfigSpec.macroCacheMaxEntries,
            FieldManagerConfigSpec.terrainFrequency,
            FieldManagerConfigSpec.climateTempBase,
            FieldManagerConfigSpec.hydroSeaLevel
        );

        MacroCacheConfig macroCache = MacroCacheConfig.builder()
            .enabled(FieldManagerConfigSpec.macroCacheEnabled)
            .maxEntries(FieldManagerConfigSpec.macroCacheMaxEntries)
            .diagnosticsEnabled(FieldManagerConfigSpec.macroCacheDiagnostics)
            .build();

        FieldManagerConfig config = FieldManagerConfig.builder()
            .macroSettings(MacroFieldSettings.defaults())
            .coastlineSettings(CoastlineSettings.defaults())
            .terrainSettings(TerrainProviderSettings.fromConfig())
            .climateSettings(ClimateProviderSettings.fromConfig())
            .hydroSettings(HydroProviderSettings.fromConfig())
            .macroCache(macroCache)
            .macroCacheEnabled(FieldManagerConfigSpec.macroCacheEnabled)
            .macroCacheSize(FieldManagerConfigSpec.macroCacheMaxEntries)
            .build();

        FieldContext context = FieldManagerFactory.create(world, config);

        if (FieldManagerConfigSpec.macroCacheDiagnostics) {
            int sampleX;
            int sampleZ;

            if (FieldManagerConfigSpec.diagnosticsSampleUsePlayer) {
                EntityPlayer player = world.playerEntities.isEmpty()
                    ? null
                    : (EntityPlayer) world.playerEntities.get(0);

                if (player != null) {
                    sampleX = MathHelper.floor_double(player.posX);
                    sampleZ = MathHelper.floor_double(player.posZ);
                } else {
                    LOGGER.warn("[TalosFieldContext][Diagnostics] No players online; falling back to spawn/static coordinates.");
                    sampleX = resolveFallbackX(world);
                    sampleZ = resolveFallbackZ(world);
                }
            } else {
                sampleX = resolveFallbackX(world);
                sampleZ = resolveFallbackZ(world);
            }

            TerrainProvider terrainProvider = context.getFieldManager().getTerrainProvider();
            TerrainSample terrain = terrainProvider.sample(sampleX, sampleZ);
            LOGGER.info(
                "[TalosFieldContext][Diagnostics/Terrain] sample x={} z={} elevation={} slope={} roughness={}",
                sampleX,
                sampleZ,
                terrain.elevation(),
                terrain.slope(),
                terrain.roughness()
            );

            ClimateProvider climateProvider = context.getFieldManager().getClimateProvider();
            ClimateSample climate = climateProvider.sample(sampleX, sampleZ);
            LOGGER.info(
                "[TalosFieldContext][Diagnostics/Climate] sample x={} z={} temp={} humidity={} rainfall={} windDirDeg={} windSpeed={} seasonPhase={}",
                sampleX,
                sampleZ,
                climate.temperature(),
                climate.humidity(),
                climate.rainfall(),
                Math.toDegrees(climate.windDirection()),
                climate.windSpeed(),
                climate.seasonPhase()
            );

            HydroSample hydro = context.getFieldManager().getHydroProvider().sample(sampleX, sampleZ);
            LOGGER.info(
                "[TalosFieldContext][Diagnostics/Hydro] sample x={} z={} saturation={} flowRate={} aquiferLevel={} riverStrength={} lakeFactor={} waterLevel={} coastDist={} riverDist={} inlandSea={}",
                sampleX,
                sampleZ,
                hydro.saturation(),
                hydro.flowRate(),
                hydro.aquiferLevel(),
                hydro.riverStrength(),
                hydro.lakeFactor(),
                hydro.waterLevel(),
                hydro.coastDistance(),
                hydro.riverDistance(),
                hydro.inlandSea()
            );
        }

        LOGGER.info(
            "[TalosFieldContext][Ready] FieldContext ready for dim={} ({}) macroCacheDiagnostics={}, terrainOctaves={}, climateHumidityFrequency={}, hydroRiverThreshold={}",
            dimensionId,
            dimensionName,
            FieldManagerConfigSpec.macroCacheDiagnostics,
            FieldManagerConfigSpec.terrainOctaves,
            FieldManagerConfigSpec.climateHumidityFrequency,
            FieldManagerConfigSpec.hydroRiverThreshold
        );

        return context;
    }

    private static int resolveFallbackX(World world) {
        if (FieldManagerConfigSpec.diagnosticsSampleUseSpawn) {
            ChunkCoordinates spawn = world.getSpawnPoint();
            return spawn != null ? spawn.posX : 0;
        }
        return FieldManagerConfigSpec.diagnosticsSampleX;
    }

    private static int resolveFallbackZ(World world) {
        if (FieldManagerConfigSpec.diagnosticsSampleUseSpawn) {
            ChunkCoordinates spawn = world.getSpawnPoint();
            return spawn != null ? spawn.posZ : 0;
        }
        return FieldManagerConfigSpec.diagnosticsSampleZ;
    }
}
