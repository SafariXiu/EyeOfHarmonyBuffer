package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

/**
 * 世界屏生成工具：把一块已注册的屏放进世界。
 * 屏蔽"创建实体、摆位置、面向、挂屏、绑定关闭回调"的样板 —— 业务只需说
 * "在玩家面前放一块屏"，不用再碰 Vec3/左方向/实体等细节。
 */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public final class HoloSpawn {

    private HoloSpawn() {}

    /** 在玩家视线前方 baseDist 格处生成一块注册屏。
     *
     * @param player   玩家（屏朝向他）
     * @param id       注册的屏类型 id（HoloScreenRegistry）
     * @param baseDist 视线正前方距离（格）
     * @param sideOff  沿视线"左"方向偏移（>0 偏左，<0 偏右；用于多屏并排）
     * @param depthOff 沿视线方向额外深度偏移（>0 离玩家更远；用于把多块屏的平面错开，避免 z-fighting）
     * @return 生成的实体；未注册或世界不可用时返回 null
     */
    public static HoloEntity spawnInFront(EntityPlayer player, String id,
                                          double baseDist, double sideOff, double depthOff) {
        if (player == null || player.worldObj == null) {
            return null;
        }
        HoloScreen screen = HoloScreenRegistry.create(id);
        if (screen == null) {
            return null;
        }
        Vec3 look = player.getLookVec();
        // 玩家视线"左"方向 = cross(look, up)
        Vec3 left = look.crossProduct(Vec3.createVectorHelper(0, 1, 0)).normalize();
        double bx = player.posX + look.xCoord * baseDist;
        double by = player.posY + player.getEyeHeight() + look.yCoord * baseDist;
        double bz = player.posZ + look.zCoord * baseDist;

        HoloEntity e = new HoloEntity(player.worldObj);
        e.setScreen(screen);
        e.setPosition(
            bx - left.xCoord * sideOff + look.xCoord * depthOff,
            by + look.yCoord * depthOff,
            bz - left.zCoord * sideOff + look.zCoord * depthOff);
        player.worldObj.spawnEntityInWorld(e);
        return e;
    }
}
