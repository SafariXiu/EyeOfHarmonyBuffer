package com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record HydroSample(double saturation, double flowRate, double aquiferLevel) {}

