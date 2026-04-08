package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * =====================================================
 * 类名：PlateCenter
 * 来源：Python 模块 worldgen_core.PlateCenter
 * 功能：
 *   - 表示一个超级大陆内部的“板块/子大陆”中心；
 *   - 包含自身坐标、所属超级大陆ID、板块ID和半径。
 * =====================================================
 */

public class PlateCenter {
    public int superId;
    public int continentId;
    public double worldX;
    public double worldZ;
    public double radius;

    public PlateCenter(int superId, int continentId, double worldX, double worldZ, double radius) {
        this.superId = superId;
        this.continentId = continentId;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.radius = radius;
    }
}
