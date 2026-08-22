package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.rbmk.physics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制棒系统总控：管理所有通道的 RodState，每 tick 驱动，输出反应性。
 * 一台机器挂一个实例；状态由机器 NBT 保存/恢复。
 */
public class RbmkRodSystem {

    private final Map<Long, RodState> rods = new LinkedHashMap<>();

    /** 注册一根通道（结构校验时调用）。initialInsertion：0=全插，1=全拔。 */
    public RodState registerChannel(long channelId, int rodType, double initialInsertion, double speed) {
        RodState st = new RodState(
            channelId,
            rodType,
            rodType == 1 ? RodPhysics.buildUaRodSegments() : RodPhysics.buildFullRodSegments(),
            initialInsertion * RbmkRodConstants.FULL_OUT,
            speed > 0 ? speed : RbmkRodConstants.DRIVE_SPEED);
        rods.put(channelId, st);
        return st;
    }

    public void removeChannel(long channelId) {
        rods.remove(channelId);
    }

    public void clear() {
        rods.clear();
    }

    /** 每 tick 驱动所有棒移动（dt 秒） */
    public void tick(double dt) {
        for (RodState rod : rods.values()) {
            RodPhysics.moveToward(rod, dt);
        }
    }

    /** AZ-5：所有棒紧急插入（走速度） */
    public void scramAll() {
        for (RodState rod : rods.values()) {
            rod.scramInsert();
        }
    }

    /** 设置某棒目标插入度（0-1）；解除 scram 标记 */
    public void setChannelInsertion(long channelId, double insertion) {
        RodState rod = rods.get(channelId);
        if (rod != null) {
            rod.setInsertion(insertion);
        }
    }

    /** 总反应性（$），所有棒各层贡献叠加 */
    public double getTotalReactivity() {
        double sum = 0;
        for (RodState rod : rods.values()) {
            sum += RodPhysics.rodTotalReactivity(rod);
        }
        return sum;
    }

    /** 第 layer 层的总反应性（$） */
    public double getLayerReactivity(int layer) {
        double sum = 0;
        for (RodState rod : rods.values()) {
            sum += RodPhysics.rodLayerReactivity(rod, layer, 1.0);
        }
        return sum;
    }

    /** 平均插入度（0=全插，1=全拔，显示用） */
    public double getAverageInsertion() {
        if (rods.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (RodState rod : rods.values()) {
            sum += rod.getInsertion();
        }
        return sum / rods.size();
    }

    public RodState getChannel(long channelId) {
        return rods.get(channelId);
    }

    public Collection<RodState> allRods() {
        return rods.values();
    }

    public int rodCount() {
        return rods.size();
    }
}