package com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record ClimateSample(double temperature, double humidity, double rainfall) {}
