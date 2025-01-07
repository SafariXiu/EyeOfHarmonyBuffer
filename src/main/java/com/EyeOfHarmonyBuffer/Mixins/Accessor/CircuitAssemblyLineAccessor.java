package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import gregtech.api.logic.AbstractProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AbstractProcessingLogic.class,remap = false)
public interface CircuitAssemblyLineAccessor {

    @Invoker("validateRecipe")
    CheckRecipeResult invokeValidateRecipe(GTRecipe recipe);
}
