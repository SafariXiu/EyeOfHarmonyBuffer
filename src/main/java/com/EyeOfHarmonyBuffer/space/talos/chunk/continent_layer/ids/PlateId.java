package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids;

public final class PlateId {
    public final SupercontinentId superId;
    public final int localPlateIndex; // 0..N-1

    public PlateId(SupercontinentId superId, int localPlateIndex) {
        this.superId = superId;
        this.localPlateIndex = localPlateIndex;
    }

    public int toInt() {
        int superPacked = superId.toInt();
        int idx = localPlateIndex & 0x7;
        int id = (superPacked << 3) ^ idx;
        return id != 0 ? id : 1;
    }

    public static PlateId fromInt(int plateId) {
        int idx = plateId & 0x7;
        int superPacked = plateId >>> 3;
        SupercontinentId sid = SupercontinentId.fromInt(superPacked);
        return new PlateId(sid, idx);
    }
}
