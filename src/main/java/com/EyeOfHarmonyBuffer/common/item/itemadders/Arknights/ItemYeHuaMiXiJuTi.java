package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * YeHuaMiXiJuTi（方舟中间产物）。
 */
public class ItemYeHuaMiXiJuTi extends ItemEOHBBatch {

    public ItemYeHuaMiXiJuTi() {
        setUnlocalizedName("YeHuaMiXiJuTi");
        setTextureName("eyeofharmonybuffer:Arknights/YeHuaMiXiJuTi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_YeHuaMiXiJuTi);
    }
}
