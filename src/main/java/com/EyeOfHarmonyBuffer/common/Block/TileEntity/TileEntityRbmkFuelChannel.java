package com.EyeOfHarmonyBuffer.common.Block.TileEntity;

import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.RbmkDanceDriver;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics.RbmkDanceMath;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * RBMK 通道基座标记 TE。
 * <p>
 * 作为渲染锚点：当 8 格通道成型时，由对应的 TESR 绘制整根 {@code FuelTube} 模型。
 * <p>
 * <b>渲染盒</b>：Angelica 按 TE 的渲染包围盒做视锥剔除。本 TE 已在 ClientProxy 注册为
 * {@code DYNAMIC}，包围盒每帧重算（跟随跳舞的棒），因此必须覆写
 * {@link #getRenderBoundingBox()} 返回真实覆盖范围，否则贴近侧面/棒被顶高时会被剔除。
 * <p>
 * <b>Y 轴偏移（每根棒独立控制）</b>，两种来源：
 * <ul>
 *   <li>直接同步：{@link #setYOffsetAndSync(double)}（服务端）→ 客户端 {@link #getRenderYOffset()}
 *       在两帧同步值之间做平滑插值。</li>
 *   <li>跳舞（种子版）：{@link #setDanceState(long,int,int,int,double,long)}（收到窗口广播后），
 *       客户端用 {@link RbmkDanceMath} + 世界时间逐帧无缝计算，无需逐帧同步。</li>
 * </ul>
 */
public class TileEntityRbmkFuelChannel extends TileEntity {

    /** 直接同步的目标 Y 偏移（格，正数向上）。 */
    private double yOffset = 0.0D;
    /** 客户端平滑：上一同步值 + 最近同步 tick。 */
    private double prevYOffset = 0.0D;
    private long lastSyncTick = -1L;

    /** 跳舞状态（客户端）：种子、坐标、基准位、窗口起始 tick。 */
    private boolean dancing = false;
    private long danceSeed;
    private int danceX, danceY, danceZ;
    private double danceBase;
    private long danceStartTick;

    /** 机器注册通道时可选打标（channelId）。 */
    private long channelId = -1L;

    public void setChannelId(long channelId) {
        this.channelId = channelId;
    }

    public long getChannelId() {
        return channelId;
    }

    // ==================== 直接同步（服务端 -> 客户端） ====================

    /** 服务端调用：设置 Y 偏移并推送更新到客户端。注意节流（建议 5~10Hz 或仅变化明显时）。 */
    public void setYOffsetAndSync(double yOffset) {
        setYOffset(yOffset);
        if (worldObj != null && !worldObj.isRemote) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    /** 设置目标 Y 偏移（仅本端生效）。 */
    public void setYOffset(double yOffset) {
        this.yOffset = yOffset;
    }

    /** 目标 Y 偏移。 */
    public double getYOffset() {
        return yOffset;
    }

    // ==================== 跳舞（种子版，客户端） ====================

    /** 客户端：收到跳舞窗口后设置本通道的跳舞状态。 */
    public void setDanceState(long seed, int x, int y, int z, double base, long startTick) {
        this.dancing = true;
        this.danceSeed = seed;
        this.danceX = x;
        this.danceY = y;
        this.danceZ = z;
        this.danceBase = base;
        this.danceStartTick = startTick;
    }

    /** 停止跳舞。 */
    public void clearDanceState() {
        this.dancing = false;
    }

    public boolean isDancing() {
        return dancing;
    }

    // ==================== 渲染取值（客户端每帧） ====================

    /** 跳舞窗口结束后，模型平滑回落到基准位的时长（tick）。1 秒。 */
    private static final int RETURN_TICKS = 20;

    /** 客户端每帧读取的渲染偏移：跳舞时用种子公式，否则在同步值间插值平滑。 */
    public double getRenderYOffset() {
        if (dancing && worldObj != null) {
            long now = worldObj.getTotalWorldTime();
            long windowEnd = danceStartTick + RbmkDanceDriver.WINDOW_TICKS;
            if (now >= windowEnd) {
                // 窗口结束：从最后跳舞位置平滑回落到基准位；回落后结束跳舞
                double lastDance = danceBase + RbmkDanceMath.danceOffset(
                    danceSeed, danceX, danceY, danceZ, RbmkDanceDriver.WINDOW_TICKS / 20.0D);
                long elapsed = now - windowEnd;
                if (elapsed >= RETURN_TICKS) {
                    dancing = false;
                    this.prevYOffset = danceBase;
                    this.yOffset = danceBase;
                    this.lastSyncTick = now;
                    return danceBase;
                }
                double k = elapsed / (double) RETURN_TICKS;
                k = k * k * (3.0D - 2.0D * k); // smoothstep
                return lastDance + (danceBase - lastDance) * k;
            }
            double seconds = (now - danceStartTick) / 20.0D;
            return danceBase + RbmkDanceMath.danceOffset(danceSeed, danceX, danceY, danceZ, seconds);
        }
        return interpolatedYOffset();
    }

    /** 同步值之间的平滑插值（约 2 tick 内平滑到目标）。 */
    private double interpolatedYOffset() {
        if (worldObj == null || lastSyncTick < 0) {
            return yOffset;
        }
        long dt = worldObj.getTotalWorldTime() - lastSyncTick;
        if (dt <= 0) {
            return yOffset;
        }
        double k = Math.min(1.0D, dt / 2.0D);
        k = k * k * (3.0D - 2.0D * k); // smoothstep
        return prevYOffset + (yOffset - prevYOffset) * k;
    }

    // ==================== 网络 / NBT ====================

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("yOffset", yOffset);
        tag.setLong("channelId", channelId);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        NBTTagCompound tag = pkt.func_148857_g();
        if (tag != null) {
            if (tag.hasKey("yOffset")) {
                this.prevYOffset = getRenderYOffset();
                this.yOffset = tag.getDouble("yOffset");
                this.lastSyncTick = worldObj == null ? -1 : worldObj.getTotalWorldTime();
            }
            if (tag.hasKey("channelId")) {
                this.channelId = tag.getLong("channelId");
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setDouble("yOffset", yOffset);
        tag.setLong("channelId", channelId);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.yOffset = tag.getDouble("yOffset");
        this.channelId = tag.getLong("channelId");
    }

    // ==================== 渲染盒（DYNAMIC，客户端每帧重算） ====================

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        double ro = getRenderYOffset();
        double minY = this.yCoord + Math.min(0.0D, ro);
        double maxY = this.yCoord + 8.0D + Math.max(0.0D, ro);
        return AxisAlignedBB.getBoundingBox(
            this.xCoord, minY, this.zCoord,
            this.xCoord + 1, maxY, this.zCoord + 1);
    }
}
