package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2TemperateSteppe extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2TemperateSteppe(int id) {
        super(id);

        this.setBiomeName("Talos Temperate Steppe");
        this.setColor(0x8FB168);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.65F;
        this.rainfall = 0.45F;

        this.rootHeight = 0.06F;
        this.heightVariation = 0.03F;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.17F;
    }
}
