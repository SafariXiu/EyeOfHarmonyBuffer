package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2Alpine extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2Alpine(int id) {
        super(id);

        this.setBiomeName("Talos Alpine Peaks");
        this.setColor(0xA0C0D8);
        this.enableRain = false;
        this.enableSnow = true;
        this.temperature = 0.1F;
        this.rainfall = 0.3F;

        this.rootHeight = 1.1F;
        this.heightVariation = 0.7F;

        this.surfaceBlock = new BlockMetaPair(Blocks.snow, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.stone, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.05F;
    }
}
