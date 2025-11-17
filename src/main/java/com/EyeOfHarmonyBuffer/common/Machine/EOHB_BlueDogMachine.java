package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.EyeOfHarmonyBuffer.utils.Utils;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

import java.util.Objects;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Mods.Chisel;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.casingTexturePages;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_BlueDogMachine extends WirelessEnergyMultiMachineBase<EOHB_BlueDogMachine> {

    private static IStructureDefinition<EOHB_BlueDogMachine> STRUCTURE_DEFINITION = null;
    protected static final String STRUCTURE_PIECE_MAIN = "mainBlueDogMachine";
    private static Boolean BlueDogModel = false;
    protected static final int DIM_INJECTION_CASING = 13;
    private static final int OffsetsX = 5;
    private static final int OffsetsY = 6;
    private static final int OffsetsZ = 0;

    public EOHB_BlueDogMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_BlueDogMachine(String mName) {
        super(mName);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_BlueDogMachine(this.mName);
    }

    protected static final String[][] shapeMain = new String[][]{
        {"           ","           ","           ","           ","           ","           ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","  BBBBBBB  "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","  BBBBBBB  "," B       B "," B       B ","BB       BB","BB       BB","BB       BB","BB       BB"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","  AABBBAA  "," B       B "," B       B ","BB       BB","BB       BB","BB       BB","BB       BB"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","  ADBBBDA  "," B       B "," B       B ","BB       BB","BB       BB","BB       BB","BB       BB"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  "," B       B "," B       B ","BB       BB","BB       BB","BB       BB","BB       BB"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"  BBD~DBB  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  BBBBBBB  "," B       B "," B       B ","BB       BB","BB       BB","BB       BB","BB       BB"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"  BBBDBBB  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  BBBBBBB  "," B       B "," B       B ","BB       BB","BB       BB","BB       BB","BB       BB"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"  BBBBBBB  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  BBBBBBB  "," B       B "," B       B ","BB       BB","BB       BB","BB       BB","BB       BB"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"   BBBBB   ","   BBBBB   ","   BBBBB   ","   BBBBB   ","   BBBBB   ","  BBBBBBB  "," B       B "," B       B ","AB       BA","AB       BA","AB       BA","AB       BA"," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           "},
        {"           ","           ","    CCC    ","    CCC    ","    CCC    ","  BBBBBBB  "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","    AAA    "},
        {"           ","    CCC    ","    CCC    ","           ","   BBBBB   ","  BBBBBBB  "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B "," B       B ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","    BBB    ","    AAA    "},
        {"    CCC    ","    CCC    ","           ","   BBBBB   ","   BBBBB   ","           ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","    BBB    ","    BBB    ","           "},
        {"    CCC    ","           ","   BBBBB   ","   BBBBB   ","           ","           ","           ","   BBBBB   ","   B   B   ","   B   B   ","   B   B   ","   B   B   ","   BBBBB   ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","           ","    BBB    ","    BBB    ","           ","           "},
        {"    CCC    ","           ","           ","           ","           ","           ","           ","   BBBBB   ","   B   B   ","   B   B   ","   B   B   ","   B   B   ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","   BBBBB   ","   BBBBB   ","    BBB    ","    BBB    ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","           ","           ","   BBBBB   ","   B   B   ","   B   B   ","   B   B   ","  BB   BB  ","  BB   BB  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  BBBBBBB  ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","           ","           ","   BBBBB   "," BB     BB "," BB     BB "," BB     BB ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  "," BB     BB "," BB     BB "," BB     BB ","  B     B  ","  BBEEEBB  ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","           ","           ","BBBBBBBBBBB","BBB     BBB","BBB     BBB","BBB     BBB","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","  B     B  ","  BBEEEBB  ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","           ","   BBBBB   ","BBBBBBBBBBB","BBB     BBB","BBB     BBB","BBB     BBB","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","  B     B  ","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","  B     B  ","  BBEEEBB  ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","           ","           ","BBB     BBB","BBBBBBBBBBB","BBBBBBBBBBB","BBBBBBBBBBB","BBBBBBBBBBB","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","  BBBBBBB  ","BBBBBBBBBBB","BBBBBBBBBBB","BBBBBBBBBBB","BBBBBBBBBBB","BBBBBBBBBBB","  BBBBBBB  ","  BBBBBBB  ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","           ","           ","           ","           ","           ","           ","           ","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","           ","           ","           ","           ","           ","           ","           "},
        {"           ","           ","           ","           ","AAA     AAA","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","           ","           ","           ","           ","           ","           ","           ","           ","           ","AAA     AAA","BBB     BBB","BBB     BBB","BBB     BBB","BBB     BBB","           ","           ","           ","           ","           ","           ","           ","           ","           "}
    };

    @Override
    public IStructureDefinition<EOHB_BlueDogMachine> getStructureDefinition(){
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_BlueDogMachine>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 0)
                )
                .addElement(
                    'B',
                    ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 11)
                )
                .addElement(
                    'C',
                    buildHatchAdder(EOHB_BlueDogMachine.class)
                        .atLeast(InputHatch)
                        .casingIndex(DIM_INJECTION_CASING)
                        .dot(1)
                        .buildAndChain(
                            ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 14)
                        )
                )
                .addElement(
                    'D',
                    ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 15)
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_BlueDogMachine.class)
                        .atLeast(OutputHatch)
                        .casingIndex(DIM_INJECTION_CASING)
                        .dot(2)
                        .buildAndChain(
                            ofBlock(Objects.requireNonNull(Block.getBlockFromName(Chisel.ID + ":hempcrete")), 15)
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
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
        return 0;
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 0;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, OffsetsX, OffsetsY, OffsetsZ, elementBudget, env, false, true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_BlueDogMachine_MachineType)
            .addInfo(Tooltip_BlueDogMachine_Controller)
            .addInfo(Tooltip_BlueDogMachine_00)
            .addInfo(Tooltip_BlueDogMachine_01)
            .addInfo(Tooltip_BlueDogMachine_02)
            .addInfo(Tooltip_BlueDogMachine_03)
            .addInfo(Tooltip_BlueDogMachine_04)
            .addInfo(Tooltip_BlueDogMachine_05)
            .addInfo(Tooltip_BlueDogMachine_06)
            .addSeparator()
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputHatch(add_inputHatch)
            .addOutputHatch(add_outputHatch)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        if(CheckMachineBluDogMode()){
            return RecipeMaps.BlueDogMachineMax;
        }
        return RecipeMaps.BlueDogMachine;
    }

    private boolean CheckMachineBluDogMode() {
        BlueDogModel = Utils.metaItemEqual(this.getControllerSlot(), MiscHelper.ASTRAL_ARRAY_FABRICATOR);
        return BlueDogModel;
    }

    @Override
    protected ProcessingLogic createProcessingLogic(){
        return new ProcessingLogic(){
            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                return new OverclockCalculator()
                    .setParallel(Integer.MAX_VALUE);
            }

            @NotNull
            @Override
            protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
                return new ParallelHelper()
                    .setRecipe(recipe)
                    .setItemInputs(inputItems)
                    .setFluidInputs(inputFluids)
                    .setAvailableEUt(Integer.MAX_VALUE)
                    .setMachine(machine, protectItems, protectFluids)
                    .setMaxParallel(Integer.MAX_VALUE)
                    .setEUtModifier(0.0)
                    .enableBatchMode(batchSize)
                    .setConsumption(true)
                    .setOutputCalculation(true);
            }

            @Override
            protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                               @Nonnull OverclockCalculator calculator) {
                return 10;
            }
        }.setMaxParallel(Integer.MAX_VALUE);
    }

    @Override
    @SuppressWarnings("ALL")
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) {
                return new ITexture[] { casingTexturePages[0][12], TextureFactory.builder()
                    .addIcon(OVERLAY_DTPF_ON)
                    .extFacing()
                    .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FUSION1_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }

            return new ITexture[] { casingTexturePages[0][12], TextureFactory.builder()
                .addIcon(OVERLAY_DTPF_OFF)
                .extFacing()
                .build() };
        }

        return new ITexture[] { casingTexturePages[0][12] };
    }
}
