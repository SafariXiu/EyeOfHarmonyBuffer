package com.EyeOfHarmonyBuffer.common.Block.TileEntity;

import net.minecraft.tileentity.TileEntity;

public class TileEntityForgeOfTheSkyCore extends TileEntity {

    public int getFacing() {
        return this.worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 40000.0;
    }
}
