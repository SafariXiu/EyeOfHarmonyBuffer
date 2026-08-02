package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2TropicalRain extends TalosBiomeBase {

    public BiomeGenTalos2TropicalRain(int id) {
        super(id);

        this.setBiomeName("Talos Tropical Rainforest");
        this.setColor(0x147B44);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.95F;
        this.rainfall = 1.0F;

        this.rootHeight = 0.12F;
        this.heightVariation = 0.18F;

        this.heightBias = 0.42;
        this.heightScale = 0.50;

        // ===== 树：2×2 巨型丛林树 =====
        this.treeStyle.perChunk = 1.5;
        this.treeBlueprint = TalosTreeBlueprints.RAINFOREST_GIANT;

        this.grass = new GrassConfig(15, 1);
        this.flowers = new FlowerConfig(4, Blocks.red_flower);
        this.shrubs = new SimpleConfig(2);
        this.reeds = new SimpleConfig(2);
        this.waterlily = new SimpleConfig(2);
        this.pond = new PondConfig(0.08, 5, 2, 0.5);
        this.rocks = new RockConfig(0.3, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.2);

        this.groundPatches.add(new GroundPatchConfig(2, Blocks.dirt, 0, 2, 0.5));
    }

    @Override
    public float getSpawningChance() {
        return 0.3F;
    }
}
