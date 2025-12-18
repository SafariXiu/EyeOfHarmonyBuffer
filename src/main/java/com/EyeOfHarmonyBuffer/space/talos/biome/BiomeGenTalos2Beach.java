package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Beach extends GSBiomeGenBase {

    public double beachMin;
    public double beachMax;

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2Beach(int id) {
        super(id);

        this.setBiomeName("Talos Beach");
        this.setColor(0xE0D8A0);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.8F;

        this.rootHeight = 0.0F;
        this.heightVariation = 0.025F;

        this.beachMin = 62.0D;
        this.beachMax = 67.0D;

        this.surfaceBlock = new BlockMetaPair(Blocks.sand, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
