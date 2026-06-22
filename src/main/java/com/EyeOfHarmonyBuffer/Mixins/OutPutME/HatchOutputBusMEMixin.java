package com.EyeOfHarmonyBuffer.Mixins.OutPutME;

import appeng.api.implementations.IPowerChannelState;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
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

@Mixin(value = MTEHatchOutputBusME.class, remap = false)
public abstract class HatchOutputBusMEMixin extends MTEHatchOutputBus implements IPowerChannelState {

    public HatchOutputBusMEMixin(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    @Shadow
    @Final
    private MTEHatchOutputMEBase<IAEItemStack> provider;

    @Shadow
    public abstract AENetworkProxy getProxy();

    @ModifyConstant(
        method = "<init>",
        constant = @Constant(longValue = 1_600L)
    )
    private static long modifyDefaultCapacity(long constant) {
        if (MainConfig.OutPutBusMEEnable) {
            return Long.MAX_VALUE;
        }
        return constant;
    }

    @Inject(method = "getInfoData", at = @At("HEAD"), cancellable = true)
    private void onGetInfoData(CallbackInfoReturnable<String[]> cir) {
        if (!MainConfig.OutPutBusMEEnable) {
            return;
        }

        List<String> ss = new ArrayList<>();

        boolean online = (getProxy() != null && getProxy().isActive());
        ss.add(
            "The bus is " +
                (online
                    ? EnumChatFormatting.GREEN + "online"
                    : EnumChatFormatting.RED + "offline" + getAEDiagnostics()
                ) +
                EnumChatFormatting.RESET
        );

        ss.add("Item cache capacity: " + EnumChatFormatting.GOLD + "∞" + EnumChatFormatting.RESET);

        long cached = provider.getCachedAmount();
        long capacity = provider.getCacheCapacity();

        if (cached <= 0) {
            ss.add("The bus has no cached items");
        } else {
            ss.add(
                String.format(
                    "The bus currently caches %s items (raw capacity %s)",
                    GTUtility.formatShortenedLong(cached),
                    capacity == Long.MAX_VALUE
                        ? "∞"
                        : GTUtility.formatShortenedLong(capacity)
                )
            );
        }

        cir.setReturnValue(ss.toArray(new String[0]));
        cir.cancel();
    }
}
