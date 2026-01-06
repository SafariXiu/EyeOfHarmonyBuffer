package com.EyeOfHarmonyBuffer.space.talos.chunk.field;

import com.EyeOfHarmonyBuffer.Config.FieldManagerCacheConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.config.MacroCacheConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManagerConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManagerFactory;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.ClimateProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.HydroProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.MacroFieldSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.TerrainProviderSettings;
import net.minecraft.world.World;

public final class TalosFieldContextBootstrap {

    private TalosFieldContextBootstrap() {

    }

    public static FieldContext create(World world) {

        MacroCacheConfig macroCache = MacroCacheConfig.builder()
            .enabled(FieldManagerCacheConfig.enabled)
            .maxEntries(FieldManagerCacheConfig.maxEntries)
            .diagnosticsEnabled(FieldManagerCacheConfig.diagnosticsEnabled)
            .build();

        FieldManagerConfig config = FieldManagerConfig.builder()
            .macroSettings(MacroFieldSettings.defaults())
            .coastlineSettings(CoastlineSettings.defaults())
            .terrainSettings(TerrainProviderSettings.defaults())
            .climateSettings(ClimateProviderSettings.defaults())
            .hydroSettings(HydroProviderSettings.defaults())
            .macroCache(macroCache)
            .macroCacheEnabled(FieldManagerCacheConfig.enabled)
            .macroCacheSize(FieldManagerCacheConfig.maxEntries)
            .build();

        return FieldManagerFactory.create(world, config);
    }
}
