package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ShuangTong（方舟中间产物）。
 */
public class ItemShuangTong extends ItemEOHBBatch {

    public ItemShuangTong() {
        setUnlocalizedName("ShuangTong");
        setTextureName("eyeofharmonybuffer:Arknights/ShuangTong");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ShuangTong);
    }
}
