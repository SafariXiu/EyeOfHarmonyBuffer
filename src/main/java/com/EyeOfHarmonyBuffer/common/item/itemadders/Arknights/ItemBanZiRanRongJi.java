package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * BanZiRanRongJi（方舟中间产物）。
 */
public class ItemBanZiRanRongJi extends ItemEOHBBatch {

    public ItemBanZiRanRongJi() {
        setUnlocalizedName("BanZiRanRongJi");
        setTextureName("eyeofharmonybuffer:Arknights/BanZiRanRongJi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_BanZiRanRongJi);
    }
}
