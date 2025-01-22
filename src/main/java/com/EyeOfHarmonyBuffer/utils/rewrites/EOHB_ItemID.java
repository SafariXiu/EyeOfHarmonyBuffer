package com.EyeOfHarmonyBuffer.utils.rewrites;

import gregtech.api.util.GTUtility;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class EOHB_ItemID extends GTUtility.ItemId{
    // region Member Variables
    private Item item;
    private int metaData;
    private NBTTagCompound nbt;
    // endregion

    // region Class Constructors
    public EOHB_ItemID(Item item, int metaData, NBTTagCompound nbt) {
        this.item = item;
        this.metaData = metaData;
        this.nbt = nbt;
    }

    public EOHB_ItemID(Item item, int metaData) {
        this.item = item;
        this.metaData = metaData;
    }

    public EOHB_ItemID(Item item) {
        this.item = item;
        this.metaData = 0;
    }

    public EOHB_ItemID() {}
    // endregion

    // region Static Methods
    public static final EOHB_ItemID NULL = new EOHB_ItemID();

    public static EOHB_ItemID create(ItemStack itemStack) {
        if (null == itemStack) return NULL;
        return new EOHB_ItemID(itemStack.getItem(), itemStack.getItemDamage(), itemStack.getTagCompound());
    }

    public static EOHB_ItemID createNoNBT(ItemStack itemStack) {
        if (null == itemStack) return NULL;
        return new EOHB_ItemID(itemStack.getItem(), itemStack.getItemDamage());
    }

    public static EOHB_ItemID createAsWildcard(ItemStack itemStack) {
        if (null == itemStack) return NULL;
        return new EOHB_ItemID(itemStack.getItem(), OreDictionary.WILDCARD_VALUE);
    }
    // endregion

    // region Special Methods
    public ItemStack getItemStack() {
        return new ItemStack(item, 1, metaData);
    }

    public ItemStack getItemStack(int amount) {
        return new ItemStack(item, amount, metaData);
    }

    public ItemStack getItemStackWithNBT() {
        ItemStack itemStack = new ItemStack(item, 1, metaData);
        itemStack.setTagCompound(nbt);
        return itemStack;
    }

    public ItemStack getItemStackWithNBT(int amount) {
        ItemStack itemStack = new ItemStack(item, amount, metaData);
        itemStack.setTagCompound(nbt);
        return itemStack;
    }

    // endregion

    // region General Methods
    public boolean isWildcard() {
        return this.metaData == OreDictionary.WILDCARD_VALUE;
    }

    public EOHB_ItemID setItem(Item item) {
        this.item = item;
        return this;
    }

    public EOHB_ItemID setMetaData(int metaData) {
        this.metaData = metaData;
        return this;
    }

    public EOHB_ItemID setNbt(NBTTagCompound nbt) {
        this.nbt = nbt;
        return this;
    }

    @Override
    protected Item item() {
        return item;
    }

    @Override
    protected int metaData() {
        return metaData;
    }

    @Nullable
    @Override
    protected NBTTagCompound nbt() {
        return nbt;
    }

    @Nullable
    @Override
    protected Integer stackSize() {
        // todo
        return null;
    }

    @Nullable
    protected NBTTagCompound getNBT() {
        return nbt;
    }

    protected Item getItem() {
        return item;
    }

    protected int getMetaData() {
        return metaData;
    }

    public boolean equalItemStack(ItemStack itemStack) {
        return this.equals(isWildcard() ? createAsWildcard(itemStack) : createNoNBT(itemStack));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EOHB_ItemID)) {
            return false;
        }
        EOHB_ItemID eohbItemID = (EOHB_ItemID) o;
        return metaData == eohbItemID.metaData && Objects.equals(item, eohbItemID.item)
            && Objects.equals(nbt, eohbItemID.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, metaData, nbt);
    }
}
