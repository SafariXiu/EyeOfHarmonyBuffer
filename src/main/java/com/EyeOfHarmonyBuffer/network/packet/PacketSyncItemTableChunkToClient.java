package com.EyeOfHarmonyBuffer.network.packet;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketSyncItemTableChunkToClient implements IMessage {

    private int uploadId;
    private int index;
    private int total;
    private byte[] data;

    public PacketSyncItemTableChunkToClient() {}

    public PacketSyncItemTableChunkToClient(int uploadId, int index, int total, byte[] data) {
        this.uploadId = uploadId;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        uploadId = buf.readInt();
        index = buf.readInt();
        total = buf.readInt();
        int len = buf.readInt();
        if (len < 0) len = 0;
        data = new byte[len];
        if (len > 0) {
            buf.readBytes(data);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(uploadId);
        buf.writeInt(index);
        buf.writeInt(total);
        buf.writeInt(data == null ? 0 : data.length);
        if (data != null && data.length > 0) {
            buf.writeBytes(data);
        }
    }

    public static class Handler implements IMessageHandler<PacketSyncItemTableChunkToClient, IMessage> {

        @Override
        public IMessage onMessage(PacketSyncItemTableChunkToClient message, MessageContext ctx) {
            com.EyeOfHarmonyBuffer.client.ClientItemTableReceiver.receiveServerItemTableChunk(
                message.uploadId, message.index, message.total, message.data
            );
            return null;
        }
    }
}
