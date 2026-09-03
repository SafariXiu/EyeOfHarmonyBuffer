package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JinJieJiaGuJianCai（方舟中间产物）。
 */
public class ItemJinJieJiaGuJianCai extends ItemEOHBBatch {

    public ItemJinJieJiaGuJianCai() {
        setUnlocalizedName("JinJieJiaGuJianCai");
        setTextureName("eyeofharmonybuffer:Arknights/JinJieJiaGuJianCai");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JinJieJiaGuJianCai);
    }
}
