package com.EyeOfHarmonyBuffer.utils;

public class PredeterminedValues {

    // 简化为单一权重
    public static final int FLUID_WEIGHT = 3; // 固定的流体权重
    public static final int ITEM_WEIGHT = 3; // 固定的物品
      // 权重

    // 获取流体权重
    public static int getNextFluidWeight() {
        System.out.println("Selected Fluid Weight: " + FLUID_WEIGHT);
        return FLUID_WEIGHT;
    }

    // 获取物品权重
    public static int getNextItemWeight() {
        System.out.println("Selected Item Weight: " + ITEM_WEIGHT);
        return ITEM_WEIGHT;
    }

}
