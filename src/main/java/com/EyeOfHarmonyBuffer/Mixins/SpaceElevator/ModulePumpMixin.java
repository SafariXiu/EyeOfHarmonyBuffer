package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModulePump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {
    TileEntityModulePump.TileEntityModulePumpT3.class,
    TileEntityModulePump.TileEntityModulePumpT2.class,
    TileEntityModulePump.TileEntityModulePumpT1.class},
    remap = false)
public abstract class ModulePumpMixin extends TileEntityModuleBase {

    protected ModulePumpMixin(int aID, String aName, String aNameRegional, int tTier, int tModuleTier, int tMinMotorTier) {
        super(aID, aName, aNameRegional, tTier, tModuleTier, tMinMotorTier);
    }

    @Inject(method = "getParallels",at = @At("HEAD"),cancellable = true)
    private void getParallels(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Integer.MAX_VALUE);
    }
}
