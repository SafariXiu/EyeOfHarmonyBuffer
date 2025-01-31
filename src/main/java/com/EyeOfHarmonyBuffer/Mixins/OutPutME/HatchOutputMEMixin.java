package com.EyeOfHarmonyBuffer.Mixins.OutPutME;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.MTEHatchOutputME;
import net.minecraft.util.EnumChatFormatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MTEHatchOutputME.class, remap = false)
public abstract class HatchOutputMEMixin extends MTEHatchOutput implements IPowerChannelState {

    public HatchOutputMEMixin(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    @Shadow(remap = false)
    @Final
    IItemList<IAEFluidStack> fluidCache;

    @Shadow
    private long baseCapacity;

    @ModifyConstant(
        method = "<init>",
        constant = @Constant(longValue = 128_000L)
    )
    private static long modifyDefaultCapacity(long constant) {
        if(MainConfig.OutPutHatchMEEnable){
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
        if(MainConfig.OutPutHatchMEEnable) {
            cir.setReturnValue(Long.MAX_VALUE);
            cir.cancel();
        }
    }

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 覆写NBT读取与高亮显示
     */
    @Inject(method = "getInfoData", at = @At("HEAD"), cancellable = true)
    private void onGetInfoData(CallbackInfoReturnable<String[]> cir) {
        if(MainConfig.OutPutHatchMEEnable) {
            List<String> ss = new ArrayList<>();
            ss.add(
                "The hatch is " + ((getProxy() != null && getProxy().isActive()) ?
                    EnumChatFormatting.GREEN + "online" :
                    EnumChatFormatting.RED + "offline" + getAEDiagnostics()) +
                    EnumChatFormatting.RESET);
            ss.add("Fluid cache capacity: " + EnumChatFormatting.GOLD + "∞ L" + EnumChatFormatting.RESET);

            if (fluidCache.isEmpty()) {
                ss.add("The hatch has no cached fluids");
            } else {
                ss.add(String.format("The hatch contains %d cached fluids: ", fluidCache.size()));
                int counter = 0;
                for (IAEFluidStack s : fluidCache) {
                    ss.add(
                        s.getFluid().getLocalizedName(s.getFluidStack()) + ": " +
                            EnumChatFormatting.GOLD +
                            GTUtility.formatNumbers(s.getStackSize()) + "L" +
                            EnumChatFormatting.RESET);
                    if (++counter > 100) break;
                }
            }
            cir.setReturnValue(ss.toArray(new String[fluidCache.size() + 2]));
            cir.cancel();
        }
    }
}
