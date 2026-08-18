package com.EyeOfHarmonyBuffer.common.orbitalrailgun;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import com.EyeOfHarmonyBuffer.client.orbitalrailgun.RailgunClientState;

import java.util.UUID;

/** 服务端 -> 客户端：通知一次轨道打击开始（播放视觉特效）。携带归属信息供多打击分流。 */
public class PacketOrbitalStrikeStart implements IMessage, IMessageHandler<PacketOrbitalStrikeStart, IMessage> {

    private int targetX;
    private int targetY;
    private int targetZ;
    private float radius;
    /** 发起者 UUID（机器打击可能为 null）。 */
    private UUID shooterUuid;
    /** 发起者所属队伍 UUID（纯坐标打击可能为 null）。 */
    private UUID teamId;

    public PacketOrbitalStrikeStart() {}

    public PacketOrbitalStrikeStart(int x, int y, int z, float radius) {
        this(x, y, z, radius, null, null);
    }

    public PacketOrbitalStrikeStart(int x, int y, int z, float radius, UUID shooterUuid, UUID teamId) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.radius = radius;
        this.shooterUuid = shooterUuid;
        this.teamId = teamId;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeFloat(radius);
        buf.writeBoolean(shooterUuid != null);
        if (shooterUuid != null) {
            buf.writeLong(shooterUuid.getMostSignificantBits());
            buf.writeLong(shooterUuid.getLeastSignificantBits());
        }
        buf.writeBoolean(teamId != null);
        if (teamId != null) {
            buf.writeLong(teamId.getMostSignificantBits());
            buf.writeLong(teamId.getLeastSignificantBits());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targetX = buf.readInt();
        targetY = buf.readInt();
        targetZ = buf.readInt();
        radius = buf.readFloat();
        if (buf.readBoolean()) {
            shooterUuid = new UUID(buf.readLong(), buf.readLong());
        } else {
            shooterUuid = null;
        }
        if (buf.readBoolean()) {
            teamId = new UUID(buf.readLong(), buf.readLong());
        } else {
            teamId = null;
        }
    }

    @Override
    public IMessage onMessage(final PacketOrbitalStrikeStart message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            // 网络 IO 线程只搬运，状态写入切回渲染主线程
            Minecraft.getMinecraft().func_152344_a(new Runnable() {
                @Override
                public void run() {
                    RailgunClientState.getInstance().onStrikeStarted(
                        message.targetX, message.targetY, message.targetZ, message.radius,
                        message.shooterUuid, message.teamId);
                }
            });
        }
        return null;
    }
}
