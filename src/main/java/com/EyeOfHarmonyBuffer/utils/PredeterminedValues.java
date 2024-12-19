package com.EyeOfHarmonyBuffer.utils;

public class PredeterminedValues {

    public static final int[] FLUID_WEIGHTS = new int[]{1, 2, 3, 4, 5, 6, 7};
    public static final int[] ITEM_WEIGHTS = new int[]{1, 3, 5, 7, 9, 11, 13};
    private static int fluidIndex = 0;
    private static int itemIndex = 0;

    public static int getNextFluidWeight() {
        if (FLUID_WEIGHTS.length == 0) {
            throw new IllegalStateException("FLUID_WEIGHTS array is empty!");
        }
        int weight = FLUID_WEIGHTS[fluidIndex];
        fluidIndex = (fluidIndex + 1) % FLUID_WEIGHTS.length;
        System.out.println("Selected Fluid Weight: " + weight);
        return weight;
    }

    public static int getNextItemWeight() {
        if (ITEM_WEIGHTS.length == 0) {
            throw new IllegalStateException("ITEM_WEIGHTS array is empty!");
        }
        int weight = ITEM_WEIGHTS[itemIndex];
        itemIndex = (itemIndex + 1) % ITEM_WEIGHTS.length;
        System.out.println("Selected Item Weight: " + weight);
        return weight;
    }

}
