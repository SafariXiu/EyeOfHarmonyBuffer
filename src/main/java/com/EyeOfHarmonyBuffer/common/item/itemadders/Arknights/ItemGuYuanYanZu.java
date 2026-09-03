package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * GuYuanYanZu（方舟中间产物）。
 */
public class ItemGuYuanYanZu extends ItemEOHBBatch {

    public ItemGuYuanYanZu() {
        setUnlocalizedName("GuYuanYanZu");
        setTextureName("eyeofharmonybuffer:Arknights/GuYuanYanZu");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_GuYuanYanZu);
    }
}
