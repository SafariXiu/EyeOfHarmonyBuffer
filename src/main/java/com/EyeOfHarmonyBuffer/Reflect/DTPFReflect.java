package com.EyeOfHarmonyBuffer.Reflect;

import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;

public class DTPFReflect {

    private static boolean isValidFuelsCleared = false;
    private static FluidStack[] originalValidFuels = null;

    public static void updateValidFuels(boolean enable) {
        try {
            Field validFuelsField = MTEPlasmaForge.class.getDeclaredField("valid_fuels");
            validFuelsField.setAccessible(true);

            // 解锁 final 修饰符
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(validFuelsField, validFuelsField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

            if (enable && !isValidFuelsCleared) {
                if (originalValidFuels == null) {
                    originalValidFuels = (FluidStack[]) validFuelsField.get(null);
                }
                validFuelsField.set(null, new FluidStack[]{});
                isValidFuelsCleared = true;
            } else if (!enable && isValidFuelsCleared) {
                validFuelsField.set(null, originalValidFuels);
                isValidFuelsCleared = false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to modify valid_fuels field in MTEPlasmaForge", e);
        }
    }
}
