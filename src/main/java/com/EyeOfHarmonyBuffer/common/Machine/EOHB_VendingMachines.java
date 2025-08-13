package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.processingLogics.GTCM_ProcessingLogic;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.TAE;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.*;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gtPlusPlus.core.block.ModBlocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Optional;
import java.util.stream.Stream;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_VendingMachines extends WirelessEnergyMultiMachineBase<EOHB_VendingMachines> {

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
    public int getMaxParallelRecipes() {
        return 5;
    }

    private GTRecipe getRecipe() {
        ItemStack outputItem = this.getControllerSlot();
        if (outputItem == null) return null;

        for (MTEHatchInputBus bus : this.mInputBusses) {
            for (int i = bus.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack inputItem = bus.mInventory[i];
                if (inputItem != null) {
                    GTRecipe tRecipe = createTemporaryRecipe(inputItem, outputItem);
                    if (tRecipe != null) {
                        return tRecipe;
                    }
                }
            }
        }

        for (IDualInputHatch craftingInputMe : this.mDualInputHatches) {
            Optional<IDualInputInventory> optionalInventory = craftingInputMe.getFirstNonEmptyInventory();

            if (optionalInventory.isPresent()) {
                IDualInputInventory inventory = optionalInventory.get();
                ItemStack[] inputItems = inventory.getItemInputs();

                for (int i = inputItems.length - 1; i >= 0; i--) {
                    ItemStack inputItem = inputItems[i];
                    if (inputItem != null) {
                        GTRecipe tRecipe = createTemporaryRecipe(inputItem, outputItem);
                        if (tRecipe != null) {
                            return tRecipe;
                        }
                    }
                }
            }
        }
        return null;
    }

    private GTRecipe createTemporaryRecipe(ItemStack inputItem, ItemStack outputItem) {
        if (inputItem == null || outputItem == null) return null;

        ItemStack outputCopy = outputItem.copy();
        outputCopy.stackSize = inputItem.stackSize;

        return new GTRecipe(
            false,
            new ItemStack[]{inputItem},
            new ItemStack[]{outputCopy},
            null, new int[]{10000},
            null, null,
            20, 100, 0);
    }

    @Override
    protected ProcessingLogic createProcessingLogic(){
        return new GTCM_ProcessingLogic(){

            @Nonnull
            @Override
            protected Stream<GTRecipe> findRecipeMatches(@Nullable RecipeMap<?> map) {
                return GTStreamUtil.ofNullable(getRecipe());
            }

            @NotNull
            @Override
            protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                return new OverclockCalculator()
                    //.setSpeedBoost(100.0) // 速度提升 100 倍
                    .setParallel(Integer.MAX_VALUE) // 最大并行数
                    .setEUt(0); // 不耗电
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
                        new String[][] {
                            { "CCC", "CCC", "CCC" },
                            { "C~C", "C-C", "CCC" },
                            { "CCC", "CCC", "CCC" },
                        }))
                .addElement(
                    'C',
                    buildHatchAdder(EOHB_VendingMachines.class)
                        .atLeast(InputBus, OutputBus)
                        .casingIndex(getTextureIndex())
                        .dot(1)
                        .buildAndChain(onElementPass(x -> ++x.mCasing, ofBlock(ModBlocks.blockCasings3Misc, 2))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(mName, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(mName, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        if(MachineLoaderConfig.VendingMachines){
            tt.addMachineType(Tooltip_VendingMachines_MachineType)
                    .addInfo(Tooltip_VendingMachines_Controller)
                    .addInfo(Tooltip_VendingMachines_00)
                    .addInfo(Tooltip_VendingMachines_01)
                    .addInfo(Tooltip_VendingMachines_02)
                    .addInfo(Tooltip_VendingMachines_03)
                    .addSeparator()
                    .addInputBus(add_InputBus,1)
                    .addOutputBus(add_OutputBus,1)
                    .addInfo(TextLocalization.StructureTooComplex)
                    .addInfo(TextLocalization.BLUE_PRINT_INFO)
                    .toolTipFinisher(TextLocalization.ModName);
        }else {
            tt.addMachineType(Tooltip_VendingMachines_MachineType)
                    .addMachineType(Tooltip_VendingMachines_Controller)
                    .addInfo(Disable_loading)
                    .addInfo(Tooltip_VendingMachines_00)
                    .addInfo(Tooltip_VendingMachines_01)
                    .addInfo(Tooltip_VendingMachines_02)
                    .addInfo(Tooltip_VendingMachines_03)
                    .addSeparator()
                    .addInputBus(add_InputBus,1)
                    .addOutputBus(add_OutputBus,1)
                    .addInfo(TextLocalization.StructureTooComplex)
                    .addInfo(TextLocalization.BLUE_PRINT_INFO)
                    .toolTipFinisher(TextLocalization.ModName);
        }
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
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureId()),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureId()),
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
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureId()) };
    }

    protected int getCasingTextureId() {
        return getTextureIndex();
    }

    public int getTextureIndex() {
        return TAE.getIndexFromPage(2, 2);
    }
}
