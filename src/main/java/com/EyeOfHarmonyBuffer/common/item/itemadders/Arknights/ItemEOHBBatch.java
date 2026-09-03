package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 中间产物物品基类：支持为物品挂多行 Tooltip。
 * 每个中间产物有独立子类（ItemEOHBBatch 的 {@code Item<Name>} 子类），
 * tooltip 在子类构造器中通过 {@link #setTooltipLines(String[])} 注入。
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
