package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2Ocean extends GSBiomeGenBase {

    public double deepMin;
    public double deepMax;

    public BiomeGenTalos2Ocean(int id) {
        super(id);

        this.setBiomeName("Talos Ocean");
        this.setColor(0x203050);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.9F;

        this.rootHeight = -1.0F;
        this.heightVariation = 0.1F;

        this.deepMin     = 16.0D;
        this.deepMax     = 46.0D;

    }

    @Override
    public float getSpawningChance() {
        return 0.0F;
    }
}
