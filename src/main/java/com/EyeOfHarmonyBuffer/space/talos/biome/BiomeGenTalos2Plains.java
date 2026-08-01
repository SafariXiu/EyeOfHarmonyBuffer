package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2Plains extends TalosBiomeBase {

    public double plainMin;
    public double plainMax;

    public BiomeGenTalos2Plains(int id) {
        super(id);

        this.setBiomeName("Talos Plains");
        this.setColor(0x8C8C99);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.8F;

        this.rootHeight = 0.125F;
        this.heightVariation = 0.05F;

        this.plainMin = 70.0D;
        this.plainMax = 96.0D;

    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
