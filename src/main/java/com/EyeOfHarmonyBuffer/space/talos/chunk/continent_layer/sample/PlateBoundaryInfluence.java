package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.PlateBoundaryState;

/**
 * 单条缝合线对当前点的影响：边界状态 + 强度。
 * 用于表达多板块交汇处「同时受多个状态影响」的情况。
 */
public final class PlateBoundaryInfluence {
    public final PlateBoundaryState state;
    public final double strength;

    public PlateBoundaryInfluence(PlateBoundaryState state, double strength) {
        this.state = state;
        this.strength = strength;
    }
}
