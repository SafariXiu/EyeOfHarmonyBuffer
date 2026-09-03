package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JiChuJiaGuJianCai（方舟中间产物）。
 */
public class ItemJiChuJiaGuJianCai extends ItemEOHBBatch {

    public ItemJiChuJiaGuJianCai() {
        setUnlocalizedName("JiChuJiaGuJianCai");
        setTextureName("eyeofharmonybuffer:Arknights/JiChuJiaGuJianCai");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JiChuJiaGuJianCai);
    }
}
