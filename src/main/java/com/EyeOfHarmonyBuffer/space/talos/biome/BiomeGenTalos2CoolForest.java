package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2CoolForest extends TalosBiomeBase {

    public BiomeGenTalos2CoolForest(int id) {
        super(id);

        this.setBiomeName("Talos Cool Conifer Forest");
        this.setColor(0x2F5C3A);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.5F;
        this.rainfall = 0.65F;

        this.rootHeight = 0.18F;
        this.heightVariation = 0.25F;

    }

    @Override
    public float getSpawningChance() {
        return 0.22F;
    }
}
