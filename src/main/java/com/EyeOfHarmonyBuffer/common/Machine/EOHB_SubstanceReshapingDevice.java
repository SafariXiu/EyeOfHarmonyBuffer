package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import gregtech.api.enums.TAE;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IWirelessEnergyHatchInformation;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.block.ModBlocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_DTPF_OFF;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_SubstanceReshapingDevice extends WirelessEnergyMultiMachineBase<EOHB_SubstanceReshapingDevice>
    implements IWirelessEnergyHatchInformation {

    public EOHB_SubstanceReshapingDevice(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_SubstanceReshapingDevice(String aName) {
        super(aName);
    }

    protected static final String STRUCTURE_PIECE_MAIN = "mainCoreDrill";
    protected static IStructureDefinition<EOHB_SubstanceReshapingDevice> STRUCTURE_DEFINITION = null;
    private int mCasing;

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
        if (mMachine) return -1;
        return survivialBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    protected static final String[][] shapeMain = new String[][]{
        { "CCC", "CCC", "CCC" },
        { "C~C", "C-C", "CCC" },
        { "CCC", "CCC", "CCC" },
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

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.SubstanceReshapingDevice;
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
    protected int getMaxParallelRecipes() {
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
