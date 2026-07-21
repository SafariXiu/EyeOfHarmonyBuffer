package com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork;

import java.util.UUID;

public final class WirelessNodeRef {

    private final UUID ownerUUID;
    private final int dimId;
    private final int x;
    private final int y;
    private final int z;

    public WirelessNodeRef(UUID ownerUUID, int dimId, int x, int y, int z) {
        this.ownerUUID = ownerUUID;
        this.dimId = dimId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public int getDimId() {
        return dimId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WirelessNodeRef)) return false;
        WirelessNodeRef that = (WirelessNodeRef) o;
        return dimId == that.dimId &&
            x == that.x &&
            y == that.y &&
            z == that.z;
    }

    @Override
    public int hashCode() {
        int result = dimId;
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "WirelessNodeRef{" +
            "owner=" + ownerUUID +
            ", dim=" + dimId +
            ", pos=(" + x + "," + y + "," + z + ")" +
            '}';
    }
}
