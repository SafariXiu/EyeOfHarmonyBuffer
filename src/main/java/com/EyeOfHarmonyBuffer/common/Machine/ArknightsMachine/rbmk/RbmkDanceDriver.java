package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk;

import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics.RbmkDanceMath;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics.RbmkRodSystem;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics.RodState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * RBMK 控制棒/燃料棒"跳舞"驱动（服务端）。
 * <p>
 * 每 5 秒开启一个窗口：从堆芯随机挑 20~30 根棒，用窗口种子驱动它们上下乱窜
 * （{@link RbmkDanceMath}）。服务端物理按公式逐 tick 写入棒位；客户端用同一套
 * 公式无缝动画，只需在窗口开始时广播一次窗口数据。
 * <p>
 * 机器侧接入：
 * <pre>
 *   RbmkDanceDriver driver = new RbmkDanceDriver();
 *   // 机器每 tick：
 *   driver.tick(world, rodSystem, channelId -> 该通道基座世界坐标或 null);
 * </pre>
 */
public class RbmkDanceDriver {

    /** 窗口时长（tick）。5 秒。 */
    public static final int WINDOW_TICKS = 100;
    /** 每窗口最少/最多舞者数。 */
    public static final int DANCERS_MIN = 20;
    public static final int DANCERS_MAX = 30;

    /** 服务端用：channelId -> 通道基座世界坐标 {x,y,z}；找不到返回 null。 */
    public interface ChannelLocator {
        int[] locate(long channelId);
    }

    /** 一根舞者。 */
    public static class Dancer {
        public final long channelId;
        public final int x;
        public final int y;
        public final int z;
        /** 舞蹈基准位（本窗口开始时的 positionBlocks）。 */
        public final double basePos;

        public Dancer(long channelId, int x, int y, int z, double basePos) {
            this.channelId = channelId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.basePos = basePos;
        }
    }

    private final Random random = new Random();
    private long windowStartTick = -1;
    private long seed;
    private final List<Dancer> dancers = new ArrayList<Dancer>();

    public long getWindowStartTick() { return windowStartTick; }
    public long getSeed() { return seed; }
    public List<Dancer> getDancers() { return dancers; }

    /**
     * 每 tick 调用。驱动窗口、写服务端棒位；窗口开始时广播给客户端。
     *
     * @param world   服务端世界
     * @param rods    棒系统（机器持有）
     * @param locator channelId -> 通道基座世界坐标
     */
    public void tick(World world, RbmkRodSystem rods, ChannelLocator locator) {
        if (world == null || world.isRemote || rods == null) {
            return;
        }
        long t = world.getTotalWorldTime();
        if (windowStartTick < 0 || t - windowStartTick >= WINDOW_TICKS) {
            startNewWindow(world, t, rods, locator);
        }
        if (dancers.isEmpty()) {
            return;
        }
        double seconds = (t - windowStartTick) / 20.0D;
        for (Dancer d : dancers) {
            RodState rod = rods.getChannel(d.channelId);
            if (rod != null) {
                rod.positionBlocks = d.basePos + RbmkDanceMath.danceOffset(seed, d.x, d.y, d.z, seconds);
            }
        }
    }

    /** 主动停止跳舞（清空当前窗口）。 */
    public void clear() {
        dancers.clear();
        windowStartTick = -1;
    }

    private void startNewWindow(World world, long tick, RbmkRodSystem rods, ChannelLocator locator) {
        windowStartTick = tick;
        seed = random.nextLong();
        dancers.clear();

        List<RodState> all = new ArrayList<RodState>(rods.allRods());
        if (all.isEmpty()) {
            return;
        }
        int count = DANCERS_MIN + random.nextInt(DANCERS_MAX - DANCERS_MIN + 1);
        Collections.shuffle(all, random);
        for (int i = 0; i < Math.min(count, all.size()); i++) {
            RodState rod = all.get(i);
            int[] pos = locator == null ? null : locator.locate(rod.channelId);
            if (pos == null) {
                continue;
            }
            dancers.add(new Dancer(rod.channelId, pos[0], pos[1], pos[2], rod.positionBlocks));
        }
        if (!dancers.isEmpty()) {
            RbmkDanceNetwork.sendWindow(world, this);
        }
    }
}
