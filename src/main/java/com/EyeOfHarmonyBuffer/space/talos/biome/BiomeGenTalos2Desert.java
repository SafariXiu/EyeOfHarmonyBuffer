package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

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

        Block[] sandy = new Block[] {
            Blocks.sand, Blocks.sandstone, Blocks.stone, Blocks.gravel,
            Blocks.grass, Blocks.dirt
        };

        // 沙漠不长树、不长草，只有仙人掌与枯灌木
        this.cactus = new SimpleConfig(2);
        this.deadBush = new SimpleConfig(4);

        this.rocks = new RockConfig(0.5, Blocks.stone, 5, 2, 5);
        this.rocks.groundBlocks = sandy;
        this.boulders = new SimpleConfig(0.3);

        this.groundPatches.add(new GroundPatchConfig(0.8, Blocks.gravel, 0, 2, 0.3));
        this.groundPatches.add(new GroundPatchConfig(0.8, Blocks.sandstone, 0, 2, 0.3));
        this.groundPatches.add(new GroundPatchConfig(0.5, Blocks.dirt, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
