package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.init.Blocks;

public class BiomeGenTalos2SubpolarTundra extends TalosBiomeBase {

    public BiomeGenTalos2SubpolarTundra(int id) {
        super(id);

        this.setBiomeName("Talos Subpolar Tundra");
        this.setColor(0x7F9C8A);
        this.enableRain = true;
        this.enableSnow = true;
        this.temperature = 0.2F;
        this.rainfall = 0.4F;

        this.rootHeight = 0.02F;
        this.heightVariation = 0.02F;

        this.heightBias = 0.35;
        this.heightScale = 0.40;

        // ===== 树：极稀疏矮云杉 =====
        this.treeStyle.perChunk = 0.1;
        this.treeBlueprint = TalosTreeBlueprints.TUNDRA_PINE;

        // 冻原：苔藓/蕨类 + 融水塘
        this.grass = new GrassConfig(12, 2);
        this.flowers = new FlowerConfig(0.5, Blocks.red_flower);
        this.pond = new PondConfig(0.06, 5, 2, 0.5);
        this.rocks = new RockConfig(0.5, Blocks.stone, 5, 2, 5);
        this.boulders = new SimpleConfig(0.5);

        this.groundPatches.add(new GroundPatchConfig(1.5, Blocks.gravel, 0, 2, 0.4));
        this.groundPatches.add(new GroundPatchConfig(1.0, Blocks.dirt, 0, 2, 0.4));
    }

    @Override
    public float getSpawningChance() {
        return 0.12F;
    }
}
