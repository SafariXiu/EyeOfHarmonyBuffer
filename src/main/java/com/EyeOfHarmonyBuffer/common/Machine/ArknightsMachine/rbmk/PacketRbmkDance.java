package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import java.util.List;

/**
 * 服务端 -> 客户端：RBMK 跳舞窗口广播。
 * <p>
 * 只发"种子 + 窗口开始 tick + 舞者坐标 + 基准位"（每 5 秒一次），
 * 客户端用 {@link RbmkDanceMath} 与当前世界时间无缝推出每帧棒位。
 */
public class PacketRbmkDance implements IMessage, IMessageHandler<PacketRbmkDance, IMessage> {

    private long seed;
    private long startTick;
    private int count;
    private int[] xs;
    private int[] ys;
    private int[] zs;
    private double[] bases;

    public PacketRbmkDance() {
    }

    public PacketRbmkDance(long seed, long startTick, List<RbmkDanceDriver.Dancer> dancers) {
        this.seed = seed;
        this.startTick = startTick;
        this.count = dancers.size();
        this.xs = new int[count];
        this.ys = new int[count];
        this.zs = new int[count];
        this.bases = new double[count];
        for (int i = 0; i < count; i++) {
            RbmkDanceDriver.Dancer d = dancers.get(i);
            this.xs[i] = d.x;
            this.ys[i] = d.y;
            this.zs[i] = d.z;
            this.bases[i] = d.basePos;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(seed);
        buf.writeLong(startTick);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(xs[i]);
            buf.writeInt(ys[i]);
            buf.writeInt(zs[i]);
            buf.writeDouble(bases[i]);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        seed = buf.readLong();
        startTick = buf.readLong();
        count = buf.readInt();
        xs = new int[count];
        ys = new int[count];
        zs = new int[count];
        bases = new double[count];
        for (int i = 0; i < count; i++) {
            xs[i] = buf.readInt();
            ys[i] = buf.readInt();
            zs[i] = buf.readInt();
            bases[i] = buf.readDouble();
        }
    }

    @Override
    public IMessage onMessage(final PacketRbmkDance message, MessageContext ctx) {
        if (ctx.side.isClient()) {
            Minecraft.getMinecraft().func_152344_a(new Runnable() {
                @Override
                public void run() {
                    World world = Minecraft.getMinecraft().theWorld;
                    if (world == null) {
                        return;
                    }
                    for (int i = 0; i < message.count; i++) {
                        TileEntity te = world.getTileEntity(message.xs[i], message.ys[i], message.zs[i]);
                        if (te instanceof TileEntityRbmkFuelChannel) {
                            ((TileEntityRbmkFuelChannel) te).setDanceState(
                                message.seed, message.xs[i], message.ys[i], message.zs[i],
                                message.bases[i], message.startTick);
                        }
                    }
                }
            });
        }
        return null;
    }
}
