package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * TanSuZu（方舟中间产物）。
 */
public class ItemTanSuZu extends ItemEOHBBatch {

    public ItemTanSuZu() {
        setUnlocalizedName("TanSuZu");
        setTextureName("eyeofharmonybuffer:Arknights/TanSuZu");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_TanSuZu);
    }
}
