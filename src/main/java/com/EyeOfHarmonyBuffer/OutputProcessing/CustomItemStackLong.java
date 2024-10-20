package com.EyeOfHarmonyBuffer.OutputProcessing;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.EyeOfHarmonyBuffer.utils.ItemInfo;

import cpw.mods.fml.common.registry.GameRegistry;

/**
 * CustomItemStackLong 类用于处理超过 int 数量限制的物品堆叠。
 * 它将 ItemStack 和 long 类型的数量封装在一起。
 */
public class CustomItemStackLong {

    // 原始的物品对象
    private final Item item;
    private final int itemMeta; // 物品的元数据（meta 值）
    private long quantity; // 物品的数量，使用 long 类型

    /**
     * 通过 ItemInfo 构造 CustomItemStackLong 对象
     *
     * @param itemInfo 包含物品信息的 ItemInfo 对象
     */
    public CustomItemStackLong(ItemInfo itemInfo) throws IllegalArgumentException {
        if (itemInfo.oreDictName != null) {
            // 处理通过矿物词典名称查找的物品
            List<ItemStack> ores = OreDictionary.getOres(itemInfo.oreDictName);
            if (ores == null || ores.isEmpty()) {
                throw new IllegalArgumentException("未找到矿物词典名称为 " + itemInfo.oreDictName + " 的物品。");
            }
            // 使用第一个找到的物品
            ItemStack oreStack = ores.get(0)
                .copy();
            this.item = oreStack.getItem();
            this.itemMeta = oreStack.getItemDamage();
        } else {
            // 处理通过 modid 和 itemName 查找的物品
            ItemStack itemStack = GameRegistry
                .findItemStack(itemInfo.modid, itemInfo.itemName, (int) itemInfo.quantity);
            if (itemStack == null) {
                throw new IllegalArgumentException("未找到物品：" + itemInfo.modid + ":" + itemInfo.itemName);
            }
            this.item = itemStack.getItem();
            this.itemMeta = itemInfo.meta;
        }
        // 设置数量
        this.quantity = itemInfo.quantity;
    }

    /**
     * 获取物品对象
     *
     * @return 物品对象
     */
    public Item getItem() {
        return item;
    }

    /**
     * 获取物品的数量
     *
     * @return 物品数量，long 类型
     */
    public long getQuantity() {
        return quantity;
    }

    /**
     * 设置物品的数量
     *
     * @param quantity 要设置的新数量
     */
    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    /**
     * 获取物品的元数据
     *
     * @return 物品的元数据（meta 值）
     */
    public int getItemMeta() {
        return itemMeta;
    }

    /**
     * 将 CustomItemStackLong 转换为标准的 ItemStack
     *
     * @return 转换后的 ItemStack 对象
     */
    public ItemStack toItemStack() {
        return new ItemStack(item, (int) Math.min(quantity, Integer.MAX_VALUE), itemMeta);
    }
}
