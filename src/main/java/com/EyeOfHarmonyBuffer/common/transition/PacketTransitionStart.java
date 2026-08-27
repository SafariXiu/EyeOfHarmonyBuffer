package com.EyeOfHarmonyBuffer.common.transition;

import com.EyeOfHarmonyBuffer.client.transition.TransitionClientState;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;

/**
 * 服务端 -> 客户端：开始一次维度转场。
 * 携带转场中心（玩家触发位置）与目标维度；客户端据此启动转场状态机。
 */
public class PacketTransitionStart implements IMessage, IMessageHandler<PacketTransitionStart, IMessage> {

    private int centerX;
    private int centerY;
    private int centerZ;
    private int targetDimension;

    public PacketTransitionStart() {}

    public PacketTransitionStart(int centerX, int centerY, int centerZ, int targetDimension) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.targetDimension = targetDimension;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(centerX);
        buf.writeInt(centerY);
        buf.writeInt(centerZ);
        buf.writeInt(targetDimension);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        centerX = buf.readInt();
        centerY = buf.readInt();
        centerZ = buf.readInt();
        targetDimension = buf.readInt();
    }

    @Override
    public IMessage onMessage(final PacketTransitionStart message, MessageContext ctx) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.LOGGER.info("[EOHB] TransitionStart packet received, center=({},{},{}) target={}",
            message.centerX, message.centerY, message.centerZ, message.targetDimension);
        TransitionClientState.startTransition(
            message.centerX, message.centerY, message.centerZ, message.targetDimension);
        return null;
    }
}
