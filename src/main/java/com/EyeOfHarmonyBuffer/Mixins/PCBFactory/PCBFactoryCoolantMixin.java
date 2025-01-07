package com.EyeOfHarmonyBuffer.Mixins.PCBFactory;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.common.tileentities.machines.multi.MTEPCBFactory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPCBFactory.class, remap = false)
public abstract class PCBFactoryCoolantMixin extends MTEExtendedPowerMultiBlockBase<MTEPCBFactory> implements ISurvivalConstructable {

    protected PCBFactoryCoolantMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "onRunningTick", at = @At("HEAD"), cancellable = true)
    private void disableCoolantConsumption(ItemStack aStack, CallbackInfoReturnable<Boolean> cir) {
        if(MainConfig.PCBFactoryCoolantEnable){
            MTEPCBFactory instance = (MTEPCBFactory) (Object) this;
            cir.setReturnValue(true);
        }
    }
}
