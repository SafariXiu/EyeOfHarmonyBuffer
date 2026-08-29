package com.EyeOfHarmonyBuffer.client.holo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 世界面板朝向基计算：渲染与射线拾取共用同一套，保证坐标一致。
 */
public class HoloMath {

    /** 面板局部坐标系：法向 n 朝玩家，right 向右，up2 向上（局部 v 向下）。 */
    public static class Frame {
        public float nx, ny, nz;
        public float rx, ry, rz;
        public float ux, uy, uz;
    }

    public static Frame frameFor(Entity entity, EntityPlayer player) {
        return frameForDirection(
            (float) (player.posX - entity.posX),
            (float) (player.posY + player.getEyeHeight() - entity.posY),
            (float) (player.posZ - entity.posZ));
    }

    /** 固定朝向（非公告板）：法向 = 给定方向，up 恒为 (0,1,0)，right = cross(up, n)。
     *  用于机器一体面板（renderTESR 用机器 facing 作法向，不随相机转动）。 */
    public static Frame frameForDirection(float nX, float nY, float nZ) {
        Frame f = new Frame();
        float len = (float) Math.sqrt(nX * nX + nY * nY + nZ * nZ);
        if (len < 1e-4f) {
            nX = 0f;
            nY = 0f;
            nZ = 1f;
        } else {
            nX /= len;
            nY /= len;
            nZ /= len;
        }
        // right = normalize(cross(up, n))，up = (0,1,0) → (nZ, 0, -nX)
        float rX = nZ;
        float rY = 0f;
        float rZ = -nX;
        float rl = (float) Math.sqrt(rX * rX + rY * rY + rZ * rZ);
        if (rl < 1e-4f) {
            rX = 1f;
            rY = 0f;
            rZ = 0f;
        } else {
            rX /= rl;
            rY /= rl;
            rZ /= rl;
        }
        // up2 = cross(n, right)
        float uX = nY * rZ - nZ * rY;
        float uY = nZ * rX - nX * rZ;
        float uZ = nX * rY - nY * rX;
        f.nx = nX;
        f.ny = nY;
        f.nz = nZ;
        f.rx = rX;
        f.ry = rY;
        f.rz = rZ;
        f.ux = uX;
        f.uy = uY;
        f.uz = uZ;
        return f;
    }
}
