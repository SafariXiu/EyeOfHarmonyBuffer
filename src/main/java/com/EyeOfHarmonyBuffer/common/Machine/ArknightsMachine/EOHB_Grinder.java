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
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_Grinder extends UpgradableOrundumWirelessMultiMachineBase<EOHB_Grinder>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_Grinder> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainGrinder";
    private static final int OffsetsX = 7;
    private static final int OffsetsY = 11;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 183;

    public EOHB_Grinder(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_Grinder(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.Grinder;
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
        {"               ","               ","               ","               ","AAAAAAABBB     ","AAAAAAAABB     ","AAAAAAABBB     ","               ","               ","               ","               "},
        {"               ","               ","               ","               "," A             "," A     A       "," A             ","               ","               ","               ","               "},
        {"               ","               ","               ","               "," A             "," A     A       "," A             ","               ","               ","               ","               "},
        {"               ","     CCCCC     ","    CCBBBCC    ","   CCBBBBBCC   "," A CBBBBBBBC   "," A CBBBABBBC   "," A CBBBBBBBC   ","   CCBBBBBCC   ","    CCBBBCC    ","     CCCCC     ","               "},
        {"               ","     CCCCC     ","    CC   CC    ","   CC     CC   "," A C       C   "," A C       C AC"," A C       C   ","   CC     CC   ","    CC   CC    ","     CCCCC     ","               "},
        {"               ","     CCCCC     ","    CC   CC    ","   CC     CC   ","CA C       C AC"," A C       C AC","CA C       C AC","   CC     CC   ","    CC   CC    ","     CCCCC     ","               "},
        {"               ","     CCCCC     ","    CC   CC    ","   CC     CC   ","CA C       C AC"," A C       C AC","CA C       C AC","   CC     CC   ","    CC   CC    ","     CCCCC     ","               "},
        {"               ","     CCCCC     ","    CC   CC    ","C  CC     CC  C","CAAA       AAAC"," AAA       AAAC","CAAA       AAAC","C  CC     CC  C","    CC   CC    ","     CCCCC     ","               "},
        {"               ","     CCCCC     ","C   CC   CC   C","C  CC     CC  C","CA C       C AC"," A C       C AC","CA C       C AC","C  CC     CC  C","C   CC   CC   C","     CCCCC     ","               "},
        {"               ","C             C","C     CCC     C","C    CCCCC    C","CA  CCCCCCC  AC"," A  CCCCCCC  AC","CA  CCCCCCC  AC","C    CCCCC    C","C     CCC     C","C             C","               "},
        {"C             C","C             C","C             C","C     BBB     C","CA   BBBBB   AC"," A   BBBBB   AC","CA   BBBBB   AC","C     BBB     C","C             C","C             C","C             C"},
        {"CCCCCCC~CCCCCCC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CAAAAAAAAAAAAAC","CCCCCCCCCCCCCCC"}
    };

    @Override
    public IStructureDefinition<EOHB_Grinder> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_Grinder>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    buildHatchAdder(EOHB_Grinder.class)
                        .atLeast(InputBus,OutputBus)
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
                    buildHatchAdder(EOHB_Grinder.class)
                        .atLeast(InputBus,OutputBus)
                        .casingIndex(CASING_INDEX)
                        .dot(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings8,7)
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_Grinder_MachineType)
            .addInfo(Tooltip_Grinder_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_Grinder_00)
            .addInfo(Tooltip_Grinder_01)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputBus(add_InputBus)
            .addOutputBus(add_OutputBus)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_Grinder(this.mName);
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
