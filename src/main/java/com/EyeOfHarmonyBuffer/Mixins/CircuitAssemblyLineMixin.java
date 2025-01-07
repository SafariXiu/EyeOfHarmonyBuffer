package com.EyeOfHarmonyBuffer.Mixins;

import bartworks.common.tileentities.multis.MTECircuitAssemblyLine;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.CircuitAssemblyLineAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.util.GTRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = MTECircuitAssemblyLine.class, remap = false)
public abstract class CircuitAssemblyLineMixin extends MTEEnhancedMultiBlockBase<MTECircuitAssemblyLine>
    implements ISurvivalConstructable {

    protected CircuitAssemblyLineMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "createProcessingLogic", at = @At("RETURN"), cancellable = true)
    private void modifyProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        ProcessingLogic originalLogic = cir.getReturnValue();

        ProcessingLogic modifiedLogic = new ProcessingLogic() {

            @Override
            @Nonnull
            protected CheckRecipeResult validateRecipe(@Nonnull GTRecipe recipe) {
                CheckRecipeResult result = ((CircuitAssemblyLineAccessor) originalLogic).invokeValidateRecipe(recipe);

                if (!result.wasSuccessful()) {
                    return result;
                }
                recipe.mDuration = 64;
                return result;
            }
        };
        cir.setReturnValue(modifiedLogic);
    }

}
