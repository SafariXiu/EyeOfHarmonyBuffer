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
import static gregtech.api.GregTechAPI.sBlockCasings2;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_EncapsulationMachine extends UpgradableOrundumWirelessMultiMachineBase<EOHB_EncapsulationMachine>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_EncapsulationMachine> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainEncapsulationMachine";
    private static final int OffsetsX = 10;
    private static final int OffsetsY = 9;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    public EOHB_EncapsulationMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_EncapsulationMachine(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.EncapsulationMachine;
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
        {"                     ","                     ","                     ","                     "," CCC                 "," CCC                 "," CCC                 ","                     ","                     ","                     ","                     "},
        {"                     ","                     ","                     ","        A B A        ","CC      A B A    CC  ","CC      A   A    CC  ","CC      A   A    CC  ","        A   A        ","        CCCCC        ","         CCC         ","                     "},
        {"          C          ","        C C C        ","       CC C CC       ","   AAAAAABBBAAAAAA   ","C  BBBBBBBBBBBBBBBCC ","C  BBBBBB B BBBBBBCC ","C  BBBBBB B BBBBBBCC ","   AAAAAA B AAAAAA   ","        CCCCC        ","         CCC         ","                     "},
        {"          C          ","        C   C        ","       CC   CC       ","  AAAAAAA B AAAAAAA  ","C AAAAAAA B AAAAAAAC ","C AAAAAAA   AAAAAAAC ","C AAAAAAA   AAAAAAAC ","  AAAAAAA   AAAAAAA  ","       CCCCCCC       ","         CCC         ","                     "},
        {"          C          ","        C   C        ","      CCC   CCC      ","  AAAAAAAAAAAAAAAAA  ","C AAAAAAAAAAAAAAAAAC ","C AAAAAAAAAAAAAAAAAC ","C AAAAAAAAAAAAAAAAAC ","  AAAAAAAAAAAAAAAAA  ","      CCCCCCCCC      ","         CCC         ","                     "},
        {"          C          ","        C   C        ","     CCCC   CCCC     ","  AAAAAAAAAAAAAAAAA  ","C AAAAAAAAAAAAAAAAAC ","C AAAAAAAAAAAAAAAAAC ","C AAAAAAAAAAAAAAAAAC ","  AAAAAAAAAAAAAAAAA  ","     CCCCCCCCCCC     ","         CCC         ","                     "},
        {"          C          ","        C C C        ","    CCCCC   CCCCC    ","  AAAAAAAAAAAAAAAAA  ","C AAAAAAAAAAAAAAAAAC ","C AAAAAAAAAAAAAAAAAC ","C AAAAAAAAAAAAAAAAAC ","  AAAAAAAAAAAAAAAAA  ","    CCCCCCCCCCCCC    ","         CCC         ","                     "},
        {"                     ","        C C C        ","   CCCCCC C CCCCCC   ","  AAAAAAAAAAAAAAAAA  "," CAAAAAAAAAAAAAAAAA  "," CAAAAAAAAAAAAAAAAA  "," CAAAAAAAAAAAAAAAAA  ","  AAAAAAAAAAAAAAAAA  ","   CCCCCCCCCCCCCCC   ","         CCC         ","                     "},
        {"                     ","   CCCCCCCCCCCCCCC   ","  CCCCCCCCCCCCCCCCC  "," AACCCCCCCCCCCCCCCAA ","  CCCCCCCCCCCCCCCCC  ","  CCCCCCCCCCCCCCCCC  ","  CCCCCCCCCCCCCCCCC  "," AACCCCCCCCCCCCCCCAA ","  CCCCCCCCCCCCCCCCC  ","   CCCCCCCCCCCCCCC   ","                     "},
        {" AAAAAAAAA~AAAAAAAAA "," AAAAAAAAAAAAAAAAAAA ","AAAAAAAAAAAAAAAAAAAAA","AAAAAAAAAAAAAAAAAAAAA","AAAAAAAAAAAAAAAAAAAAA","AAAAAAAAAAAAAAAAAAAAA","AAAAAAAAAAAAAAAAAAAAA","AAAAAAAAAAAAAAAAAAAAA","AAAAAAAAAAAAAAAAAAAAA"," AAAAAAAAAAAAAAAAAAA "," AAAAAAAAAAAAAAAAAAA "}
    };

    @Override
    public IStructureDefinition<EOHB_EncapsulationMachine> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_EncapsulationMachine>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    buildHatchAdder(EOHB_EncapsulationMachine.class)
                        .atLeast(InputBus,OutputBus)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
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
                    ofBlock(sBlockCasings8,7)
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_EncapsulationMachine_MachineType)
            .addInfo(Tooltip_EncapsulationMachine_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_EncapsulationMachine_00)
            .addInfo(Tooltip_EncapsulationMachine_01)
            .addInfo(Tooltip_EncapsulationMachine_02)
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
        return new EOHB_EncapsulationMachine(this.mName);
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
