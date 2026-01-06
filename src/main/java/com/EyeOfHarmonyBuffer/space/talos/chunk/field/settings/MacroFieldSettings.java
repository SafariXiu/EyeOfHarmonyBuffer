package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

public final class MacroFieldSettings {

    private final double continentalFrequency;
    private final double humidityFrequency;
    private final double temperatureFrequency;
    private final double ridgeFrequency;
    private final int macroBaseHeightMin;
    private final int macroBaseHeightMax;
    private final int plateauBaseHeight;
    private final int plateauVariance;

    private MacroFieldSettings(double continentalFrequency,
                               double humidityFrequency,
                               double temperatureFrequency,
                               double ridgeFrequency,
                               int macroBaseHeightMin,
                               int macroBaseHeightMax,
                               int plateauBaseHeight,
                               int plateauVariance) {

        this.continentalFrequency = continentalFrequency;
        this.humidityFrequency = humidityFrequency;
        this.temperatureFrequency = temperatureFrequency;
        this.ridgeFrequency = ridgeFrequency;
        this.macroBaseHeightMin = macroBaseHeightMin;
        this.macroBaseHeightMax = macroBaseHeightMax;
        this.plateauBaseHeight = plateauBaseHeight;
        this.plateauVariance = plateauVariance;
    }

    public static MacroFieldSettings defaults() {
        return new MacroFieldSettings(
            1.0 / 1536.0,
            1.0 / 1024.0,
            1.0 / 2048.0,
            1.0 / 384.0,
            48,
            120,
            64,
            48
        );
    }

    public double continentalFrequency() { return continentalFrequency; }
    public double humidityFrequency() { return humidityFrequency; }
    public double temperatureFrequency() { return temperatureFrequency; }
    public double ridgeFrequency() { return ridgeFrequency; }
    public int macroBaseHeightMin() { return macroBaseHeightMin; }
    public int macroBaseHeightMax() { return macroBaseHeightMax; }
    public int plateauBaseHeight() { return plateauBaseHeight; }
    public int plateauVariance() { return plateauVariance; }

    public MacroFieldSettings withContinentalFrequency(double value) {
        return new MacroFieldSettings(value, humidityFrequency, temperatureFrequency, ridgeFrequency,
            macroBaseHeightMin, macroBaseHeightMax, plateauBaseHeight, plateauVariance);
    }

    public MacroFieldSettings withHumidityFrequency(double value) {
        return new MacroFieldSettings(continentalFrequency, value, temperatureFrequency, ridgeFrequency,
            macroBaseHeightMin, macroBaseHeightMax, plateauBaseHeight, plateauVariance);
    }

    public MacroFieldSettings withTemperatureFrequency(double value) {
        return new MacroFieldSettings(continentalFrequency, humidityFrequency, value, ridgeFrequency,
            macroBaseHeightMin, macroBaseHeightMax, plateauBaseHeight, plateauVariance);
    }

    public MacroFieldSettings withMacroBaseRange(int min, int max) {
        return new MacroFieldSettings(continentalFrequency, humidityFrequency, temperatureFrequency, ridgeFrequency,
            min, Math.max(min + 8, max), plateauBaseHeight, plateauVariance);
    }

    public MacroFieldSettings withPlateau(int base, int variance) {
        return new MacroFieldSettings(continentalFrequency, humidityFrequency, temperatureFrequency, ridgeFrequency,
            macroBaseHeightMin, macroBaseHeightMax, base, Math.max(1, variance));
    }
}
