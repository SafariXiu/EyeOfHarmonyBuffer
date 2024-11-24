package com.EyeOfHarmonyBuffer.Mixins.EOH;

import java.lang.reflect.Method;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config.ItemConfig;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.OutputProcessing.CustomItemStackLong;
import com.EyeOfHarmonyBuffer.utils.ItemInfo;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyItemMixin {

    @Inject(method = "outputAfterRecipe_EM", at = @At("TAIL"))
    private void injectCustomOutput(CallbackInfo ci) {
        System.out.println("injectCustomOutput 方法被调用。");

        try {
            // 检查是否启用额外产出
            if (!MainConfig.EOHItemInPut) {
                System.out.println("额外产出已禁用");
                return;
            }

            // 检查配置的输出物品列表是否为空
            if (ItemConfig.outputItems == null || ItemConfig.outputItems.isEmpty()) {
                System.err.println("错误：Config.outputItems 为空。");
                return;
            }

            System.out.println("要输出的物品数量：" + ItemConfig.outputItems.size());

            // 遍历配置中的每个物品
            for (ItemInfo itemInfo : ItemConfig.outputItems) {
                try {
                    // 处理物品，并使用 CustomItemStackLong 包装物品
                    CustomItemStackLong customItemStack = new CustomItemStackLong(itemInfo);

                    String itemIdentifier = (itemInfo.oreDictName != null) ? "oreDict:" + itemInfo.oreDictName
                        : itemInfo.modid + ":" + itemInfo.itemName;

                    System.out.println("处理物品：" + itemIdentifier);

                    // 如果成功创建了 CustomItemStackLong 对象，输出到 AE 网络
                    if (customItemStack != null) {
                        outputLongToAENetwork(customItemStack);
                    }
                } catch (IllegalArgumentException e) {
                    // 捕获异常并输出错误信息
                    String itemIdentifier = (itemInfo.oreDictName != null) ? "oreDict:" + itemInfo.oreDictName
                        : itemInfo.modid + ":" + itemInfo.itemName;
                    System.err.println("处理物品时发生异常：" + itemIdentifier);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("injectCustomOutput 方法中发生异常：");
            e.printStackTrace();
        }

        System.out.println("injectCustomOutput 方法结束。");
    }

    /**
     * 自定义方法，用于将带有 long 类型数量的物品注入到 AE 网络中。
     * 这里假设 outputItemToAENetwork 方法可以接收 long 类型的物品数量。
     *
     * @param customStack 自定义的 CustomItemStackLong 对象
     */
    private void outputLongToAENetwork(CustomItemStackLong customStack) {
        try {
            Class<?> clazz = GT_MetaTileEntity_EM_EyeOfHarmony.class;
            // 假设 outputItemToAENetwork 方法可以处理 ItemStack 和 long 类型的数量
            Method method = clazz.getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
            method.setAccessible(true);

            // 将 CustomItemStackLong 转换为标准的 ItemStack
            ItemStack itemStack = customStack.toItemStack();

            // 调用 outputItemToAENetwork 方法，传递物品堆栈和数量
            method.invoke(this, itemStack, customStack.getQuantity());

            System.out.println(
                "成功注入 " + customStack.getQuantity()
                    + " 个 "
                    + customStack.getItem()
                        .getUnlocalizedName()
                    + " 到 AE 网络中。");
        } catch (Exception e) {
            System.err.println("输出物品时发生异常：");
            e.printStackTrace();
        }
    }
}
