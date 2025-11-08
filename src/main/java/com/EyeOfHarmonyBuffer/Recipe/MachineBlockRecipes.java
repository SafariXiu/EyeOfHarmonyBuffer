package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTUtility;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.core.material.MaterialMisc;
import gtPlusPlus.core.material.MaterialsElements;
import gtPlusPlus.core.material.Particle;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

import java.lang.reflect.Field;
import java.util.*;

import static com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool.EOHBCatalyst;
import static com.EyeOfHarmonyBuffer.utils.Utils.copyAmount;
import static com.EyeOfHarmonyBuffer.utils.WriteOnceOnly.isSubstanceReshapingDeviceEnabled;
import static com.dreammaster.item.NHItemList.*;
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

    private enum Tier {
        LV, MV, HV, EV, IV, LuV, ZPM, UV, UHV, UEV, UIV, UMV, UXV, MAX
    }

    private static final Map<Tier, ItemStack> CIRCUIT_MAP = new EnumMap<>(Tier.class);

    static {
        CIRCUIT_MAP.put(Tier.LV,  CircuitLV.getIS(1));
        CIRCUIT_MAP.put(Tier.MV,  CircuitMV.getIS(1));
        CIRCUIT_MAP.put(Tier.HV,  CircuitHV.getIS(1));
        CIRCUIT_MAP.put(Tier.EV,  CircuitEV.getIS(1));
        CIRCUIT_MAP.put(Tier.IV,  CircuitIV.getIS(1));
        CIRCUIT_MAP.put(Tier.LuV, CircuitLuV.getIS(1));
        CIRCUIT_MAP.put(Tier.ZPM, CircuitZPM.getIS(1));
        CIRCUIT_MAP.put(Tier.UV,  CircuitUV.getIS(1));
        CIRCUIT_MAP.put(Tier.UHV, CircuitUHV.getIS(1));
        CIRCUIT_MAP.put(Tier.UEV, CircuitUEV.getIS(1));
        CIRCUIT_MAP.put(Tier.UIV, CircuitUIV.getIS(1));
        CIRCUIT_MAP.put(Tier.UMV, CircuitUMV.getIS(1));
        CIRCUIT_MAP.put(Tier.UXV, CircuitUXV.getIS(1));
        CIRCUIT_MAP.put(Tier.MAX, CircuitMAX.getIS(1));
    }

    private static final Map<Tier, ItemStack> SINGULARITY_CASINGS_MAP = new EnumMap<>(Tier.class);

    static {
        SINGULARITY_CASINGS_MAP.put(Tier.LV,  GTCMItemList.SingularityStabilizationRingCasingsLV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.MV,  GTCMItemList.SingularityStabilizationRingCasingsMV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.HV,  GTCMItemList.SingularityStabilizationRingCasingsHV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.EV,  GTCMItemList.SingularityStabilizationRingCasingsEV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.IV,  GTCMItemList.SingularityStabilizationRingCasingsIV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.LuV, GTCMItemList.SingularityStabilizationRingCasingsLuV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.ZPM, GTCMItemList.SingularityStabilizationRingCasingsZPM.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.UV,  GTCMItemList.SingularityStabilizationRingCasingsUV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.UHV, GTCMItemList.SingularityStabilizationRingCasingsUHV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.UEV, GTCMItemList.SingularityStabilizationRingCasingsUEV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.UIV, GTCMItemList.SingularityStabilizationRingCasingsUIV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.UMV, GTCMItemList.SingularityStabilizationRingCasingsUMV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.UXV, GTCMItemList.SingularityStabilizationRingCasingsUXV.get(1));
        SINGULARITY_CASINGS_MAP.put(Tier.MAX, GTCMItemList.SingularityStabilizationRingCasingsMAX.get(1));
    }

    private static final String[] COMPONENT_NAMES = {
        "Electric_Motor", "Electric_Pump", "Electric_Piston", "Conveyor_Module", "FluidRegulator"
    };

    private static final Map<Tier, ItemStack[]> MACHINE_COMPONENTS = new EnumMap<>(Tier.class);

    static {
        for (Tier tier : Tier.values()) {
            List<ItemStack> partList = new ArrayList<>();

            for (String base : COMPONENT_NAMES) {
                String fieldName = base + "_" + tier.name();
                try {
                    Field f = ItemList.class.getField(fieldName);
                    Object val = f.get(null);

                    if (val instanceof gregtech.api.enums.ItemList) {
                        ItemStack stack = ((gregtech.api.enums.ItemList) val).get(1);
                        if (stack != null) partList.add(stack);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            MACHINE_COMPONENTS.put(tier, partList.toArray(new ItemStack[0]));
        }
    }

    @Override
    public void loadRecipes() {

        //工业交易箱
        if(MachineLoaderConfig.VendingMachines){
            GTValues.RA.stdBuilder()
                .itemInputs(
                    getModItem(IndustrialCraft2.ID,"blockPersonal",64,1),
                    CircuitEV.getIS(16),
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
                GTOreDictUnificator.get(OrePrefixes.plateDense, Materials.SiliconSG,16),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UV,16),
                ItemList.Machine_HV_SolarPanel.get(64),
                ItemList.Machine_EV_SolarPanel.get(64),
                ItemList.Machine_IV_SolarPanel.get(64),
                ItemList.Machine_LuV_SolarPanel.get(64)
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

        //奇点稳定环
        Tier[] tiers = Tier.values();

        for (int i = 0; i < tiers.length; i++) {
            Tier tier = tiers[i];
            ItemStack[] components = MACHINE_COMPONENTS.get(tier);
            if (components == null || components.length == 0) continue;

            ItemStack[] adjustedComponents = new ItemStack[components.length];
            for (int j = 0; j < components.length; j++) {
                ItemStack stack = components[j].copy();
                stack.stackSize = 1024;
                adjustedComponents[j] = stack;
            }

            ItemStack circuit = CIRCUIT_MAP.get(tier).copy();
            circuit.stackSize = 123123;

            List<ItemStack> allInputs = new ArrayList<>(Arrays.asList(
                GTUtility.copyAmountUnsafe(1024, EOHBCatalyst.get(OrePrefixes.ingot, 1)),
                GTUtility.copyAmountUnsafe(123, getModItem(AppliedEnergistics2.ID, "item.ItemExtremeStorageCell.Universe", 1)),
                GTUtility.copyAmountUnsafe(123, getModItem(AppliedEnergistics2.ID, "item.ItemExtremeStorageCell.Singularity", 1)),
                circuit
            ));
            Collections.addAll(allInputs, adjustedComponents);

            ItemStack output = SINGULARITY_CASINGS_MAP.get(tier).copy();

            ItemStack researchInput;

            if (tier == Tier.LV) {
                researchInput = EOHBCatalyst.get(OrePrefixes.ingot, 1);
            } else {
                researchInput = SINGULARITY_CASINGS_MAP.get(tiers[i - 1]).copy();
            }

            GTValues.RA.stdBuilder()
                .metadata(RESEARCH_ITEM, researchInput)
                .metadata(SCANNING, new Scanning(500 * MINUTES, TierEU.RECIPE_UEV))
                .itemInputsUnsafe(
                    allInputs.toArray(new ItemStack[0])
                )
                .fluidInputs(
                    MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(128000000),
                    Materials.Infinity.getMolten(128000000),
                    MaterialsUEVplus.SpaceTime.getMolten(128000000)
                )
                .itemOutputs(
                    output
                )
                .duration(400 + tier.ordinal() * 60)
                .eut(1)
                .addTo(AssemblyLine);
        }
    }
}
