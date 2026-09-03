package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ChiJin（方舟中间产物）。
 */
public class ItemChiJin extends ItemEOHBBatch {

    public ItemChiJin() {
        setUnlocalizedName("ChiJin");
        setTextureName("eyeofharmonybuffer:Arknights/ChiJin");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ChiJin);
    }
}
