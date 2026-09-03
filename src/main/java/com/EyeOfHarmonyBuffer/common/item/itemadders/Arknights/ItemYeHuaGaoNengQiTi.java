package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * YeHuaGaoNengQiTi（方舟中间产物）。
 */
public class ItemYeHuaGaoNengQiTi extends ItemEOHBBatch {

    public ItemYeHuaGaoNengQiTi() {
        setUnlocalizedName("YeHuaGaoNengQiTi");
        setTextureName("eyeofharmonybuffer:Arknights/YeHuaGaoNengQiTi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_YeHuaGaoNengQiTi);
    }
}
