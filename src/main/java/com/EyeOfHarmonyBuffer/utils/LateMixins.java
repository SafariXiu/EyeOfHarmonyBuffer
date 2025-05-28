package com.EyeOfHarmonyBuffer.utils;

import java.util.*;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public class LateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.eyeofharmonybuffer.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        // 创建 Mixin 列表
        List<String> mixins = new ArrayList<>(Arrays.asList(
            "Accessor.DigesterAccessor",
            "Accessor.EyeOfHarmonyAccessor",
            "Accessor.ExoticModuleAccessor",
            "Accessor.FOGAccessor",
            "Accessor.SpaceElevatorAccessor",
            "Accessor.TTMultiblockBaseAccessor",
            "Accessor.TreatedWater.Grade4WaterPurificationAccessor",
            "Accessor.PCBFactory.PCBFactoryParallelAccessor",
            "Accessor.PCBFactory.PCBFactoryParallelThisAccessor",
            "AlloyBlastSmelterMixin",
            "BioLab.BioLabAdvancedMixin",
            "BioLab.BioLabMixin",
            "BioLab.BasicMachineMixin",
            "BioVatMixin",
            "GTPPMultiBlockBaseMixin",
            "HatchElementBuilderMixin",
            "BlackHoleCompressorMixin",
            "CircuitAssemblyLineMixin",
            "ChemicalPlantMixin",
            "DigesterMixin",
            "DissolutionTankMixin",
            "DEFusionCrafterMixin",
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
            "ElementalDuplicatorMixin",
            "FOGShardsAvailable",
            "FuelFactory.NaquadahFuelRefineryMixin",
            "FuelFactory.FuelRefineFactoryMixin",
            "FusionComputerMixin",
            "GodOfForgeModuleMixin.ExoticModuleMixin",
            "GodOfForgeModuleMixin.GravitonShardMixin",
            "GodOfForgeModuleMixin.MoltenModuleMixin",
            "GodOfForgeModuleMixin.PlasmaModuleMixin",
            "GodOfForgeModuleMixin.SmeltingModuleMixin",
            "HIPCompressorMixin",
            "IndustrialLaserEngraver.IndustrialLaserEngraverParallelMixin",
            "IndustrialLaserEngraver.IndustrialLaserEngraverSpeedMixin",
            "Invoker.TTMultiblockBaseInvoker",
            "IndustrialChiselMixin",
            "IndustrialCuttingMachineMixin",
            "LargeFusion.LargeFusionMixin",
            "LargeFusion.LargeFusionPara",
            "LargerTurbinePlasmaMixin",
            "MaskListMixin",
            "Mask.TargetChamberMixin",
            "MegaBlastFurnaceMixin",
            "OutPutME.HatchOutputBusMEMixin",
            "OutPutME.HatchOutputMEMixin",
            "PlanetaryGasSiphonMixin",
            "PlasmaForgeMixin",
            "PCBFactory.PCBFactoryCoolantMixin",
            "PCBFactory.PCBFactoryParallelMixin",
            "PreciseAssemblerMixin",
            "PrimitiveBlastFurnaceMixin",
            "RareEarth.IndustrialDehydratorMixin",
            "RareEarth.FrothFlotationCellMixin",
            "RareEarth.IsaMillMixin",
            "Recipe.CyclotronRecipeMixin",
            "Recipe.naquadahFuelRefineFactoryRecipesMixin",
            "Recipe.SpaceMiningRecipesMixin",
            "SpaceElevator.ModuleAssemblerMixin",
            "SpaceElevator.ModuleEUMixin",
            "SpaceElevator.ModuleMinerMixin",
            "SpaceElevator.ModuleMinerParallelsMixin",
            "SpaceElevator.ModuleAssemblerPowerMixin",
            "SpaceElevator.ModulePumpMixin",
            "TreatedWater.Grade1WaterPurificationMixin",
            "TreatedWater.Grade2WaterPurificationMixin",
            "TreatedWater.Grade3WaterPurificationMixin",
            "TreatedWater.Grade4WaterPurificationMixin",
            "TreatedWater.Grade5WaterPurificationMixin",
            "TreatedWater.Grade6WaterPurificationMixin",
            "TreatedWater.Grade7WaterPurificationMixin",
            "TreatedWater.Grade8WaterPurificationMixin",
            "UUMixin"
        ));

        // 定义每个 mod 对应的 mixin 列表
        Map<String, List<String>> modMixins = new HashMap<>();
        modMixins.put("TwistSpaceTechnology", Arrays.asList(
            "TST.IndistinctTentaclePrototypeMK2Mixin",
            "TST.LightningSpireMixin",
            "TST.ManufacturingCenterMixin"
        ));
        modMixins.put("123Technology", Arrays.asList(
            "Invoker.MegaNineInOneInvoker",
            "OthTech.MegaNineInOneMixin",
            "OthTech.WoodenFusionReactorMixin",
            "OthTech.Recipe.WoodenFusionReactorRecipe",
            "OthTech.MegaIsaFactoryMixin"
        ));
        modMixins.put("programmablehatches", Arrays.asList(
            "PHM.StorageOutputBusMixin",
            "PHM.StorageOutputHatchMixin"
        ));
        // 如果有其他 mod 需要 mixin，可以在这里添加，比如：
        // modMixins.put("AnotherMod", Arrays.asList("AnotherMod.SomeMixin1", "AnotherMod.SomeMixin2"));

        loadModSpecificMixins(loadedMods, modMixins, mixins);

        return mixins;
    }

    private void loadModSpecificMixins(Set<String> loadedMods, Map<String, List<String>> modMixins, List<String> mixins) {
        for (Map.Entry<String, List<String>> entry : modMixins.entrySet()) {
            String modName = entry.getKey();
            List<String> modMixinList = entry.getValue();

            if (loadedMods.contains(modName)) {
                for (String mixin : modMixinList) {
                    mixins.add(mixin);
                    System.out.println(modName + " mod detected! Loading " + mixin + ".");
                }
            } else {
                for (String mixin : modMixinList) {
                    System.out.println(modName + " mod not detected. Skipping " + mixin + ".");
                }
            }
        }
    }
}
