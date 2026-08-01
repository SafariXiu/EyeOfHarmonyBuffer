package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2Mountains extends TalosBiomeBase {

    public double mountainMin;
    public double mountainMax;

    public BiomeGenTalos2Mountains(int id) {
        super(id);

        this.setBiomeName("Talos Mountains");
        this.setColor(0x6E6E7A);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.4F;

        this.rootHeight = 1.0F;
        this.heightVariation = 0.8F;

        this.mountainMin = 110.0D;
        this.mountainMax = 200.0D;

    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
