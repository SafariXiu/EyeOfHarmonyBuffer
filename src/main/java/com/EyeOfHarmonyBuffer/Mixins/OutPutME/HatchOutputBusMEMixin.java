package com.EyeOfHarmonyBuffer.Mixins.OutPutME;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.items.storage.ItemBasicStorageCell;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchOutputBusME;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MTEHatchOutputBusME.class, remap = false)
public abstract class HatchOutputBusMEMixin extends MTEHatchOutputBus implements IPowerChannelState {

    public HatchOutputBusMEMixin(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    @Shadow(remap = false)
    @Final
    IItemList<IAEItemStack> itemCache;

    @Shadow
    private long baseCapacity;

    @ModifyConstant(
        method = "<init>",
        constant = @Constant(longValue = 1_600L)
    )
    private static long modifyDefaultCapacity(long constant) {
        if(MainConfig.OutPutBusMEEnable){
            return Long.MAX_VALUE;
        }
        return constant;
    }

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 跳过检测单元的逻辑
     */
    @Inject(method = "getCacheCapacity", at = @At("HEAD"), cancellable = true)
    private void onGetCacheCapacity(CallbackInfoReturnable<Long> cir) {
        if(MainConfig.OutPutBusMEEnable) {
            cir.setReturnValue(Long.MAX_VALUE);
            return;
        }

        // 如果不是无限模式，保持原有逻辑
        ItemStack upgradeItemStack = mInventory[0];
        if (upgradeItemStack != null && upgradeItemStack.getItem() instanceof ItemBasicStorageCell) {
            cir.setReturnValue(((ItemBasicStorageCell) upgradeItemStack.getItem()).getBytesLong(upgradeItemStack) * 8);
        } else {
            cir.setReturnValue(baseCapacity);
        }
    }

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 覆写NBT读取与高亮显示
     */
    @Inject(method = "getInfoData", at = @At("HEAD"), cancellable = true)
    private void onGetInfoData(CallbackInfoReturnable<String[]> cir) {
        if(MainConfig.OutPutBusMEEnable) {
            List<String> ss = new ArrayList<>();
            ss.add(
                "The bus is " + ((getProxy() != null && getProxy().isActive()) ?
                    EnumChatFormatting.GREEN + "online" :
                    EnumChatFormatting.RED + "offline" + getAEDiagnostics()) +
                    EnumChatFormatting.RESET);
            ss.add("Item cache capacity: " + EnumChatFormatting.GOLD + "∞" + EnumChatFormatting.RESET);

            if (itemCache.isEmpty()) {
                ss.add("The bus has no cached items");
            } else {
                ss.add(String.format("The bus contains %d cached stacks: ", itemCache.size()));
                int counter = 0;
                for (IAEItemStack s : itemCache) {
                    ss.add(
                        s.getItem().getItemStackDisplayName(s.getItemStack()) + ": " +
                            EnumChatFormatting.GOLD +
                            GTUtility.formatNumbers(s.getStackSize()) +
                            EnumChatFormatting.RESET);
                    if (++counter > 100) break;
                }
            }
            cir.setReturnValue(ss.toArray(new String[itemCache.size() + 2]));
        }
    }
}
