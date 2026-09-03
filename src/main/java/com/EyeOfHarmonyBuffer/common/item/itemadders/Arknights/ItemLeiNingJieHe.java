package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * LeiNingJieHe（方舟中间产物）。
 */
public class ItemLeiNingJieHe extends ItemEOHBBatch {

    public ItemLeiNingJieHe() {
        setUnlocalizedName("LeiNingJieHe");
        setTextureName("eyeofharmonybuffer:Arknights/LeiNingJieHe");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_LeiNingJieHe);
    }
}
