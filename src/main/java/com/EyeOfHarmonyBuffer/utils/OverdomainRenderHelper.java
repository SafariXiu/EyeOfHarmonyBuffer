package com.EyeOfHarmonyBuffer.utils;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlcokOverdomainErosion;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class OverdomainRenderHelper {

    public static boolean isPlayerInsidePortal(EntityPlayer player) {
        if (player == null) return false;

        double eyeX = player.posX;
        double eyeY = player.posY + player.getEyeHeight();
        double eyeZ = player.posZ;

        World world = player.worldObj;
        int x = (int)Math.floor(player.posX);
        int y = (int)Math.floor(player.posY);
        int z = (int)Math.floor(player.posZ);

        Block block = world.getBlock(x, y, z);
        if (!(block instanceof BlcokOverdomainErosion)) {
            return false;
        }

        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            x, y, z,
            x + 1, y + 1, z + 1);

        return box.isVecInside(Vec3.createVectorHelper(eyeX, eyeY, eyeZ));
    }
}
