package com.EyeOfHarmonyBuffer.space.blackhole;

import galaxyspace.core.world.GSBiomeGenBase;

/**
 * 翡翠王座主群系：翡翠绿基调的温和类地环境。
 * <p>必须继承 {@link GSBiomeGenBase}：GalaxySpace 的 ChunkProviderSpaceLakes /
 * WorldChunkManagerSpaceGS 会按 GS 群系体系强转 biome（塔罗斯-2 全部群系同款）。
 * 固定 ID 197（塔罗斯-2 占用 180-196，从 197 起找空槽）。
 */
public class BiomeGenEmeraldThrone extends GSBiomeGenBase {

    private static final int MIN_ID = 197;

    public static final BiomeGenEmeraldThrone INSTANCE = new BiomeGenEmeraldThrone(findFreeBiomeId());

    private static int findFreeBiomeId() {
        net.minecraft.world.biome.BiomeGenBase[] list = GSBiomeGenBase.getBiomeGenArray();
        for (int i = MIN_ID; i < list.length; i++) {
            if (list[i] == null) {
                return i;
            }
        }
        throw new RuntimeException("No free biome id for Emerald Throne");
    }

    private BiomeGenEmeraldThrone(int id) {
        super(id);
        this.setBiomeName("Emerald Throne");
        this.setColor(0x3FA05A);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.8F;
        this.rootHeight = 0.1F;
        this.heightVariation = 0.35F;
        this.theBiomeDecorator.treesPerChunk = 3;
        this.theBiomeDecorator.flowersPerChunk = 3;
        this.theBiomeDecorator.grassPerChunk = 12;
        this.waterColorMultiplier = 0x2E8B57;
    }

    /** GS 群系：怪物流刷概率（塔罗斯平原同款）。 */
    @Override
    public float getSpawningChance() {
        return 0.1F;
    }

    /** 翡翠绿植被着色。 */
    @Override
    public int getBiomeGrassColor(int x, int y, int z) {
        return 0x55C26B;
    }

    @Override
    public int getBiomeFoliageColor(int x, int y, int z) {
        return 0x4FA85F;
    }
}
