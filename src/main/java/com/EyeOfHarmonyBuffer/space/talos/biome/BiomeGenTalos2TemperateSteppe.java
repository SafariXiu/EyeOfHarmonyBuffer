package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2TemperateSteppe extends TalosBiomeBase {

    public BiomeGenTalos2TemperateSteppe(int id) {
        super(id);

        this.setBiomeName("Talos Temperate Steppe");
        this.setColor(0x8FB168);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.65F;
        this.rainfall = 0.45F;

        this.rootHeight = 0.06F;
        this.heightVariation = 0.03F;

    }

    @Override
    public float getSpawningChance() {
        return 0.17F;
    }
}
