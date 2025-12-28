package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;

public interface CoastlineAtlas {
    boolean isLand(int x, int z);
    int distanceToCoast(int x, int z);
    int beachWidth(int x, int z, MacroBiome macroHint);
    int shelfWidth(int x, int z, MacroBiome macroHint);
}
