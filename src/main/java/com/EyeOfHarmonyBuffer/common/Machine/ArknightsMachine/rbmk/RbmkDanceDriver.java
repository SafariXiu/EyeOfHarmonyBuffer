package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk;

import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics.RbmkDanceMath;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics.RbmkRodSystem;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics.RodState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * RBMK 控制棒/燃料棒"跳舞"驱动（服务端）。
 * <p>
 * <b>三套接力</b>：每一轮（5 秒）内分 3 套舞者，分别在第 0/2/4 秒起跳，每套跳 5 秒、
 * 错开接力（第 1 套 0~5s，第 2 套 2~7s，第 3 套 4~9s），营造真正混乱、不重复的观感。
 * <ul>
 *   <li>同一轮内 3 套<b>互不重复</b>（同一根棒只属于一套）；</li>
 *   <li>下一轮选角时<b>排除仍在跳的棒</b>，避免两套同时驱动同一根；</li>
 *   <li>客户端用窗口广播的种子 + 世界时间无缝动画，服务端物理写棒位。</li>
 * </ul>
 * 机器侧接入：
 * <pre>
 *   RbmkDanceDriver driver = new RbmkDanceDriver();
 *   driver.tick(world, rodSystem, channelId -> 通道基座世界坐标或 null);
 * </pre>
 */
public class RbmkDanceDriver {

    /** 一轮的时长（tick）。5 秒。 */
    public static final int CYCLE_TICKS = 100;
    /** 每轮分几套舞者。 */
    public static final int SUBWAVE_COUNT = 3;
    /** 每套之间的起跳间隔（tick）。2 秒。 */
    public static final int SUBWAVE_STAGGER_TICKS = 40;
    /** 每套的跳舞时长（tick）。5 秒。 */
    public static final int SUBWAVE_DURATION_TICKS = 100;
    /** 每套最少/最多舞者数。 */
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
        /** 舞蹈基准位（本套被选中时的 positionBlocks）。 */
        public final double basePos;

        public Dancer(long channelId, int x, int y, int z, double basePos) {
            this.channelId = channelId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.basePos = basePos;
        }
    }

    /** 一套舞者：独立种子 + 起跳 tick + 舞者列表。 */
    public static class SubWave {
        public final int index;
        public final long startTick;
        public final long seed;
        public final List<Dancer> dancers;

        public SubWave(int index, long startTick, long seed, List<Dancer> dancers) {
            this.index = index;
            this.startTick = startTick;
            this.seed = seed;
            this.dancers = dancers;
        }
    }

    private final Random random = new Random();
    private long cycleStartTick = -1;
    private final List<SubWave> subWaves = new ArrayList<SubWave>();

    public long getCycleStartTick() { return cycleStartTick; }
    public List<SubWave> getSubWaves() { return subWaves; }

    /**
     * 每 tick 调用。驱动各套舞者棒位；轮换时广播给客户端。
     */
    public void tick(World world, RbmkRodSystem rods, ChannelLocator locator) {
        if (world == null || world.isRemote || rods == null) {
            return;
        }
        long t = world.getTotalWorldTime();
        if (cycleStartTick < 0 || t - cycleStartTick >= CYCLE_TICKS) {
            startNewCycle(world, t, rods, locator);
        }
        for (SubWave sw : subWaves) {
            if (t < sw.startTick || t >= sw.startTick + SUBWAVE_DURATION_TICKS) {
                continue; // 未起跳或已结束
            }
            double seconds = (t - sw.startTick) / 20.0D;
            for (Dancer d : sw.dancers) {
                RodState rod = rods.getChannel(d.channelId);
                if (rod != null) {
                    rod.positionBlocks = d.basePos + RbmkDanceMath.danceOffset(sw.seed, d.x, d.y, d.z, seconds);
                }
            }
        }
    }

    /** 主动停止跳舞（清空当前轮次）。 */
    public void clear() {
        subWaves.clear();
        cycleStartTick = -1;
    }

    private void startNewCycle(World world, long tick, RbmkRodSystem rods, ChannelLocator locator) {
        // 仍在跳的棒（上一轮未结束的套）不参与本轮选角，避免双驱动
        Set<Long> busy = new HashSet<Long>();
        for (SubWave sw : subWaves) {
            if (tick < sw.startTick + SUBWAVE_DURATION_TICKS) {
                for (Dancer d : sw.dancers) {
                    busy.add(d.channelId);
                }
            }
        }
        cycleStartTick = tick;
        subWaves.clear();

        List<RodState> pool = new ArrayList<RodState>();
        for (RodState rod : rods.allRods()) {
            if (!busy.contains(rod.channelId)) {
                pool.add(rod);
            }
        }
        Collections.shuffle(pool, random);

        int idx = 0;
        for (int i = 0; i < SUBWAVE_COUNT; i++) {
            int count = DANCERS_MIN + random.nextInt(DANCERS_MAX - DANCERS_MIN + 1);
            count = Math.min(count, pool.size() - idx);
            List<Dancer> list = new ArrayList<Dancer>(count);
            for (int j = 0; j < count; j++) {
                RodState rod = pool.get(idx++);
                int[] pos = locator == null ? null : locator.locate(rod.channelId);
                if (pos != null) {
                    list.add(new Dancer(rod.channelId, pos[0], pos[1], pos[2], rod.positionBlocks));
                }
            }
            subWaves.add(new SubWave(i, tick + (long) i * SUBWAVE_STAGGER_TICKS, random.nextLong(), list));
        }
        if (!subWaves.isEmpty()) {
            RbmkDanceNetwork.sendWindow(world, this);
        }
    }
}
