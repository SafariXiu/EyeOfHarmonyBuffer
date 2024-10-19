package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config;
import com.EyeOfHarmonyBuffer.info.ItemInfo;
import com.EyeOfHarmonyBuffer.OutputProcessing.CustomItemStackLong;

import cpw.mods.fml.common.registry.GameRegistry;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyItem {

    @Inject(method = "outputAfterRecipe_EM", at = @At("TAIL"))
    private void injectCustomOutput(CallbackInfo ci) {
        System.out.println("injectCustomOutput 方法被调用。");

        try {
            if (!Config.EOHItemInPut) {
                System.out.println("额外产出已禁用");
                return;
            }

            if (Config.outputItems == null || Config.outputItems.isEmpty()) {
                System.err.println("错误：Config.outputItems 为空。");
                return;
            }

            System.out.println("要输出的物品数量：" + Config.outputItems.size());

            for (ItemInfo itemInfo : Config.outputItems) {
                try {
                    String itemIdentifier = (itemInfo.oreDictName != null) ? "oreDict:" + itemInfo.oreDictName
                        : itemInfo.modid + ":" + itemInfo.itemName;
                    System.out.println("处理物品：" + itemIdentifier);

                    CustomItemStackLong customItemStack = null;

                    if (itemInfo.oreDictName != null) {
                        List<ItemStack> ores = OreDictionary.getOres(itemInfo.oreDictName);
                        if (ores == null || ores.isEmpty()) {
                            System.err.println("未找到矿物词典名称为 " + itemInfo.oreDictName + " 的物品。");
                            continue;
                        }

                        ItemStack oreStack = ores.get(0)
                            .copy();
                        customItemStack = new CustomItemStackLong(
                            oreStack.getItem(),
                            itemInfo.quantity,
                            oreStack.getItemDamage());
                        System.out.println("通过矿物词典名称 " + itemInfo.oreDictName + " 获取到物品 " + oreStack.getDisplayName());
                    } else {
                        ItemStack itemStack = GameRegistry
                            .findItemStack(itemInfo.modid, itemInfo.itemName, (int) itemInfo.quantity);
                        if (itemStack == null) {
                            System.err.println("未找到物品：" + itemIdentifier);
                            continue;
                        }
                        customItemStack = new CustomItemStackLong(
                            itemStack.getItem(),
                            itemInfo.quantity,
                            itemInfo.meta);
                    }

                    if (customItemStack != null) {
                        // 使用长整型数量直接输出
                        outputLongToAENetwork(customItemStack);
                    }
                } catch (Exception e) {
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
            Class<?> clazz = MTEEyeOfHarmony.class;
            // 假设 outputItemToAENetwork 方法可以处理 ItemStack 和 long 类型的数量
            Method method = clazz.getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
            method.setAccessible(true);

            ItemStack itemStack = new ItemStack(
                customStack.getItem(),
                (int) Math.min(customStack.getQuantity(), Integer.MAX_VALUE),
                customStack.getItemMeta());

            // 调用方法，传递物品堆栈和数量
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
