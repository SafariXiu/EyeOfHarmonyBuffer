package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import static gregtech.api.enums.Mods.*;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public final class AssemblerRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        //2空间组件
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.EngineeringProcessorSpatialPulsatingCore",1,0),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.Glowstone,4),
                GTOreDictUnificator.get(OrePrefixes.pearl, Materials.Fluix,4)
            )
            .itemOutputs(
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",1,32)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        //16空间组件
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.EngineeringProcessorSpatialPulsatingCore",1,0),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderPearl,4),
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",4,32)
            )
            .itemOutputs(
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",1,33)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        //128空间组件
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.EngineeringProcessorSpatialPulsatingCore",1,0),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderEye,4),
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",4,33)
            )
            .itemOutputs(
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",1,34)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        //福鲁伊克斯珍珠
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.EnderPearl,1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderEye,4),
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.Fluix,4)
            )
            .itemOutputs(
                GTOreDictUnificator.get(OrePrefixes.pearl, Materials.Fluix,4)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        //福鲁伊克斯珍珠
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.gem, Materials.EnderPearl,1),
                GTOreDictUnificator.get(OrePrefixes.plate, Materials.EnderEye,4),
                getModItem(AppliedEnergistics2.ID,"item.ItemMultiMaterial",4,12)
            )
            .itemOutputs(
                GTOreDictUnificator.get(OrePrefixes.pearl, Materials.Fluix,4)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(20 * SECONDS)
            .addTo(assemblerRecipes);

        //HV变压器
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_HV.get(1),
                GTOreDictUnificator.get(OrePrefixes.cableGt01,Materials.Gold,4),
                GTOreDictUnificator.get(OrePrefixes.cableGt01,Materials.Aluminium,1),
                ItemList.Circuit_Chip_ULPIC.get(2)
            )
            .itemOutputs(
                ItemList.Transformer_EV_HV.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //标定框架
        GTValues.RA.stdBuilder()
            .itemInputs(
                GTOreDictUnificator.get(OrePrefixes.screw,Materials.Steel,4),
                getModItem(Forestry.ID, "propolis",1,0),
                getModItem(Forestry.ID, "propolis",1,3),
                getModItem(Forestry.ID, "royalJelly",1,0),
                getModItem(Forestry.ID, "frameImpregnated",1,0)
            )
            .fluidInputs(
                FluidRegistry.getFluidStack("for.honey", 100)
                )
            .itemOutputs(
                getModItem(Forestry.ID, "frameProven",1,0)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //红石灯
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(Blocks.glowstone,1),
                new ItemStack(Blocks.glass_pane,7),
                GTOreDictUnificator.get(OrePrefixes.stick,Materials.RedAlloy,1)
            )
            .itemOutputs(
                new ItemStack(Blocks.redstone_lamp,1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //Z-逻辑控制器
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(Items.skull, 1, 2),
                GTOreDictUnificator.get(OrePrefixes.plate,Materials.Soularium,2),
                GTOreDictUnificator.get(OrePrefixes.plate,Materials.SiliconSG,2),
                GTOreDictUnificator.get(OrePrefixes.plate,Materials.RedAlloy,2)
            )
            .itemOutputs(
                getModItem(EnderIO.ID, "itemFrankenSkull",1,1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //压缩太阳能
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.ReinforcedAluminiumIronPlate",1,0),
                getModItem(IndustrialCraft2.ID, "blockGenerator",8,3)
            )
            .itemOutputs(
                getModItem(ElectroMagicTools.ID, "EMTSolars",1,0)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        //2级压缩太阳能
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(NewHorizonsCoreMod.ID,"item.IrradiantReinforcedTitaniumPlate",1,0),
                getModItem(ElectroMagicTools.ID, "EMTSolars",8,0)
            )
            .itemOutputs(
                getModItem(ElectroMagicTools.ID, "EMTSolars",1,1)
            )
            .eut(TierEU.RECIPE_EV)
            .duration(30 * SECONDS)
            .addTo(assemblerRecipes);

        //红色按钮
        GTValues.RA.stdBuilder()
            .itemInputs(
                new ItemStack(Blocks.stone_button,1),
                getModItem(ProjectRedCore.ID, "projectred.core.part",2,33)
            )
            .itemOutputs(
                getModItem(ProjectRedIllumination.ID, "projectred.illumination.lightbutton",1,14)
            )
            .eut(TierEU.RECIPE_LV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //充能龙块
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(DraconicEvolution.ID, "draconium",1,0)
            )
            .itemOutputs(
                getModItem(DraconicEvolution.ID, "draconium",1,2)
            )
            .eut(TierEU.RECIPE_MAX)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //超级箱1
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Automation_ChestBuffer_LV.get(1),
                GTOreDictUnificator.get(OrePrefixes.plateDense,Materials.Iron,3),
                GTOreDictUnificator.get(OrePrefixes.plate,Materials.PulsatingIron,1),
                GTOreDictUnificator.get(OrePrefixes.circuit,Materials.Basic,4)
            )
            .itemOutputs(
                ItemList.Super_Chest_LV.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //低压缓存器
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_LV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit,Materials.Basic,1),
                ItemList.Conveyor_Module_LV.get(1),
                new ItemStack(Blocks.chest,1)
            )
            .itemOutputs(
                ItemList.Automation_ChestBuffer_LV.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //超级缸1
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Casing_Tank_1.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit,Materials.Basic,4),
                ItemList.Electric_Pump_MV.get(1),
                GTOreDictUnificator.get(OrePrefixes.plate,Materials.Aluminium,2),
                GTOreDictUnificator.get(OrePrefixes.plate,Materials.PulsatingIron,1)
            )
            .itemOutputs(
                ItemList.Super_Tank_LV.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //量子缸1
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Casing_Tank_6.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit,Materials.Master,4),
                ItemList.Electric_Pump_IV.get(1),
                GTOreDictUnificator.get(OrePrefixes.plate,Materials.TungstenSteel,2),
                ItemList.Field_Generator_EV.get(1)
            )
            .itemOutputs(
                ItemList.Quantum_Tank_LV.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //量子箱1
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Automation_ChestBuffer_LuV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit,Materials.Master,4),
                ItemList.Field_Generator_EV.get(1),
                GTOreDictUnificator.get(OrePrefixes.plateQuadruple,Materials.TungstenSteel,3)
            )
            .itemOutputs(
                ItemList.Quantum_Chest_LV.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);

        //剧差压缓存器
        GTValues.RA.stdBuilder()
            .itemInputs(
                ItemList.Hull_LuV.get(1),
                ItemList.Conveyor_Module_LuV.get(1),
                GTOreDictUnificator.get(OrePrefixes.circuit,Materials.Master,1),
                new ItemStack(Blocks.chest,1)
            )
            .itemOutputs(
                ItemList.Automation_ChestBuffer_LuV.get(1)
            )
            .eut(TierEU.RECIPE_HV)
            .duration(5 * SECONDS)
            .addTo(assemblerRecipes);
    }
}
