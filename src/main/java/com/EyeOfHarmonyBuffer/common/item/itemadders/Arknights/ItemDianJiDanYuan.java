package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * DianJiDanYuan（方舟中间产物）。
 */
public class ItemDianJiDanYuan extends ItemEOHBBatch {

    public ItemDianJiDanYuan() {
        setUnlocalizedName("DianJiDanYuan");
        setTextureName("eyeofharmonybuffer:Arknights/DianJiDanYuan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_DianJiDanYuan);
    }
}
