package com.EyeOfHarmonyBuffer.space.talos.biome;

public class BiomeGenTalos2Desert extends TalosBiomeBase {

    public double desertMin;
    public double desertMax;

    public BiomeGenTalos2Desert(int id) {
        super(id);

        this.setBiomeName("Talos Desert");
        this.setColor(0xD8C27A);

        this.enableRain = false;
        this.enableSnow = false;
        this.rainfall = 0.0F;

        this.rootHeight = 0.125F;
        this.heightVariation = 0.03F;

        this.desertMin = 72.0D;
        this.desertMax = 98.0D;

    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
