package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2TemperateSteppe extends TalosBiomeBase {

    public BiomeGenTalos2TemperateSteppe(int id) {
        super(id);

        this.setBiomeName("Talos Temperate Steppe");
        this.setColor(0x8FB168);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.65F;
        this.rainfall = 0.45F;

        this.rootHeight = 0.06F;
        this.heightVariation = 0.03F;

        // ===== 树：稀疏散布橡树 =====
        this.treeStyle.perChunk = 0.2;
        this.treeStyle.woodBlock = Blocks.log;
        this.treeStyle.woodMeta = 0;
        this.treeStyle.leafBlock = Blocks.leaves;
        this.treeStyle.leafMeta = 0;
        this.treeStyle.trunkMin = 4;
        this.treeStyle.trunkMax = 6;
        this.treeStyle.shape = TreeShape.ROUND;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.8;
        this.treeStyle.leanChance = 0.6;

        this.grass = new GrassConfig(35, 1);
        this.flowers = new FlowerConfig(1, Blocks.red_flower);
        this.deadBush = new SimpleConfig(3);
        this.pond = new PondConfig(0.06, 5, 2, 0.5);
        this.rocks = new RockConfig(0.15, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.2);

        this.groundPatches.add(new GroundPatchConfig(2, Blocks.dirt, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(0.5, Blocks.gravel, 0, 2, 0.25));
    }

    @Override
    public float getSpawningChance() {
        return 0.17F;
    }
}
