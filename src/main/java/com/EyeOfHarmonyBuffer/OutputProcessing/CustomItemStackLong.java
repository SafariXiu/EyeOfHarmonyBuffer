package com.EyeOfHarmonyBuffer.OutputProcessing;

import net.minecraft.item.Item;

/**
 * CustomItemStackLong 类用于处理超过 int 数量限制的物品堆叠。
 * 它将 ItemStack 和 long 类型的数量封装在一起。
 */
public class CustomItemStackLong {

    // 原始的物品对象（不是 ItemStack）
    private final Item item;
    private final int itemMeta; // 物品的元数据（meta 值）
    private long quantity; // 物品的数量，使用 long 类型

    /**
     * 构造函数
     *
     * @param item     要包装的物品对象
     * @param quantity 物品数量，使用 long 类型
     * @param itemMeta 物品的元数据
     */
    public CustomItemStackLong(Item item, long quantity, int itemMeta) {
        this.item = item;
        this.quantity = quantity;
        this.itemMeta = itemMeta;
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
}
