package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2PolarDesert extends TalosBiomeBase {

    public BiomeGenTalos2PolarDesert(int id) {
        super(id);

        this.setBiomeName("Talos Polar Desert");
        this.setColor(0xE6F0FF);
        this.enableRain = false;
        this.enableSnow = true;
        this.temperature = 0.05F;
        this.rainfall = 0.1F;

        this.rootHeight = 0.0F;
        this.heightVariation = 0.04F;

        this.heightBias = 0.15;
        this.heightScale = 0.20;

        Block[] frozen = new Block[] {
            Blocks.snow, Blocks.packed_ice, Blocks.stone, Blocks.gravel
        };

        // 极地荒漠：无树无草，只有冰原上的石头
        this.rocks = new RockConfig(0.6, Blocks.stone, 5, 2, 5);
        this.rocks.groundBlocks = frozen;
        this.boulders = new SimpleConfig(0.5);

        this.groundPatches.add(new GroundPatchConfig(1.2, Blocks.gravel, 0, 2, 0.4));
        this.groundPatches.add(new GroundPatchConfig(0.8, Blocks.stone, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.08F;
    }
}
