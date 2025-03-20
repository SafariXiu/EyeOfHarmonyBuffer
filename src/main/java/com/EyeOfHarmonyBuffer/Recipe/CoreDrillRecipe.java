package com.EyeOfHarmonyBuffer.Recipe;

import bartworks.system.material.WerkstoffLoader;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gtPlusPlus.core.material.MaterialsElements;
import gtnhlanth.common.register.WerkstoffMaterialPool;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.CoreDrill;
import static com.EyeOfHarmonyBuffer.utils.Utils.setStackSize;
import static goodgenerator.items.GGMaterial.*;
import static gregtech.api.enums.Materials.*;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gtPlusPlus.core.material.MaterialMisc.MUTATED_LIVING_SOLDER;
import static gtPlusPlus.core.material.MaterialsElements.STANDALONE.ASTRAL_TITANIUM;
import static gtPlusPlus.core.material.MaterialsElements.STANDALONE.CELESTIAL_TUNGSTEN;

public class CoreDrillRecipe implements IRecipePool {

    @Override
    public void loadRecipes() {

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
            .addTo(CoreDrill);

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
            .addTo(CoreDrill);

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
            .addTo(CoreDrill);

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
            .addTo(CoreDrill);

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
            .addTo(CoreDrill);

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
            .addTo(CoreDrill);

        //氡
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(1),
                GTUtility.copyAmount(0,GTOreDictUnificator.get(OrePrefixes.ingot,Materials.Plutonium,64)),
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
            .addTo(CoreDrill);

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
            .addTo(CoreDrill);

        //干细胞
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(3),
                GTUtility.copyAmount(0,GTOreDictUnificator.get(OrePrefixes.dust,Materials.Calcium,1))
            )
            .fluidInputs(
                Materials.Water.getFluid(1)
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
            .addTo(CoreDrill);
    }
}
