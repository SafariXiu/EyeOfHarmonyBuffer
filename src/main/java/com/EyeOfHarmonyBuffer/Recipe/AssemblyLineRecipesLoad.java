package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.core.material.MaterialMisc;
import gtPlusPlus.core.material.MaterialsElements;
import gtPlusPlus.core.material.Particle;

import static com.EyeOfHarmonyBuffer.utils.Utils.copyAmount;
import static com.EyeOfHarmonyBuffer.utils.WriteOnceOnly.isSubstanceReshapingDeviceEnabled;
import static gregtech.api.util.GTRecipeBuilder.*;
import static gregtech.api.util.GTRecipeConstants.*;
import static gtPlusPlus.core.material.Particle.*;
import static gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList.Controller_ElementalDuplicator;
import static tectech.thing.CustomItemList.*;

public final class AssemblyLineRecipesLoad {

    public static void RecipeLoad(){

        if(isSubstanceReshapingDeviceEnabled()){
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

        }

    }
}
