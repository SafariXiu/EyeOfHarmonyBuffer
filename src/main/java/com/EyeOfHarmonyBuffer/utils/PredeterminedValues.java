package com.EyeOfHarmonyBuffer.utils;

public class PredeterminedValues {

    public static final int[] FLUID_WEIGHTS = new int[]{/* 预定义的流体权重 */};
    public static final int[] ITEM_WEIGHTS = new int[]{/* 预定义的物品权重 */};
    private static int fluidIndex = 0;
    private static int itemIndex = 0;

    public static int getNextFluidWeight() {
        int weight = FLUID_WEIGHTS[fluidIndex];
        fluidIndex = (fluidIndex + 1) % FLUID_WEIGHTS.length;
        return weight;
    }

    public static int getNextItemWeight() {
        int weight = ITEM_WEIGHTS[itemIndex];
        itemIndex = (itemIndex + 1) % ITEM_WEIGHTS.length;
        return weight;
    }

}
