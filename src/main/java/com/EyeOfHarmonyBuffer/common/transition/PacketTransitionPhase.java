package com.EyeOfHarmonyBuffer.common.transition;

import com.EyeOfHarmonyBuffer.client.transition.TransitionClientState;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** 服务端 -> 客户端：转场相位推进（对齐源库 S2CRitualPhasePayload）。 */
public class PacketTransitionPhase implements IMessage, IMessageHandler<PacketTransitionPhase, IMessage> {

    private int phase;

    public PacketTransitionPhase() {}

    public PacketTransitionPhase(int phase) {
        this.phase = phase;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(phase);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        phase = buf.readInt();
    }

    @Override
    public IMessage onMessage(final PacketTransitionPhase message, MessageContext ctx) {
        TransitionClientState.setPhase(message.phase);
        return null;
    }
}
