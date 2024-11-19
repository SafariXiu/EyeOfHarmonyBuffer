package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {
    TileEntityModuleMiner.TileEntityModuleMinerT1.class,
    TileEntityModuleMiner.TileEntityModuleMinerT2.class,
    TileEntityModuleMiner.TileEntityModuleMinerT3.class,
},remap = false)
public class ModuleMinerParallelsMixin {

    @Inject(method = "getMaxParallels",at = @At("HEAD"), cancellable = true)
    public void getMaxParallels(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(128);
    }
}
