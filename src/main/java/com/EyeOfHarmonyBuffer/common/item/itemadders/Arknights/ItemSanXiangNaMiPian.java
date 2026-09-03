package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * SanXiangNaMiPian（方舟中间产物）。
 */
public class ItemSanXiangNaMiPian extends ItemEOHBBatch {

    public ItemSanXiangNaMiPian() {
        setUnlocalizedName("SanXiangNaMiPian");
        setTextureName("eyeofharmonybuffer:Arknights/SanXiangNaMiPian");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_SanXiangNaMiPian);
    }
}
