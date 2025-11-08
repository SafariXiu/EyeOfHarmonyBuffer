package com.EyeOfHarmonyBuffer.Mixins.GT;

import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Contract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = GTUtility.class, remap = false)
public abstract class GTUtilityMixin {

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 修改堆叠上限
     */
    @Contract("_, null -> null")
    @Overwrite
    public static ItemStack copyAmount(int aAmount, ItemStack aStack) {
        ItemStack rStack = gregtech.api.util.GTUtility.copy(aStack);
        if (gregtech.api.util.GTUtility.isStackInvalid(rStack)) return null;

        if (aAmount < 0) aAmount = 0;
        if (aAmount > Integer.MAX_VALUE - 1) aAmount = Integer.MAX_VALUE - 1;

        rStack.stackSize = aAmount;
        return rStack;
    }
}
