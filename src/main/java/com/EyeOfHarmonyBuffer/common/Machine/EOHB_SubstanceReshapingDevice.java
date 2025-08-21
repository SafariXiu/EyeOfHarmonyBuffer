package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import gregtech.api.enums.TAE;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import static com.EyeOfHarmonyBuffer.common.Block.BasicBlocks.SingularityStabilizationRingCasingsUpgrade;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_OFF;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_SubstanceReshapingDevice extends WirelessEnergyMultiMachineBase<EOHB_SubstanceReshapingDevice> {

    public EOHB_SubstanceReshapingDevice(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_SubstanceReshapingDevice(String aName) {
        super(aName);
    }

    protected static final String STRUCTURE_PIECE_MAIN = "mainSubstanceReshapingDevice";
    protected static IStructureDefinition<EOHB_SubstanceReshapingDevice> STRUCTURE_DEFINITION = null;
    private int mCasing;
    private int totalSpeedIncrement = 0;

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_SubstanceReshapingDevice(this.mName);
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasing = 0;
        return checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0) && mCasing >= 6;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (this.mMachine) return -1;
        return this.survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            1,
            1,
            0,
            elementBudget,
            env,
            false,
            true);
    }

    protected static final String[][] shapeMain = new String[][]{
        { "CCC", "CCC", "CCC", "CCC" },
        { "C~C", "CAC", "CAC", "CCC" },
        { "CCC", "CCC", "CCC", "CCC" },
    };

    @Override
    public IStructureDefinition<EOHB_SubstanceReshapingDevice> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = IStructureDefinition.<EOHB_SubstanceReshapingDevice>builder()
                .addShape(STRUCTURE_PIECE_MAIN,transpose(shapeMain))
                .addElement(
                    'C',
                    buildHatchAdder(EOHB_SubstanceReshapingDevice.class)
                        .atLeast(InputBus, OutputBus, InputHatch, OutputHatch)
                        .casingIndex(getTextureIndex())
                        .dot(1)
                        .buildAndChain(onElementPass(x -> ++x.mCasing, ofBlock(ModBlocks.blockCasings3Misc, 2))))
                .addElement(
                    'A',
                    withChannel(
                        "SingularityStabilizationRingCasings",
                        ofBlocksTiered(
                            EOHB_SubstanceReshapingDevice::getSingularityStabilizationRingCasingsLevel,
                            ImmutableList.of(
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 0),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 1),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 2),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 3),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 4),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 5),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 6),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 7),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 8),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 9),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 10),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 11),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 12),
                                Pair.of(SingularityStabilizationRingCasingsUpgrade, 13)
                                ),
                            0,
                            (m, t) -> m.totalSpeedIncrement = t,
                            m -> m.totalSpeedIncrement)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_SubstanceReshapingDevice_MachineType)
            .addInfo(Tooltip_SubstanceReshapingDevice_Controller)
            .addInfo(EOHB_Starry_Miracle_Project)
            .addInfo(EOHB_Text_SeparatingLine)
            .addInfo(Tooltip_SubstanceReshapingDevice_00)
            .addInfo(Tooltip_SubstanceReshapingDevice_01)
            .addInfo(Tooltip_SubstanceReshapingDevice_02)
            .addInfo(Tooltip_SubstanceReshapingDevice_03)
            .addInfo(Tooltip_SubstanceReshapingDevice_04)
            .addInfo(Tooltip_SubstanceReshapingDevice_05)
            .addInfo(EOHB_Text_SeparatingLine)
            .addInfo(Tooltip_SubstanceReshapingDevice_06)
            .addInfo(Tooltip_SubstanceReshapingDevice_07)
            .addInfo(Tooltip_SubstanceReshapingDevice_08)
            .addInfo(EOHB_Text_SeparatingLine)
            .addInfo(Tooltip_SubstanceReshapingDevice_09)
            .addInfo(Tooltip_SubstanceReshapingDevice_10)
            .addSeparator()
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .addInputBus(add_InputBus)
            .addInputHatch(add_inputHatch)
            .addOutputBus(add_OutputBus)
            .addOutputHatch(add_outputHatch)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
    }

    private static int getSingularityStabilizationRingCasingsLevel(Block block,int meta){
        if(!MachineLoaderConfig.SubstanceReshapingDevice.get()){
            if(block == SingularityStabilizationRingCasingsUpgrade) return meta + 1;
            return -1;
        }
        return Integer.MAX_VALUE;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.SubstanceReshapingDevice;
    }

    @Override
    protected ProcessingLogic createProcessingLogic(){
        return new ProcessingLogic(){
            @NotNull
            @Override
            protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe){
                if (recipe.mSpecialValue <= totalSpeedIncrement){
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                } else
                    return SimpleCheckRecipeResult.ofFailure("SingularityStabilizationRingCasingsLive");
            }
        };
    }

    @Override
    public boolean getDefaultWirelessMode() {
        return true;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 1;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 64;
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 100;
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

    public int getTextureIndex() {
        return TAE.getIndexFromPage(2, 2);
    }
}
