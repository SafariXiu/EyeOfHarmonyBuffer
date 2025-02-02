package com.EyeOfHarmonyBuffer.Mixins.Recipe;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gtPlusPlus.core.recipe.RecipesGregTech;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = RecipesGregTech.class,remap = false)
public abstract class CyclotronRecipeMixin {

    @ModifyArg(
        method = "cyclotronRecipes", // 方法名
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/util/GTRecipeBuilder;outputChances([I)Lgregtech/api/util/GTRecipeBuilder;"
        ),
        index = 0
    )
    private static int[] modifyOutputChances(int[] originalChances) {
        if(MainConfig.CyclotronRecipeMixinEnable){
            int[] newChances = new int[originalChances.length];
            for (int i = 0; i < originalChances.length; i++) {
                newChances[i] = 10000;
            }
            return newChances;
        }
        return originalChances;
    }
}
