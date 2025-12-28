package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2PolarDesert extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

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

        this.surfaceBlock = new BlockMetaPair(Blocks.snow, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.packed_ice, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.08F;
    }
}
