package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Plateau extends GSBiomeGenBase {

    public double plateauMin;
    public double plateauMax;

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2Plateau(int id) {
        super(id);

        this.setBiomeName("Talos Plateau");
        this.setColor(0x7E8C6A);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.6F;

        this.rootHeight = 0.35F;
        this.heightVariation = 0.08F;

        this.plateauMin = 92.0D;
        this.plateauMax = 124.0D;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
