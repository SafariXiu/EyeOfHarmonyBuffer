package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * ZhongXiangWeiDuiYingTi（方舟中间产物）。
 */
public class ItemZhongXiangWeiDuiYingTi extends ItemEOHBBatch {

    public ItemZhongXiangWeiDuiYingTi() {
        setUnlocalizedName("ZhongXiangWeiDuiYingTi");
        setTextureName("eyeofharmonybuffer:Arknights/ZhongXiangWeiDuiYingTi");
        setCreativeTab(tabMetaItem01);
        setTooltipLines(TextLocalization.EOHB_ArknightsItem_ZhongXiangWeiDuiYingTi);
    }
}
