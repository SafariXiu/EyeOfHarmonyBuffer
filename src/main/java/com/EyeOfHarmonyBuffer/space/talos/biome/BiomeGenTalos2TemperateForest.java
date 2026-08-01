package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2TemperateForest extends TalosBiomeBase {

    public BiomeGenTalos2TemperateForest(int id) {
        super(id);

        this.setBiomeName("Talos Temperate Forest");
        this.setColor(0x4D9456);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.7F;
        this.rainfall = 0.8F;

        this.rootHeight = 0.10F;
        this.heightVariation = 0.16F;

    }

    @Override
    public float getSpawningChance() {
        return 0.25F;
    }
}
