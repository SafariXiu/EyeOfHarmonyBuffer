package com.EyeOfHarmonyBuffer.space.talos.chunk.coastline;

import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;

public interface CoastlineProvider {

    CoastlineSample sample(int blockX, int blockZ, MacroTag macroTag);

}
