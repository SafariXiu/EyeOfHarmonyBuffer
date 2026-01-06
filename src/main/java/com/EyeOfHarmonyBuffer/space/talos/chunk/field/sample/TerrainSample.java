package com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record TerrainSample(double elevation, double slope, double roughness) {}
