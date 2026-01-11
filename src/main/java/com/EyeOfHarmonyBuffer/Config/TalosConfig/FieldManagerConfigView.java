package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FieldManagerConfigView {

    private static final MacroCacheView MACRO_CACHE = new MacroCacheView();
    private static final GenericSectionView MACRO_SELECTOR =
        new GenericSectionView(MacroSelectorConfigSection.class);
    private static final GenericSectionView MACRO_SELECTOR_HEIGHT =
        new GenericSectionView(MacroSelectorHeightConfigSection.class);
    private static final GenericSectionView MACRO_SELECTOR_TRANSITION =
        new GenericSectionView(MacroSelectorTransitionConfigSection.class);
    private static final GenericSectionView DIAGNOSTICS =
        new GenericSectionView(DiagnosticsConfigSection.class);
    private static final GenericSectionView TERRAIN =
        new GenericSectionView(TerrainConfigSection.class);
    private static final ClimateView CLIMATE = new ClimateView();
    private static final HydroView HYDRO = new HydroView();

    public FieldManagerConfigView() {}

    public MacroCacheView macroCache() {
        return MACRO_CACHE;
    }

    public GenericSectionView macroSelector() {
        return MACRO_SELECTOR;
    }

    public GenericSectionView macroSelectorHeight() {
        return MACRO_SELECTOR_HEIGHT;
    }

    public GenericSectionView macroSelectorTransition() {
        return MACRO_SELECTOR_TRANSITION;
    }

    public GenericSectionView diagnostics() {
        return DIAGNOSTICS;
    }

    public GenericSectionView terrain() {
        return TERRAIN;
    }

    public ClimateView climate() {
        return CLIMATE;
    }

    public HydroView hydro() {
        return HYDRO;
    }


    public static final class MacroCacheView extends AbstractSectionView {

        private MacroCacheView() {
            super(MacroCacheConfigSection.class);
        }

        public boolean enabled() {
            return bool("macroCacheEnabled");
        }

        public int maxEntries() {
            return integer("macroCacheMaxEntries");
        }

        public boolean diagnosticsEnabled() {
            return bool("macroCacheDiagnostics");
        }
    }

    public static final class ClimateView extends AbstractSectionView {

        private ClimateView() {
            super(ClimateConfigSection.class);
        }

        // Temperature
        public double temperatureBase() {
            return dbl("climateTempBase");
        }

        public double temperatureVariance() {
            return dbl("climateTempVariance");
        }

        public double temperatureFrequency() {
            return dbl("climateTempFrequency");
        }

        public int temperatureOctaves() {
            return integer("climateTempOctaves");
        }

        public int temperatureSeedOffset() {
            return integer("climateTempSeedOffset");
        }

        // Humidity
        public double humidityBase() {
            return dbl("climateHumidityBase");
        }

        public double humidityVariance() {
            return dbl("climateHumidityVariance");
        }

        public double humidityFrequency() {
            return dbl("climateHumidityFrequency");
        }

        public int humidityOctaves() {
            return integer("climateHumidityOctaves");
        }

        public int humiditySeedOffset() {
            return integer("climateHumiditySeedOffset");
        }

        // Rainfall
        public double rainfallBase() {
            return dbl("climateRainfallBase");
        }

        public double rainfallVariance() {
            return dbl("climateRainfallVariance");
        }

        public double rainfallFrequency() {
            return dbl("climateRainfallFrequency");
        }

        public int rainfallOctaves() {
            return integer("climateRainfallOctaves");
        }

        public int rainfallSeedOffset() {
            return integer("climateRainfallSeedOffset");
        }

        // Wind
        public boolean windEnabled() {
            return bool("climateWindEnabled");
        }

        public double windDirectionVariance() {
            return dbl("climateWindDirectionVariance");
        }

        public double windSpeedBase() {
            return dbl("climateWindSpeedBase");
        }

        public double windSpeedVariance() {
            return dbl("climateWindSpeedVariance");
        }

        // Season
        public boolean seasonEnabled() {
            return bool("climateSeasonEnabled");
        }

        public double seasonLengthDays() {
            return dbl("climateSeasonLengthDays");
        }

        public double seasonPhaseOffset() {
            return dbl("climateSeasonPhaseOffset");
        }

        public double seasonTemperatureAmplitude() {
            return dbl("climateSeasonTemperatureAmplitude");
        }

        public double seasonHumidityAmplitude() {
            return dbl("climateSeasonHumidityAmplitude");
        }

        public double seasonRainfallAmplitude() {
            return dbl("climateSeasonRainfallAmplitude");
        }
    }

    public static final class HydroView extends AbstractSectionView {

        private HydroView() {
            super(HydroConfigSection.class);
        }

        // Core / groundwater
        public double seaLevel() {
            return dbl("hydroSeaLevel");
        }

        public double baseSaturation() {
            return dbl("hydroBaseSaturation");
        }

        public double saturationVariance() {
            return dbl("hydroSaturationVariance");
        }

        public double baseAquiferNormalized() {
            return dbl("hydroBaseAquiferNormalized");
        }

        public double aquiferVariance() {
            return dbl("hydroAquiferVariance");
        }

        public double maxFlowRate() {
            return dbl("hydroMaxFlowRate");
        }

        public double heightFalloffBlocks() {
            return dbl("hydroHeightFalloffBlocks");
        }

        public double heightWeight() {
            return dbl("hydroHeightWeight");
        }

        public double saturationNoiseFrequency() {
            return dbl("hydroSaturationNoiseFrequency");
        }

        public double saturationNoiseLacunarity() {
            return dbl("hydroSaturationNoiseLacunarity");
        }

        public double saturationNoisePersistence() {
            return dbl("hydroSaturationNoisePersistence");
        }

        public int saturationNoiseOctaves() {
            return integer("hydroSaturationNoiseOctaves");
        }

        public double flowNoiseFrequency() {
            return dbl("hydroFlowNoiseFrequency");
        }

        public double flowNoiseLacunarity() {
            return dbl("hydroFlowNoiseLacunarity");
        }

        public double flowNoisePersistence() {
            return dbl("hydroFlowNoisePersistence");
        }

        public int flowNoiseOctaves() {
            return integer("hydroFlowNoiseOctaves");
        }

        public double waterTableBufferBlocks() {
            return dbl("hydroWaterTableBufferBlocks");
        }

        // River
        public double riverFrequency() {
            return dbl("hydroRiverFrequency");
        }

        public double riverDetailFrequency() {
            return dbl("hydroRiverDetailFrequency");
        }

        public double riverStrength() {
            return dbl("hydroRiverStrength");
        }

        public double riverThreshold() {
            return dbl("hydroRiverThreshold");
        }

        public int riverSeedOffset() {
            return integer("hydroRiverSeedOffset");
        }

        public int riverSmoothRadius() {
            return integer("hydroRiverSmoothRadius");
        }

        // Lake
        public boolean lakeEnabled() {
            return bool("hydroLakeEnabled");
        }

        public double lakeThreshold() {
            return dbl("hydroLakeThreshold");
        }

        public int lakeSeedOffset() {
            return integer("hydroLakeSeedOffset");
        }

        // Coast
        public boolean coastSyncWithMacro() {
            return bool("hydroCoastSyncWithMacro");
        }

        public double coastFalloff() {
            return dbl("hydroCoastFalloff");
        }

        // Diagnostics
        public boolean diagnosticsLogSamples() {
            return bool("hydroDiagLogSamples");
        }

        public int diagnosticsProbeInterval() {
            return integer("hydroDiagProbeInterval");
        }
    }

    public static class GenericSectionView extends AbstractSectionView {

        private GenericSectionView(Class<?> sectionClass) {
            super(sectionClass);
        }

        public Object get(String fieldName) {
            return raw(fieldName);
        }
    }

    public abstract static class AbstractSectionView {

        private final Class<?> sectionClass;

        protected AbstractSectionView(Class<?> sectionClass) {
            this.sectionClass = sectionClass;
        }

        protected boolean bool(String field) {
            return Boolean.TRUE.equals(raw(field));
        }

        protected int integer(String field) {
            Object value = raw(field);
            if (value instanceof Number number) {
                return number.intValue();
            }
            throw wrongType(field, "int", value);
        }

        protected double dbl(String field) {
            Object value = raw(field);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            throw wrongType(field, "double", value);
        }

        protected Object raw(String field) {
            try {
                Field f = sectionClass.getField(field);
                if (!Modifier.isStatic(f.getModifiers())) {
                    throw new IllegalStateException(sectionClass.getSimpleName() +
                        "." + field + " is not static.");
                }
                return f.get(null);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Unable to read field " +
                    sectionClass.getSimpleName() + "." + field, ex);
            }
        }

        public Map<String, Object> asMap() {
            try {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                for (Field field : sectionClass.getFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    snapshot.put(field.getName(), field.get(null));
                }
                return Collections.unmodifiableMap(snapshot);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Unable to snapshot section " +
                    sectionClass.getSimpleName(), ex);
            }
        }

        private static IllegalStateException wrongType(String field,
                                                       String expected,
                                                       Object actual) {
            return new IllegalStateException(
                "Expected " + expected + " for field '" + field +
                    "', but got " + (actual == null ? "null" :
                    actual.getClass().getSimpleName())
            );
        }
    }
}
