package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Desert extends GSBiomeGenBase {

    public double desertMin;
    public double desertMax;

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

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

        this.surfaceBlock = new BlockMetaPair(Blocks.sand, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.sand, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.sandstone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
