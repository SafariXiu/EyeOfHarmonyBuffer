package com.EyeOfHarmonyBuffer.network.packet;

import com.EyeOfHarmonyBuffer.server.EOHItemTableManager;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

public class PacketUploadItemTableChunk implements IMessage{

    public int uploadId;
    public int index;
    public int total;
    public byte[] data;

    public PacketUploadItemTableChunk() {
    }

    public PacketUploadItemTableChunk(int uploadId, int index, int total, byte[] data) {
        this.uploadId = uploadId;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(uploadId);
        buf.writeInt(index);
        buf.writeInt(total);

        if (data == null) {
            buf.writeInt(0);
        } else {
            buf.writeInt(data.length);
            buf.writeBytes(data);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        uploadId = buf.readInt();
        index = buf.readInt();
        total = buf.readInt();

        int len = buf.readInt();
        if (len > 0) {
            data = new byte[len];
            buf.readBytes(data);
        } else {
            data = new byte[0];
        }
    }

    public static class Handler implements IMessageHandler<PacketUploadItemTableChunk, IMessage> {

        @Override
        public IMessage onMessage(PacketUploadItemTableChunk message, MessageContext ctx) {
            if (!ctx.side.isServer()) {
                return null;
            }

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;

            EOHItemTableManager.receiveItemTableChunk(
                message.uploadId,
                message.index,
                message.total,
                message.data,
                player
            );

            return null;
        }
    }
}
