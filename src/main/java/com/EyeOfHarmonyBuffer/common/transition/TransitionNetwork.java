package com.EyeOfHarmonyBuffer.common.transition;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/** 维度转场网络通道（M0 骨架）。 */
public final class TransitionNetwork {

    public static final SimpleNetworkWrapper INSTANCE =
        NetworkRegistry.INSTANCE.newSimpleChannel("EOHB|Transition");

    private TransitionNetwork() {}

    public static void init() {
        INSTANCE.registerMessage(
            PacketTransitionStart.class,
            PacketTransitionStart.class,
            0,
            Side.CLIENT);
        INSTANCE.registerMessage(
            PacketTransitionPhase.class,
            PacketTransitionPhase.class,
            1,
            Side.CLIENT);
    }
}
