package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JingLianRongJi（方舟中间产物）。
 */
public class ItemJingLianRongJi extends ItemEOHBBatch {

    public ItemJingLianRongJi() {
        setUnlocalizedName("JingLianRongJi");
        setTextureName("eyeofharmonybuffer:Arknights/JingLianRongJi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JingLianRongJi);
    }
}
