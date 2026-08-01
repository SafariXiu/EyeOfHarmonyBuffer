package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2Alpine extends GSBiomeGenBase {

    public BiomeGenTalos2Alpine(int id) {
        super(id);

        this.setBiomeName("Talos Alpine Peaks");
        this.setColor(0xA0C0D8);
        this.enableRain = false;
        this.enableSnow = true;
        this.temperature = 0.1F;
        this.rainfall = 0.3F;

        this.rootHeight = 1.1F;
        this.heightVariation = 0.7F;

    }

    @Override
    public float getSpawningChance() {
        return 0.05F;
    }
}
