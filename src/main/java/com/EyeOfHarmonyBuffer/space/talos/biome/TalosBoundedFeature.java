package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;

/**
 * 群系装饰特征。
 *
 * 读取必须只落在当前 16×16 区块内（越界按空气处理），装饰期间绝不主动加载区块；
 * 写入允许跨到已加载的相邻一格（±1 区块）：
 * populate 阶段 3×3 邻域已经生成，跨区块写入不会触发新区块生成，
 * 因此树冠等大特征可以和原版一样跨过区块边界，不再被裁掉。
 */
public interface TalosBoundedFeature {

    /**
     * @param chunk   正在被填充的当前区块（必然已加载）
     * @param localX  区块内局部 X [0,15]
     * @param localZ  区块内局部 Z [0,15]
     */
    boolean generate(World world, Random random, Chunk chunk, int localX, int localZ);
}
