package com.EyeOfHarmonyBuffer.space.blackhole;

import java.util.Random;

import galaxyspace.core.world.GSBiomeGenBase;
import net.minecraft.world.World;

/**
 * 死亡之海（y ≤ 64）：荒芜低地 / 海洋。枯黄植被着色、暗色水体、无雨。
 */
public class BiomeGenEmeraldDeadSea extends GSBiomeGenBase {

    public static final BiomeGenEmeraldDeadSea INSTANCE = new BiomeGenEmeraldDeadSea(findFreeBiomeId(197));

    private static int findFreeBiomeId(int from) {
        net.minecraft.world.biome.BiomeGenBase[] list = GSBiomeGenBase.getBiomeGenArray();
        for (int i = from; i < list.length; i++) {
            if (list[i] == null) {
                return i;
            }
        }
        throw new RuntimeException("No free biome id for Emerald Dead Sea");
    }

    private BiomeGenEmeraldDeadSea(int id) {
        super(id);
        this.setBiomeName("Emerald Dead Sea");
        this.setColor(0x6B6B4A);
        this.enableRain = false;
        this.enableSnow = false;
        this.rainfall = 0.1F;
        this.rootHeight = -0.5F;
        this.heightVariation = 0.05F;
        this.waterColorMultiplier = 0x3B3B3B;
    }

    @Override
    public float getSpawningChance() {
        return 0.05F;
    }

    /** 禁用原版装饰（树 / 草 / 花 / 湖泊等），地表完全由地形生成器显式控制（塔罗斯-2 同款）。 */
    @Override
    public void decorate(World world, Random random, int chunkX, int chunkZ) {
    }

    /** 枯黄植被。 */
    @Override
    public int getBiomeGrassColor(int x, int y, int z) {
        return 0x8A7F52;
    }

    @Override
    public int getBiomeFoliageColor(int x, int y, int z) {
        return 0x6E6A44;
    }
}
