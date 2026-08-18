package com.EyeOfHarmonyBuffer.space.blackhole;

import java.util.Random;

import galaxyspace.core.world.GSBiomeGenBase;
import net.minecraft.world.World;

/**
 * 生之大陆（y &gt; 64）：翡翠绿基调的温和类地环境。
 */
public class BiomeGenEmeraldLivingLand extends GSBiomeGenBase {

    public static final BiomeGenEmeraldLivingLand INSTANCE = new BiomeGenEmeraldLivingLand(findFreeBiomeId(198));

    private static int findFreeBiomeId(int from) {
        net.minecraft.world.biome.BiomeGenBase[] list = GSBiomeGenBase.getBiomeGenArray();
        for (int i = from; i < list.length; i++) {
            if (list[i] == null) {
                return i;
            }
        }
        throw new RuntimeException("No free biome id for Emerald Living Land");
    }

    private BiomeGenEmeraldLivingLand(int id) {
        super(id);
        this.setBiomeName("Emerald Living Land");
        this.setColor(0x3FA05A);
        this.enableRain = true;
        this.enableSnow = false;
        this.rainfall = 0.8F;
        this.rootHeight = 0.3F;
        this.heightVariation = 0.25F;
        this.waterColorMultiplier = 0x2E8B57;
    }

    @Override
    public float getSpawningChance() {
        return 0.1F;
    }

    /** 禁用原版装饰（树 / 草 / 花 / 湖泊等），地表完全由地形生成器显式控制（塔罗斯-2 同款）。 */
    @Override
    public void decorate(World world, Random random, int chunkX, int chunkZ) {
    }

    /** 翡翠绿植被。 */
    @Override
    public int getBiomeGrassColor(int x, int y, int z) {
        return 0x55C26B;
    }

    @Override
    public int getBiomeFoliageColor(int x, int y, int z) {
        return 0x4FA85F;
    }
}
