package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

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

        // ===== 树：普通橡树，疏密适中 =====
        this.treeStyle.perChunk = 0.6;
        this.treeStyle.woodBlock = Blocks.log;
        this.treeStyle.woodMeta = 0;
        this.treeStyle.leafBlock = Blocks.leaves;
        this.treeStyle.leafMeta = 0;
        this.treeStyle.trunkMin = 4;
        this.treeStyle.trunkMax = 6;
        this.treeStyle.shape = TreeShape.ROUND;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.9;
        this.treeStyle.leanChance = 0.5;

        this.grass = new GrassConfig(30, 1);
        this.flowers = new FlowerConfig(2, Blocks.red_flower);
        this.pond = new PondConfig(0.08, 5, 2, 0.5);
        this.rocks = new RockConfig(0.2, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.3);

        this.groundPatches.add(new GroundPatchConfig(2, Blocks.dirt, 0, 2, 0.6));
        this.groundPatches.add(new GroundPatchConfig(0.5, Blocks.gravel, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
