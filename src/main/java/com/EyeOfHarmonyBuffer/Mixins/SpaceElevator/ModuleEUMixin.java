package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevator.TileEntitySpaceElevator;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {
    TileEntityModuleBase.class,
    TileEntitySpaceElevator.class},
    remap = false)
public class ModuleEUMixin {

    @Inject(method = "maxEUStore",at = @At("RETURN"),cancellable = true)
    public void MixinEU_BUFFER_BASE_SIZE(CallbackInfoReturnable<Long> cir) {
        if(MainConfig.SpaceElevatorMiningParallelsEnable || MainConfig.SpaceElevatorMiningTicksTrue || MainConfig.SpaceElevatorAssemblerParallelEnable || MainConfig.ModulePumpEnable){
            cir.setReturnValue(Long.MAX_VALUE);
        }
    }
}
