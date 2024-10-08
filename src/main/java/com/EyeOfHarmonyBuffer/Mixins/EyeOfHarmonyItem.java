package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Method;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config;
import com.EyeOfHarmonyBuffer.ItemInfo;

import cpw.mods.fml.common.registry.GameRegistry;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyItem {

    @Inject(method = "outputAfterRecipe_EM", at = @At("TAIL"))
    private void injectCustomOutput(CallbackInfo ci) {
        System.out.println("injectCustomOutput method called.");

        try {

            for (ItemInfo itemInfo : Config.outputItems) {
                try {
                    Item item = GameRegistry.findItem(itemInfo.modid, itemInfo.itemName);
                    if (item != null) {
                        ItemStack itemStack = new ItemStack(item, itemInfo.quantity, itemInfo.meta);
                        Class<?> clazz = MTEEyeOfHarmony.class;
                        Method method = clazz.getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
                        method.setAccessible(true);
                        method.invoke(this, itemStack, (long) itemStack.stackSize);
                    } else {
                        System.err.println("Item not found: " + itemInfo.modid + ":" + itemInfo.itemName);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
