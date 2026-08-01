package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2Savanna extends TalosBiomeBase {

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

        this.heightBias = 0.45;
        this.heightScale = 0.50;

        this.treeStyle.perChunk = 0.1;
        this.treeStyle.woodBlock = Blocks.log2;
        this.treeStyle.woodMeta = 0;
        this.treeStyle.leafBlock = Blocks.leaves2;
        this.treeStyle.leafMeta = 0;
        this.treeStyle.trunkMin = 4;
        this.treeStyle.trunkMax = 6;
        this.treeStyle.shape = TreeShape.ROUND;
        this.treeStyle.canopyRadius = 2;
        this.treeStyle.leafDensity = 0.55;
        this.treeStyle.leanChance = 1.0;

        this.grass = new GrassConfig(45, 1);
        this.deadBush = new SimpleConfig(12);

        this.pond = new PondConfig(0.1, 5, 2, 0.5);

        this.rocks = new RockConfig(0.1, Blocks.stone, 5, 2, 5);

        this.groundPatches.add(new GroundPatchConfig(1, Blocks.dirt, 0, 2, 1));
        this.groundPatches.add(new GroundPatchConfig(1, Blocks.stone, 0, 2, 0.1));
        this.groundPatches.add(new GroundPatchConfig(1, Blocks.gravel, 0, 2, 0.1));
        this.groundPatches.add(new GroundPatchConfig(0.8, Blocks.sand, 0, 2, 0.1));
    }

    @Override
    public float getSpawningChance() {
        return 0.2F;
    }
}
