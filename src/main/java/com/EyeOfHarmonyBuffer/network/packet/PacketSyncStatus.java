package com.EyeOfHarmonyBuffer.network.packet;

import com.EyeOfHarmonyBuffer.client.ClientStatus;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSyncStatus implements IMessage {

    public boolean serverInitialized;
    public boolean allowClientInit;

    public PacketSyncStatus() {}

    public PacketSyncStatus(boolean init, boolean allow) {
        this.serverInitialized = init;
        this.allowClientInit = allow;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        serverInitialized = buf.readBoolean();
        allowClientInit = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(serverInitialized);
        buf.writeBoolean(allowClientInit);
    }

    public static class Handler implements IMessageHandler<PacketSyncStatus, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncStatus message, MessageContext ctx) {
            System.out.println("[EOH] Client got PacketSyncStatus: init="
                + message.serverInitialized + ", allow=" + message.allowClientInit);

            ClientStatus.serverInitialized = message.serverInitialized;
            ClientStatus.serverAllowClientInit = message.allowClientInit;

            if (!message.serverInitialized && message.allowClientInit) {
                ClientStatus.needUploadItemTable = true;
            } else {
                ClientStatus.needUploadItemTable = false;
            }

            return null;
        }
    }
}
