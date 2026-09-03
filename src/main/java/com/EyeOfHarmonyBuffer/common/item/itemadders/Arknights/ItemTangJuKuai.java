package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * TangJuKuai（方舟中间产物）。
 */
public class ItemTangJuKuai extends ItemEOHBBatch {

    public ItemTangJuKuai() {
        setUnlocalizedName("TangJuKuai");
        setTextureName("eyeofharmonybuffer:Arknights/TangJuKuai");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_TangJuKuai);
    }
}
