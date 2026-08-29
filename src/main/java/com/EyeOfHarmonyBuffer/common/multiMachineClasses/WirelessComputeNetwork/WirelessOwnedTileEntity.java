package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import java.util.UUID;

public abstract class WirelessOwnedTileEntity extends TileEntity {

    protected UUID placedByUUID;

    public void setPlacedBy(UUID playerUUID) {
        this.placedByUUID = playerUUID;
        this.markDirty();
    }

    public UUID getOwnerUUID() {
        return placedByUUID;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey("OwnerUUID")) {
            try {
                placedByUUID = UUID.fromString(tag.getString("OwnerUUID"));
            } catch (IllegalArgumentException e) {
                placedByUUID = null;
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        if (placedByUUID != null) {
            tag.setString("OwnerUUID", placedByUUID.toString());
        }
    }
}
