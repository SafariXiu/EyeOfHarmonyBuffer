package com.EyeOfHarmonyBuffer.utils;

import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Method;

public class RecipeAssemblyLineAccessor {

    private static Method getLastRecipeInputsMethod;
    private static Method setLastRecipeInputsMethod;

    static {
        try {
            // 获取私有方法的引用
            getLastRecipeInputsMethod = GTRecipe.RecipeAssemblyLine.class.getDeclaredMethod("getLastRecipeInputs");
            getLastRecipeInputsMethod.setAccessible(true);

            setLastRecipeInputsMethod = GTRecipe.RecipeAssemblyLine.class.getDeclaredMethod("setLastRecipeInputs", ItemStack[].class);
            setLastRecipeInputsMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    // 使用反射调用私有 Getter 方法
    public static ItemStack[] getLastRecipeInputs() {
        try {
            return (ItemStack[]) getLastRecipeInputsMethod.invoke(null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 使用反射调用私有 Setter 方法
    public static void setLastRecipeInputs(ItemStack[] inputs) {
        try {
            setLastRecipeInputsMethod.invoke(null, (Object) inputs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
