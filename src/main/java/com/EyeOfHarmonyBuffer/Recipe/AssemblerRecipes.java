package com.EyeOfHarmonyBuffer.Recipe;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.*;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.recipe.Scanning;
import gtPlusPlus.core.material.MaterialsElements;
import gtPlusPlus.core.material.Particle;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.Utils.copyAmount;
import static gregtech.api.enums.Mods.*;
import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.*;
import static gtPlusPlus.core.material.Particle.*;
import static gtPlusPlus.core.material.Particle.GRAVITON;
import static gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList.Controller_ElementalDuplicator;
import static tectech.thing.CustomItemList.*;

public final class AssemblerRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        ItemStack CapBank = getModItem(EnderIO.ID, "blockCapBank",1,3);
        NBTTagCompound RfNBT = CapBank.getTagCompound();
        if (RfNBT != null) {
            RfNBT.setLong("storedEnergyRF", 25000000);
        } else {
            RfNBT = new NBTTagCompound();
            RfNBT.setLong("storedEnergyRF", 25000000);
            CapBank.setTagCompound(RfNBT);
        }

        ItemStack DraconiumFluxCapacitor = getModItem(DraconicEvolution.ID, "draconiumFluxCapacitor",1,0);
        NBTTagCompound RFNBT = DraconiumFluxCapacitor.getTagCompound();
        if(RFNBT != null) {
            RFNBT.setLong("Energy", 80000000);
        } else {
            RFNBT = new NBTTagCompound();
            RFNBT.setLong("Energy", 80000000);
            DraconiumFluxCapacitor.setTagCompound(RFNBT);
        }

        List<ItemStack> materials = Arrays.asList(
            new ItemStack(Blocks.stonebrick, 1, 0), // 普通石砖
            new ItemStack(Blocks.stonebrick, 1, 1), // 青苔石砖
            new ItemStack(Blocks.stonebrick, 1, 2), // 裂石砖
            new ItemStack(Blocks.brick_block, 1, 0), // 砖块
            new ItemStack(Blocks.sandstone, 1, 2), // 雕刻砂岩
            new ItemStack(Blocks.sandstone, 1, 1), // 浮雕砂岩
            new ItemStack(Blocks.nether_brick, 1, 0), // 下界砖
            new ItemStack(Blocks.stonebrick, 1, 3), // 浮雕石砖
            new ItemStack(Blocks.quartz_block, 1, 0), // 石英
            new ItemStack(Blocks.quartz_block, 1, 1), // 浮雕石英
            new ItemStack(Blocks.quartz_block, 1, 2) // 平滑石英
        );

        List<Integer> farmBlockValues = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        ItemStack forestryFarm = getModItem(Forestry.ID, "ffarm", 1, 0);
        ItemStack gearCaseFarmBlock = getModItem(Forestry.ID, "ffarm", 1, 2);

        List<ItemStack> farmBlocks = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            ItemStack farmBlock = forestryFarm.copy();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("FarmBlock", farmBlockValues.get(i));
            farmBlock.setTagCompound(tag);

            farmBlocks.add(farmBlock);
        }

        //农场方块
        for (int i = 0; i < materials.size(); i++) {
            ItemStack input = materials.get(i);
            int farmBlockValue = farmBlockValues.get(i);

            ItemStack output = forestryFarm.copy();
            output.stackSize = 4;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("FarmBlock", farmBlockValue);
            output.setTagCompound(tag);

            GTValues.RA.stdBuilder()
                .itemInputs(
                    input,
                    GTOreDictUnificator.get(OrePrefixes.screw, Materials.Steel, 4),
                    GTOreDictUnificator.get(OrePrefixes.itemCasing, Materials.Copper, 4),
                    getModItem(Forestry.ID, "thermionicTubes", 1, 10)
                )
                .fluidInputs(
                    FluidRegistry.getFluidStack("creosote", 500)
                )
                .itemOutputs(output)
                .eut(TierEU.RECIPE_EV)
                .duration(10 * SECONDS)
                .addTo(assemblerRecipes);
        }

        //农场齿轮箱
        for (int i = 0; i < farmBlocks.size(); i++) {
            ItemStack input = farmBlocks.get(i);

            ItemStack output = gearCaseFarmBlock.copy();
            output.stackSize = 2;

            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("FarmBlock", i);
            output.setTagCompound(tag);

            GTValues.RA.stdBuilder()
                .itemInputs(
                    input,
                    GTOreDictUnificator.get(OrePrefixes.gear, Materials.Steel, 1),
                    ItemList.Electric_Motor_LV.get(4),
                    getModItem(Forestry.ID, "thermionicTubes", 4, 2)
                )
                .fluidInputs(
                    FluidRegistry.getFluidStack("creosote", 1000)
                )
                .itemOutputs(output)
                .nbtSensitive()
                .eut(TierEU.RECIPE_EV)
                .duration(10 * SECONDS)
                .addTo(assemblerRecipes);
        }

        //农场出货箱
        for (int i = 0; i < farmBlocks.size(); i++) {
            ItemStack input = farmBlocks.get(i);

            ItemStack output = getModItem(Forestry.ID, "ffarm", 2, 3).copy();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("FarmBlock", i);
            output.setTagCompound(tag);

            GTValues.RA.stdBuilder()
                .itemInputs(
                    input,
                    GTOreDictUnificator.get(OrePrefixes.gear, Materials.Steel, 1),
                    ItemList.Electric_Motor_LV.get(1),
                    ItemList.Conveyor_Module_LV.get(2),
                    getModItem(Forestry.ID, "thermionicTubes", 4, 1),
                    new ItemStack(Blocks.hopper, 1)
                )
                .fluidInputs(
                    FluidRegistry.getFluidStack("creosote", 1000)
                )
                .itemOutputs(output)
                .nbtSensitive()
                .eut(TierEU.RECIPE_EV)
                .duration(10 * SECONDS)
                .addTo(assemblerRecipes);
        }

        //农场水阀
        for (int i = 0; i < farmBlocks.size(); i++) {
            ItemStack input = farmBlocks.get(i);

            ItemStack output = getModItem(Forestry.ID, "ffarm", 2, 4).copy();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("FarmBlock", i);
            output.setTagCompound(tag);

            GTValues.RA.stdBuilder()
                .itemInputs(
                    input,
                    GTOreDictUnificator.get(OrePrefixes.gear, Materials.Steel, 1),
                    ItemList.Electric_Motor_LV.get(1),
                    ItemList.Electric_Pump_LV.get(2),
                    getModItem(Forestry.ID, "thermionicTubes", 4, 11),
                    GTOreDictUnificator.get(OrePrefixes.ring, Materials.Rubber, 1)
                )
                .fluidInputs(
                    FluidRegistry.getFluidStack("creosote", 1000)
                )
                .itemOutputs(output)
                .nbtSensitive()
                .eut(TierEU.RECIPE_EV)
                .duration(10 * SECONDS)
                .addTo(assemblerRecipes);
        }

        //农场控制盒
        for (int i = 0; i < farmBlocks.size(); i++) {
            ItemStack input = farmBlocks.get(i);
            input.stackSize = 2;

            ItemStack output = getModItem(Forestry.ID, "ffarm", 2, 5).copy();
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("FarmBlock", i);
            output.setTagCompound(tag);

            GTValues.RA.stdBuilder()
                .itemInputs(
                    input,
                    ItemList.Electric_Motor_LV.get(1),
                    getModItem(Forestry.ID, "thermionicTubes", 4, 4),
                    GTOreDictUnificator.get(OrePrefixes.circuit, Materials.Basic, 2),
                    GTOreDictUnificator.get(OrePrefixes.cableGt01, Materials.Tin, 1)
                )
                .fluidInputs(
                    FluidRegistry.getFluidStack("creosote", 1000)
                )
                .itemOutputs(output)
                .nbtSensitive()
                .eut(TierEU.RECIPE_EV)
                .duration(10 * SECONDS)
                .addTo(assemblerRecipes);
        }

        //满电双足飞龙通量电容器
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(DraconicEvolution.ID, "draconiumFluxCapacitor",1,0)
            )
            .itemOutputs(
                DraconiumFluxCapacitor
            )
            .eut(TierEU.RECIPE_UXV)
            .duration(4 * SECONDS)
            .addTo(assemblerRecipes);

        //满电谐振电容库
        GTValues.RA.stdBuilder()
            .itemInputs(
                getModItem(EnderIO.ID, "blockCapBank",1,3)
            )
            .itemOutputs(
                CapBank
            )
            .eut(TierEU.RECIPE_UMV)
            .duration(2 * SECONDS)
            .addTo(assemblerRecipes);

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
