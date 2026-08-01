package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids;

public final class SupercontinentId {
    public final int cellX;
    public final int cellZ;

    public SupercontinentId(int cellX, int cellZ) {
        this.cellX = cellX;
        this.cellZ = cellZ;
    }

    public int toInt() {
        int x17 = cellX & 0x1FFFF;
        int z17 = cellZ & 0x1FFFF;
        int id = (x17 << 17) | z17;
        return id != 0 ? id : 1;
    }

    public static SupercontinentId fromInt(int id) {
        int x17 = (id >>> 17) & 0x1FFFF;
        int z17 = id & 0x1FFFF;
        // 17 位有符号扩展：覆盖 ±65536 个格点（40000 格距下为 ±26.2 亿格块）
        int cellX = (x17 << 15) >> 15;
        int cellZ = (z17 << 15) >> 15;
        return new SupercontinentId(cellX, cellZ);
    }
}
