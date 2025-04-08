package com.EyeOfHarmonyBuffer.Mixins.PHM;

import appeng.api.storage.ICellContainer;
import appeng.me.helpers.IGridProxyable;
import gregtech.common.tileentities.machines.MTEHatchOutputBusME;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reobf.proghatches.gt.metatileentity.StorageOutputBus;
import reobf.proghatches.gt.metatileentity.util.IStoageCellUpdate;

@Mixin(value = StorageOutputBus.class, remap = false)
public abstract class StorageOutputBusMixin extends MTEHatchOutputBusME implements ICellContainer, IGridProxyable, IStoageCellUpdate {

    public StorageOutputBusMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "getCacheCapacity", at = @At("RETURN"), cancellable = true)
    private void injectGetCacheCapacity(CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(Long.MAX_VALUE);
    }
}
