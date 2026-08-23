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
 * 服务端 -> 客户端：RBMK 跳舞轮次广播（含多套舞者，错开接力）。
 * <p>
 * 每轮（5 秒）广播一次：每套携带 种子 + 起跳 tick + 舞者坐标 + 基准位。
 * 客户端用 {@link RbmkDanceMath} 与当前世界时间无缝推出每帧棒位。
 */
public class PacketRbmkDance implements IMessage, IMessageHandler<PacketRbmkDance, IMessage> {

    private int subwaveCount;
    private long[] seeds;
    private long[] startTicks;
    private int[] counts;
    private int[] xs;
    private int[] ys;
    private int[] zs;
    private double[] bases;

    public PacketRbmkDance() {
    }

    public PacketRbmkDance(List<RbmkDanceDriver.SubWave> subWaves) {
        this.subwaveCount = subWaves.size();
        this.seeds = new long[subwaveCount];
        this.startTicks = new long[subwaveCount];
        this.counts = new int[subwaveCount];
        int total = 0;
        for (int w = 0; w < subwaveCount; w++) {
            RbmkDanceDriver.SubWave sw = subWaves.get(w);
            this.seeds[w] = sw.seed;
            this.startTicks[w] = sw.startTick;
            this.counts[w] = sw.dancers.size();
            total += sw.dancers.size();
        }
        this.xs = new int[total];
        this.ys = new int[total];
        this.zs = new int[total];
        this.bases = new double[total];
        int idx = 0;
        for (RbmkDanceDriver.SubWave sw : subWaves) {
            for (RbmkDanceDriver.Dancer d : sw.dancers) {
                this.xs[idx] = d.x;
                this.ys[idx] = d.y;
                this.zs[idx] = d.z;
                this.bases[idx] = d.basePos;
                idx++;
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(subwaveCount);
        for (int w = 0; w < subwaveCount; w++) {
            buf.writeLong(seeds[w]);
            buf.writeLong(startTicks[w]);
            buf.writeInt(counts[w]);
        }
        for (int i = 0; i < xs.length; i++) {
            buf.writeInt(xs[i]);
            buf.writeInt(ys[i]);
            buf.writeInt(zs[i]);
            buf.writeDouble(bases[i]);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        subwaveCount = buf.readInt();
        seeds = new long[subwaveCount];
        startTicks = new long[subwaveCount];
        counts = new int[subwaveCount];
        int total = 0;
        for (int w = 0; w < subwaveCount; w++) {
            seeds[w] = buf.readLong();
            startTicks[w] = buf.readLong();
            counts[w] = buf.readInt();
            total += counts[w];
        }
        xs = new int[total];
        ys = new int[total];
        zs = new int[total];
        bases = new double[total];
        for (int i = 0; i < total; i++) {
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
                    int idx = 0;
                    for (int w = 0; w < message.subwaveCount; w++) {
                        for (int j = 0; j < message.counts[w]; j++, idx++) {
                            TileEntity te = world.getTileEntity(message.xs[idx], message.ys[idx], message.zs[idx]);
                            if (te instanceof TileEntityRbmkFuelChannel) {
                                ((TileEntityRbmkFuelChannel) te).setDanceState(
                                    message.seeds[w], message.xs[idx], message.ys[idx], message.zs[idx],
                                    message.bases[idx], message.startTicks[w]);
                            }
                        }
                    }
                }
            });
        }
        return null;
    }
}
