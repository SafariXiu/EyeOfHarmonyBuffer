package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraftforge.fluids.FluidRegistry;

import static gregtech.api.enums.Mods.*;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public final class MachineBlockRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        if(MachineLoaderConfig.VendingMachines){
            GTValues.RA.stdBuilder()
                .itemInputs(
                    getModItem(IndustrialCraft2.ID,"blockPersonal",64,1),
                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.Data,16),
                    getModItem(GTPlusPlus.ID, "gtplusplus.blockcasings.3",16,2)
                )
                .itemOutputs(
                    GTCMItemList.VendingMachines.get(1)
                )
                .eut(TierEU.RECIPE_EV)
                .duration(20 * SECONDS)
                .addTo(assemblerRecipes);
        }

        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Block_Plascrete.get(64),
                ItemList.Electric_Motor_HV.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.Advanced,16)
            )
            .itemOutputs(
                GTCMItemList.WindTurbines.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(AdvancedSolarPanel.ID, "BlockAdvSolarPanel",64,3),
                getModItem(AdvancedSolarPanel.ID, "BlockAdvSolarPanel",64,2),
                getModItem(AdvancedSolarPanel.ID, "BlockAdvSolarPanel",64,1),
                getModItem(AdvancedSolarPanel.ID, "BlockAdvSolarPanel",64,0),
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.SiliconSG,16),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.Superconductor,16)
            )
            .fluidInputs(
                FluidRegistry.getFluidStack("molten.solderingalloy",4608)
            )
            .itemOutputs(
                GTCMItemList.SolarEnergyArray.get(1)
            )
            .eut(TierEU.RECIPE_UV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);
    }
}
