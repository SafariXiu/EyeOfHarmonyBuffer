package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2TropicalRain extends TalosBiomeBase {

    public BiomeGenTalos2TropicalRain(int id) {
        super(id);

        this.setBiomeName("Talos Tropical Rainforest");
        this.setColor(0x147B44);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.95F;
        this.rainfall = 1.0F;

        this.rootHeight = 0.12F;
        this.heightVariation = 0.18F;

    }

    @Override
    public float getSpawningChance() {
        return 0.3F;
    }
}
