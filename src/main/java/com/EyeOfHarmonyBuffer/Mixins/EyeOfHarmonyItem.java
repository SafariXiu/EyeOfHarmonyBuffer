package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

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

                    ItemStack itemStack = null;

                    if (itemInfo.oreDictName != null) {
                        List<ItemStack> ores = OreDictionary.getOres(itemInfo.oreDictName);
                        if (ores == null || ores.isEmpty()) {
                            System.err.println("未找到矿物词典名称为 " + itemInfo.oreDictName + " 的物品。");
                            continue;
                        }
                        itemStack = ores.get(0)
                            .copy();
                        itemStack.stackSize = itemInfo.quantity;
                        System.out.println("通过矿物词典名称 " + itemInfo.oreDictName + " 获取到物品 " + itemStack.getDisplayName());
                    } else {
                        Item item = GameRegistry.findItem(itemInfo.modid, itemInfo.itemName);
                        if (item == null) {
                            System.err.println("未找到物品：" + itemIdentifier);
                            continue;
                        }
                        itemStack = new ItemStack(item, itemInfo.quantity, itemInfo.meta);
                    }

                    if (itemStack != null) {
                        Class<?> clazz = MTEEyeOfHarmony.class;
                        Method method = clazz.getDeclaredMethod("outputItemToAENetwork", ItemStack.class, long.class);
                        method.setAccessible(true);

                        method.invoke(this, itemStack, (long) itemStack.stackSize);
                        System.out
                            .println("成功注入 " + itemStack.stackSize + " 个 " + itemStack.getDisplayName() + " 到 AE 网络中。");
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
}
