package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleAssembler;

import gregtech.api.logic.ProcessingLogic;

@Mixin(value = TileEntityModuleAssembler.class, remap = false)
public class ModuleAssemblerTicksMixin {

    @Inject(method = "createProcessingLogic", at = @At("RETURN"), cancellable = true)
    private void injectAmperageOC(CallbackInfoReturnable<ProcessingLogic> cir) {
        if (MainConfig.SpaceElevatorAssemblerOverClock) {
            ProcessingLogic logic = cir.getReturnValue();
            logic.setAmperageOC(true);
            cir.setReturnValue(logic);
        }
    }
}
