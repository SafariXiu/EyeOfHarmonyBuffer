package com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager;

import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.config.MacroCacheConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.ClimateProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.HydroProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.MacroFieldSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.TerrainProviderSettings;

import java.util.Objects;

public final class FieldManagerConfig {

    public enum StrategyMode {
        FULL,
        SIMPLIFIED
    }

    private final MacroFieldSettings macroSettings;
    private final CoastlineSettings coastlineSettings;
    private final int macroCacheSize;
    private final boolean macroCacheEnabled;
    private final StrategyMode strategyMode;
    private final String strategyVersion;
    private final TerrainProviderSettings terrainSettings;
    private final ClimateProviderSettings climateSettings;
    private final HydroProviderSettings hydroSettings;
    private final MacroCacheConfig macroCache;

    private FieldManagerConfig(Builder builder) {
        this.macroSettings = builder.macroSettings;
        this.coastlineSettings = builder.coastlineSettings;
        this.macroCacheSize = builder.macroCacheSize;
        this.macroCacheEnabled = builder.macroCacheEnabled;
        this.strategyMode = builder.strategyMode;
        this.strategyVersion = builder.strategyVersion;
        this.terrainSettings = builder.terrainSettings;
        this.climateSettings = builder.climateSettings;
        this.hydroSettings = builder.hydroSettings;
        this.macroCache = Objects.requireNonNull(builder.macroCache, "macroCache");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FieldManagerConfig defaults() {
        return builder().build();
    }

    public MacroFieldSettings getMacroSettings() {
        return macroSettings;
    }

    public CoastlineSettings getCoastlineSettings() {
        return coastlineSettings;
    }

    public int getMacroCacheSize() {
        return macroCacheSize;
    }

    public StrategyMode getStrategyMode() {
        return strategyMode;
    }

    public String getStrategyVersion() {
        return strategyVersion;
    }

    public TerrainProviderSettings getTerrainSettings () {
        return terrainSettings;
    }

    public ClimateProviderSettings getClimateSettings () {
        return climateSettings;
    }

    public HydroProviderSettings getHydroSettings () {
        return hydroSettings;
    }

    public boolean isMacroCacheEnabled() {
        return macroCacheEnabled;
    }

    public MacroCacheConfig getMacroCache() {
        return macroCache;
    }

    public static final class Builder {

        private MacroFieldSettings macroSettings = MacroFieldSettings.defaults();
        private CoastlineSettings coastlineSettings = CoastlineSettings.defaults();
        private int macroCacheSize = 1024;
        private MacroCacheConfig macroCache = MacroCacheConfig.defaults();
        private boolean macroCacheEnabled = true;
        private StrategyMode strategyMode = StrategyMode.SIMPLIFIED;
        private String strategyVersion = "1.0.0";
        private TerrainProviderSettings terrainSettings = TerrainProviderSettings.defaults();
        private ClimateProviderSettings climateSettings = ClimateProviderSettings.defaults();
        private HydroProviderSettings hydroSettings = HydroProviderSettings.defaults();

        private Builder() {}

        public Builder macroSettings(MacroFieldSettings macroSettings) {
            this.macroSettings = Objects.requireNonNull(macroSettings, "macroSettings");
            return this;
        }

        public Builder coastlineSettings(CoastlineSettings coastlineSettings) {
            this.coastlineSettings = Objects.requireNonNull(coastlineSettings, "coastlineSettings");
            return this;
        }

        public Builder macroCacheSize(int macroCacheSize) {
            this.macroCacheSize = macroCacheSize;
            return this;
        }

        public Builder macroCache(MacroCacheConfig macroCache) {
            this.macroCache = Objects.requireNonNull(macroCache, "macroCache");
            return this;
        }

        public Builder macroCacheEnabled(boolean macroCacheEnabled) {
            this.macroCacheEnabled = macroCacheEnabled;
            return this;
        }

        public Builder strategyMode(StrategyMode strategyMode) {
            this.strategyMode = Objects.requireNonNull(strategyMode, "strategyMode");
            return this;
        }

        public Builder strategyVersion(String strategyVersion) {
            this.strategyVersion = Objects.requireNonNull(strategyVersion, "strategyVersion");
            return this;
        }

        public Builder terrainSettings(TerrainProviderSettings terrainSettings) {
            this.terrainSettings = Objects.requireNonNull(terrainSettings, "terrainSettings");
            return this;
        }

        public Builder climateSettings(ClimateProviderSettings climateSettings) {
            this.climateSettings = Objects.requireNonNull(climateSettings, "climateSettings");
            return this;
        }

        public Builder hydroSettings(HydroProviderSettings hydroSettings) {
            this.hydroSettings = Objects.requireNonNull(hydroSettings, "hydroSettings");
            return this;
        }

        public FieldManagerConfig build() {
            return new FieldManagerConfig(this);
        }
    }
}
