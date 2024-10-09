package com.EyeOfHarmonyBuffer;

public class FluidInfo {

    public String modid;
    public String fluidName; // 流体注册名
    public int amount; // 流体数量，单位为毫桶

    public FluidInfo(String fluidName, int amount) {
        this.fluidName = fluidName;
        this.amount = amount;
    }

    public FluidInfo(String modid, String fluidName, int amount) {
        this.modid = modid;
        this.fluidName = fluidName;
        this.amount = amount;
    }
}
