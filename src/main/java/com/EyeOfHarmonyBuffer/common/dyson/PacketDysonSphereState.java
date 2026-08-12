package com.EyeOfHarmonyBuffer.common.dyson;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;

/** 服务端 -> 客户端：同步戴森球阶段/进度/归属。 */
public class PacketDysonSphereState implements IMessage, IMessageHandler<PacketDysonSphereState, IMessage> {

    private int stage;
    private float progress;
    private int cloudCount;
    private int frameCount;
    private int pasteCount;
    private String ownerName;

    public PacketDysonSphereState() {}

    public PacketDysonSphereState(int stage, float progress, int cloudCount, int frameCount,
                                  int pasteCount, String ownerName) {
        this.stage = stage;
        this.progress = progress;
        this.cloudCount = cloudCount;
        this.frameCount = frameCount;
        this.pasteCount = pasteCount;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(stage);
        buf.writeFloat(progress);
        buf.writeInt(cloudCount);
        buf.writeInt(frameCount);
        buf.writeInt(pasteCount);
        byte[] nameBytes = ownerName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(nameBytes.length);
        buf.writeBytes(nameBytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        stage = buf.readInt();
        progress = buf.readFloat();
        cloudCount = buf.readInt();
        frameCount = buf.readInt();
        pasteCount = buf.readInt();
        int len = buf.readShort();
        byte[] nameBytes = new byte[len];
        buf.readBytes(nameBytes);
        ownerName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public IMessage onMessage(final PacketDysonSphereState message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            // 网络 IO 线程只搬运，状态写入切回渲染主线程，避免和天空盒渲染竞争
            Minecraft.getMinecraft().func_152344_a(new Runnable() {
                @Override
                public void run() {
                    DysonSphereState.apply(
                        message.stage, message.progress, message.cloudCount, message.frameCount,
                        message.pasteCount, message.ownerName);
                }
            });
        }
        return null;
    }
}
