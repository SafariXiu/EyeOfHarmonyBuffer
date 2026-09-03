package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * NingJiao（方舟中间产物）。
 */
public class ItemNingJiao extends ItemEOHBBatch {

    public ItemNingJiao() {
        setUnlocalizedName("NingJiao");
        setTextureName("eyeofharmonybuffer:Arknights/NingJiao");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_NingJiao);
    }
}
