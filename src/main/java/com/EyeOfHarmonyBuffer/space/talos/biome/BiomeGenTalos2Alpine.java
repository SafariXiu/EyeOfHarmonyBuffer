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

        this.heightBias = 0.68;
        this.heightScale = 0.50;

        Block[] frozen = new Block[] {
            Blocks.snow, Blocks.packed_ice, Blocks.stone, Blocks.gravel
        };

        this.rocks = new RockConfig(0.1, Blocks.stone, 5, 2, 5);
        this.rocks.groundBlocks = frozen;
        this.boulders = new SimpleConfig(0.5);

        /*this.groundPatches.add(new GroundPatchConfig(1.2, Blocks.gravel, 0, 2, 0.4));
        this.groundPatches.add(new GroundPatchConfig(0.8, Blocks.stone, 0, 2, 0.3));*/
    }

    @Override
    public float getSpawningChance() {
        return 0.05F;
    }
}
