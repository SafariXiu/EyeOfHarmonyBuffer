package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2SubpolarTundra extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2SubpolarTundra(int id) {
        super(id);

        this.setBiomeName("Talos Subpolar Tundra");
        this.setColor(0x7F9C8A);
        this.enableRain = true;
        this.enableSnow = true;
        this.temperature = 0.2F;
        this.rainfall = 0.4F;

        this.rootHeight = 0.02F;
        this.heightVariation = 0.02F;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 1);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.12F;
    }
}
