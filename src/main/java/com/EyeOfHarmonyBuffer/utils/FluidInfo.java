package com.EyeOfHarmonyBuffer.utils;

public class FluidInfo {

    public String modid;
    public String fluidName; // 流体注册名
    public long amount; // 流体数量，单位为毫桶

    public FluidInfo(String fluidName, long amount) {
        this.fluidName = fluidName;
        this.amount = amount;
    }

    public FluidInfo(String modid, String fluidName, int amount) {
        this.modid = modid;
        this.fluidName = fluidName;
        this.amount = amount;
    }
}
