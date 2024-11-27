package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import gregtech.common.tileentities.machines.multi.compressor.MTEBlackHoleCompressor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MTEBlackHoleCompressor.class,remap = false)
public interface BlackHoleCompressorAccessor {
    @Accessor("blackHoleStatus")
    void setBlackHoleStatus(byte blackHoleStatus);

    @Accessor("blackHoleStatus")
    byte getBlackHoleStatus();
}
