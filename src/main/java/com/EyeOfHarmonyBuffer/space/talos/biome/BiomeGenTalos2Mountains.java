package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

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

        Block[] rocky = new Block[] {
            Blocks.stone, Blocks.gravel, Blocks.grass, Blocks.dirt
        };

        // ===== 树：稀疏矮云杉（石缝里也能长） =====
        this.treeStyle.perChunk = 0.2;
        this.treeStyle.woodBlock = Blocks.log;
        this.treeStyle.woodMeta = 1;
        this.treeStyle.leafBlock = Blocks.leaves;
        this.treeStyle.leafMeta = 1;
        this.treeStyle.trunkMin = 3;
        this.treeStyle.trunkMax = 5;
        this.treeStyle.shape = TreeShape.CONE;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.6;
        this.treeStyle.leanChance = 0.3;
        this.treeStyle.groundBlocks = rocky;

        // 石山：不生长草，多石头
        this.rocks = new RockConfig(1.2, Blocks.stone, 5, 2, 5);
        this.rocks.groundBlocks = rocky;
        this.boulders = new SimpleConfig(1.0);

        this.groundPatches.add(new GroundPatchConfig(2.0, Blocks.gravel, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(1.5, Blocks.stone, 0, 2, 0.4));
        this.groundPatches.add(new GroundPatchConfig(0.3, Blocks.dirt, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
