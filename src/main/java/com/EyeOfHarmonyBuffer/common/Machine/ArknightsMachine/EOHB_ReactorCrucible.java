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
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

public class EOHB_ReactorCrucible extends UpgradableOrundumWirelessMultiMachineBase<EOHB_ReactorCrucible>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_ReactorCrucible> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainReactorCrucible";
    private static final int OffsetsX = 8;
    private static final int OffsetsY = 16;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    public EOHB_ReactorCrucible(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_ReactorCrucible(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.ReactorCrucible;
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
        {"                 ","                 ","   B         B   ","   B         B   ","                 ","                 ","                 ","   B         B   ","   B         B   ","                 ","                 "},
        {"                 ","                 ","   B         B   ","  DB      B  BD  ","  BBB     B BBB  ","  BBB     B BBB  ","  BBB     B BBB  ","  DB      B  BD  ","   B         B   ","                 ","                 "},
        {"                 ","                 ","                 ","  DDB     B BDD  ","  DDBBBBBBBBBDD  ","  DDBBBBBBBBBDD  ","  DDBBBBBBBBBDD  ","  DDB     B BDD  ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","  DDDDDDDDDDDDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDDDDDDDDDDDD  ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","  DDADADADADADD  ","  DDBBBBBBBBBDD  ","  DDB       BDD  ","  DDBBBBBBBBBDD  ","  DDADADADADADD  ","                 ","                 ","                 "},
        {"                 ","                 ","                 ","  DDADADADADADD  ","  DDBBBBBBBBBDD  ","  DDB       BDD  ","  DDBBBBBBBBBDD  ","  DDADADADADADD  ","                 ","                 ","                 "},
        {"                 ","                 "," DDD         DDD "," DDDADADADADADDD "," DDDBBBBBBBBBDDD "," DDDB       BDDD "," DDDBBBBBBBBBDDD "," DDDADADADADADDD "," DDD         DDD ","                 ","                 "},
        {"                 ","                 "," BDD         DDB "," BDDADADADADADDB "," BDDBBBBBBBBBDDB "," BDDB       BDDB "," BDDBBBBBBBBBDDB "," BDDADADADADADDB "," BDD         DDB ","                 ","                 "},
        {"                 "," B             B "," BDD         DDB "," CDDADADADADADDC "," CDDBBBBBBBBBDDC "," CDDB       BDDC "," CDDBBBBBBBBBDDC "," CDDADADADADADDC "," BDD         DDB "," B             B ","                 "},
        {"                 ","                 "," BDD         DDB "," BDDADADADADADDB ","BBDDBBBBBBBBBDDBB","BBDDB       BDDBB","BBDDBBBBBBBBBDDBB"," BDDADADADADADDB "," BDD         DDB ","                 ","                 "},
        {"                 ","                 ","  DD         DD  ","  DDADADADADADD  ","BBDDBBBBBBBBBDDBB","BBDDB       BDDBB","BBDDBBBBBBBBBDDBB","  DDADADADADADD  ","  DD         DD  ","                 ","                 "},
        {"                 ","                 ","  DD         DD  ","  DDADADADADADD  ","  DDBBBBBBBBBDD  ","  DDB       BDD  ","  DDBBBBBBBBBDD  ","  DDADADADADADD  ","  DD         DD  ","                 ","                 "},
        {"                 ","                 ","   D         D   ","   DDDDDDDDDDD   ","   DBBBBBBBBBD   ","   DBBBBBBBBBD   ","   DBBBBBBBBBD   ","   DDDDDDDDDDD   ","   D         D   ","                 ","                 "},
        {"                 ","                 ","   D         D   ","   DB       BD   ","   DB  BBB  BD   ","   DB  BBB  BD   ","   DB  BBB  BD   ","   DB       BD   ","   D         D   ","                 ","                 "},
        {"                 ","                 ","  DD         DD  ","  DDB       BDD  ","  DDB       BDD  ","  DDB   B   BDD  ","  DDB       BDD  ","  DDB       BDD  ","  DD         DD  ","                 ","                 "},
        {"                 ","                 ","  DD   DDD   DD  ","  DDB DDDDD BDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDBDDDDDDDBDD  ","  DDB DDDDD BDD  ","  DD   DDD   DD  ","                 ","                 "},
        {" EEEEEEE~EEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "," EEEEEEEEEEEEEEE "},
        {" B             B ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 ","                 "," B             B "}
    };

    @Override
    public IStructureDefinition<EOHB_ReactorCrucible> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_ReactorCrucible>builder()
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
                    buildHatchAdder(EOHB_ReactorCrucible.class)
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
        tt.addMachineType(Tooltip_ReactorCrucible_MachineType)
            .addInfo(Tooltip_ReactorCrucible_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_ReactorCrucible_00)
            .addInfo(Tooltip_ReactorCrucible_01)
            .addInfo(Tooltip_ReactorCrucible_02)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addInputHatch("1+", EOHB_MachineType_1)
            .addOutputBus("1+", EOHB_MachineType_1)
            .addOutputHatch("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_ReactorCrucible(this.mName);
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
