package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gtnhlanth.common.tileentity.MTEDigester;

@Mixin(value = MTEDigester.class, remap = false)
public class DigesterMixin {

    @Inject(method = "createProcessingLogic", at = @At("HEAD"), cancellable = true)
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        ProcessingLogic customLogic = new ProcessingLogic() {

            @Override
            protected OverclockCalculator createOverclockCalculator(GTRecipe recipe) {
                OverclockCalculator calculator = super.createOverclockCalculator(recipe);

                if (MainConfig.DigesterMixin) {
                    MTEDigester digester = getEnclosingInstance();
                    HeatingCoilLevel coilLevel = digester.getCoilLevel();

                    return adjustOverclockCalculator(calculator, coilLevel.getHeat());
                } else {
                    return calculator;
                }
            }

            @Override
            protected CheckRecipeResult validateRecipe(GTRecipe recipe) {
                MTEDigester digester = getEnclosingInstance();

                return recipe.mSpecialValue <= digester.getCoilLevel()
                    .getHeat() ? CheckRecipeResultRegistry.SUCCESSFUL
                        : CheckRecipeResultRegistry.insufficientHeat(recipe.mSpecialValue);
            }

            private MTEDigester getEnclosingInstance() {
                try {
                    Field enclosingInstanceField = this.getClass()
                        .getDeclaredField("this$0");
                    enclosingInstanceField.setAccessible(true);
                    return (MTEDigester) enclosingInstanceField.get(this);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IllegalStateException("无法获取封闭类实例", e);
                }
            }
        };

        cir.setReturnValue(customLogic);
    }

    private OverclockCalculator adjustOverclockCalculator(OverclockCalculator calculator, long heat) {
        double heatMultiplier = heat * 0.1;
        calculator.setDurationDecreasePerOC(2 + heatMultiplier);
        calculator.setEUtIncreasePerOC(4);
        return calculator;
    }
}
