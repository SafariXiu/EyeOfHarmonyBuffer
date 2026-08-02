package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2CoolForest extends TalosBiomeBase {

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

        this.heightBias = 0.52;
        this.heightScale = 0.55;

        // ===== 树：茂密云杉 =====
        this.treeStyle.perChunk = 3.0;
        this.treeBlueprint = TalosTreeBlueprints.SPRUCE_TOWER;

        // 林下以蕨类为主（meta 2）
        this.grass = new GrassConfig(10, 2);
        this.flowers = new FlowerConfig(1, Blocks.red_flower);
        this.shrubs = new SimpleConfig(1);
        this.pond = new PondConfig(0.06, 5, 2, 0.5);
        this.rocks = new RockConfig(0.4, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.3);

        this.groundPatches.add(new GroundPatchConfig(2, Blocks.dirt, 0, 2, 0.5));
        this.groundPatches.add(new GroundPatchConfig(0.6, Blocks.gravel, 0, 2, 0.3));
    }

    @Override
    public float getSpawningChance() {
        return 0.22F;
    }
}
