package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * YiTieZu（方舟中间产物）。
 */
public class ItemYiTieZu extends ItemEOHBBatch {

    public ItemYiTieZu() {
        setUnlocalizedName("YiTieZu");
        setTextureName("eyeofharmonybuffer:Arknights/YiTieZu");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_YiTieZu);
    }
}
