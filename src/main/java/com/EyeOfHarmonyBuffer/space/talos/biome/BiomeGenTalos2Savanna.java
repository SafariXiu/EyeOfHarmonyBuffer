package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Savanna extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2Savanna(int id) {
        super(id);

        this.setBiomeName("Talos Savanna");
        this.setColor(0xC6B260);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.9F;
        this.rainfall = 0.35F;

        this.rootHeight = 0.10F;
        this.heightVariation = 0.05F;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 1); // 稍偏黄
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.2F;
    }
}
