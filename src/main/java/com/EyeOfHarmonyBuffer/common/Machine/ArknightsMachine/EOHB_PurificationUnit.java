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
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings2;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_PurificationUnit extends UpgradableOrundumWirelessMultiMachineBase<EOHB_PurificationUnit>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_PurificationUnit> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainPurificationUnit";
    private static final int OffsetsX = 7;
    private static final int OffsetsY = 8;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    public EOHB_PurificationUnit(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_PurificationUnit(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.PurificationUnit;
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
        {"               ","  C            ","  C         A  ","  C         A  "," CC         AC "," CA         AC "," CA         CC ","  A         C  ","  A         C  ","            C  ","               "},
        {"               ","  C         A  ","  C         A  "," CCBBBBBBBBBAC "," CAA       AAC "," CAA       AAC "," CAA       AAC "," CABBBBBBBBBCC ","  A         C  ","  A         C  ","               "},
        {"               ","            A  "," C          AC "," CAA       AAC "," C  AA C AA  C "," A  AA C AA  A "," C  AA C AA  C "," CAA       AAC "," CA          C ","  A            ","               "},
        {"               ","            A  "," C          AC "," CAA       AAC "," A  AA C AA  A "," ABBBBBBBBBBBA "," A  AA C AA  A "," CAA       AAC "," CA          C ","  A            ","               "},
        {"               ","               "," C           C "," CAA       AAC "," C  AA C AA  C "," A  AA C AA  A "," C  AA C AA  C "," CAA       AAC "," C           C ","               ","               "},
        {"               ","               ","               "," CBBBBBBBBBBBC "," CAA       AAC "," CAA       AAC "," CAA       AAC "," CBBBBBBBBBBBC ","               ","               ","               "},
        {"               ","               "," A           A ","               "," C           C "," C           C "," C           C ","               "," A           A ","               ","               "},
        {"               "," A           A ","      CCC      ","     CCACC     "," AAACCAAACC    "," AAACAAAAAC    "," AAACCAAACC    ","     CCACC     ","      CCC      "," A           A ","               "},
        {"DDDDDDD~DDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD","DDDDDDDDDDDDDDD"}
    };

    @Override
    public IStructureDefinition<EOHB_PurificationUnit> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_PurificationUnit>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
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
                    ofBlock(sBlockCasings8,7)
                )
                .addElement(
                    'D',
                    buildHatchAdder(EOHB_PurificationUnit.class)
                        .atLeast(InputHatch,OutputHatch)
                        .casingIndex(CASING_INDEX)
                        .dot(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_PurificationUnit_MachineType)
            .addInfo(Tooltip_PurificationUnit_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_PurificationUnit_00)
            .addInfo(Tooltip_PurificationUnit_01)
            .addInfo(Tooltip_PurificationUnit_02)
            .addInfo(Tooltip_PurificationUnit_03)
            .addInfo(Tooltip_PurificationUnit_04)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputBus(add_inputHatch)
            .addOutputBus(add_OutputBus)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_PurificationUnit(this.mName);
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
