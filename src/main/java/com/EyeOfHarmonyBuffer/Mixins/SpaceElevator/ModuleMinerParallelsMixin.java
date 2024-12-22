package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;

@Mixin(
    value = { TileEntityModuleMiner.TileEntityModuleMinerT1.class, TileEntityModuleMiner.TileEntityModuleMinerT2.class,
        TileEntityModuleMiner.TileEntityModuleMinerT3.class, },
    remap = false)
public class ModuleMinerParallelsMixin {

    @Inject(method = "getMaxParallels", at = @At("HEAD"), cancellable = true)
    public void getMaxParallels(CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.SpaceElevatorMiningParallelsEnable){
            cir.setReturnValue(MainConfig.SpaceElevatorModuleMiningParallels);
        }
    }
}
