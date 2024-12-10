package com.EyeOfHarmonyBuffer.Mixins.EOH;

import java.lang.reflect.Method;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config.ItemConfig;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.OutputProcessing.CustomItemStackLong;
import com.EyeOfHarmonyBuffer.utils.ItemInfo;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyItemMixin {

    @Inject(method = "outputAfterRecipe_EM", at = @At("TAIL"))
    private void injectCustomOutput(CallbackInfo ci) {

        try {
            if (!MainConfig.EOHItemInPut) {
                return;
            }

            if (ItemConfig.outputItems == null || ItemConfig.outputItems.isEmpty()) {
                return;
            }

            for (ItemInfo itemInfo : ItemConfig.outputItems) {
                try {
                    CustomItemStackLong customItemStack = new CustomItemStackLong(itemInfo);
                    if (customItemStack != null) {
                        outputLongToAENetwork(customItemStack);
                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("injectCustomOutput 方法中发生异常：");
            e.printStackTrace();
        }

    }

    /**
     * 自定义方法，用于将带有 long 类型数量的物品注入到 AE 网络中。
     * 这里假设 outputItemToAENetwork 方法可以接收 long 类型的物品数量。
     *
     * @param customStack 自定义的 CustomItemStackLong 对象
     */
    private void outputLongToAENetwork(CustomItemStackLong customStack) {
        try {
            Class<?> clazz = MTEEyeOfHarmony.class;
            Method method = clazz.getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
            method.setAccessible(true);

            ItemStack itemStack = customStack.toItemStack();

            method.invoke(this, itemStack, customStack.getQuantity());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
