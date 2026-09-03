package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * YanMoShi（方舟中间产物）。
 */
public class ItemYanMoShi extends ItemEOHBBatch {

    public ItemYanMoShi() {
        setUnlocalizedName("YanMoShi");
        setTextureName("eyeofharmonybuffer:Arknights/YanMoShi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_YanMoShi);
    }
}
