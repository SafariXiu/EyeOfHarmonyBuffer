package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.common.item.items.ItemBlockGlowCasing;

/**
 * 戴森核心信息导流块（可点亮机械外壳，带 CTM 连接纹理）。
 * meta 0 = 熄灯态（默认材质），meta 8 = 点亮态（蓝色发光贴图 + 光照 15）。
 * 连接贴图按约定放在：
 *   Arknights/HunNingTuDaoXian_conn_0..15        （熄灯）
 *   Arknights/HunNingTuDaoXian_Ligth_conn_0..15  （点亮）
 */
public class BlockCasingsDysonFlow extends BlockGlowCasingBase {

    public static final int META_DYSON_FLOW = 0;

    public BlockCasingsDysonFlow() {
        super(ItemBlockGlowCasing.class, "eoh.blockcasingsdysonflow");
    }

    @Override
    protected int getVariantCount() {
        return 1;
    }

    @Override
    protected String getIconBasePath(int variant) {
        return "Arknights/HunNingTuDaoXian";
    }
}
