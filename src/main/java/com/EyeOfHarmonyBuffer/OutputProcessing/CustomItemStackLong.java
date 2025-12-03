package com.EyeOfHarmonyBuffer.OutputProcessing;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import com.EyeOfHarmonyBuffer.utils.ItemInfo;

import cpw.mods.fml.common.registry.GameRegistry;

public class CustomItemStackLong {

    private final Item item;
    private final int itemMeta;
    private final NBTTagCompound nbt;
    private long quantity;


    public CustomItemStackLong(ItemInfo itemInfo) throws IllegalArgumentException {
        if (itemInfo.oreDictName != null) {
            List<ItemStack> ores = OreDictionary.getOres(itemInfo.oreDictName);
            if (ores == null || ores.isEmpty()) {
                throw new IllegalArgumentException("未找到矿物词典名称为 " + itemInfo.oreDictName + " 的物品。");
            }
            ItemStack oreStack = ores.get(0).copy();
            this.item = oreStack.getItem();
            this.itemMeta = oreStack.getItemDamage();
            this.nbt = null;
        } else {
            Item item = GameRegistry.findItem(itemInfo.modid, itemInfo.itemName);
            if (item == null) {
                throw new IllegalArgumentException("未找到物品：" + itemInfo.modid + ":" + itemInfo.itemName);
            }
            this.item = item;
            this.itemMeta = itemInfo.meta;

            this.nbt = (itemInfo.nbt == null)
                ? null
                : (NBTTagCompound) itemInfo.nbt.copy();
        }

        this.quantity = itemInfo.quantity;
    }

    public Item getItem() {
        return item;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public int getItemMeta() {
        return itemMeta;
    }

    public ItemStack toItemStack() {
        if (item == null) {
            return null;
        }

        int count = (int) Math.min(quantity, Integer.MAX_VALUE);
        ItemStack stack = new ItemStack(item, count, itemMeta);

        if (nbt != null) {
            stack.setTagCompound((NBTTagCompound) nbt.copy());
        }

        return stack;
    }
}
