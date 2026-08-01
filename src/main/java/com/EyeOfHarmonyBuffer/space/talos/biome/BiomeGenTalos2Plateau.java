package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2Plateau extends GSBiomeGenBase {

    public double plateauMin;
    public double plateauMax;

    public BiomeGenTalos2Plateau(int id) {
        super(id);

        this.setBiomeName("Talos Plateau");
        this.setColor(0x7E8C6A);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.6F;

        this.rootHeight = 0.35F;
        this.heightVariation = 0.08F;

        this.plateauMin = 92.0D;
        this.plateauMax = 124.0D;

    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
