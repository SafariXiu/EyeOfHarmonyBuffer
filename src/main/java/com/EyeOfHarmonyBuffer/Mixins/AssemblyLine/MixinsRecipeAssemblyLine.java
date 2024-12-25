package com.EyeOfHarmonyBuffer.Mixins.AssemblyLine;

import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = GTRecipe.RecipeAssemblyLine.class,remap = false)
public abstract class MixinsRecipeAssemblyLine {

    @Unique
    private static ItemStack[] lastRecipeInputs; // 注入私有静态字段

    @Unique
    private static ItemStack[] getLastRecipeInputs() { // 私有静态 Getter 方法
        return lastRecipeInputs;
    }

    @Unique
    private static void setLastRecipeInputs(ItemStack[] inputs) { // 私有静态 Setter 方法
        lastRecipeInputs = inputs;
    }
}
