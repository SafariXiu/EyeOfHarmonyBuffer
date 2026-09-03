package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * WuShuiYanMoShi（方舟中间产物）。
 */
public class ItemWuShuiYanMoShi extends ItemEOHBBatch {

    public ItemWuShuiYanMoShi() {
        setUnlocalizedName("WuShuiYanMoShi");
        setTextureName("eyeofharmonybuffer:Arknights/WuShuiYanMoShi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_WuShuiYanMoShi);
    }
}
