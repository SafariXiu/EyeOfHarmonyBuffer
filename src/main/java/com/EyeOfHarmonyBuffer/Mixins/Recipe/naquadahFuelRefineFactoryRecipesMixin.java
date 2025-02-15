package com.EyeOfHarmonyBuffer.Mixins.Recipe;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import goodgenerator.loader.FuelRecipeLoader;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = FuelRecipeLoader.class,remap = false)
public abstract class naquadahFuelRefineFactoryRecipesMixin {

    @ModifyVariable(
        method = "RegisterFuel",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTRecipeBuilder;fluidOutputs([Lnet/minecraftforge/fluids/FluidStack;)Lgregtech/api/util/GTRecipeBuilder;"
        ),
        index = 0
    )
    private static FluidStack[] modifyOutputChances(FluidStack[] value){
        if(MainConfig.NaquadahFuelRefineryMixinTrue){
            for(int i = 0 ;i < value.length; i++){
                if (value[i] != null){
                    value[i] = new FluidStack(value[i],value[i].amount*MainConfig.NaquadahFuelRefineryMagnification);
                }
            }
        }
        return value;
    }
}
