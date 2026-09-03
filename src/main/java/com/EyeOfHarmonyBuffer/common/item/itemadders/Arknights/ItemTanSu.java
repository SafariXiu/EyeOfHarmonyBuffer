package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * TanSu（方舟中间产物）。
 */
public class ItemTanSu extends ItemEOHBBatch {

    public ItemTanSu() {
        setUnlocalizedName("TanSu");
        setTextureName("eyeofharmonybuffer:Arknights/TanSu");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_TanSu);
    }
}
