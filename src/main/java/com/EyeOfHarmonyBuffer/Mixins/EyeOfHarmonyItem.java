package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Method;

import com.EyeOfHarmonyBuffer.Config;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyItem {

    @Inject(method = "outputAfterRecipe_EM", at = @At("TAIL"))
    private void injectCustomOutput(CallbackInfo ci) {
        System.out.println("Inject method called!");
        int outputQuantity = Config.outputItem;
        Item eldritchPearl = Item.getItemById(4445);
        if (eldritchPearl != null) {
            ItemStack eldritchPearlStack = new ItemStack(eldritchPearl, outputQuantity, 3);

            try {
                Class<?> clazz = MTEEyeOfHarmony.class;
                Method method = clazz.getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
                method.setAccessible(true);
                method.invoke(this, eldritchPearlStack, (long) eldritchPearlStack.stackSize);

                System.out.println("Injected Eldritch Pearl (元始珍珠) into the AE network.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
