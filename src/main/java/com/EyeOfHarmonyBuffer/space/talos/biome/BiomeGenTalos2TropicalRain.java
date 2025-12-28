package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2TropicalRain extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2TropicalRain(int id) {
        super(id);

        this.setBiomeName("Talos Tropical Rainforest");
        this.setColor(0x147B44);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.95F;
        this.rainfall = 1.0F;

        this.rootHeight = 0.12F;
        this.heightVariation = 0.18F;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.3F;
    }
}
