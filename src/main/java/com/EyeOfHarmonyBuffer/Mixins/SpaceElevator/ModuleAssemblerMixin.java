package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleAssembler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value =
    {TileEntityModuleAssembler.TileEntityModuleAssemblerT1.class,
    TileEntityModuleAssembler.TileEntityModuleAssemblerT2.class,
    TileEntityModuleAssembler.TileEntityModuleAssemblerT3.class},
    remap = false)
public class ModuleAssemblerMixin {

    @Inject(method = "getMaxParallels",at = @At("RETURN"),cancellable = true)
    public void getMaxParallels(CallbackInfoReturnable<Integer> cir){
        if(MainConfig.SpaceElevatorAssemblerParallelEnable){
            cir.setReturnValue(MainConfig.SpaceElevatorAssemblerParallel);
        }
    }
}
