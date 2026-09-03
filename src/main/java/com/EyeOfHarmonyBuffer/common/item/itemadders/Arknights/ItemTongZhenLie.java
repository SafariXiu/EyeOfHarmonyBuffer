package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * TongZhenLie（方舟中间产物）。
 */
public class ItemTongZhenLie extends ItemEOHBBatch {

    public ItemTongZhenLie() {
        setUnlocalizedName("TongZhenLie");
        setTextureName("eyeofharmonybuffer:Arknights/TongZhenLie");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_TongZhenLie);
    }
}
