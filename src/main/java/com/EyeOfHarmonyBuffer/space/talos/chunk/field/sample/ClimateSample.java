package com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample;

import com.github.bsideup.jabel.Desugar;

/**
 * temperature/humidity/rainfall 仍为 0~1。
 * windDirection 以弧度表示（0~2π，0 指东，逆时针为正）。
 * windSpeed 归一化为 0~1。
 * seasonPhase 范围 [0,1)，0 表示季节起点。
 */
@Desugar
public record ClimateSample(
    double temperature,
    double humidity,
    double rainfall,
    double windDirection,
    double windSpeed,
    double seasonPhase
) {}
