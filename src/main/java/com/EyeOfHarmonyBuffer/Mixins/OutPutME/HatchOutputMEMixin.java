package com.EyeOfHarmonyBuffer.Mixins.OutPutME;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.helpers.AENetworkProxy;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;
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

    @Shadow @Final
    private MTEHatchOutputMEBase<IAEFluidStack> provider;

    @Shadow
    public abstract AENetworkProxy getProxy();

    @ModifyConstant(
        method = "<init>",
        constant = @Constant(longValue = 128_000L)
    )
    private static long modifyDefaultCapacity(long constant) {
        if (MainConfig.OutPutHatchMEEnable) {
            return Long.MAX_VALUE;
        }
        return constant;
    }

    @Inject(method = "getInfoData", at = @At("HEAD"), cancellable = true)
    private void onGetInfoData(CallbackInfoReturnable<String[]> cir) {
        if (!MainConfig.OutPutHatchMEEnable) {
            return;
        }

        List<String> ss = new ArrayList<>();

        boolean online = (getProxy() != null && getProxy().isActive());
        ss.add(
            "The hatch is " +
                (online
                    ? EnumChatFormatting.GREEN + "online"
                    : EnumChatFormatting.RED + "offline" + getAEDiagnostics()
                ) +
                EnumChatFormatting.RESET
        );

        ss.add("Fluid cache capacity: " + EnumChatFormatting.GOLD + "∞ L" + EnumChatFormatting.RESET);

        long cached = provider.getCachedAmount();
        long capacity = provider.getCacheCapacity();

        if (cached <= 0) {
            ss.add("The hatch has no cached fluids");
        } else {
            ss.add(
                String.format(
                    "The hatch currently caches %s L of fluids (raw capacity %s L)",
                    String.valueOf(cached),
                    capacity == Long.MAX_VALUE ? "∞" : String.valueOf(capacity)
                )
            );
        }

        cir.setReturnValue(ss.toArray(new String[0]));
        cir.cancel();
    }
}
