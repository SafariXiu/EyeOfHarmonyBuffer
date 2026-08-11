package com.EyeOfHarmonyBuffer.common.dyson;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/** 戴森球网络通道。 */
public final class DysonSphereNetwork {

    public static final SimpleNetworkWrapper INSTANCE =
        NetworkRegistry.INSTANCE.newSimpleChannel("EOHB|DysonSphere");

    private DysonSphereNetwork() {}

    public static void init() {
        INSTANCE.registerMessage(
            PacketDysonSphereState.class,
            PacketDysonSphereState.class,
            0,
            Side.CLIENT);
    }
}
