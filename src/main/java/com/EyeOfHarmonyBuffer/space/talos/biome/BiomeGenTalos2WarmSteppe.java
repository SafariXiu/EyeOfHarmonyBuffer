package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2WarmSteppe extends TalosBiomeBase {

    public BiomeGenTalos2WarmSteppe(int id) {
        super(id);

        this.setBiomeName("Talos Warm Steppe");
        this.setColor(0xB9A768);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.8F;
        this.rainfall = 0.3F;

        this.rootHeight = 0.08F;
        this.heightVariation = 0.04F;

        // ===== 树：稀疏歪橡树（旱地感） =====
        this.treeStyle.perChunk = 0.2;
        this.treeStyle.woodBlock = Blocks.log;
        this.treeStyle.woodMeta = 0;
        this.treeStyle.leafBlock = Blocks.leaves;
        this.treeStyle.leafMeta = 0;
        this.treeStyle.trunkMin = 4;
        this.treeStyle.trunkMax = 6;
        this.treeStyle.shape = TreeShape.ROUND;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.6;
        this.treeStyle.leanChance = 0.6;

        this.grass = new GrassConfig(30, 1);
        this.deadBush = new SimpleConfig(6);
        this.flowers = new FlowerConfig(1, Blocks.red_flower);
        this.pond = new PondConfig(0.06, 5, 2, 0.5);
        this.rocks = new RockConfig(0.2, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.2);

        this.groundPatches.add(new GroundPatchConfig(2, Blocks.dirt, 0, 2, 0.6));
        this.groundPatches.add(new GroundPatchConfig(0.5, Blocks.gravel, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.18F;
    }
}
