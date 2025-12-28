package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;

public class BiomeGenTalos2CoolForest extends GSBiomeGenBase {

    public BlockMetaPair surfaceBlock;
    public BlockMetaPair fillerBlock;
    public BlockMetaPair stoneBlock;

    public BiomeGenTalos2CoolForest(int id) {
        super(id);

        this.setBiomeName("Talos Cool Conifer Forest");
        this.setColor(0x2F5C3A);
        this.enableRain = true;
        this.enableSnow = false;
        this.temperature = 0.5F;
        this.rainfall = 0.65F;

        this.rootHeight = 0.18F;
        this.heightVariation = 0.25F;

        this.surfaceBlock = new BlockMetaPair(Blocks.grass, (byte) 0);
        this.fillerBlock  = new BlockMetaPair(Blocks.dirt, (byte) 0);
        this.stoneBlock   = new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    @Override
    public float getSpawningChance() {
        return 0.22F;
    }
}
