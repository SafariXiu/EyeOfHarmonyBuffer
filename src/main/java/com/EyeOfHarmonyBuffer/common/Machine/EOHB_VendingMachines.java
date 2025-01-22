package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.processingLogics.GTCM_ProcessingLogic;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.TAE;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IWirelessEnergyHatchInformation;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.*;
import gregtech.common.tileentities.machines.MTEHatchCraftingInputME;
import gtPlusPlus.core.block.ModBlocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_VendingMachines extends WirelessEnergyMultiMachineBase<EOHB_VendingMachines> implements IWirelessEnergyHatchInformation {

    private ItemStack target;
    private static IStructureDefinition<EOHB_VendingMachines> STRUCTURE_DEFINITION = null;
    private int mCasing;

    public EOHB_VendingMachines(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_VendingMachines(String aName) {
        super(aName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_VendingMachines(this.mName);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 64;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 0;
    }

    @Override
    protected int getMaxParallelRecipes() {
        return 5;
    }

    private GTRecipe getRecipe() {
        for (MTEHatchInputBus bus : this.mInputBusses) {
            // 如果是特殊输入总线，特殊逻辑处理
            if (bus instanceof MTEHatchCraftingInputME) {
                // 检查最后一个槽位（假设是目标槽位）
                if (bus.mInventory[bus.getSizeInventory() - 1] == null) continue;
                this.target = bus.mInventory[bus.getSizeInventory() - 1];

                // 遍历其他槽位，从倒数第二个开始
                for (int i = bus.getSizeInventory() - 2; i >= 0; i--) {
                    ItemStack itemsInSlot = bus.mInventory[i];
                    if (itemsInSlot != null) {
                        // 动态生成配方
                        GTRecipe tRecipe = createTemporaryRecipe(itemsInSlot);
                        if (tRecipe != null) {
                            return tRecipe;
                        }
                    }
                }
            } else {
                // 普通输入总线逻辑
                this.target = this.getControllerSlot(); // 获取控制器槽位
                for (int i = bus.getSizeInventory() - 1; i >= 0; i--) {
                    ItemStack itemsInSlot = bus.mInventory[i];
                    if (itemsInSlot != null) {
                        // 动态生成配方
                        GTRecipe tRecipe = createTemporaryRecipe(itemsInSlot);
                        if (tRecipe != null) {
                            return tRecipe;
                        }
                    }
                }
            }
        }
        return null;
    }

    private GTRecipe createTemporaryRecipe(ItemStack inputItem) {
        if (inputItem == null) return null;

        // 输入物品：将输入物品作为配方输入
        ItemStack[] aInputs = new ItemStack[]{inputItem};
        // 输出物品：复制输入物品作为输出
        ItemStack[] aOutputs = new ItemStack[]{inputItem.copy()};
        aOutputs[0].stackSize = 1; // 输出物品的数量（这里设置为 1，可根据需求调整）

        // 概率数组（这里假设 100% 掉落率）
        int[] aChances = new int[]{10000}; // 数值 10000 表示 100% 掉落率

        // 流体输入和输出（这里为空）
        FluidStack[] aFluidInputs = null;  // 没有流体输入
        FluidStack[] aFluidOutputs = null; // 没有流体输出

        // 配方其他属性
        boolean aOptimize = false;  // 不启用优化
        int aDuration = 20;         // 配方持续时间（20 ticks）
        int aEUt = 100;             // 每 tick 消耗的能量（100 EU）
        int aSpecialValue = 0;      // 特殊值（默认 0）

        // 创建并返回配方
        return new GTRecipe(
            aOptimize,
            aInputs,
            aOutputs,
            null,       // 特殊物品
            aChances,   // 掉落概率
            aFluidInputs,
            aFluidOutputs,
            aDuration,
            aEUt,
            aSpecialValue
        );
    }

    @Override
    protected ProcessingLogic createProcessingLogic(){
        return new GTCM_ProcessingLogic(){

            @Override
            protected @NotNull OverclockCalculator createOverclockCalculator(@Nonnull GTRecipe recipe) {
                return OverclockCalculator.ofNoOverclock(recipe);
            }

            @NotNull
            @Override
            protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
                return new ParallelHelper()
                    .setRecipe(recipe)
                    .setItemInputs(inputItems)
                    .setFluidInputs(inputFluids)
                    .setAvailableEUt(Integer.MAX_VALUE) // 设置无限能量
                    .setMachine(machine, protectItems, protectFluids)
                    .setMaxParallel(Integer.MAX_VALUE) // 设置极大并行
                    .setEUtModifier(0.0) // 不耗电
                    .enableBatchMode(batchSize) // 启用批量模式
                    .setConsumption(true)
                    .setOutputCalculation(true);
            }

            @Override
            protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                               @Nonnull OverclockCalculator calculator) {
                return 128;
            }
        }.setMaxParallel(Integer.MAX_VALUE);
    }

    @Override
    public IStructureDefinition<EOHB_VendingMachines> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_VendingMachines>builder()
                .addShape(
                    mName,
                    transpose(
                        // spotless:off
                        new String[][] {
                            { "CCC", "CCC", "CCC" },
                            { "C~C", "C-C", "CCC" },
                            { "CCC", "CCC", "CCC" },
                        }))
                // spotless:on
                .addElement(
                    'C',
                    buildHatchAdder(EOHB_VendingMachines.class)
                        .atLeast(InputBus, OutputBus, Maintenance, Energy, Muffler)
                        .casingIndex(90)
                        .dot(1)
                        .buildAndChain(onElementPass(x -> ++x.mCasing, ofBlock(ModBlocks.blockCasings5Misc, 5))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(mName, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_VendingMachines_MachineType)
            .addInfo(Tooltip_VendingMachines_Controller)
            .addInfo(Tooltip_VendingMachines_00)
            .addInfo(Tooltip_VendingMachines_01);
        return tt;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasing = 0;
        return checkPiece(mName, 1, 1, 0) && mCasing >= 6;
    }

    @Override
    protected void sendStartMultiBlockSoundLoop() {
        sendLoopStart(PROCESS_START_SOUND_INDEX);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    public int getCasingTextureID() {
        return TAE.getIndexFromPage(2, 2);
    }

    public ItemStack getTarget() {
        return target;
    }
}
