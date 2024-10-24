package com.EyeOfHarmonyBuffer.Mixins;

import java.math.BigInteger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.EyeOfHarmonyBuffer.Config.Config;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public class EyeOfHarmonyEU {

    @ModifyArg(
        method = "outputAfterRecipe_EM",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/misc/WirelessNetworkManager;addEUToGlobalEnergyMap(Ljava/util/UUID;Ljava/math/BigInteger;)Z"),
        index = 1)
    private BigInteger modifyOutputEU(BigInteger originalOutputEU) {

        BigInteger constantOutputEU = Config.getConstantOutputEU();

        return constantOutputEU;
    }
}
