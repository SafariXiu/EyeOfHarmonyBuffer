package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ZhiYuanLiao（方舟中间产物）。
 */
public class ItemZhiYuanLiao extends ItemEOHBBatch {

    public ItemZhiYuanLiao() {
        setUnlocalizedName("ZhiYuanLiao");
        setTextureName("eyeofharmonybuffer:Arknights/ZhiYuanLiao");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ZhiYuanLiao);
    }
}
