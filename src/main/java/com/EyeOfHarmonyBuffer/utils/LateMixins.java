package com.EyeOfHarmonyBuffer.utils;

import java.util.ArrayList;
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
        // 创建 Mixin 列表
        List<String> mixins = new ArrayList<>(Arrays.asList(
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
            "Accessor.TreatedWater.Grade4WaterPurificationAccessor",
            "BioLab.BioLabAdvancedMixin",
            "BioLab.BioLabMixin",
            "EOH.EyeOfHarmonyAstralArrayAmount",
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
            "HIPCompressorMixin",
            "SpaceElevator.ModuleMinerMixin",
            "SpaceElevator.ModuleMinerParallelsMixin",
            "BlackHoleCompressorMixin",
            "IndustrialLaserEngraverMixin",
            "Accessor.BlackHoleCompressorAccessor",
            "MaskListMixin",
            "TreatedWater.Grade8WaterPurificationMixin",
            "TreatedWater.Grade7WaterPurificationMixin",
            "TreatedWater.Grade6WaterPurificationMixin",
            "TreatedWater.Grade5WaterPurificationMixin",
            "TreatedWater.Grade4WaterPurificationMixin",
            "TreatedWater.Grade3WaterPurificationMixin",
            "TreatedWater.Grade2WaterPurificationMixin",
            "TreatedWater.Grade1WaterPurificationMixin"
        ));

        if (loadedMods.contains("TwistSpaceTechnology")) {
            mixins.add("TST.IndistinctTentaclePrototypeMK2Mixin");
            System.out.println("TwistSpaceTechnology mod detected! Loading TST.IndistinctTentaclePrototypeMK2Mixin.");
        } else {
            System.out.println("TwistSpaceTechnology mod not detected. Skipping TST.IndistinctTentaclePrototypeMK2Mixin.");
        }

        return mixins;
    }
}
