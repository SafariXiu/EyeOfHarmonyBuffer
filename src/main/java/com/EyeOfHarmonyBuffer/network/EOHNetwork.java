package com.EyeOfHarmonyBuffer.network;

import com.EyeOfHarmonyBuffer.network.packet.PacketSyncItemTableChunkToClient;
import com.EyeOfHarmonyBuffer.network.packet.PacketSyncStatus;
import com.EyeOfHarmonyBuffer.network.packet.PacketUploadItemTableChunk;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class EOHNetwork {

    public static final SimpleNetworkWrapper NETWORK =
        NetworkRegistry.INSTANCE.newSimpleChannel("eyeofharmony");

    private static int id = 0;

    public static void init() {

        NETWORK.registerMessage(
            PacketSyncStatus.Handler.class,
            PacketSyncStatus.class,
            id++,
            Side.CLIENT
        );

        NETWORK.registerMessage(
            PacketUploadItemTableChunk.Handler.class,
            PacketUploadItemTableChunk.class,
            id++,
            Side.SERVER
        );

        NETWORK.registerMessage(
            PacketSyncItemTableChunkToClient.Handler.class,
            PacketSyncItemTableChunkToClient.class,
            id++,
            Side.CLIENT
        );
    }
}
