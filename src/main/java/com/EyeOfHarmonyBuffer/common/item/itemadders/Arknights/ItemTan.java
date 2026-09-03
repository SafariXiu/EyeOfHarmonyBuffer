package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * Tan（方舟中间产物）。
 */
public class ItemTan extends ItemEOHBBatch {

    public ItemTan() {
        setUnlocalizedName("Tan");
        setTextureName("eyeofharmonybuffer:Arknights/Tan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_Tan);
    }
}
