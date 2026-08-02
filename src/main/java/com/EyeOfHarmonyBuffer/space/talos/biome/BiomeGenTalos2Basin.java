package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2Basin extends TalosBiomeBase {

    public double basinMin;
    public double basinMax;

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

        this.heightBias = 0.30;
        this.heightScale = 0.40;

        // ===== 树：湿地橡树，密度中等 =====
        this.treeStyle.perChunk = 1.5;
        this.treeBlueprint = TalosTreeBlueprints.BASIN_TREE;

        // 沼泽盆地：多水、多芦苇睡莲
        this.grass = new GrassConfig(20, 1);
        this.flowers = new FlowerConfig(2, Blocks.red_flower);
        this.reeds = new SimpleConfig(3);
        this.waterlily = new SimpleConfig(3);
        this.shrubs = new SimpleConfig(1);
        this.pond = new PondConfig(0.15, 5, 2, 0.5);
        this.rocks = new RockConfig(0.15, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.2);

        this.groundPatches.add(new GroundPatchConfig(3, Blocks.dirt, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(0.4, Blocks.gravel, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }
}
