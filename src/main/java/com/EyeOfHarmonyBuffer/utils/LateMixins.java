package com.EyeOfHarmonyBuffer.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public class LateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.EyeOfHarmonyBuffer.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return Arrays.asList(
            "Accessor.EyeOfHarmonyAccessor",
            "EOH.EyeOfHarmonyEU",
            "EOH.EyeOfHarmonyFluidMixin",
            "EOH.EyeOfHarmonyGas",
            "EOH.EyeOfHarmonyItemMixin",
            "EOH.EyeOfHarmonyLV",
            "EOH.EyeOfHarmonyOutputRateControl",
            "EOH.EyeOfHarmonySuccessRateControl",
            "EOH.EyeOfHarmonyWorkTime",
            "EOH.EyeOfHarmonyZeroPowerStart");
    }
}
