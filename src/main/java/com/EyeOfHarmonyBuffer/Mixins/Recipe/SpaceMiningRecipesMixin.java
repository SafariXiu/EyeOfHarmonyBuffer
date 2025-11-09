package com.EyeOfHarmonyBuffer.Mixins.Recipe;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gtnhintergalactic.item.ItemMiningDrones;
import gtnhintergalactic.recipe.SpaceMiningRecipes;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpaceMiningRecipes.class, remap = false)
public abstract class SpaceMiningRecipesMixin {

    @Shadow
    @Final
    @Mutable
    private static ItemStack[] MINING_DRILLS;

    @Shadow
    @Final
    @Mutable
    private static ItemStack[] MINING_RODS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void init(CallbackInfo ci) {
        if(MainConfig.SpaceMiningRecipesEnable){
            MINING_DRILLS = new ItemStack[ItemMiningDrones.DroneMaterials.values().length];
            MINING_RODS = new ItemStack[ItemMiningDrones.DroneMaterials.values().length];
        }
    }
}
