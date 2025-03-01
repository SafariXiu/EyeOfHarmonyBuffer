package com.EyeOfHarmonyBuffer.Mixins.Recipe;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import goodgenerator.loader.FuelRecipeLoader;
import gregtech.api.util.GTRecipeBuilder;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FuelRecipeLoader.class,remap = false)
public abstract class naquadahFuelRefineFactoryRecipesMixin {

    @Redirect(
        method = "RegisterFuel",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTRecipeBuilder;fluidOutputs([Lnet/minecraftforge/fluids/FluidStack;)Lgregtech/api/util/GTRecipeBuilder;"
        )
    )
    private static GTRecipeBuilder modifyFluidOutputs(GTRecipeBuilder instance, FluidStack[] fluidOutputs) {
        if(MainConfig.NaquadahFuelRefineryMixinTrue){
            if ((instance.getItemInputsBasic() != null && instance.getItemInputsBasic().length > 0) ||
                (instance.getItemInputsOreDict() != null && instance.getItemInputsOreDict().length > 0)) {

                for (FluidStack stack : fluidOutputs) {
                    if (stack != null) {
                        stack.amount *= MainConfig.NaquadahFuelRefineryMagnification;
                    }
                }
            }
            return instance.fluidOutputs(fluidOutputs);
        }
        return instance.fluidOutputs(fluidOutputs);
    }
}
