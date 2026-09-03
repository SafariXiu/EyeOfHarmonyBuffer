package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * GaoJiJiaGuJianCai（方舟中间产物）。
 */
public class ItemGaoJiJiaGuJianCai extends ItemEOHBBatch {

    public ItemGaoJiJiaGuJianCai() {
        setUnlocalizedName("GaoJiJiaGuJianCai");
        setTextureName("eyeofharmonybuffer:Arknights/GaoJiJiaGuJianCai");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_GaoJiJiaGuJianCai);
    }
}
