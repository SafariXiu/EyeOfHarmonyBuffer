package com.EyeOfHarmonyBuffer.Mixins.BioLab;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import bartworks.common.loaders.BioRecipeLoader;

@Mixin(value = BioRecipeLoader.class, remap = false)
public class BioLabAdvancedMixin {

    @ModifyArg(
        method = "registerWaterBasedBioLabIncubations", // 指定方法
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTRecipeBuilder;outputChances([I)Lgregtech/api/util/GTRecipeBuilder;"),
        index = 0)
    private static int[] modifyOutputChances(int[] chances) {
        if (MainConfig.BioLabMixin) {
            return new int[] { 10000 };
        }
        return chances;
    }
}
