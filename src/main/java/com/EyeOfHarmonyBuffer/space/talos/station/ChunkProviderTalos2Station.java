package com.EyeOfHarmonyBuffer.space.talos.station;

import net.minecraft.world.World;

import galaxyspace.core.dimension.ChunkProviderDefaultSpaceStation;
import galaxyspace.core.world.WorldGenDefaultSpaceStation;

/**
 * 塔罗斯-1 空间站区块生成器：站体使用 GT5U 字符数组结构
 * （{@link WorldGenTileStructure}：shapeMain + addElement 映射，扫描仪输出直接粘贴）。
 */
public class ChunkProviderTalos2Station extends ChunkProviderDefaultSpaceStation {

    public ChunkProviderTalos2Station(World world, long seed, boolean mapFeaturesEnabled) {
        super(world, seed, mapFeaturesEnabled);
    }

    @Override
    public String makeString() {
        return "Talos2StationLevelSource";
    }

    @Override
    protected WorldGenDefaultSpaceStation provideSSWorldGenerator() {
        return new WorldGenTileStructure();
    }
}
