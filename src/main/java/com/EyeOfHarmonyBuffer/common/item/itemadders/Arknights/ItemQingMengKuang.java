package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * QingMengKuang（方舟中间产物）。
 */
public class ItemQingMengKuang extends ItemEOHBBatch {

    public ItemQingMengKuang() {
        setUnlocalizedName("QingMengKuang");
        setTextureName("eyeofharmonybuffer:Arknights/QingMengKuang");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_QingMengKuang);
    }
}
