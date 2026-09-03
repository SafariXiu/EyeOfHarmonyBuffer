package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JuNengDongLiDanYuan（方舟中间产物）。
 */
public class ItemJuNengDongLiDanYuan extends ItemEOHBBatch {

    public ItemJuNengDongLiDanYuan() {
        setUnlocalizedName("JuNengDongLiDanYuan");
        setTextureName("eyeofharmonybuffer:Arknights/JuNengDongLiDanYuan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JuNengDongLiDanYuan);
    }
}
