package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.MacroSample;

public interface MacroFieldProvider {

    MacroSample sample(int blockX, int blockZ);

}
