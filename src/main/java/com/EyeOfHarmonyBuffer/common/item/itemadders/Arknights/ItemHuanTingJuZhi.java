package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * HuanTingJuZhi（方舟中间产物）。
 */
public class ItemHuanTingJuZhi extends ItemEOHBBatch {

    public ItemHuanTingJuZhi() {
        setUnlocalizedName("HuanTingJuZhi");
        setTextureName("eyeofharmonybuffer:Arknights/HuanTingJuZhi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_HuanTingJuZhi);
    }
}
