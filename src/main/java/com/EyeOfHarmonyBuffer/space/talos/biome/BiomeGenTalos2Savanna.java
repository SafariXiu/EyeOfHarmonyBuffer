package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2Savanna extends GSBiomeGenBase {

    public BiomeGenTalos2Savanna(int id) {
        super(id);

        this.setBiomeName("Talos Savanna");
        this.setColor(0xC6B260);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.9F;
        this.rainfall = 0.35F;

        this.rootHeight = 0.10F;
        this.heightVariation = 0.05F;

    }

    @Override
    public float getSpawningChance() {
        return 0.2F;
    }
}
