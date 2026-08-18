package com.EyeOfHarmonyBuffer.common.orbitalrailgun;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import com.EyeOfHarmonyBuffer.client.orbitalrailgun.RailgunClientState;

/** 服务端 -> 客户端：通知一次轨道打击开始（播放视觉特效）。 */
public class PacketOrbitalStrikeStart implements IMessage, IMessageHandler<PacketOrbitalStrikeStart, IMessage> {

    private int targetX;
    private int targetY;
    private int targetZ;
    private float radius;

    public PacketOrbitalStrikeStart() {}

    public PacketOrbitalStrikeStart(int x, int y, int z, float radius) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.radius = radius;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeFloat(radius);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetX = buf.readInt();
        targetY = buf.readInt();
        targetZ = buf.readInt();
        radius = buf.readFloat();
    }

    @Override
    public IMessage onMessage(final PacketOrbitalStrikeStart message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            // 网络 IO 线程只搬运，状态写入切回渲染主线程
            Minecraft.getMinecraft().func_152344_a(new Runnable() {
                @Override
                public void run() {
                    RailgunClientState.getInstance().onStrikeStarted(
                        message.targetX, message.targetY, message.targetZ, message.radius);
                }
            });
        }
        return null;
    }
}
