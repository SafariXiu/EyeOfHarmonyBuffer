package com.EyeOfHarmonyBuffer.common.Block.TileEntity;

import com.EyeOfHarmonyBuffer.overdomain.entity.OverdomainErosionData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import java.util.List;

public class TileEntityOverdomainErosion extends TileEntity {

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            return;
        }

        int x = xCoord;
        int y = yCoord;
        int z = zCoord;

        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(
            x, y, z,
            x + 1, y + 0.875, z + 1
        );

        @SuppressWarnings("unchecked")
        List<Entity> entities = worldObj.getEntitiesWithinAABB(Entity.class, box);

        for (Entity entity : entities) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }

            EntityLivingBase living = (EntityLivingBase) entity;

            if (living instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) living;
                if (player.capabilities.isCreativeMode) {
                    continue;
                }
            }

            OverdomainErosionData data = OverdomainErosionData.get(living);
            if (data != null) {
                data.markInErosionThisTick();
            }
        }
    }
}
