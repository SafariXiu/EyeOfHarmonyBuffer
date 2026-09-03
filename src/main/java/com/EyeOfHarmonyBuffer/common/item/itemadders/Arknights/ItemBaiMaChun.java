package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * BaiMaChun（方舟中间产物）。
 */
public class ItemBaiMaChun extends ItemEOHBBatch {

    public ItemBaiMaChun() {
        setUnlocalizedName("BaiMaChun");
        setTextureName("eyeofharmonybuffer:Arknights/BaiMaChun");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_BaiMaChun);
    }
}
