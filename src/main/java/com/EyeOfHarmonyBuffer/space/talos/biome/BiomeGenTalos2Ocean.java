package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Ocean extends GSBiomeGenBase {

    public double deepMin;
    public double deepMax;
    public double shelfTopMin;
    public double shelfTopMax;

    public BlockMetaPair bottomBlock;
    public BlockMetaPair shelfBlock;
    public BlockMetaPair deepBlock;

    public BiomeGenTalos2Ocean(int id) {
        super(id);

        this.setBiomeName("Talos Ocean");
        this.setColor(0x203050);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.9F;

        this.rootHeight = -1.0F;
        this.heightVariation = 0.1F;

        this.deepMin     = 16.0D;
        this.deepMax     = 46.0D;
        this.shelfTopMin = 52.0D;
        this.shelfTopMax = 58.0D;

        this.bottomBlock = new BlockMetaPair(Blocks.stone, (byte) 0);
        this.shelfBlock  = new BlockMetaPair(Blocks.stone, (byte) 0);
        this.deepBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.0F;
    }
}
