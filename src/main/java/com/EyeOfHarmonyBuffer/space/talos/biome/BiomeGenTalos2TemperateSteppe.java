package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2TemperateSteppe extends GSBiomeGenBase {

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
