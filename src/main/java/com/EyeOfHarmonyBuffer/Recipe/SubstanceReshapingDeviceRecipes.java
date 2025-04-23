package com.EyeOfHarmonyBuffer.Recipe;

import bartworks.system.material.WerkstoffLoader;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.MaterialsElements;
import gtnhlanth.common.register.WerkstoffMaterialPool;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import static bartworks.system.material.WerkstoffLoader.*;
import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.SubstanceReshapingDevice;
import static com.EyeOfHarmonyBuffer.utils.Utils.setStackSize;
import static com.dreammaster.item.ItemList.TCetiESeaweedExtract;
import static goodgenerator.items.GGMaterial.*;
import static gregtech.api.enums.Materials.*;
import static gregtech.api.enums.Mods.*;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.QFT_FOCUS_TIER;
import static gtPlusPlus.core.material.MaterialMisc.MUTATED_LIVING_SOLDER;
import static gtPlusPlus.core.material.MaterialsElements.STANDALONE.ASTRAL_TITANIUM;
import static gtPlusPlus.core.material.MaterialsElements.STANDALONE.CELESTIAL_TUNGSTEN;
import static gtnhlanth.common.register.WerkstoffMaterialPool.Iodine;

public class SubstanceReshapingDeviceRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        /*
          1号：只有流体输出
          2号：只有物品输出
          3号：物品流体输出都有
          其他均为特殊输出
          Value的起始值是1对应LV，往后依次+1
         */

        //橡胶
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.RawRubber,1),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Sulfur,1)
            )
            .fluidOutputs(
                Materials.Silicone.getMolten(Integer.MAX_VALUE),
                Materials.StyreneButadieneRubber.getMolten(Integer.MAX_VALUE),
                Materials.Rubber.getMolten(Integer.MAX_VALUE),
                WerkstoffMaterialPool.PTMEGElastomer.getMolten(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(1)
            .addTo(SubstanceReshapingDevice);

        //铂
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                WerkstoffLoader.PTMetallicPowder.get(OrePrefixes.dust, 1)
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Platinum,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Palladium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Iridium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Osmium,1),Integer.MAX_VALUE),
                WerkstoffLoader.Rhodium.get(OrePrefixes.dust, Integer.MAX_VALUE),
                WerkstoffLoader.Ruthenium.get(OrePrefixes.dust,Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(4)
            .addTo(SubstanceReshapingDevice);

        //独居石
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Monazite,1)
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Samarium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Holmium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Cerium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Neodymium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Europium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Gadolinium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Lanthanum,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Terbium,1),Integer.MAX_VALUE),
                WerkstoffLoader.Zirconium.get(OrePrefixes.dust, Integer.MAX_VALUE),
                MaterialsElements.getInstance().HAFNIUM.getDust(Integer.MAX_VALUE),
                setStackSize(WerkstoffMaterialPool.Hafnia.get(OrePrefixes.dust,1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(5)
            .addTo(SubstanceReshapingDevice);

        //硅
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.SiliconDioxide,1)
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Silicon,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.SiliconSG,1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(2)
            .addTo(SubstanceReshapingDevice);

        //钨
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Tungstate,1)
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Tungsten,1),Integer.MAX_VALUE),
                CELESTIAL_TUNGSTEN.getDust(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(3)
            .addTo(SubstanceReshapingDevice);

        //钛
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Rutile,1)
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Titanium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Tritanium,1),Integer.MAX_VALUE),
                ASTRAL_TITANIUM.getDust(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(3)
            .addTo(SubstanceReshapingDevice);

        //氡
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTUtility.copyAmount(0,GTOreDictUnificator.get(OrePrefixes.ingot,Materials.Plutonium,1)),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Uranium,1)
            )
            .fluidInputs(
                Materials.Air.getGas(1)
            )
            .fluidOutputs(
                Materials.Radon.getGas(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(3)
            .addTo(SubstanceReshapingDevice);

        //硅岩
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                naquadahEarth.get(OrePrefixes.dust,1)
            )
            .itemOutputs(
                setStackSize(naquadahine.get(OrePrefixes.dust,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Naquadah,1),Integer.MAX_VALUE),
                setStackSize(NaquadahEnriched.getDust(1),Integer.MAX_VALUE),
                setStackSize(Materials.Naquadria.getDust(1),Integer.MAX_VALUE),
                setStackSize(extremelyUnstableNaquadah.get(OrePrefixes.dust,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Trinium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Adamantium,1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(6)
            .addTo(SubstanceReshapingDevice);

        //干细胞
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(3),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Calcium,1)
            )
            .fluidInputs(
                Materials.Water.getFluid(1000)
            )
            .itemOutputs(
                setStackSize(ItemList.Circuit_Chip_Stemcell.get(1),Integer.MAX_VALUE),
                setStackSize(ItemList.Circuit_Chip_Biocell.get(1),Integer.MAX_VALUE)
            )
            .fluidOutputs(
                MUTATED_LIVING_SOLDER.getFluidStack(Integer.MAX_VALUE),
                GrowthMediumSterilized.getFluid(Integer.MAX_VALUE),
                GrowthMediumRaw.getFluid(Integer.MAX_VALUE),
                BioMediumSterilized.getFluid(Integer.MAX_VALUE),
                BioMediumRaw.getFluid(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(7)
            .addTo(SubstanceReshapingDevice);

        //液态空气
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1)
            )
            .fluidInputs(
                Air.getGas(1000)
            )
            .fluidOutputs(
                Nitrogen.getGas(Integer.MAX_VALUE),
                Oxygen.getGas(Integer.MAX_VALUE),
                Argon.getGas(Integer.MAX_VALUE),
                CarbonDioxide.getGas(Integer.MAX_VALUE),
                Neon.getFluidOrGas(Integer.MAX_VALUE),
                Helium.getGas(Integer.MAX_VALUE),
                Methane.getGas(Integer.MAX_VALUE),
                Krypton.getFluidOrGas(Integer.MAX_VALUE),
                Hydrogen.getGas(Integer.MAX_VALUE),
                Xenon.getFluidOrGas(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(3)
            .addTo(SubstanceReshapingDevice);

        //海藻提取物
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(3),
                getModItem(PamsHarvestCraft.ID, "seaweedItem",1,0)
            )
            .fluidInputs(
                Water.getFluid(1000)
            )
            .itemOutputs(
                setStackSize(getModItem(GalaxySpace.ID, "tcetiedandelions", 1, 4),Integer.MAX_VALUE),
                setStackSize(TCetiESeaweedExtract.getIS(1),Integer.MAX_VALUE)
            )
            .fluidOutputs(
                Iodine.getFluidOrGas(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(8)
            .addTo(SubstanceReshapingDevice);

        //各种乙烯？
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Carbon,1)
            )
            .fluidOutputs(
                Materials.Plastic.getMolten(Integer.MAX_VALUE),
                Materials.PolyvinylChloride.getMolten(Integer.MAX_VALUE),
                Materials.Polystyrene.getMolten(Integer.MAX_VALUE),
                Materials.Polytetrafluoroethylene.getMolten(Integer.MAX_VALUE),
                Materials.Epoxid.getMolten(Integer.MAX_VALUE),
                Materials.Polybenzimidazole.getMolten(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(4)
            .addTo(SubstanceReshapingDevice);

        //QFT特殊材料
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(4)
            )
            .fluidInputs(
                Water.getFluid(1000),
                Air.getGas(1000)
            )
            .itemOutputs(
                setStackSize(ItemList.StableAdhesive.get(1),Integer.MAX_VALUE),
                setStackSize(ItemList.SuperconductorComposite.get(1),Integer.MAX_VALUE),
                setStackSize(ItemList.NaquadriaSupersolid.get(1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(9)
            .addTo(SubstanceReshapingDevice);

        //催化剂
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1)
            )
            .fluidInputs(
                Hydrogen.getGas(1000)
            )
            .fluidOutputs(
                MaterialsUEVplus.ExcitedDTRC.getFluid(Integer.MAX_VALUE),
                MaterialsUEVplus.ExcitedDTCC.getFluid(Integer.MAX_VALUE),
                MaterialsUEVplus.ExcitedDTEC.getFluid(Integer.MAX_VALUE),
                MaterialsUEVplus.ExcitedDTPC.getFluid(Integer.MAX_VALUE),
                MaterialsUEVplus.ExcitedDTSC.getFluid(Integer.MAX_VALUE),
                MaterialsUEVplus.StargateCrystalSlurry.getFluid(Integer.MAX_VALUE),
                MaterialsUEVplus.PrimordialMatter.getFluid(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(10)
            .addTo(SubstanceReshapingDevice);

        //鸿蒙
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(4)
            )
            .fluidInputs(
                Helium.getGas(1000),
                Hydrogen.getGas(1000)
            )
            .fluidOutputs(
                MaterialsUEVplus.BlackDwarfMatter.getMolten(Integer.MAX_VALUE),
                MaterialsUEVplus.WhiteDwarfMatter.getMolten(Integer.MAX_VALUE),
                MaterialsUEVplus.Universium.getMolten(Integer.MAX_VALUE),
                MaterialsUEVplus.RawStarMatter.getFluid(Integer.MAX_VALUE),
                MaterialsUEVplus.MagnetohydrodynamicallyConstrainedStarMatter.getMolten(Integer.MAX_VALUE),
                MaterialsUEVplus.SpaceTime.getMolten(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(11)
            .addTo(SubstanceReshapingDevice);

        //一些奇怪的东西......
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(24),
                new ItemStack(Items.iron_ingot, 4),
                new ItemStack(Items.stick, 3)
            )
            .itemOutputs(
                getModItem(SGCraft.ID, "stargateRing", 16, 0),
                getModItem(SGCraft.ID, "stargateRing", 14, 1),
                getModItem(SGCraft.ID, "stargateBase", 2, 0),
                getModItem(SGCraft.ID, "stargateController", 2, 0),
                getModItem(SGCraft.ID, "rfPowerUnit", 2, 0),
                getModItem(SGCraft.ID, "ocInterface", 2, 0),
                getModItem(SGCraft.ID, "sgChevronUpgrade", 2, 0),
                getModItem(SGCraft.ID, "sgIrisUpgrade", 2, 0)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(14)
            .addTo(SubstanceReshapingDevice);

        //无尽之类的
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTUtility.copyAmount(0,getModItem(Avaritia.ID, "Resource",1,5))
            )
            .fluidInputs(
                Water.getFluid(1000)
            )
            .fluidOutputs(
                Infinity.getMolten(Integer.MAX_VALUE),
                MaterialsUEVplus.Eternity.getMolten(Integer.MAX_VALUE),
                MaterialsUEVplus.MagMatter.getMolten(Integer.MAX_VALUE),
                MaterialsUEVplus.QuarkGluonPlasma.getFluid(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(9)
            .addTo(SubstanceReshapingDevice);

        //电路板
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTUtility.copyAmount(0,getModItem(IndustrialCraft2.ID, "itemPartCircuit",1))
            )
            .itemOutputs(
                setStackSize(com.dreammaster.item.ItemList.CircuitULV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitLV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitMV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitHV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitEV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitIV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitLuV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitZPM.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitUV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitUHV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitUEV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitUIV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitUMV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitUXV.getIS(1),Integer.MAX_VALUE),
                setStackSize(com.dreammaster.item.ItemList.CircuitMAX.getIS(1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(9)
            .addTo(SubstanceReshapingDevice);
    }
}
