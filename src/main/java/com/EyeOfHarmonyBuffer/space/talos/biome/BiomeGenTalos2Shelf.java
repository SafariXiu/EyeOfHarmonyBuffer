package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2Shelf extends TalosBiomeBase {

    public double shelfTopMin;
    public double shelfTopMax;

    public BiomeGenTalos2Shelf(int id) {
        super(id);

        this.setBiomeName("Talos Shelf");
        this.setColor(0x203050);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.9F;

        this.rootHeight = -1.0F;
        this.heightVariation = 0.1F;

        this.shelfTopMin = 52.0D;
        this.shelfTopMax = 58.0D;

    }

    @Override
    public float getSpawningChance() {
        return 0.0F;
    }
}
