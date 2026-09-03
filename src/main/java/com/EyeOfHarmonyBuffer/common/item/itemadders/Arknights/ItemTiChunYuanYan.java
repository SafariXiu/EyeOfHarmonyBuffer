package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * TiChunYuanYan（方舟中间产物）。
 */
public class ItemTiChunYuanYan extends ItemEOHBBatch {

    public ItemTiChunYuanYan() {
        setUnlocalizedName("TiChunYuanYan");
        setTextureName("eyeofharmonybuffer:Arknights/TiChunYuanYan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_TiChunYuanYan);
    }
}
