package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleAssembler;

@Mixin(
    value = { TileEntityModuleAssembler.TileEntityModuleAssemblerT1.class,
        TileEntityModuleAssembler.TileEntityModuleAssemblerT2.class,
        TileEntityModuleAssembler.TileEntityModuleAssemblerT3.class },
    remap = false)
public class ModuleAssemblerParallelsMixin {

    @Inject(method = "getMaxParallels", at = @At("HEAD"), cancellable = true)
    private void modifyGetMaxParallels(CallbackInfoReturnable<Integer> cir) {
        if (MainConfig.SpaceElevatorAssemblerParallelsTrue) {
            int modifiedValue = MainConfig.SpaceElevatorAssemblerParallels;
            cir.setReturnValue(modifiedValue);
        }
    }
}
