package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2TemperateForest extends TalosBiomeBase {

    public BiomeGenTalos2TemperateForest(int id) {
        super(id);

        this.setBiomeName("Talos Temperate Forest");
        this.setColor(0x4D9456);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.7F;
        this.rainfall = 0.8F;

        this.rootHeight = 0.10F;
        this.heightVariation = 0.16F;

        this.heightBias = 0.50;
        this.heightScale = 0.55;

        // ===== 树：茂密橡树 =====
        this.treeStyle.perChunk = 3.0;
        this.treeStyle.woodBlock = Blocks.log;
        this.treeStyle.woodMeta = 0;
        this.treeStyle.leafBlock = Blocks.leaves;
        this.treeStyle.leafMeta = 0;
        this.treeStyle.trunkMin = 5;
        this.treeStyle.trunkMax = 7;
        this.treeStyle.shape = TreeShape.ROUND;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.95;
        this.treeStyle.leanChance = 0.4;

        this.grass = new GrassConfig(18, 1);
        this.flowers = new FlowerConfig(3, Blocks.red_flower);
        this.shrubs = new SimpleConfig(1);
        this.pond = new PondConfig(0.08, 5, 2, 0.5);
        this.rocks = new RockConfig(0.3, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.3);

        this.groundPatches.add(new GroundPatchConfig(2, Blocks.dirt, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(0.5, Blocks.gravel, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.25F;
    }
}
