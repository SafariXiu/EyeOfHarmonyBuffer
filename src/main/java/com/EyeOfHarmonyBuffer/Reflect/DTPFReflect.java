package com.EyeOfHarmonyBuffer.Reflect;

import gregtech.common.tileentities.machines.multi.MTEPlasmaForge;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;

public class DTPFReflect {

    private static boolean isValidFuelsCleared = false;
    private static FluidStack[] originalValidFuels = null;

    public static void DTPFUpdateValidFuels(boolean enable) {
        try {
            Field DTPFvalidFuelsField = MTEPlasmaForge.class.getDeclaredField("valid_fuels");
            DTPFvalidFuelsField.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(DTPFvalidFuelsField, DTPFvalidFuelsField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

            if (enable && !isValidFuelsCleared) {
                if (originalValidFuels == null) {
                    originalValidFuels = (FluidStack[]) DTPFvalidFuelsField.get(null);
                }
                DTPFvalidFuelsField.set(null, new FluidStack[]{});
                isValidFuelsCleared = true;
            } else if (!enable && isValidFuelsCleared) {
                DTPFvalidFuelsField.set(null, originalValidFuels);
                isValidFuelsCleared = false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to modify valid_fuels field in MTEPlasmaForge", e);
        }
    }
}
