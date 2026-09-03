package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * YuanShiSuiPian（方舟中间产物）。
 */
public class ItemYuanShiSuiPian extends ItemEOHBBatch {

    public ItemYuanShiSuiPian() {
        setUnlocalizedName("YuanShiSuiPian");
        setTextureName("eyeofharmonybuffer:Arknights/YuanShiSuiPian");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_YuanShiSuiPian);
    }
}
