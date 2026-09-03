package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * TangZu（方舟中间产物）。
 */
public class ItemTangZu extends ItemEOHBBatch {

    public ItemTangZu() {
        setUnlocalizedName("TangZu");
        setTextureName("eyeofharmonybuffer:Arknights/TangZu");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_TangZu);
    }
}
