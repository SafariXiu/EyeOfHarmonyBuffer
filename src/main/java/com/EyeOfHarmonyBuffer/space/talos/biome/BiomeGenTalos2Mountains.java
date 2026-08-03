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

        this.heightBias = 0.72;
        this.heightScale = 0.60;

        Block[] rocky = new Block[] {
            Blocks.stone, Blocks.gravel, Blocks.grass, Blocks.dirt
        };

        // ===== 树：稀疏矮云杉（石缝里也能长） =====
        this.treeStyle.perChunk = 0.2;
        this.treeBlueprint = TalosTreeBlueprints.MOUNTAIN_SPRUCE;

        // 石山：不生长草，多石头
        this.rocks = new RockConfig(1.2, Blocks.stone, 5, 2, 5);
        this.rocks.groundBlocks = rocky;
        this.boulders = new SimpleConfig(1.0);

        this.groundPatches.add(new GroundPatchConfig(2.0, Blocks.gravel, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(1.5, Blocks.stone, 0, 2, 0.4));
        this.groundPatches.add(new GroundPatchConfig(0.3, Blocks.dirt, 0, 2, 0.3));
        this.groundPatches.add(new GroundPatchConfig(1.5, Blocks.snow, 0, 2, 0.4));
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
