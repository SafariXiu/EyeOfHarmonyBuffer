package com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample;

import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import net.minecraft.util.MathHelper;

public final class MacroSample {

    private final MacroTag primary;
    private final MacroTag secondary;
    private final double blendPrimary;
    private final byte tier;
    private final short plateId;
    private final float plateauHeight;
    private final float anchorWeight;
    private final float hardEdge;
    private final short macroBaseHeight;
    private final byte patchVariant;
    private final boolean patchSingleBiome;
    private final double patchEdgeBlend;

    public MacroSample(MacroTag primary,
                       MacroTag secondary,
                       double blendPrimary,
                       byte tier,
                       short plateId,
                       float plateauHeight,
                       float anchorWeight,
                       float hardEdge,
                       short macroBaseHeight,
                       byte patchVariant,
                       boolean patchSingleBiome,
                       double patchEdgeBlend) {

        this.primary = primary;
        this.secondary = secondary;
        this.blendPrimary = MathHelper.clamp_double(blendPrimary, 0.0D, 1.0D);
        this.tier = tier;
        this.plateId = plateId;
        this.plateauHeight = plateauHeight;
        this.anchorWeight = anchorWeight;
        this.hardEdge = hardEdge;
        this.macroBaseHeight = macroBaseHeight;
        this.patchVariant = patchVariant;
        this.patchSingleBiome = patchSingleBiome;
        this.patchEdgeBlend = MathHelper.clamp_double(patchEdgeBlend, 0.0D, 1.0D);
    }

    public MacroTag primary() { return primary; }
    public MacroTag secondary() { return secondary; }
    public double blendPrimary() { return blendPrimary; }
    public byte tier() { return tier; }
    public short plateId() { return plateId; }
    public float plateauHeight() { return plateauHeight; }
    public float anchorWeight() { return anchorWeight; }
    public float hardEdge() { return hardEdge; }
    public short macroBaseHeight() { return macroBaseHeight; }
    public byte patchVariant() { return patchVariant; }
    public boolean patchSingleBiome() { return patchSingleBiome; }
    public double patchEdgeBlend() { return patchEdgeBlend; }
}
