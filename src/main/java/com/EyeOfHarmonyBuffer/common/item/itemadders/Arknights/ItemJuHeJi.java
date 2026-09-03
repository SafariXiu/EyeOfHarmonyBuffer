package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * JuHeJi（方舟中间产物）。
 */
public class ItemJuHeJi extends ItemEOHBBatch {

    public ItemJuHeJi() {
        setUnlocalizedName("JuHeJi");
        setTextureName("eyeofharmonybuffer:Arknights/JuHeJi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_JuHeJi);
    }
}
