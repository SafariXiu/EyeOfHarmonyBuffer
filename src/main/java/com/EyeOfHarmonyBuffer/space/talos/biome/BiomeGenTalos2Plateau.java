package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2Plateau extends TalosBiomeBase {

    public double plateauMin;
    public double plateauMax;

    public BiomeGenTalos2Plateau(int id) {
        super(id);

        this.setBiomeName("Talos Plateau");
        this.setColor(0x7E8C6A);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.6F;

        this.rootHeight = 0.35F;
        this.heightVariation = 0.08F;

        this.plateauMin = 92.0D;
        this.plateauMax = 124.0D;

        // ===== 树：稀疏橡树 =====
        this.treeStyle.perChunk = 0.4;
        this.treeStyle.woodBlock = Blocks.log;
        this.treeStyle.woodMeta = 0;
        this.treeStyle.leafBlock = Blocks.leaves;
        this.treeStyle.leafMeta = 0;
        this.treeStyle.trunkMin = 4;
        this.treeStyle.trunkMax = 6;
        this.treeStyle.shape = TreeShape.ROUND;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.85;
        this.treeStyle.leanChance = 0.5;

        this.grass = new GrassConfig(25, 1);
        this.deadBush = new SimpleConfig(2);
        this.pond = new PondConfig(0.05, 5, 2, 0.5);
        this.rocks = new RockConfig(0.5, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.4);

        this.groundPatches.add(new GroundPatchConfig(2, Blocks.dirt, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(1.0, Blocks.gravel, 0, 2, 0.4));
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
