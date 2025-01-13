package com.EyeOfHarmonyBuffer.Mixins.FuelFactory;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import goodgenerator.blocks.tileEntity.MTEFuelRefineFactory;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gtPlusPlus.core.material.Material;
import gtPlusPlus.xmod.gregtech.common.helpers.FlotationRecipeHandler;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = MTEFuelRefineFactory.class, remap = false)
public abstract class FuelRefineFactoryMixin extends MTETooltipMultiBlockBaseEM implements IConstructable, ISurvivalConstructable {

    protected FuelRefineFactoryMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int Tier = -1;

    @Inject(
        method = "createProcessingLogic",
        at = @At("RETURN"),
        cancellable = true
    )
    private void modifyProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.FuelRefineFactoryEnable){
            ProcessingLogic originalLogic = cir.getReturnValue();

            ProcessingLogic modifiedLogic = new ProcessingLogic() {

                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    if (recipe.mSpecialValue > Tier) {
                        return CheckRecipeResultRegistry.insufficientMachineTier(recipe.mSpecialValue);
                    }
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @NotNull
                @Override
                protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                    int overclockAmount = Tier - recipe.mSpecialValue;
                    return super.createOverclockCalculator(recipe).limitOverclockCount(overclockAmount);
                }
            };

            modifiedLogic.setOverclock(4, 4);

            cir.setReturnValue(modifiedLogic);
        }
    }

    @Inject(
        method = "setProcessingLogicPower",
        at = @At("HEAD"),
        cancellable = true
    )
    private void disablePowerConsumption(ProcessingLogic logic, CallbackInfo ci) {
        if(MainConfig.FuelRefineFactoryEnable){
            logic.setAvailableVoltage(Long.MAX_VALUE);
            logic.setAvailableAmperage(Integer.MAX_VALUE);
            ci.cancel();
        }
    }
}
