package com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject;

import net.minecraft.world.World;

import java.util.Random;

public class WorldGenYuanShiVeinTalos {

    private final WorldGenYuanShiDepositTalos singleGen = new WorldGenYuanShiDepositTalos();

    public void generate(World world, Random rand, int chunkX, int chunkZ) {

        if (rand.nextInt(500) != 0) {
            return;
        }

        int veinCount = 4 + rand.nextInt(5); // [4,8]

        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;

        double minDist = 10.0D; // 最小半径
        double maxDist = 15.0D; // 最大半径

        for (int i = 0; i < veinCount; i++) {

            double angle = rand.nextDouble() * Math.PI * 2.0D;
            double dist  = minDist + rand.nextDouble() * (maxDist - minDist);

            int baseX = centerX + (int) Math.round(Math.cos(angle) * dist);
            int baseZ = centerZ + (int) Math.round(Math.sin(angle) * dist);

            singleGen.generateAt(world, rand, baseX, baseZ);
        }
    }
}
