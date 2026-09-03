package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ShouXingQuGuangTi（方舟中间产物）。
 */
public class ItemShouXingQuGuangTi extends ItemEOHBBatch {

    public ItemShouXingQuGuangTi() {
        setUnlocalizedName("ShouXingQuGuangTi");
        setTextureName("eyeofharmonybuffer:Arknights/ShouXingQuGuangTi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ShouXingQuGuangTi);
    }
}
