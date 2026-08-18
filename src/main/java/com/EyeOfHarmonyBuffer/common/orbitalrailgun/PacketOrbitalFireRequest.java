package com.EyeOfHarmonyBuffer.common.orbitalrailgun;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** 客户端 -> 服务端：请求一次轨道打击。 */
public class PacketOrbitalFireRequest implements IMessage, IMessageHandler<PacketOrbitalFireRequest, IMessage> {

    private int targetX;
    private int targetY;
    private int targetZ;

    public PacketOrbitalFireRequest() {}

    public PacketOrbitalFireRequest(int x, int y, int z) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetX = buf.readInt();
        targetY = buf.readInt();
        targetZ = buf.readInt();
    }

    @Override
    public IMessage onMessage(final PacketOrbitalFireRequest message, MessageContext ctx) {
        // 1.7.10 的 simpleimpl 处理器由 FML 在主线程派发（processCustomPayload），可直接操作世界
        final EntityPlayerMP player = ctx.getServerHandler().playerEntity;
        if (player == null) {
            return null;
        }
        OrbitalStrikeManager.handleFireRequest(player, message.targetX, message.targetY, message.targetZ);
        return null;
    }
}
