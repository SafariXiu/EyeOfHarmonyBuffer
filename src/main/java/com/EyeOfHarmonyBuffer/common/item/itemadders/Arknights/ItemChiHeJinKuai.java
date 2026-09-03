package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ChiHeJinKuai（方舟中间产物）。
 */
public class ItemChiHeJinKuai extends ItemEOHBBatch {

    public ItemChiHeJinKuai() {
        setUnlocalizedName("ChiHeJinKuai");
        setTextureName("eyeofharmonybuffer:Arknights/ChiHeJinKuai");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ChiHeJinKuai);
    }
}
