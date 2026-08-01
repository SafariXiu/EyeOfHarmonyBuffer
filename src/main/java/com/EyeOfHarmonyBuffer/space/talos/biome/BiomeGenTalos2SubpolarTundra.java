package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2SubpolarTundra extends GSBiomeGenBase {

    public BiomeGenTalos2SubpolarTundra(int id) {
        super(id);

        this.setBiomeName("Talos Subpolar Tundra");
        this.setColor(0x7F9C8A);
        this.enableRain = true;
        this.enableSnow = true;
        this.temperature = 0.2F;
        this.rainfall = 0.4F;

        this.rootHeight = 0.02F;
        this.heightVariation = 0.02F;

    }

    @Override
    public float getSpawningChance() {
        return 0.12F;
    }
}
