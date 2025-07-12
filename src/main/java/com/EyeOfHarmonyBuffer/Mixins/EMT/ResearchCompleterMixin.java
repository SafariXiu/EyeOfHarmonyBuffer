package com.EyeOfHarmonyBuffer.Mixins.EMT;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import emt.tile.GT_MetaTileEntity_ResearchCompleter;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GT_MetaTileEntity_ResearchCompleter.class, remap = false)
public abstract class ResearchCompleterMixin extends MTEEnhancedMultiBlockBase<GT_MetaTileEntity_ResearchCompleter> {

    protected ResearchCompleterMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "onRunningTick", at = @At("HEAD"), cancellable = true)
    private void skipVisConsumption(ItemStack aStack, CallbackInfoReturnable<Boolean> cir) {
        if(MainConfig.ResearchCompleterEnable){
            cir.setReturnValue(true);
        }
    }

}
