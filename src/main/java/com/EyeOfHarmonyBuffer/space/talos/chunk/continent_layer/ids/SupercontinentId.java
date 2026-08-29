package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids;

public final class SupercontinentId {
    public final int cellX;
    public final int cellZ;

    public SupercontinentId(int cellX, int cellZ) {
        this.cellX = cellX;
        this.cellZ = cellZ;
    }

    public int toInt() {
        // 16 位有符号格点 + 0x8000 偏移：
        //   - 旧实现 (x17 << 17) | z17 是 17+17=34 位，int 溢出丢失 x17 的最高位，
        //     导致负 cellX 解码错乱（cellX=-1 -> 32767，大陆中心被算到 13.1 亿格，
        //     河网实例化到世界外、TP 指令拿到非法坐标）；
        //   - 现在 x16+0x8000 ∈ [0x8000, 0x17FFF]，<<16 后 x 部分恒非 0，
        //     id 永不为 0（0 保留给「无大陆」哨兵），且 32 位内永不溢出；
        //   - 低 16 位取 & 0xFFFF，避免 z 部分进位污染 x 部分；
        //   - 有效范围 ±32767 布点格（40000 格距 = ±13.1 亿格块，远超世界边界
        //     ±3000 万格）；cellX=-32768 边界理论碰撞但对应世界坐标不可达。
        int x16 = cellX & 0xFFFF;
        int z16 = cellZ & 0xFFFF;
        return ((x16 + 0x8000) << 16) | ((z16 + 0x8000) & 0xFFFF);
    }

    public static SupercontinentId fromInt(int id) {
        int x16 = ((id >>> 16) & 0xFFFF) - 0x8000;
        int z16 = (id & 0xFFFF) - 0x8000;
        return new SupercontinentId(x16, z16);
    }
}
