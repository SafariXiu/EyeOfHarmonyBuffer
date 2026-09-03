package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JingTiYuanJian（方舟中间产物）。
 */
public class ItemJingTiYuanJian extends ItemEOHBBatch {

    public ItemJingTiYuanJian() {
        setUnlocalizedName("JingTiYuanJian");
        setTextureName("eyeofharmonybuffer:Arknights/JingTiYuanJian");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JingTiYuanJian);
    }
}
