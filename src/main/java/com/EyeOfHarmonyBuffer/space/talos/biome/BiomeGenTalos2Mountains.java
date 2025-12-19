package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Mountains extends GSBiomeGenBase {

    public double mountainMin;
    public double mountainMax;

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2Mountains(int id) {
        super(id);

        this.setBiomeName("Talos Mountains");
        this.setColor(0x6E6E7A);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.4F;

        this.rootHeight = 1.0F;
        this.heightVariation = 0.8F;

        this.mountainMin = 110.0D;
        this.mountainMax = 200.0D;

        this.surfaceBlock = new BlockMetaPair(Blocks.stone, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.stone, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
