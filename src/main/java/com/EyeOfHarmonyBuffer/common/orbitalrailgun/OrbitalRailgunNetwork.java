package com.EyeOfHarmonyBuffer.common.orbitalrailgun;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/** 轨道炮网络通道。 */
public final class OrbitalRailgunNetwork {

    public static final SimpleNetworkWrapper INSTANCE =
        NetworkRegistry.INSTANCE.newSimpleChannel("EOHB|OrbitalRailgun");

    private OrbitalRailgunNetwork() {}

    public static void init() {
        INSTANCE.registerMessage(
            PacketOrbitalFireRequest.class,
            PacketOrbitalFireRequest.class,
            0,
            Side.SERVER);
        INSTANCE.registerMessage(
            PacketOrbitalStrikeStart.class,
            PacketOrbitalStrikeStart.class,
            1,
            Side.CLIENT);
    }
}
