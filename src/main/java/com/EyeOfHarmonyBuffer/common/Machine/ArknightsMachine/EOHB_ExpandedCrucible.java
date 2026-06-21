package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import bartworks.common.loaders.ItemRegistry;
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
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_ExpandedCrucible extends UpgradableOrundumWirelessMultiMachineBase<EOHB_ExpandedCrucible>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_ExpandedCrucible> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainExpandedCrucible";
    private static final int OffsetsX = 8;
    private static final int OffsetsY = 16;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    public EOHB_ExpandedCrucible(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(4);
    }

    public EOHB_ExpandedCrucible(String aName) {
        super(aName);
        setWirelessCycleNum(4);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.ReactorCrucible;
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
        {"                 ","                 ","   B         B   ","   B         B   ","                 ","                 ","                 ","   B         B   ","   B         B   ","                 ","                 "},
        {"                 ","                 ","   B         B   ","  DB      B  BD  ","  BBB     B BBB  ","  BBB     B BBB  ","  BBB     B BBB  ","  DB      B  BD  ","   B         B   ","                 ","                 "},
        {"                 ","                 ","                 ","  DDB     B BDD  ","  DDBBBBBBBBBDD  ","  DDBBBBBBBBBDD  ","  DDBBBBBBBBBDD  ","  DDB     B BDD  ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","  DDDDDDDDDDDDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDDDDDDDDDDDD  ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","  DDDADADADADDD  ","  DDBBBBBBBBBDD  ","  DDB       BDD  ","  DDBBBBBBBBBDD  ","  DDDADADADADDD  ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","  DDDADADADADDD  ","  DDBBBBBBBBBDD  ","  DDB       BDD  ","  DDBBBBBBBBBDD  ","  DDDADADADADDD  ","                 ","                 ","                 "},
        {"                 ","                 "," DDD         DDD "," DDDDADADADADDDD "," DDDBBBBBBBBBDDD "," DDDB       BDDD "," DDDBBBBBBBBBDDD "," DDDDADADADADDDD "," DDD         DDD ","                 ","                 "},
        {"                 ","                 "," BDD         DDB "," BDDDADADADADDDB "," BDDBBBBBBBBBDDB "," BDDB       BDDB "," BDDBBBBBBBBBDDB "," BDDDADADADADDDB "," BDD         DDB ","                 ","                 "},
        {"                 "," B             B "," BDDDDDDDDDDDDDB "," CDDDDDDDDDDDDDC "," CDDBBBBBBBBBDDC "," CDDB       BDDC "," CDDBBBBBBBBBDDC "," CDDDDDDDDDDDDDC "," BDDDDDDDDDDDDDB "," B             B ","                 "},
        {"                 ","                 "," BDD         DDB "," BDDDADADADADDDB ","BBDDBBBBBBBBBDDBB","BBDDB       BDDBB","BBDDBBBBBBBBBDDBB"," BDDDADADADADDDB "," BDD         DDB ","                 ","                 "},
        {"                 ","                 ","  DD         DD  ","  DDDADADADADDD  ","BBDDBBBBBBBBBDDBB","BBDDB       BDDBB","BBDDBBBBBBBBBDDBB","  DDDADADADADDD  ","  DD         DD  ","                 ","                 "},
        {"                 ","                 ","  DD         DD  ","  DDDADADADADDD  ","  DDBBBBBBBBBDD  ","  DDB       BDD  ","  DDBBBBBBBBBDD  ","  DDDADADADADDD  ","  DD         DD  ","                 ","                 "},
        {"                 ","                 ","   D         D   ","   DDDDDDDDDDD   ","   DBBBBBBBBBD   ","   DBBBBBBBBBD   ","   DBBBBBBBBBD   ","   DBBBBBBBBBD   ","   D         D   ","                 ","                 "},
        {"                 ","                 ","   D         D   ","   DB       BD   ","   DB  BBB  BD   ","   DB  BBB  BD   ","   DB  BBB  BD   ","   DB       BD   ","   D         D   ","                 ","                 "},
        {"                 ","                 ","  DD         DD  ","  DDB       BDD  ","  DDB       BDD  ","  DDB   B   BDD  ","  DDB       BDD  ","  DDB       BDD  ","  DD         DD  ","                 ","                 "},
        {"                 ","                 ","  DD   DDD   DD  ","  DDB DDDDD BDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDB DDDDD BDD  ","  DD   DDD   DD  ","                 ","                 "},
        {" EEEEEEE~EEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "},
        {" B             B ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "," B             B "}
    };

    @Override
    public IStructureDefinition<EOHB_ExpandedCrucible> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_ExpandedCrucible>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    ofBlock(ItemRegistry.bw_realglas, 0)
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings2, 0)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockCasings8, 7)
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_ExpandedCrucible.class)
                        .atLeast(InputBus, InputHatch, OutputBus, OutputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
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
        tt.addMachineType(Tooltip_ExpandedCrucible_MachineType)
            .addInfo(Tooltip_ExpandedCrucible_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_ExpandedCrucible_00)
            .addInfo(Tooltip_ExpandedCrucible_01)
            .addInfo(Tooltip_ExpandedCrucible_02)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputBus(add_InputBus)
            .addInputHatch(add_inputHatch)
            .addOutputBus(add_OutputBus)
            .addOutputHatch(add_outputHatch)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_ExpandedCrucible(this.mName);
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
