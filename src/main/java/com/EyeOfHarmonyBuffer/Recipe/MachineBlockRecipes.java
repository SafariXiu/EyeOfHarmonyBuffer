package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTModHandler;
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
import static com.EyeOfHarmonyBuffer.utils.Utils.max;
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

    private enum Tier {
        LV, MV, HV, EV, IV, LuV, ZPM, UV, UHV, UEV, UIV, UMV, UXV, MAX
    }

    private static final Map<Tier, ItemStack> CIRCUIT_MAP = new EnumMap<>(Tier.class);

    static {
        CIRCUIT_MAP.put(Tier.LV,  GTOreDictUnificator.get(OrePrefixes.circuit, Materials.LV, 1));
        CIRCUIT_MAP.put(Tier.MV,  GTOreDictUnificator.get(OrePrefixes.circuit, Materials.MV, 1));
        CIRCUIT_MAP.put(Tier.HV,  GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 1));
        CIRCUIT_MAP.put(Tier.EV,  GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 1));
        CIRCUIT_MAP.put(Tier.IV,  GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 1));
        CIRCUIT_MAP.put(Tier.LuV, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.LuV, 1));
        CIRCUIT_MAP.put(Tier.ZPM, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.ZPM, 1));
        CIRCUIT_MAP.put(Tier.UV,  GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UV, 1));
        CIRCUIT_MAP.put(Tier.UHV, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UHV, 1));
        CIRCUIT_MAP.put(Tier.UEV, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UEV, 1));
        CIRCUIT_MAP.put(Tier.UIV, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UIV, 1));
        CIRCUIT_MAP.put(Tier.UMV, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UMV, 1));
        CIRCUIT_MAP.put(Tier.UXV, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.UXV, 1));
        CIRCUIT_MAP.put(Tier.MAX, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.MAX, 1));
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
        "Electric_Motor",
        "Electric_Pump",
        "Electric_Piston",
        "Conveyor_Module",
        "Robot_Arm",
        "Emitter" ,
        "Field_Generator",
        "Sensor"
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

        if(MachineLoaderConfig.VendingMachines){
            GTValues.RA.stdBuilder()
                .itemInputs(
                    getModItem(IndustrialCraft2.ID,"blockPersonal",64,1),
                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
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
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.HV, 1)
            )
            .itemOutputs(
                GTCMItemList.WindTurbines.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

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
                    Materials.SpaceTime.getMolten(128000000)
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

        Tier[] tiers = Tier.values();

        for (int i = 0; i < tiers.length; i++) {
            Tier tier = tiers[i];
            if (tier == Tier.MAX) continue;

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

            ItemStack previousCasing;
            int casingCount;

            if (tier == Tier.LV) {
                casingCount = 1;
                previousCasing = EOHBCatalyst.get(OrePrefixes.ingot, casingCount);
            } else {
                casingCount = 2;
                previousCasing = SINGULARITY_CASINGS_MAP.get(tiers[i - 1]).copy();
                previousCasing.stackSize = casingCount;
            }

            List<ItemStack> allInputs = new ArrayList<>(Arrays.asList(
                previousCasing,
                GTUtility.copyAmountUnsafe(123, getModItem(AppliedEnergistics2.ID, "item.ItemExtremeStorageCell.Universe", 1)),
                GTUtility.copyAmountUnsafe(123, getModItem(AE2FluidCraft.ID, "fluid_storage.Universe", 1)),
                GTUtility.copyAmountUnsafe(123, getModItem(AppliedEnergistics2.ID, "item.ItemVoidStorageCell", 1)),
                GTUtility.copyAmountUnsafe(1280000, getModItem(AppliedEnergistics2.ID, "tile.BlockSingularityCraftingStorage", 1)),
                GTUtility.copyAmountUnsafe(1280000, getModItem(AppliedEnergistics2.ID, "tile.BlockAdvancedCraftingUnit", 1,3)),
                GTUtility.copyAmountUnsafe(100000000, getModItem(AppliedEnergistics2.ID, "item.ItemMultiMaterial", 1,47)),
                circuit
            ));
            Collections.addAll(allInputs, adjustedComponents);

            ItemStack output = SINGULARITY_CASINGS_MAP.get(tier).copy();

            ItemStack researchInput = (tier == Tier.LV)
                ? EOHBCatalyst.get(OrePrefixes.ingot, 1)
                : SINGULARITY_CASINGS_MAP.get(tiers[i - 1]).copy();

            GTValues.RA.stdBuilder()
                .metadata(RESEARCH_ITEM, researchInput)
                .metadata(SCANNING, new Scanning(500 * MINUTES, TierEU.RECIPE_UEV))
                .itemInputsUnsafe(
                    allInputs.toArray(new ItemStack[0])
                )
                .fluidInputs(
                    MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(128000000),
                    Materials.Infinity.getMolten(128000000),
                    Materials.SpaceTime.getMolten(128000000)
                )
                .itemOutputs(
                    output
                )
                .duration(5000 * SECONDS + + tier.ordinal() * (1000 * SECONDS))
                .eut(TierEU.RECIPE_MAX)
                .addTo(AssemblyLine);
        }

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, GTCMItemList.SingularityStabilizationRingCasingsUXV.get(1))
            .metadata(SCANNING, new Scanning(500 * MINUTES, TierEU.RECIPE_UEV))
            .itemInputsUnsafe(
                GTCMItemList.SingularityStabilizationRingCasingsUXV.get(1),
                GTUtility.copyAmountUnsafe(114514, getModItem(SGCraft.ID, "sgChevronUpgrade", 1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(SGCraft.ID, "sgCoreCrystal", 1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(SGCraft.ID, "ic2Capacitor", 1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(SGCraft.ID, "sgControllerCrystal", 1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(NewHorizonsCoreMod.ID, "StargateChevron",1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(SGCraft.ID, "sgIrisUpgrade", 1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(SGCraft.ID, "sgIrisBlade", 1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(SGCraft.ID, "stargateBase", 1)),
                GTUtility.copyAmountUnsafe(102400000, getModItem(AppliedEnergistics2.ID, "item.ItemExtremeStorageCell.Universe", 1)),
                GTUtility.copyAmountUnsafe(102400000, getModItem(AE2FluidCraft.ID, "fluid_storage.Universe", 1)),
                GTUtility.copyAmountUnsafe(102400000, getModItem(AppliedEnergistics2.ID, "item.ItemVoidStorageCell", 1)),
                GTUtility.copyAmountUnsafe(10240000, GTOreDictUnificator.get(OrePrefixes.circuit, Materials.MAX, 1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(NewHorizonsCoreMod.ID, "StargateShieldingFoil",1)),
                GTUtility.copyAmountUnsafe(114514, getModItem(NewHorizonsCoreMod.ID, "StargateFramePart",1)),
                GTUtility.copyAmountUnsafe(10240000, ItemList.Field_Generator_MAX.get(1))
            )
            .fluidInputs(
                MaterialMisc.MUTATED_LIVING_SOLDER.getFluidStack(128000000),
                Materials.Infinity.getMolten(128000000),
                Materials.SpaceTime.getMolten(128000000)
            )
            .itemOutputs(
                GTCMItemList.SingularityStabilizationRingCasingsMAX.get(1)
            )
            .duration(20000000 * SECONDS)
            .eut(TierEU.RECIPE_MAX)
            .addTo(AssemblyLine);

        GTValues.RA.stdBuilder()
            .metadata(RESEARCH_ITEM, GTCMItemList.Monkey.get(1))
            .metadata(SCANNING, new Scanning(500 * MINUTES, TierEU.RECIPE_UEV))
            .itemInputsUnsafe(
                GTCMItemList.Shit.get(64),
                GTCMItemList.Shit.get(64),
                GTCMItemList.Shit.get(64),
                GTCMItemList.Shit.get(64),

                GTCMItemList.Monkey.get(64),
                GTCMItemList.Monkey.get(64),
                GTCMItemList.Monkey.get(64),
                GTCMItemList.Monkey.get(64),

                GTCMItemList.Shit.get(64),
                GTCMItemList.Shit.get(64),
                GTCMItemList.Shit.get(64),
                GTCMItemList.Shit.get(64),

                GTCMItemList.Monkey.get(64),
                GTCMItemList.Monkey.get(64),
                GTCMItemList.Monkey.get(64),
                GTCMItemList.Monkey.get(64)
            )
            .fluidInputs(
                Materials.Water.getFluid(11451419)
            )
            .itemOutputs(
                GTCMItemList.MonkeyShitS.get(1)
            )
            .duration(200 * SECONDS)
            .eut(TierEU.RECIPE_EV)
            .addTo(AssemblyLine);

        GTModHandler.addCraftingRecipe(
            GTCMItemList.ElectricTypeOneMiningMachines.get(1),
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] {
            "DED",
            "CAC",
            "BBB",
            'A', GTCMItemList.YuanShi.get(1),
            'B', ItemList.Casing_Advanced_Iridium.get(1),
            'C', "circuitData",
            'D', ItemList.Conveyor_Module_EV.get(1),
            'E', ItemList.Electric_Motor_EV.get(1)
            });

        GTModHandler.addCraftingRecipe(
            GTCMItemList.OrundumDynamos.get(1),
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[] {
            "DED",
            "CAC",
            "BBB",
            'A', GTCMItemList.YuanShi.get(1),
            'B', ItemList.Casing_SolidSteel.get(1),
            'C', "circuitData",
            'D', ItemList.Electric_Piston_EV.get(1),
            'E', GTCMItemList.YiTie.get(1)
            });

        GTModHandler.addCraftingRecipe(
            GTCMItemList.ProtocolCore.get(1),
            GTModHandler.RecipeBits.BUFFERED | GTModHandler.RecipeBits.NOT_REMOVABLE,
            new Object[]{
                "...",
                ".A.",
                "...",
                'A', GTCMItemList.XieYiYuanShi.get(1)
            }
        );

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.QuanXinZhuangZhi.get(8),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                ItemList.Shape_Empty.get(64),
                GTCMItemList.JingTiWaiKe.get(64),
                GTCMItemList.ZiJingXianWei.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.ShapingMachines.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.GuYuanYanZu.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.ZhiYuanLiao.get(64),
                GTCMItemList.YuanShiKuang.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.RefiningFurnaces.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.QuanXinZhuangZhi.get(8),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.TangZu.get(64),
                ItemList.Shape_Empty.get(64),
                GTCMItemList.ZiJingXianWei.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.AccessoriesMachines.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.YanMoShi.get(8),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.GuYuanYanZu.get(64),
                GTCMItemList.QuanXinZhuangZhi.get(64),
                GTCMItemList.JingTiWaiKe.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.Pulverizers.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.YiTieZu.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.GuYuanYanZu.get(64),
                GTCMItemList.ZiJingZhiPing.get(64),
                GTCMItemList.JingTiWaiKe.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.EncapsulationMachines.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.YanMoShi.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.GangKuai.get(64),
                GTCMItemList.TanKuai.get(64),
                GTCMItemList.ShaYeFenMo.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.Grinders.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.TongNingJi.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.ZhuanZhiYanZu.get(64),
                GTCMItemList.DianJiDanYuan.get(64),
                GTCMItemList.WenDingTanKuai.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.ReactorCrucibles.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.TangZu.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.QuanXinZhuangZhi.get(16),
                GTCMItemList.TanKuai.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.Planters.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.QiaoHuaZhongZi.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.QuanXinZhuangZhi.get(16),
                GTCMItemList.TanKuai.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.SeedCollectingMachines.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.QuanXinZhuangZhi.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.LanTiePing.get(64),
                GTCMItemList.ZiJingZhiPing.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.FillingUnits.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.NingJiao.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.WenDingTanKuai.get(64),
                GTCMItemList.JingTiYuanJian.get(16),
                GTCMItemList.TangJuKuai.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.ForgeOfTheSkys.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.TanSu.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.LanTiePing.get(64),
                GTCMItemList.ZiJingZhiPing.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.SeparatingUnit.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.HuaHeQieXiaoYe.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.TangZu.get(64),
                GTCMItemList.ZiJingXianWei.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.GearingUnit.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.XiRang.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.QingMengKuang.get(64),
                GTCMItemList.ZhiYuanLiao.get(64),
                GTCMItemList.MiZhiJingTi.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.XirangAssembler.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.QieXiaoYuanYe.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.TongZhenLie.get(64),
                GTCMItemList.JuSuanZhiKuai.get(64),
                GTCMItemList.WenDingTanKuai.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.PurificationUnits.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.JuHeNingJiao.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.YeHuaMiXiJuTi.get(64),
                GTCMItemList.BaiMaChun.get(64),
                GTCMItemList.WenDingTanKuai.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.GasReactorGlobe.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.ChiHeJinKuai.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.GaoJingXianWei.get(64),
                GTCMItemList.HeTongKuai.get(64),
                GTCMItemList.HuanTingYuZhiTi.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.GasExtractor.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JiChuJiaGuJianCai.get(8),
                GTCMItemList.QuanXinZhuangZhi.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 16),
                GTCMItemList.QingMengKuang.get(64),
                GTCMItemList.ZiJingXianWei.get(64),
                GTCMItemList.GuYuanYanZu.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(8000)
            )
            .itemOutputs(
                GTCMItemList.ElectricTypeTwoMiningMachine.get(1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.JinJieJiaGuJianCai.get(8),
                GTCMItemList.YiTieKuai.get(64),
                GTOreDictUnificator.get(OrePrefixes.circuit, Materials.IV, 16),
                GTCMItemList.RMA70_12.get(64),
                GTCMItemList.MiZhiJingTi.get(64),
                GTCMItemList.WenDingTanKuai.get(64),
                GTCMItemList.YuanShi.get(16)
            )
            .fluidInputs(
                Materials.Lubricant.getFluid(16000)
            )
            .itemOutputs(
                GTCMItemList.HydroMiningRig.get(1)
            )
            .eut(TierEU.RECIPE_IV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);
    }
}
