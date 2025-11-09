package com.EyeOfHarmonyBuffer.Mixins.OthTech.Recipe;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.newmaa.othtech.recipe.IRecipePool;
import com.newmaa.othtech.recipe.RecipesWoodenFusionReactor;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = RecipesWoodenFusionReactor.class, remap = false)
public abstract class WoodenFusionReactorRecipe implements IRecipePool {

    @ModifyArg(
        method = "loadRecipes",
        at = @At(value = "INVOKE",
            target = "Lcom/newmaa/othtech/utils/RecipeBuilder;fluidOutputs([Lnet/minecraftforge/fluids/FluidStack;)Lcom/newmaa/othtech/utils/RecipeBuilder;"),
        index = 0
    )
    private FluidStack[] modifyFluidOutput(FluidStack[] outputFluids){
        if(MainConfig.WoodenFusionReactorEnable){
            for (int i = 0; i < outputFluids.length; i++) {
                if (outputFluids[i] != null) {
                    outputFluids[i] = new FluidStack(outputFluids[i].getFluid(), outputFluids[i].amount * 100);
                }
            }
            return outputFluids;
        }
        return outputFluids;
    }

    @ModifyArg(
        method = "loadRecipes",
        at = @At(value = "INVOKE",
            target = "Lcom/newmaa/othtech/utils/RecipeBuilder;fluidInputs([Lnet/minecraftforge/fluids/FluidStack;)Lcom/newmaa/othtech/utils/RecipeBuilder;"),
        index = 0
    )
    private FluidStack[] modifyFluidInput(FluidStack[] inputFluids){
        if(MainConfig.WoodenFusionReactorEnable){
            for (int i = 0; i < inputFluids.length; i++) {
                if (inputFluids[i] != null) {
                    inputFluids[i] = new FluidStack(inputFluids[i].getFluid(), 1);
                }
            }
            return inputFluids;
        }
        return inputFluids;
    }
}
