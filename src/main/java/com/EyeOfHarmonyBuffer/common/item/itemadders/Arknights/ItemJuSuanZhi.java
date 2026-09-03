package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JuSuanZhi（方舟中间产物）。
 */
public class ItemJuSuanZhi extends ItemEOHBBatch {

    public ItemJuSuanZhi() {
        setUnlocalizedName("JuSuanZhi");
        setTextureName("eyeofharmonybuffer:Arknights/JuSuanZhi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JuSuanZhi);
    }
}
