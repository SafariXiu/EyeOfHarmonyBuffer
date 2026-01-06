package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record MacroSettings(
    int cellSize,
    double plateauHeight
) {
    public static MacroSettings defaults() {
        return new MacroSettings(64, 80.0);
    }
}
