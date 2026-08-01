package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Alpine extends TalosBiomeBase {

    public BiomeGenTalos2Alpine(int id) {
        super(id);

        this.setBiomeName("Talos Alpine Peaks");
        this.setColor(0xA0C0D8);
        this.enableRain = false;
        this.enableSnow = true;
        this.temperature = 0.1F;
        this.rainfall = 0.3F;

        this.rootHeight = 1.1F;
        this.heightVariation = 0.7F;

        Block[] rocky = new Block[] {
            Blocks.snow, Blocks.stone, Blocks.gravel, Blocks.grass, Blocks.dirt
        };

        this.treeStyle.perChunk = 0.3;
        this.treeStyle.woodBlock = Blocks.log;
        this.treeStyle.woodMeta = 1;
        this.treeStyle.leafBlock = Blocks.leaves;
        this.treeStyle.leafMeta = 1;
        this.treeStyle.trunkMin = 3;
        this.treeStyle.trunkMax = 5;
        this.treeStyle.shape = TreeShape.CONE;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.7;
        this.treeStyle.leanChance = 0.3;
        this.treeStyle.groundBlocks = rocky;

        this.rocks = new RockConfig(1.0, Blocks.stone, 5, 2, 5);
        this.rocks.groundBlocks = rocky;
        this.boulders = new SimpleConfig(0.8);

        this.groundPatches.add(new GroundPatchConfig(1.5, Blocks.gravel, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(1.0, Blocks.stone, 0, 2, 0.4));
        this.groundPatches.add(new GroundPatchConfig(0.5, Blocks.dirt, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.05F;
    }
}
