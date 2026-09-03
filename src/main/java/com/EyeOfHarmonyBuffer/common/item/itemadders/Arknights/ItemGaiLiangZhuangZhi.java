package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * GaiLiangZhuangZhi（方舟中间产物）。
 */
public class ItemGaiLiangZhuangZhi extends ItemEOHBBatch {

    public ItemGaiLiangZhuangZhi() {
        setUnlocalizedName("GaiLiangZhuangZhi");
        setTextureName("eyeofharmonybuffer:Arknights/GaiLiangZhuangZhi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_GaiLiangZhuangZhi);
    }
}
