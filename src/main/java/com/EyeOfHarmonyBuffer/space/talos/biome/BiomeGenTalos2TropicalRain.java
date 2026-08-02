package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2TropicalRain extends TalosBiomeBase {

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

        this.heightBias = 0.42;
        this.heightScale = 0.50;

        // ===== 树：2×2 巨型丛林树（30~40 格高，带树冠大分支） =====
        this.treeStyle.perChunk = 3.0;
        this.treeBlueprint = TalosTreeBlueprints.RAINFOREST_GIANT;

        // ===== 地貌装饰：高草 + 蕨混生、蘑菇、潮湿地面 =====
        this.grass = new GrassConfig(15, 1);
        this.ferns = new GrassConfig(18, 2);
        this.flowers = new FlowerConfig(2, Blocks.red_flower);
        this.shrubs = new SimpleConfig(3);
        this.mushrooms = new SimpleConfig(2.5);
        this.reeds = new SimpleConfig(2);
        this.waterlily = new SimpleConfig(3);
        this.pond = new PondConfig(0.12, 5, 2, 0.5);
        this.fallenLogs = new SimpleConfig(0.8);
        this.rocks = new RockConfig(0.2, Blocks.mossy_cobblestone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.15);

        this.groundPatches.add(new GroundPatchConfig(1.5, Blocks.dirt, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(1.0, Blocks.gravel, 0, 2, 0.4));
        this.groundPatches.add(new GroundPatchConfig(0.6, Blocks.clay, 0, 2, 0.4));
    }

    @Override
    public float getSpawningChance() {
        return 0.3F;
    }
}
