package com.EyeOfHarmonyBuffer.common.Block.Arknights;

/**
 * 发光版水下纯净玻璃（Glowing Underwater Pure Glass）：
 * 独立注册的方块，复用 BlockCleanGlass 的连接纹理与水体侧壁跳过逻辑
 * （WaterGlassMixin / isConnected 均按 BlockCleanGlass 判断，子类自动兼容），
 * 自身发光等级 15。
 */
public class BlockCleanGlassGlow extends BlockCleanGlass {

    public BlockCleanGlassGlow() {
        setLightLevel(1.0F); // 发光等级 15
    }
}
