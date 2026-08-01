package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2WarmSteppe extends TalosBiomeBase {

    public BiomeGenTalos2WarmSteppe(int id) {
        super(id);

        this.setBiomeName("Talos Warm Steppe");
        this.setColor(0xB9A768);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.8F;
        this.rainfall = 0.3F;

        this.rootHeight = 0.08F;
        this.heightVariation = 0.04F;

    }

    @Override
    public float getSpawningChance() {
        return 0.18F;
    }
}
