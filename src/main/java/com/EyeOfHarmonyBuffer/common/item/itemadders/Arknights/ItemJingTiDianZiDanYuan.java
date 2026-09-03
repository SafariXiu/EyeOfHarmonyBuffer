package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JingTiDianZiDanYuan（方舟中间产物）。
 */
public class ItemJingTiDianZiDanYuan extends ItemEOHBBatch {

    public ItemJingTiDianZiDanYuan() {
        setUnlocalizedName("JingTiDianZiDanYuan");
        setTextureName("eyeofharmonybuffer:Arknights/JingTiDianZiDanYuan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JingTiDianZiDanYuan);
    }
}
