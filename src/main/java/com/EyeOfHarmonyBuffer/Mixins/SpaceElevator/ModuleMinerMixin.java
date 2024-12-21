package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleMiner;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

@Mixin(value = TileEntityModuleMiner.class, remap = false)
public abstract class ModuleMinerMixin extends MTEMultiBlockBase {

    @Shadow
    protected abstract int getParallels(FluidStack plasma, int plasmaUsage);

    public ModuleMinerMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "getTierFromPlasma", at = @At("HEAD"), cancellable = true)
    private void modifyGetTierFromPlasma(FluidStack fluidStack, CallbackInfoReturnable<Integer> cir) {

        if (!MainConfig.SpaceElevatorMiningPlasmaEnable) {
            return;
        }

        if (fluidStack == null) {
            cir.setReturnValue(0);
            return;
        }

        if (fluidStack.isFluidEqual(Materials.Radon.getPlasma(1))) {
            cir.setReturnValue(3);
        }

        else if (fluidStack.isFluidEqual(Materials.Bismuth.getPlasma(1))) {
            cir.setReturnValue(2);
        }

        else if (fluidStack.isFluidEqual(Materials.Helium.getPlasma(1))) {
            cir.setReturnValue(1);
        } else {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "getPlasmaUsageFromTier", at = @At("HEAD"), cancellable = true)
    private void modifyGetPlasmaUsageFromTier(int plasmaTier, CallbackInfoReturnable<Integer> cir) {
        if (MainConfig.SpaceElevatorMiningPlasmaEnable) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "getRecipeTime", at = @At("HEAD"), cancellable = true)
    protected void modifyGetRecipeTime(int unboostedTime, int plasmaTier, CallbackInfoReturnable<Integer> cir) {
        if (MainConfig.SpaceElevatorMiningTicksTrue) {
            int modifiedTime = MainConfig.SpaceElevatorMiningTicks;
            cir.setReturnValue(modifiedTime);
        }
    }

}
