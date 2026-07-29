package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids;

public final class SupercontinentId {
    public final int cellX;
    public final int cellZ;

    public SupercontinentId(int cellX, int cellZ) {
        this.cellX = cellX;
        this.cellZ = cellZ;
    }

    public int toInt() {
        int x16 = cellX & 0xFFFF;
        int z16 = cellZ & 0xFFFF;
        int id = (x16 << 16) | z16;
        return id != 0 ? id : 1;
    }

    public static SupercontinentId fromInt(int id) {
        int x16 = (id >>> 16) & 0xFFFF;
        int z16 = id & 0xFFFF;
        int cellX = (short) x16;
        int cellZ = (short) z16;
        return new SupercontinentId(cellX, cellZ);
    }
}
