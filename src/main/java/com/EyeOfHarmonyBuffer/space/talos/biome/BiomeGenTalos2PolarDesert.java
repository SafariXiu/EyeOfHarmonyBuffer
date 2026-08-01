package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2PolarDesert extends GSBiomeGenBase {

    public BiomeGenTalos2PolarDesert(int id) {
        super(id);

        this.setBiomeName("Talos Polar Desert");
        this.setColor(0xE6F0FF);
        this.enableRain = false;
        this.enableSnow = true;
        this.temperature = 0.05F;
        this.rainfall = 0.1F;

        this.rootHeight = 0.0F;
        this.heightVariation = 0.04F;

    }

    @Override
    public float getSpawningChance() {
        return 0.08F;
    }
}
