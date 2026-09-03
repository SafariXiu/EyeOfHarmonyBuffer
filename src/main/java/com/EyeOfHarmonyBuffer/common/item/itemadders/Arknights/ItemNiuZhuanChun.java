package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * NiuZhuanChun（方舟中间产物）。
 */
public class ItemNiuZhuanChun extends ItemEOHBBatch {

    public ItemNiuZhuanChun() {
        setUnlocalizedName("NiuZhuanChun");
        setTextureName("eyeofharmonybuffer:Arknights/NiuZhuanChun");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_NiuZhuanChun);
    }
}
