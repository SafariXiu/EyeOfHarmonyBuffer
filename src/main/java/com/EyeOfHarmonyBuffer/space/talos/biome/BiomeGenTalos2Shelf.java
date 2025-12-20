package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Shelf extends GSBiomeGenBase {

    public double shelfTopMin;
    public double shelfTopMax;

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair shelfBlock;
    public BlockMetaPair deepBlock;

    public BiomeGenTalos2Shelf(int id) {
        super(id);

        this.setBiomeName("Talos Shelf");
        this.setColor(0x203050);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.9F;

        this.rootHeight = -1.0F;
        this.heightVariation = 0.1F;

        this.shelfTopMin = 52.0D;
        this.shelfTopMax = 58.0D;

        this.surfaceBlock = new BlockMetaPair(Blocks.gravel, (byte) 0);
        this.shelfBlock  = new BlockMetaPair(Blocks.stone, (byte) 0);
        this.deepBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.0F;
    }
}
