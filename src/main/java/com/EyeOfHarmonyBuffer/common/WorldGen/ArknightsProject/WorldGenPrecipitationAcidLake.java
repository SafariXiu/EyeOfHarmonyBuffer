package com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.Random;

public class WorldGenPrecipitationAcidLake {

    private final Block fluidBlock;

    public WorldGenPrecipitationAcidLake(Block fluidBlock) {
        this.fluidBlock = fluidBlock;
    }

    public void generateAt(World world, Random rand, int baseX, int baseZ) {
        if (fluidBlock == null) {
            return;
        }

        int groundY = findGroundY(world, baseX, baseZ);
        if (groundY <= 0) {
            return;
        }

        Block top = world.getBlock(baseX, groundY, baseZ);
        if (top != Blocks.grass && top != Blocks.stone) {
            return;
        }

        if (!isAreaFlatEnough(world, baseX, groundY, baseZ, 7, 3)) {
            return;
        }

        generateLakeShape(world, rand, baseX, groundY, baseZ);
    }

    private int findGroundY(World world, int x, int z) {
        int y = world.getTopSolidOrLiquidBlock(x, z);
        if (y <= 0) return -1;

        int max = world.getActualHeight() - 1;
        if (y > max) y = max;

        return y - 1;
    }

    private boolean isAreaFlatEnough(World world, int centerX, int centerY, int centerZ,
                                     int radius, int maxDiff) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;

                int y = findGroundY(world, x, z);
                if (y <= 0) return false;

                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }

        return (maxY - minY) <= maxDiff;
    }

    private void generateLakeShape(World world, Random rand,
                                   int centerX, int groundY, int centerZ) {

        double baseRadiusX = 5.0D + rand.nextDouble() * 3.0D;
        double baseRadiusZ = 5.0D + rand.nextDouble() * 3.0D;

        double jitter = 0.8D;
        double noiseScale = 0.35D;

        int globalBottomY = groundY;

        for (int dx = (int) -Math.ceil(baseRadiusX) - 2; dx <= (int) Math.ceil(baseRadiusX) + 2; dx++) {
            for (int dz = (int) -Math.ceil(baseRadiusZ) - 2; dz <= (int) Math.ceil(baseRadiusZ) + 2; dz++) {

                int x = centerX + dx;
                int z = centerZ + dz;

                double nx = dx + (rand.nextDouble() - 0.5D) * jitter;
                double nz = dz + (rand.nextDouble() - 0.5D) * jitter;

                double d = (nx * nx) / (baseRadiusX * baseRadiusX)
                    + (nz * nz) / (baseRadiusZ * baseRadiusZ);

                if (d > 1.35D) {
                    continue;
                }

                double centerFactor = 1.0D - Math.min(1.0D, d);
                double depthBase = 4.0D + centerFactor; // [4,5]
                double noise = (rand.nextDouble() - 0.5D) * noiseScale;
                int depth = (int) Math.round(depthBase + noise);

                if (depth < 4) depth = 4;
                if (depth > 5) depth = 5;

                int bottomY = carveAndFillColumn(world, x, groundY, z, depth);
                if (bottomY < globalBottomY) {
                    globalBottomY = bottomY;
                }
            }
        }

        buildStoneWall(world, rand, centerX, groundY, centerZ,
            baseRadiusX, baseRadiusZ, globalBottomY);
    }

    private int carveAndFillColumn(World world, int groundX, int groundY, int groundZ,
                                   int depth) {

        int bottomY = groundY - depth;
        if (bottomY < 1) {
            bottomY = 1;
        }

        for (int y = groundY + 1; y <= groundY + 3; y++) {
            if (!world.isAirBlock(groundX, y, groundZ)) {
                world.setBlockToAir(groundX, y, groundZ);
            }
        }

        for (int y = groundY; y >= bottomY; y--) {
            world.setBlock(groundX, y, groundZ, Blocks.stone, 0, 2);
        }

        for (int y = bottomY + 1; y <= groundY; y++) {
            world.setBlock(groundX, y, groundZ, fluidBlock, 0, 2);
        }

        return bottomY;
    }

    private void buildStoneWall(World world, Random rand,
                                int centerX, int groundY, int centerZ,
                                double baseRadiusX, double baseRadiusZ,
                                int globalBottomY) {

        double inner = 1.0D;
        double outer = 1.6D;

        int wallTopY = groundY + 1;
        int wallBottomY = globalBottomY;

        if (wallBottomY < 1) wallBottomY = 1;

        for (int dx = (int) -Math.ceil(baseRadiusX) - 3; dx <= (int) Math.ceil(baseRadiusX) + 3; dx++) {
            for (int dz = (int) -Math.ceil(baseRadiusZ) - 3; dz <= (int) Math.ceil(baseRadiusZ) + 3; dz++) {

                int x = centerX + dx;
                int z = centerZ + dz;

                double nx = dx;
                double nz = dz;

                double d = (nx * nx) / (baseRadiusX * baseRadiusX)
                    + (nz * nz) / (baseRadiusZ * baseRadiusZ);

                if (d <= inner || d > outer) {
                    continue;
                }

                for (int y = wallBottomY; y <= wallTopY; y++) {
                    Block b = world.getBlock(x, y, z);

                    if (b == fluidBlock) {
                        continue;
                    }

                    world.setBlock(x, y, z, Blocks.stone, 0, 2);
                }
            }
        }
    }
}
