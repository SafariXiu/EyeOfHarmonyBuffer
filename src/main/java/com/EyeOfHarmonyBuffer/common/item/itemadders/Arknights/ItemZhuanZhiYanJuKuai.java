package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ZhuanZhiYanJuKuai（方舟中间产物）。
 */
public class ItemZhuanZhiYanJuKuai extends ItemEOHBBatch {

    public ItemZhuanZhiYanJuKuai() {
        setUnlocalizedName("ZhuanZhiYanJuKuai");
        setTextureName("eyeofharmonybuffer:Arknights/ZhuanZhiYanJuKuai");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ZhuanZhiYanJuKuai);
    }
}
