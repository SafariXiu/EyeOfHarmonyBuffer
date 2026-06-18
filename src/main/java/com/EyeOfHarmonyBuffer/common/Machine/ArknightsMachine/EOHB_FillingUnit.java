package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.UpgradableOrundumWirelessMultiMachineBase;
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
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_FillingUnit extends UpgradableOrundumWirelessMultiMachineBase<EOHB_FillingUnit>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_FillingUnit> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainFillingUnit";
    private static final int OffsetsX = 7;
    private static final int OffsetsY = 10;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    public EOHB_FillingUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_FillingUnit(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.FillingUnit;
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

    private static final String[][] shapeMain = new String[][]{
        {"               ","               ","               ","               "," BBB           "," BBB           "," BBB           ","               ","               ","               ","               "},
        {"               ","               ","               ","               ","               ","  BBBB         ","               ","               ","               ","               ","               "},
        {"               ","               ","               ","               ","               ","BBB  BBB       ","               ","               ","               ","               ","               "},
        {"               ","               ","               ","               ","               ","B B    BBB  B  ","               ","               ","               ","               ","               "},
        {"               ","               ","               ","               ","          CCCC ","BBB       BBBC ","          CCCC ","               ","               ","               ","               "},
        {"               ","               ","               ","               ","   AAAAAAACCCC ","  BAAAAAAACCCC ","   AAAAAAACCCC ","               ","               ","               ","               "},
        {"               ","               ","               ","           CCC ","   CCCCCCCC   C","  BB      C   C","   CCCCCCCC   C","           CCC ","               ","               ","               "},
        {"               ","               ","               ","         CCCCC ","   CCCCCCCC   C","   C      C   A","   CCCCCCCC   C","         CCCCC ","               ","               ","               "},
        {"               ","               ","               ","   CCCCCCCCCCC ","  CCCCCCCCC   C","  CC      C   A","  CCCCCCCCC   C","   CCCCCCCCCCC ","               ","               ","               "},
        {"               "," CCCCCCCCCCCCC "," CCCCCCCCCCCCC "," CCCCCCCCCCCCC "," CCCCCCCCCCCCCA"," CCCCCCCCCCCCCA"," CCCCCCCCCCCCCA"," CCCCCCCCCCCCC "," CCCCCCCCCCCCC "," CCCCCCCCCCCCC ","               "},
        {"AAAAAAA~AAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA","AAAAAAAAAAAAAAA"}
    };

    @Override
    public IStructureDefinition<EOHB_FillingUnit> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_FillingUnit>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    buildHatchAdder(EOHB_FillingUnit.class)
                        .atLeast(InputBus,OutputBus,InputHatch)
                        .casingIndex(CASING_INDEX)
                        .dot(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
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
        tt.addMachineType(Tooltip_FillingUnit_MachineType)
            .addInfo(Tooltip_FillingUnit_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_FillingUnit_00)
            .addInfo(Tooltip_FillingUnit_01)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputBus(add_InputBus)
            .addOutputBus(add_OutputBus)
            .addInputHatch(add_inputHatch)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_FillingUnit(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX), TextureFactory.builder()
                .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE)
                .extFacing()
                .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
    }
}
