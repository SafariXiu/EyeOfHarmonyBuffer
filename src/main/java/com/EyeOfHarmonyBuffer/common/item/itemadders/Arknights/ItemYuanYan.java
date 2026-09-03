package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * YuanYan（方舟中间产物）。
 */
public class ItemYuanYan extends ItemEOHBBatch {

    public ItemYuanYan() {
        setUnlocalizedName("YuanYan");
        setTextureName("eyeofharmonybuffer:Arknights/YuanYan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_YuanYan);
    }
}
