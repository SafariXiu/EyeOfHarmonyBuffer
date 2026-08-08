package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import bartworks.system.material.WerkstoffLoader;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBGTMaterials;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;

import static gregtech.api.recipe.RecipeMaps.*;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.COIL_HEAT;

public class ArknightsRecipesLoad implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                Materials.Magnesium.getDust(32),
                WerkstoffLoader.TantalumCarbideHafniumCarbideMixture.get(OrePrefixes.dust, 16),
                Materials.Europium.getDust(16)
            )
            .itemOutputs(
                EOHBMaterialPool.Hoyomixium.get(OrePrefixes.dust, 64)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(325 * SECONDS)
            .addTo(mixerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(2),
                GTCMItemList.YuanShiFuelRod1.get(2),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stick, 4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod2.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(5),
                GTCMItemList.YuanShiFuelRod2.get(2),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stick, 4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod4.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.getIntegratedCircuit(4),
                GTCMItemList.YuanShiFuelRod1.get(4),
                EOHBMaterialPool.ArsenicImpact.get(OrePrefixes.stickLong, 6)
            )
            .itemOutputs(
                GTCMItemList.YuanShiFuelRod4.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(10 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiDepletedFuelRod1.get(8)
            )
            .itemOutputs(
                GTCMItemList.YuanShiTongWeiSu_Alpha.get(1),
                GTCMItemList.FuelRod_empty1.get(8)
            )
            .outputChances(
                200,
                10000
            )
            .fluidOutputs(
                EOHBMaterialPool.ContaminatedOrundumSlurry.getFluidOrGas(100)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(512 * SECONDS)
            .addTo(centrifugeRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiDepletedFuelRod2.get(4)
            )
            .itemOutputs(
                GTCMItemList.YuanShiTongWeiSu_Alpha.get(1),
                GTCMItemList.FuelRod_empty1.get(8)
            )
            .outputChances(
                200,
                10000
            )
            .fluidOutputs(
                EOHBMaterialPool.ContaminatedOrundumSlurry.getFluidOrGas(100)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(512 * SECONDS)
            .addTo(centrifugeRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YuanShiDepletedFuelRod4.get(2)
            )
            .itemOutputs(
                GTCMItemList.YuanShiTongWeiSu_Alpha.get(1),
                GTCMItemList.FuelRod_empty1.get(8)
            )
            .outputChances(
                200,
                10000
            )
            .fluidOutputs(
                EOHBMaterialPool.ContaminatedOrundumSlurry.getFluidOrGas(100)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(512 * SECONDS)
            .addTo(centrifugeRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, EOHBGTMaterials.YiTie, 1)
            )
            .itemOutputs(
                GTCMItemList.YiTie.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(50 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, EOHBGTMaterials.ZhuanZhiYan, 32)
            )
            .fluidInputs(
                Materials.Water.getFluid(64000)
            )
            .itemOutputs(
                GTCMItemList.ZhuanZhiYanZu.get(16)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(60 * SECONDS)
            .addTo(autoclaveRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.dust, EOHBGTMaterials.QingMengKuang, 8)
            )
            .itemOutputs(
                GTCMItemList.QingMengKuang.get(1)
            )
            .fluidInputs(
                Materials.SulfuricAcid.getFluid(2000)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(50 * SECONDS)
            .metadata(COIL_HEAT, 4500)
            .addTo(blastFurnaceRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.YiTieZu.get(1)
            )
            .fluidOutputs(
                EOHBGTMaterials.YiTie.getMolten(144)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(2 * SECONDS)
            .addTo(fluidExtractionRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuanZhiYanZu.get(1)
            )
            .fluidOutputs(
                EOHBGTMaterials.ZhuanZhiYan.getMolten(144)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(2 * SECONDS)
            .addTo(fluidExtractionRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.QingMengKuang.get(1)
            )
            .fluidOutputs(
                EOHBGTMaterials.QingMengKuang.getMolten(144)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(2 * SECONDS)
            .addTo(fluidExtractionRecipes);
    }
}
