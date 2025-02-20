package com.EyeOfHarmonyBuffer.common.Block.TileEntity;

import net.minecraft.tileentity.TileEntity;

public class TileEntityWindmill extends TileEntity {

    private float rotation = 0;

    @Override
    public void updateEntity() {
        if(!worldObj.isRemote) {
        } else {
            rotation += 1.0F;
            if(rotation >= 360.0F) {
                rotation = 0;
            }
        }
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
