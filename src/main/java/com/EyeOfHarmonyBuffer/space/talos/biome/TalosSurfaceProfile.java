package com.EyeOfHarmonyBuffer.space.talos.biome;

import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;

/**
 * 单个群系的地表 / 地下方块规格。
 *
 * 分层（自地表向下）：
 *   - surfaceBlock × surfaceDepth  表层（草 / 沙 / 雪…）
 *   - fillerBlock  × fillerDepth   填充层（泥土 / 砂岩 / 石头…）
 *   - deepBlock                    深层（一直延伸到基岩）
 *   - pocketBlock（可选）          随机袋：按列哈希命中时，把顶部 pocketDepth
 *                                  格替换为随机袋方块（例如高山混入砾石）。
 *
 * 河流挖开的河床不铺表层 / 填充层，直接露出 deepBlock。
 */
public final class TalosSurfaceProfile {

    public final BlockMetaPair surfaceBlock;
    public final int surfaceDepth;

    public final BlockMetaPair fillerBlock;
    public final int fillerDepth;

    public final BlockMetaPair deepBlock;

    /** 随机袋方块；null 表示该群系没有随机袋。 */
    public final BlockMetaPair pocketBlock;
    public final int pocketDepth;
    public final double pocketChance;

    public TalosSurfaceProfile(BlockMetaPair surfaceBlock, int surfaceDepth,
                               BlockMetaPair fillerBlock, int fillerDepth,
                               BlockMetaPair deepBlock,
                               BlockMetaPair pocketBlock, int pocketDepth,
                               double pocketChance) {
        this.surfaceBlock = surfaceBlock;
        this.surfaceDepth = surfaceDepth;
        this.fillerBlock = fillerBlock;
        this.fillerDepth = fillerDepth;
        this.deepBlock = deepBlock;
        this.pocketBlock = pocketBlock;
        this.pocketDepth = pocketDepth;
        this.pocketChance = pocketChance;
    }
}
