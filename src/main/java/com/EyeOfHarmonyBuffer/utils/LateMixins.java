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
            "BioVatMixin",
            "DigesterMixin",
            "DissolutionTankMixin",
            "DTPFBuffer",
            "FOGShardsAvailable",
            "UUMixin",
            "Accessor.DigesterAccessor",
            "Accessor.EyeOfHarmonyAccessor",
            "Accessor.FOGAccessor",
            "Accessor.SpaceElevatorAccessor",
            "Accessor.TTMultiblockBaseAccessor",
            "BioLab.BioLabAdvancedMixin",
            "BioLab.BioLabMixin",
            "EOH.EyeOfHarmonyAstralArrayAmount",
            "EOH.EyeOfHarmonyBus",
            "EOH.EyeOfHarmonyEU",
            "EOH.EyeOfHarmonyFluidMixin",
            "EOH.EyeOfHarmonyGas",
            "EOH.EyeOfHarmonyItemMixin",
            "EOH.EyeOfHarmonyLV",
            "EOH.EyeOfHarmonyOutputRateControl",
            "EOH.EyeOfHarmonySuccessRateControl",
            "EOH.EyeOfHarmonyWorkTime",
            "EOH.EyeOfHarmonyZeroPowerStart",
            "LargeFusion.LargeFusionMixin",
            "LargeFusion.LargeFusionPara",
            "FuelFactory.NaquadahFuelRefineryMixin",
            "SpaceElevator.ModuleMinerMixin",
            "SpaceElevator.ModuleMinerParallelsMixin",
            "BlackHoleCompressorMixin",
            "IndustrialLaserEngraverMixin",
            "Accessor.BlackHoleCompressorAccessor",
            "MaskListMixin",
            "TreatedWater.Grade8WaterPurificationMixin",
            "TreatedWater.Grade7WaterPurificationMixin"
        );
    }
}
