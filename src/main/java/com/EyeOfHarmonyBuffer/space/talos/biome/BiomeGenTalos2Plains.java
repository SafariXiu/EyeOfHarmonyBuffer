package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Plains extends GSBiomeGenBase {

    public double plainMin;
    public double plainMax;

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2Plains(int id) {
        super(id);

        this.setBiomeName("Talos Plains");
        this.setColor(0x8C8C99);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.8F;

        this.rootHeight = 0.125F;
        this.heightVariation = 0.05F;

        this.plainMin = 70.0D;
        this.plainMax = 96.0D;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
