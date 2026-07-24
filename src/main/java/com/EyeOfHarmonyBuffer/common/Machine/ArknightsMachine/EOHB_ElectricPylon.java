package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.angelica.shadow.javax.annotation.Nonnull;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings2;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;

public class EOHB_ElectricPylon extends OrundumWirelessMultiMachineBase<EOHB_ElectricPylon>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_ElectricPylon> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainElectricPylon";
    private static final int OffsetsX = 3;
    private static final int OffsetsY = 24;
    private static final int OffsetsZ = 2;
    private static final int CASING_INDEX1 = 183;

    public EOHB_ElectricPylon(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_ElectricPylon(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 20;
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
        return 1;
    }

    @Override
    protected boolean shouldRequireOrundumField() {
        return false;
    }

    @Override
    protected boolean shouldShowWirelessWaila(NBTTagCompound tag) {
        return false;
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || base == null || !base.isAllowedToWork()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        mEfficiency = 10000;
        mEfficiencyIncrease = 0;
        mMaxProgresstime = getWirelessModeProcessingTime();
        mProgresstime = 0;

        mEUt = 0;
        mOutputItems = null;
        mOutputFluids = null;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return true;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {
        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);

        if (!ok) return;
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

    private static final String[][] shapeMain = new String[][]{
        {"       ","A      "," A     ","  AAAAA","  AAA  ","   A   ","  A    "," A     "},
        {"       ","       ","       ","   A   ","       ","       ","       ","       "},
        {"       ","       ","   C   ","  CCC  ","   C   ","       ","       ","       "},
        {"       ","       ","   C   ","  CCC  ","   C   ","       ","       ","       "},
        {"       ","       ","       ","   A   ","       ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  C C  ","   C   ","   C   ","       ","       ","       "},
        {"       ","       ","  A A  ","   A   ","   A   ","       ","       ","       "},
        {"       ","       ","  A A  ","       ","   A   ","       ","       ","       "},
        {"       ","       ","  A A  ","       ","   A   ","       ","       ","       "},
        {"       ","       ","  AAA  ","  AAA  ","  AAA  ","       ","       ","       "},
        {"       "," C   C ","  CCC  ","  C C  ","  CCC  "," C   C ","       ","       "},
        {"       "," C   C ","  CCC  ","  C C  ","  CCC  "," C   C ","       ","       "},
        {"C     C"," C   C ","  CCC  ","  C C  ","  CCC  "," C   C ","C     C","       "},
        {"C     C","       ","  CCC  ","  C C  ","  CCC  ","       ","C     C","       "},
        {"C     C","       ","  C~C  ","  C C  ","  CCC  ","       ","C     C","       "},
        {"B     B"," AAAAA "," AAAAA "," AAAAA "," AAAAA "," AAAAA ","B     B","       "},
        {"B     B","  AAA  "," AAAAA "," AAAAA "," AAAAA ","  AAA  ","B     B","       "}
    };

    @Override
    public IStructureDefinition<EOHB_ElectricPylon> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_ElectricPylon>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement(
                    'A',
                    ofBlock(sBlockCasings2, 0)
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings8, 7)
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_ElectricPylon_MachineType)
            .addInfo(Tooltip_ElectricPylon_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_ElectricPylon_00)
            .addInfo(Tooltip_ElectricPylon_01)
            .addInfo(Tooltip_ElectricPylon_02)
            .addInfo(Tooltip_ElectricPylon_03)
            .addInfo(Tooltip_ElectricPylon_04)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_ElectricPylon(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive)
                return new ITexture[]{
                    Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                        .extFacing()
                        .glow()
                        .build()
                };
            return new ITexture[]{
                Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build()
            };
        }
        return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1)};
    }
}
