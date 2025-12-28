package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2TemperateForest extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

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

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.25F;
    }
}
