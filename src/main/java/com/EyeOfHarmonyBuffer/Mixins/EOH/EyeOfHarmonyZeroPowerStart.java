package com.EyeOfHarmonyBuffer.Mixins.EOH;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.spongepowered.asm.mixin.injection.Redirect;
import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

import java.math.BigInteger;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonyZeroPowerStart {

    /**
     * 拦截 BigInteger.valueOf(long) 的调用，将返回值强制修改为 BigInteger.ZERO
     */
    @Redirect(
        method = "processRecipe",
        at = @At(
            value = "INVOKE",
            target = "Ljava/math/BigInteger;valueOf(J)Ljava/math/BigInteger;"
        )
    )
    private BigInteger redirectBigIntegerValueOf(long value) {
        System.out.println("Redirecting BigInteger.valueOf(" + value + ") to BigInteger.ZERO");
        return BigInteger.ZERO; // 强制返回 0
    }
}
