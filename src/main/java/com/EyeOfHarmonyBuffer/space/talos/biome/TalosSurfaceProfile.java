package com.EyeOfHarmonyBuffer.space.talos.biome;

import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;

/**
 * 单个群系的地表 / 地下方块规格。
 *
 * 分层（自地表向下）：
 *   - surfaceBlock × surfaceDepth  表层（草 / 沙 / 雪…）
 *   - fillerBlock  × fillerDepth   填充层（泥土 / 砂岩 / 石头…）
 *   - deepBlock                    深层（一直延伸到基岩）
 *
 * 河流挖开的河床不铺表层 / 填充层，直接露出 deepBlock。
 */
public final class TalosSurfaceProfile {

    public final BlockMetaPair surfaceBlock;
    public final int surfaceDepth;

    public final BlockMetaPair fillerBlock;
    public final int fillerDepth;

    public final BlockMetaPair deepBlock;

    public TalosSurfaceProfile(BlockMetaPair surfaceBlock, int surfaceDepth,
                               BlockMetaPair fillerBlock, int fillerDepth,
                               BlockMetaPair deepBlock) {
        this.surfaceBlock = surfaceBlock;
        this.surfaceDepth = surfaceDepth;
        this.fillerBlock = fillerBlock;
        this.fillerDepth = fillerDepth;
        this.deepBlock = deepBlock;
    }
}
