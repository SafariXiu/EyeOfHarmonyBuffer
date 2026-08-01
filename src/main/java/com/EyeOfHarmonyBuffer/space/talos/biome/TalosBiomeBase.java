package com.EyeOfHarmonyBuffer.space.talos.biome;

import galaxyspace.core.world.GSBiomeGenBase;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Talos 群系公共基类。
 *
 * 覆盖原版群系装饰入口 decorate(...) 为空实现：
 * Galacticraft 的 ChunkProviderSpaceLakes.populate 会调用
 * biome.decorate() 触发原版 theBiomeDecorator（草 / 树 / 花、
 * 水岩浆泉、沙砾黏土斑块、湖泊等），这里全部阻断，
 * 让地表完全由地形生成器与 BiomeDecoratorTalos2 显式控制。
 */
public abstract class TalosBiomeBase extends GSBiomeGenBase {

    public TalosBiomeBase(int id) {
        super(id);
    }

    @Override
    public void decorate(World world, Random random, int chunkX, int chunkZ) {
        // 禁用原版装饰；后续装饰统一走 BiomeDecoratorTalos2 / 新装饰系统
    }
}
