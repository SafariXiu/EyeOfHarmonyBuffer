package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemTang extends ItemEOHBBatch {

    public ItemTang() {
        setUnlocalizedName("Tang");
        setTextureName("eyeofharmonybuffer:Arknights/Tang");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_Tang);
    }
}
