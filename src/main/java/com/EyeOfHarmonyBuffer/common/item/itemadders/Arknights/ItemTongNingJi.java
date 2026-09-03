package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * TongNingJi（方舟中间产物）。
 */
public class ItemTongNingJi extends ItemEOHBBatch {

    public ItemTongNingJi() {
        setUnlocalizedName("TongNingJi");
        setTextureName("eyeofharmonybuffer:Arknights/TongNingJi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_TongNingJi);
    }
}
