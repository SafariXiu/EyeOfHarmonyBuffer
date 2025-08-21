package com.EyeOfHarmonyBuffer.Mixins.PHM;

import appeng.api.storage.ICellContainer;
import appeng.me.helpers.IGridProxyable;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.common.tileentities.machines.MTEHatchOutputME;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reobf.proghatches.gt.metatileentity.StorageOutputHatch;
import reobf.proghatches.gt.metatileentity.util.IStoageCellUpdate;

@Mixin(value = StorageOutputHatch.class, remap = false)
public abstract class StorageOutputHatchMixin extends MTEHatchOutputME implements ICellContainer, IGridProxyable, IStoageCellUpdate {

    public StorageOutputHatchMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "getCacheCapacity", at = @At("RETURN"), cancellable = true)
    private void injectGetCacheCapacity(CallbackInfoReturnable<Long> cir) {
        if(MainConfig.StorageOutputHatchEnable){
            cir.setReturnValue(Long.MAX_VALUE);
        }
    }
}
