package com.EyeOfHarmonyBuffer.Mixins.FuelFactory;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import goodgenerator.loader.FuelRecipeLoader;

@Mixin(value = FuelRecipeLoader.class, remap = false)
public class NaquadahFuelRefineryMixin {

    @ModifyArg(
        method = "RegisterFuel",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTRecipeBuilder;fluidOutputs([Lnet/minecraftforge/fluids/FluidStack;)Lgregtech/api/util/GTRecipeBuilder;"),
        index = 0)
    private static FluidStack[] modifyFluidOutputs(FluidStack[] originalOutputs) {
        if (MainConfig.NaquadahFuelRefineryMixinTrue) {
            for (FluidStack output : originalOutputs) {
                if (output != null) {
                    output.amount *= MainConfig.NaquadahFuelRefineryMagnification;
                }
            }
            return originalOutputs;
        }
        return originalOutputs;
    }

    @ModifyArg(
        method = "RegisterFuel",
        at = @At(
            value = "INVOKE",
            target = "Lbartworks/system/material/Werkstoff;getFluidOrGas(I)Lnet/minecraftforge/fluids/FluidStack;",
            args = "ldc=1"
        ),
        index = 0)
    private int modifyFluidAmount(int amount){
        if(MainConfig.NaquadahFuelOutPutMagnificationTrue){
            if(amount == 1){
                return amount * MainConfig.NaquadahFuelOutPutMagnification;
            }
        }
        return amount;
    }
}
