package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2Basin extends GSBiomeGenBase {

    public double basinMin;
    public double basinMax;

    public BiomeGenTalos2Basin(int id) {
        super(id);

        this.setBiomeName("Talos Basin");
        this.setColor(0x5E7A5E);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.9F;

        this.rootHeight = -0.10F;
        this.heightVariation = 0.02F;

        this.basinMin = 66.0D;
        this.basinMax = 82.0D;

    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
