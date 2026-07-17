package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentType;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.IGasEnvironmentProvider;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_GasDiffuser extends OrundumWirelessMultiMachineBase<EOHB_GasDiffuser>
    implements IConstructable, ISurvivalConstructable, IGasEnvironmentProvider {

    private static IStructureDefinition<EOHB_GasDiffuser> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainGasDiffuser";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 20;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    public EOHB_GasDiffuser(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_GasDiffuser(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 1200;
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

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.GasDiffuser;
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
        {"         ","         ","   CCC   ","  CCCCC  ","  CCBCC  ","  CCCCC  ","   CCC   ","         ","         "},
        {"         ","         ","   BBB   ","  B   B  ","  B   B  ","  B   B  ","   BBB   ","         ","         "},
        {"         ","         ","   AAA   ","  A   A  ","  A   A  ","  A   A  ","   AAA   ","         ","         "},
        {"   CCC   ","  C   C  "," C     C ","C  AAA  C","CD AAA DC","C  AAA  C"," C     C ","  C   C  ","   CCC   "},
        {"         ","         ","         ","   AAA   ","AD A A DA","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   ","AD A A DA","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   ","A DA AD A","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   ","A DA AD A","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   BBB   ","A DBBBD A","   BBB   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","A  C C  A","   CCC   ","         ","         ","         "},
        {"   EEE   ","   AAA   ","   AAA   ","  ACCCA  ","AAAC CAAA","  ACCCA  ","   AAA   ","   BBB   ","         "},
        {"   EEE   ","   AAA   ","   AAA   ","   CCC   ","ABBC CBBA","   CCC   ","   AAA   ","   BBB   ","         "},
        {"         ","         ","   CCC   ","  C   C  ","ABC   CBA","  C   C  ","   CCC   ","         ","         "},
        {"         ","  CCCCC  "," CC   CC "," C     C ","AC     CA"," C     C "," CC   CC ","  CCCCC  ","         "},
        {"         ","  CCCCC  "," CC   CC "," C     C ","AC     CA"," C     C "," CC   CC ","  CCCCC  ","         "},
        {"         ","         ","   CCC   ","  C   C  ","A C   C A","  C   C  ","   CCC   ","         ","         "},
        {"         ","         ","         ","   CCC   ","A  CCC  A","   CCC   ","         ","         ","         "},
        {"         ","  CCCCC  "," CCCCCCC "," CCCCCCC ","ACCCCCCCA"," CCCCCCC "," CCCCCCC ","  CCCCC  ","         "},
        {"         ","  AAAAA  "," AAAAAAA "," AAAAAAA ","AAAAAAAAA"," AAAAAAA "," AAAAAAA ","  AAAAA  ","         "},
        {"BAAAAAAAB","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","BAAAAAAAB"},
        {"BAAA~AAAB","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","BAAAAAAAB"}
    };

    @Override
    public IStructureDefinition<EOHB_GasDiffuser> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_GasDiffuser>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings2, 0))
                .addElement('B', ofBlock(sBlockCasings2, 13))
                .addElement('C', ofBlock(sBlockCasings8, 7))
                .addElement('D', ofBlock(sBlockFrames, 305))
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_GasDiffuser.class)
                        .atLeast(InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings2, 0
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_GasDiffuser_MachineType)
            .addInfo(Tooltip_GasDiffuser_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_GasDiffuser_00)
            .addInfo(Tooltip_GasDiffuser_01)
            .addInfo(Tooltip_GasDiffuser_02)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_GasDiffuser(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive)
                return new ITexture[]{
                    Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
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
                Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
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
        return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_INDEX)};
    }

    @Override
    public GasEnvironmentType getProvidedEnvironmentType() {
        return null;
    }
}
