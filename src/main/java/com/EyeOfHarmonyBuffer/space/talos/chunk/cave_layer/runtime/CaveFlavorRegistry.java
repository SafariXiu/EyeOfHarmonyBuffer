package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.format.CaveTag;

import java.util.ArrayList;
import java.util.List;

/**
 * 洞穴风味注册表：维护所有区域性洞穴风格的列表。
 *
 * 每个风味是一格 256×256 的确定性区域：用坐标 + 种子 + 独立盐哈希判定。
 * 装饰器走这里的查询（热路径用 {@link #hasTag}，避免分配列表）；
 * 外部层通过洞穴层 API 的 tagsAt 读取同一份标签。
 */
public final class CaveFlavorRegistry {

    /** 石笋区：沿用装饰器旧盐值与概率，生成结果不变。 */
    private static final int SALT_SPIKE_ZONE = 0x25;
    private static final double SPIKE_ZONE_CHANCE = 0.10;

    private static final CaveFlavor[] FLAVORS = {
        new CaveFlavor(CaveTag.SPIKE_ZONE,
            SALT_SPIKE_ZONE, SPIKE_ZONE_CHANCE)
    };

    private CaveFlavorRegistry() {}

    /** 某 256 格单元命中的所有风格标签；没有命中时为 [DEFAULT]。 */
    public static List<CaveTag> tagsForCell(int cellX, int cellZ, long seed) {
        List<CaveTag> out = new ArrayList<CaveTag>(2);
        for (CaveFlavor f : FLAVORS) {
            if (CaveMath.hash01(cellX, cellZ, 0, seed, f.salt) < f.chance) {
                out.add(f.tag);
            }
        }
        if (out.isEmpty()) {
            out.add(CaveTag.DEFAULT);
        }
        return out;
    }

    /** 装饰热路径：只判断是否属于某个标签，不分配列表。 */
    public static boolean hasTag(CaveTag tag, int cellX, int cellZ, long seed) {
        if (tag == CaveTag.DEFAULT) {
            for (CaveFlavor f : FLAVORS) {
                if (CaveMath.hash01(cellX, cellZ, 0, seed, f.salt)
                    < f.chance) {
                    return false;
                }
            }
            return true;
        }
        for (CaveFlavor f : FLAVORS) {
            if (f.tag == tag
                && CaveMath.hash01(cellX, cellZ, 0, seed, f.salt)
                    < f.chance) {
                return true;
            }
        }
        return false;
    }

    private static final class CaveFlavor {
        final CaveTag tag;
        final int salt;
        final double chance;

        CaveFlavor(CaveTag tag, int salt, double chance) {
            this.tag = tag;
            this.salt = salt;
            this.chance = chance;
        }
    }
}
