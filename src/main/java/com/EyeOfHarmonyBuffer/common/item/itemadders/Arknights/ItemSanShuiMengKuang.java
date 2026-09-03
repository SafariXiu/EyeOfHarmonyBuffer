package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * SanShuiMengKuang（方舟中间产物）。
 */
public class ItemSanShuiMengKuang extends ItemEOHBBatch {

    public ItemSanShuiMengKuang() {
        setUnlocalizedName("SanShuiMengKuang");
        setTextureName("eyeofharmonybuffer:Arknights/SanShuiMengKuang");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_SanShuiMengKuang);
    }
}
