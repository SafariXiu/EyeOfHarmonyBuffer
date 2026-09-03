package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * PoSunZhuangZhi（方舟中间产物）。
 */
public class ItemPoSunZhuangZhi extends ItemEOHBBatch {

    public ItemPoSunZhuangZhi() {
        setUnlocalizedName("PoSunZhuangZhi");
        setTextureName("eyeofharmonybuffer:Arknights/PoSunZhuangZhi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_PoSunZhuangZhi);
    }
}
