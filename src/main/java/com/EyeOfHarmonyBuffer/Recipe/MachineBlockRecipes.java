package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.core.material.MaterialMisc;
import gtPlusPlus.core.material.MaterialsElements;
import gtPlusPlus.core.material.Particle;
import net.minecraftforge.fluids.FluidRegistry;

import static com.EyeOfHarmonyBuffer.utils.Utils.copyAmount;
import static com.EyeOfHarmonyBuffer.utils.WriteOnceOnly.isSubstanceReshapingDeviceEnabled;
import static gregtech.api.enums.Mods.*;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.MINUTES;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.*;
import static gtPlusPlus.core.material.Particle.*;
import static gtPlusPlus.core.material.Particle.GRAVITON;
import static gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList.Controller_ElementalDuplicator;
import static tectech.thing.CustomItemList.*;

public final class MachineBlockRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        //工业交易箱
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

        //大型风力发电机
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

        //大型太阳能
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

        //物质重塑仪
        if(!isSubstanceReshapingDeviceEnabled()){
            GTValues.RA.stdBuilder()
                .metadata(RESEARCH_ITEM, Controller_ElementalDuplicator.get(1))
                .metadata(SCANNING, new Scanning(500 * MINUTES, TierEU.RECIPE_UEV))
                .itemInputs(
                    eM_Hollow.get(1),
                    Godforge_MagneticConfinementCasing.get(8),
                    Godforge_GravitonFlowModulatorTier1.get(8),
                    Godforge_HarmonicPhononTransmissionConduit.get(8),

                    Godforge_StellarEnergySiphonCasing.get(8),
                    ItemList.Field_Generator_UIV.get(32),
                    ItemList.Emitter_UIV.get(8),
                    ItemList.Sensor_UIV.get(8),

                    copyAmount(8, Particle.getBaseParticle(HIGGS_BOSON)),
                    copyAmount(8, Particle.getBaseParticle(ELECTRON_NEUTRINO)),
                    copyAmount(8, Particle.getBaseParticle(UNKNOWN)),
                    copyAmount(8, Particle.getBaseParticle(GRAVITON)),

                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UIV, 32),
                    GTOreDictUnificator.get(OrePrefixes.wireGt01, Materials.SuperconductorUIV, 64),
                    MaterialsElements.STANDALONE.CELESTIAL_TUNGSTEN.getPlate(64),
                    MaterialsElements.STANDALONE.ASTRAL_TITANIUM.getPlate(64)
                )
                .fluidInputs(
                    MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(128000000),
                    Materials.Infinity.getMolten(128000000),
                    MaterialsUEVplus.SpaceTime.getMolten(128000000)
                )
                .itemOutputs(
                    GTCMItemList.SubstanceReshapingDevice.get(1)
                )
                .eut(TierEU.RECIPE_UIV)
                .duration(2000 * SECONDS)
                .addTo(AssemblyLine);
        } else {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.Casing_RobustTungstenSteel.get(8),
                    ItemList.Emitter_EV.get(8),
                    ItemList.Sensor_EV.get(8),
                    ItemList.Field_Generator_EV.get(8),
                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16)
                )
                .itemOutputs(
                    GTCMItemList.SubstanceReshapingDevice.get(1)
                )
                .eut(TierEU.RECIPE_EV)
                .duration(30 * SECONDS)
                .addTo(assemblerRecipes);
        }
    }
}
