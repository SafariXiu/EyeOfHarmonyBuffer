package com.EyeOfHarmonyBuffer.Mixins.EOH;

import java.math.BigInteger;

import com.github.technus.tectech.thing.metaTileEntity.multi.GT_MetaTileEntity_EM_EyeOfHarmony;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.EyeOfHarmonyBuffer.Config.Config;
import com.EyeOfHarmonyBuffer.Config.MainConfig;

@Mixin(value = GT_MetaTileEntity_EM_EyeOfHarmony.class, remap = false)
public class EyeOfHarmonyEU {

    @ModifyArg(
        method = "outputAfterRecipe_EM",
        at = @At(
            value = "INVOKE",
            target = "Lcom/github/technus/tectech/thing/metaTileEntity/multi/GT_MetaTileEntity_EM_EyeOfHarmony;addEUToGlobalEnergyMap(Ljava/lang/String;Ljava/math/BigInteger;)Z"),
        index = 1)
    private BigInteger modifyOutputEU(BigInteger originalOutputEU) {

        if (MainConfig.EOHOpenEuOutPut) {
            BigInteger constantOutputEU = Config.getConstantOutputEU();
            return constantOutputEU;
        } else {
            return originalOutputEU;
        }
    }
}
