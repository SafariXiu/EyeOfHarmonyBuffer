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
import static com.dreammaster.item.NHItemList.*;
import static goodgenerator.items.GGMaterial.*;
import static gregtech.api.enums.Materials.*;
import static gregtech.api.enums.Mods.*;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gtPlusPlus.core.material.MaterialMisc.MUTATED_LIVING_SOLDER;
import static gtPlusPlus.core.material.MaterialsElements.STANDALONE.ASTRAL_TITANIUM;
import static gtPlusPlus.core.material.MaterialsElements.STANDALONE.CELESTIAL_TUNGSTEN;
import static gtnhlanth.common.register.WerkstoffMaterialPool.Iodine;

public class SubstanceReshapingDeviceRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.RubberRaw,1),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Sulfur,1)
            )
            .fluidOutputs(
                Materials.RubberSilicone.getMolten(Integer.MAX_VALUE),
                Materials.StyreneButadieneRubber.getMolten(Integer.MAX_VALUE),
                Materials.Rubber.getMolten(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(1)
            .addTo(SubstanceReshapingDevice);

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
                setStackSize(WerkstoffLoader.Zirconium.get(OrePrefixes.dust, 1),Integer.MAX_VALUE),
                setStackSize(MaterialsElements.getInstance().HAFNIUM.getDust(1),Integer.MAX_VALUE),
                setStackSize(WerkstoffMaterialPool.Hafnia.get(OrePrefixes.dust,1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(5)
            .addTo(SubstanceReshapingDevice);

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

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Tungstate,1)
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Tungsten,1),Integer.MAX_VALUE),
                setStackSize(CELESTIAL_TUNGSTEN.getDust(1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(3)
            .addTo(SubstanceReshapingDevice);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Rutile,1)
            )
            .itemOutputs(
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Titanium,1),Integer.MAX_VALUE),
                setStackSize(GTOreDictUnificator.get(OrePrefixes.dust,Materials.Tritanium,1),Integer.MAX_VALUE),
                setStackSize(ASTRAL_TITANIUM.getDust(1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(3)
            .addTo(SubstanceReshapingDevice);

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
                setStackSize(TCetiESeaweedExtract.get(1),Integer.MAX_VALUE)
            )
            .fluidOutputs(
                Iodine.getFluidOrGas(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(8)
            .addTo(SubstanceReshapingDevice);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTOreDictUnificator.get(OrePrefixes.dust,Materials.Carbon,1)
            )
            .fluidOutputs(
                Materials.Polycaprolactam.getMolten(Integer.MAX_VALUE),
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

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1)
            )
            .fluidInputs(
                Hydrogen.getGas(1000)
            )
            .fluidOutputs(
                Materials.ExcitedDTRC.getFluid(Integer.MAX_VALUE),
                Materials.ExcitedDTCC.getFluid(Integer.MAX_VALUE),
                Materials.ExcitedDTEC.getFluid(Integer.MAX_VALUE),
                Materials.ExcitedDTPC.getFluid(Integer.MAX_VALUE),
                Materials.ExcitedDTSC.getFluid(Integer.MAX_VALUE),
                Materials.StargateCrystalSlurry.getFluid(Integer.MAX_VALUE),
                Materials.PrimordialMatter.getFluid(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(10)
            .addTo(SubstanceReshapingDevice);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(4)
            )
            .fluidInputs(
                Helium.getGas(1000),
                Hydrogen.getGas(1000)
            )
            .fluidOutputs(
                Materials.BlackDwarfMatter.getMolten(Integer.MAX_VALUE),
                Materials.WhiteDwarfMatter.getMolten(Integer.MAX_VALUE),
                Materials.Universium.getMolten(Integer.MAX_VALUE),
                Materials.RawStarMatter.getFluid(Integer.MAX_VALUE),
                Materials.MHDCSM.getMolten(Integer.MAX_VALUE),
                Materials.SpaceTime.getMolten(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(11)
            .addTo(SubstanceReshapingDevice);

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

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                getModItem(Avaritia.ID, "Resource",1,5)
            )
            .fluidInputs(
                Water.getFluid(1000)
            )
            .fluidOutputs(
                Infinity.getMolten(Integer.MAX_VALUE),
                Materials.Eternity.getMolten(Integer.MAX_VALUE),
                Materials.MagMatter.getMolten(Integer.MAX_VALUE),
                Materials.QuarkGluonPlasma.getFluid(Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(9)
            .addTo(SubstanceReshapingDevice);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                getModItem(IndustrialCraft2.ID, "itemPartCircuit",1)
            )
            .itemOutputs(
                setStackSize(CircuitULV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitLV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitMV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitHV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitEV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitIV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitLuV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitZPM.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitUV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitUHV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitUEV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitUIV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitUMV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitUXV.get(1),Integer.MAX_VALUE),
                setStackSize(CircuitMAX.get(1),Integer.MAX_VALUE)
            )
            .eut(0)
            .duration(5 * SECONDS)
            .specialValue(9)
            .addTo(SubstanceReshapingDevice);
    }
}
