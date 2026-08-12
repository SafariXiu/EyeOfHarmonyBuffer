package com.EyeOfHarmonyBuffer.common.dyson;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

/**
 * 戴森球服务端统一入口：改状态、广播、登录同步都走这里，
 * 后续发射机/接收机等机器只调用 {@link #update} 即可。
 */
public final class DysonSphereSystem {

    private DysonSphereSystem() {}

    /**
     * 统一更新云/框架数量与归属（服务端）。
     * 数值没有实际变化时不广播；阶段/进度由双参数统一推导。
     */
    public static void update(World world, int cloudCount, int frameCount, String ownerName) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null) {
            return;
        }
        int cloud = Math.max(0, Math.min(DysonSphereState.CLOUD_CAP, cloudCount));
        int frame = Math.max(0, Math.min(DysonSphereState.FRAME_COMPLETE, frameCount));
        String owner = ownerName == null ? "" : ownerName;
        if (data.getCloudCount() == cloud && data.getFrameCount() == frame && data.getOwnerName().equals(owner)) {
            return;
        }
        int stage = computeStage(cloud, frame);
        float progress = Math.max(
            (float) cloud / DysonSphereState.CLOUD_CAP,
            (float) frame / DysonSphereState.FRAME_COMPLETE);
        data.setState(stage, progress, cloud, frame, owner);
        syncToAll(world);
    }

    /** 把当前状态广播给所有在线玩家（服务端）。 */
    public static void syncToAll(World world) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null) {
            return;
        }
        DysonSphereNetwork.INSTANCE.sendToAll(toPacket(data));
    }

    /** 把当前状态单独发给某个玩家（登录/进入维度时同步）。 */
    public static void syncToPlayer(EntityPlayerMP player) {
        DysonSphereWorldData data = DysonSphereWorldData.get(player.worldObj);
        if (data == null) {
            return;
        }
        DysonSphereNetwork.INSTANCE.sendTo(toPacket(data), player);
    }

    /** 阶段由云/框架双参数统一推导，存档/指令/机器共用同一套规则。 */
    public static int computeStage(int cloud, int frame) {
        if (frame >= DysonSphereState.FRAME_COMPLETE) {
            return DysonSphereState.STAGE_COMPLETE;
        }
        if (frame >= DysonSphereState.FRAME_STAGE_3) {
            return DysonSphereState.STAGE_FRAME_80;
        }
        if (frame >= DysonSphereState.FRAME_STAGE_2) {
            return DysonSphereState.STAGE_HALF;
        }
        if (frame >= DysonSphereState.FRAME_MIN) {
            return DysonSphereState.STAGE_FRAME_25;
        }
        return cloud > 0 ? DysonSphereState.STAGE_CLOUD : DysonSphereState.STAGE_NONE;
    }

    private static PacketDysonSphereState toPacket(DysonSphereWorldData data) {
        return new PacketDysonSphereState(
            data.getStage(), data.getProgress(), data.getCloudCount(),
            data.getFrameCount(), data.getOwnerName());
    }
}
