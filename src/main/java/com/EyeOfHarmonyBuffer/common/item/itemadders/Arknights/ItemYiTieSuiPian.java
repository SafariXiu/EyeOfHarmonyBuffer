package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * YiTieSuiPian（方舟中间产物）。
 */
public class ItemYiTieSuiPian extends ItemEOHBBatch {

    public ItemYiTieSuiPian() {
        setUnlocalizedName("YiTieSuiPian");
        setTextureName("eyeofharmonybuffer:Arknights/YiTieSuiPian");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_YiTieSuiPian);
    }
}
