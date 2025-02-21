package com.EyeOfHarmonyBuffer.common.Block.TileEntity;

import net.minecraft.tileentity.TileEntity;

public class TileEntityWindmill extends TileEntity {

    private float rotation = 0;

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            rotation += 1.0F;
            if (rotation >= 360.0F) {
                rotation = 0;
            }
        }
    }

    public int getFacing() {
        return this.worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
    }

    public float getRotation() {
        return rotation;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 40000.0;
    }

    @Override
    public boolean canUpdate() {
        return true;
    }
}
