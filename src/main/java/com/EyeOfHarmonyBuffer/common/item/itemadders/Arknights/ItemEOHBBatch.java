package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 批量注册物品的基础类：支持为物品挂多行 Tooltip。
 * 由 ItemIntermediateProducts 反射创建并注入 Tooltip 行。
 */
public class ItemEOHBBatch extends Item {

    private String[] tooltipLines;

    public void setTooltipLines(String[] tooltipLines) {
        this.tooltipLines = tooltipLines;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        super.addInformation(stack, player, list, advanced);
        if (tooltipLines != null) {
            for (String line : tooltipLines) {
                list.add(line);
            }
        }
    }
}
