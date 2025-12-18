package com.EyeOfHarmonyBuffer.space.talos;

import galaxyspace.core.world.GSBiomeGenBase;

public class BiomeGenTalos2 extends GSBiomeGenBase {

    public static final BiomeGenTalos2 talos2 = new BiomeGenTalos2(180);

    public BiomeGenTalos2(int id) {
        super(id);

        this.setBiomeName("Talos II");
        this.setColor(0x8C8C99);

        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.8F;

        this.rootHeight = 0.125F;
        this.heightVariation = 0.05F;
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
