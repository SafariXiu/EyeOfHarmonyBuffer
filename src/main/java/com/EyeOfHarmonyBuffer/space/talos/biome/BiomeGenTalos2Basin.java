package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Basin extends GSBiomeGenBase {

    public double basinMin;
    public double basinMax;

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2Basin(int id) {
        super(id);

        this.setBiomeName("Talos Basin");
        this.setColor(0x5E7A5E);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.9F;

        this.rootHeight = -0.10F;
        this.heightVariation = 0.02F;

        this.basinMin = 66.0D;
        this.basinMax = 82.0D;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
