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
            "Accessor.BlackHoleCompressorAccessor",
            "Accessor.DigesterAccessor",
            "Accessor.EyeOfHarmonyAccessor",
            "Accessor.ExoticModuleAccessor",
            "Accessor.FOGAccessor",
            "Accessor.SpaceElevatorAccessor",
            "Accessor.TTMultiblockBaseAccessor",
            "Accessor.TreatedWater.Grade4WaterPurificationAccessor",
            "Accessor.PCBFactory.PCBFactoryParallelAccessor",
            "Accessor.PCBFactory.PCBFactoryParallelThisAccessor",
            "BioLab.BioLabAdvancedMixin",
            "BioLab.BioLabMixin",
            "BioVatMixin",
            "BlackHoleCompressorMixin",
            "CircuitAssemblyLineMixin",
            "DigesterMixin",
            "DissolutionTankMixin",
            "DTPFBuffer",
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
            "FOGShardsAvailable",
            "FuelFactory.NaquadahFuelRefineryMixin",
            "GodOfForgeModuleMixin.ExoticModuleMixin",
            "GodOfForgeModuleMixin.GravitonShardMixin",
            "HIPCompressorMixin",
            "IndustrialLaserEngraver.IndustrialLaserEngraverParallelMixin",
            "IndustrialLaserEngraver.IndustrialLaserEngraverSpeedMixin",
            "LargeEssentiaGeneratorMixin",
            "LargeFusion.LargeFusionMixin",
            "LargeFusion.LargeFusionPara",
            "MaskListMixin",
            "Mask.TargetChamberMixin",
            "OutPutME.HatchOutputBusMEMixin",
            "OutPutME.HatchOutputMEMixin",
            "PlanetaryGasSiphonMixin",
            "PCBFactory.PCBFactoryCoolantMixin",
            "PCBFactory.PCBFactoryParallelMixin",
            "RareEarth.IndustrialDehydratorMixin",
            "RareEarth.FrothFlotationCellMixin",
            "RareEarth.IsaMillMixin",
            "SpaceElevator.ModuleAssemblerMixin",
            "SpaceElevator.ModuleEUMixin",
            "SpaceElevator.ModuleMinerMixin",
            "SpaceElevator.ModuleMinerParallelsMixin",
            "TreatedWater.Grade1WaterPurificationMixin",
            "TreatedWater.Grade2WaterPurificationMixin",
            "TreatedWater.Grade3WaterPurificationMixin",
            "TreatedWater.Grade4WaterPurificationMixin",
            "TreatedWater.Grade5WaterPurificationMixin",
            "TreatedWater.Grade6WaterPurificationMixin",
            "TreatedWater.Grade7WaterPurificationMixin",
            "TreatedWater.Grade8WaterPurificationMixin",
            "TranscendentPlasmaMixerMixin",
            "UUMixin"
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
