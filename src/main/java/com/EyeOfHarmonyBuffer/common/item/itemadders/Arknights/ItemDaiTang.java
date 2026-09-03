package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * DaiTang（方舟中间产物）。
 */
public class ItemDaiTang extends ItemEOHBBatch {

    public ItemDaiTang() {
        setUnlocalizedName("DaiTang");
        setTextureName("eyeofharmonybuffer:Arknights/DaiTang");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_DaiTang);
    }
}
