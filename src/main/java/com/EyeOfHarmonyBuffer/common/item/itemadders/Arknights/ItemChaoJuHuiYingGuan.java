package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ChaoJuHuiYingGuan（方舟中间产物）。
 */
public class ItemChaoJuHuiYingGuan extends ItemEOHBBatch {

    public ItemChaoJuHuiYingGuan() {
        setUnlocalizedName("ChaoJuHuiYingGuan");
        setTextureName("eyeofharmonybuffer:Arknights/ChaoJuHuiYingGuan");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ChaoJuHuiYingGuan);
    }
}
