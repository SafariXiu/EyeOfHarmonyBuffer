package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * HeSuXianWei（方舟中间产物）。
 */
public class ItemHeSuXianWei extends ItemEOHBBatch {

    public ItemHeSuXianWei() {
        setUnlocalizedName("HeSuXianWei");
        setTextureName("eyeofharmonybuffer:Arknights/HeSuXianWei");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_HeSuXianWei);
    }
}
