package com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.Random;

public class WorldGenYuanShiDepositTalos {

    public void generateAt(World world, Random rand, int baseX, int baseZ,
                           Block oreBlock, Block coreBlock) {

        if (!isAreaSuitable(world, baseX, baseZ)) {
            return;
        }

        generateDepositInternal(world, baseX, baseZ, rand, oreBlock, coreBlock);
    }

    private int findGroundYAt(World world, int x, int z) {
        int y = world.getTopSolidOrLiquidBlock(x, z);
        if (y <= 0) return -1;

        int max = world.getActualHeight() - 1;
        if (y > max) y = max;

        return y - 1;
    }

    private boolean isAreaSuitable(World world, int baseX, int baseZ) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int dx = 0; dx < 12; dx++) {
            for (int dz = 0; dz < 12; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;

                int y = findGroundYAt(world, x, z);
                if (y <= 0) return false;

                Block b = world.getBlock(x, y, z);
                if (b != Blocks.grass && b != Blocks.stone) {
                    return false;
                }

                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }

        return (maxY - minY) <= 2;
    }

    private void generateDepositInternal(World world, int baseX, int baseZ, Random rand,
                                         Block oreBlock, Block coreBlock) {

        final int size = 12;

        int cx = 3 + rand.nextInt(6); // [3,8] 内的随机中心
        int cz = 3 + rand.nextInt(6);

        int peakHeight = 1 + rand.nextInt(4); // 丘高 1-4 格

        double maxRadius = 5.5D; // 占地半径
        double jitterScale = 0.8D; // 轮廓抖动

        boolean corePlaced = false;

        int fallbackCoreX = baseX + cx;
        int fallbackCoreZ = baseZ + cz;
        int fallbackCoreY = -1;

        int[][] groundY = new int[size][size];
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                groundY[dx][dz] = -1;
            }
        }

        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {

                int x = baseX + dx;
                int z = baseZ + dz;

                int columnSurfaceY = findGroundYAt(world, x, z);
                if (columnSurfaceY <= 0) {
                    continue;
                }

                int columnBaseY = columnSurfaceY;
                groundY[dx][dz] = columnBaseY;

                double distX = dx - cx + (rand.nextDouble() - 0.5D) * jitterScale;
                double distZ = dz - cz + (rand.nextDouble() - 0.5D) * jitterScale;
                double dist = Math.sqrt(distX * distX + distZ * distZ);

                if (dist > maxRadius) {
                    continue;
                }

                double heightRatio = 1.0D - (dist / maxRadius);
                if (heightRatio <= 0) {
                    continue;
                }

                int localHeight = (int) Math.round(heightRatio * peakHeight);
                if (localHeight <= 0) {
                    continue;
                }

                int topY = columnBaseY + localHeight;

                for (int y = columnBaseY + 1; y <= topY + 1; y++) {
                    if (!world.isAirBlock(x, y, z)) {
                        world.setBlockToAir(x, y, z);
                    }
                }

                for (int y = columnBaseY + 1; y <= topY; y++) {
                    world.setBlock(x, y, z,
                        oreBlock,
                        0, 2);
                }

                if (dx == cx && dz == cz) {
                    fallbackCoreY = topY;
                }

                if (!corePlaced && localHeight == peakHeight && dx == cx && dz == cz) {
                    world.setBlock(x, topY, z,
                        coreBlock,
                        0, 2);
                    corePlaced = true;
                }
            }
        }

        if (!corePlaced && fallbackCoreY > 0) {
            int x = fallbackCoreX;
            int z = fallbackCoreZ;
            int columnSurfaceY = findGroundYAt(world, x, z);
            if (columnSurfaceY > 0) {
                int topY = columnSurfaceY + peakHeight;

                for (int y = columnSurfaceY + 1; y < topY; y++) {
                    world.setBlock(x, y, z,
                        oreBlock,
                        0, 2);
                }
                world.setBlock(x, topY, z,
                    coreBlock,
                    0, 2);
            }
        }

        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {

                int x = baseX + dx;
                int z = baseZ + dz;

                int columnBaseY = groundY[dx][dz];
                if (columnBaseY <= 0) {
                    continue;
                }

                int baseY = columnBaseY + 1;

                // 只在这次生成的矿块上套石壳
                if (world.getBlock(x, baseY, z) != oreBlock) {
                    continue;
                }

                double distX = dx - cx;
                double distZ = dz - cz;
                double dist  = Math.sqrt(distX * distX + distZ * distZ);

                double inner = maxRadius - 2.0D;
                double outer = maxRadius + 0.8D;

                if (dist < inner || dist > outer) {
                    continue;
                }

                world.setBlock(x, baseY, z,
                    Blocks.stone,
                    0, 2);

                int below = baseY - 1;
                if (world.isAirBlock(x, below, z)) {
                    while (below > 0 && world.isAirBlock(x, below, z)) {
                        world.setBlock(x, below, z, Blocks.stone, 0, 2);
                        below--;
                    }
                }
            }
        }
    }
}
