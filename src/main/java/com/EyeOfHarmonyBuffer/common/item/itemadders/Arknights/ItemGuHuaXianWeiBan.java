package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * GuHuaXianWeiBan（方舟中间产物）。
 */
public class ItemGuHuaXianWeiBan extends ItemEOHBBatch {

    public ItemGuHuaXianWeiBan() {
        setUnlocalizedName("GuHuaXianWeiBan");
        setTextureName("eyeofharmonybuffer:Arknights/GuHuaXianWeiBan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_GuHuaXianWeiBan);
    }
}
