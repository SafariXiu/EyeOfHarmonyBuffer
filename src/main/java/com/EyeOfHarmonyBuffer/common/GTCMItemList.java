package com.EyeOfHarmonyBuffer.common;

import com.EyeOfHarmonyBuffer.utils.Utils;
import gregtech.api.util.GTLog;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;

public enum GTCMItemList {

    TestItem0,

    //机器主方块
    VendingMachines,
    WindTurbines,
    SolarEnergyArray,
    SubstanceReshapingDevice,
    BlueDogMachines,
    MonkeyShitS,

    //物品
    ChengDuHeart,

    //机器结构方块
    SingularityStabilizationRingCasingsLV,
    SingularityStabilizationRingCasingsMV,
    SingularityStabilizationRingCasingsHV,
    SingularityStabilizationRingCasingsEV,
    SingularityStabilizationRingCasingsIV,
    SingularityStabilizationRingCasingsLuV,
    SingularityStabilizationRingCasingsZPM,
    SingularityStabilizationRingCasingsUV,
    SingularityStabilizationRingCasingsUHV,
    SingularityStabilizationRingCasingsUEV,
    SingularityStabilizationRingCasingsUIV,
    SingularityStabilizationRingCasingsUMV,
    SingularityStabilizationRingCasingsUXV,
    SingularityStabilizationRingCasingsMAX;


    private boolean mHasNotBeenSet;
    private boolean mDeprecated;
    private boolean mWarned;
    private ItemStack mStack;

    GTCMItemList() {
        mHasNotBeenSet = true;
    }

    GTCMItemList(boolean aDeprecated) {
        if (aDeprecated) {
            mDeprecated = true;
            mHasNotBeenSet = true;
        }
    }

    public GTCMItemList set(Item aItem) {
        mHasNotBeenSet = false;
        if (aItem == null) return this;
        ItemStack aStack = new ItemStack(aItem, 1, 0);
        mStack = Utils.copyAmount(1, aStack);
        return this;
    }

    public GTCMItemList set(ItemStack aStack) {
        if (aStack != null) {
            mHasNotBeenSet = false;
            mStack = Utils.copyAmount(1, aStack);
        }
        return this;
    }

    public ItemStack get(int aAmount, Object... aReplacements) {
        sanityCheck();
        if (Utils.isStackInvalid(mStack)) {
            GTLog.out.println("Object in the ItemList is null at:");
            new NullPointerException().printStackTrace(GTLog.out);
            return Utils.copyAmount(aAmount, TestItem0.get(1));
        }
        return Utils.copyAmount(aAmount, mStack);
    }

    public Item getItem() {
        sanityCheck();
        if (Utils.isStackInvalid(mStack)) return null;
        return mStack.getItem();
    }

    public Block getBlock() {
        sanityCheck();
        return Block.getBlockFromItem(getItem());
    }

    private void sanityCheck() {
        if (mHasNotBeenSet)
            throw new IllegalAccessError("The Enum '" + name() + "' has not been set to an Item at this time!");
        if (mDeprecated && !mWarned) {
            new Exception(this + " is now deprecated").printStackTrace(GTLog.err);
            mWarned = true;
        }
    }

    public int getMeta() {
        return mStack.getItemDamage();
    }

    public boolean hasBeenSet() {
        return !mHasNotBeenSet;
    }

    public ItemStack getInternalStack_unsafe() {
        return mStack;
    }
}
