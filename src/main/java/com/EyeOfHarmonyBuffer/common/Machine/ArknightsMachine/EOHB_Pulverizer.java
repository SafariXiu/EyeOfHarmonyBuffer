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
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;

public class EOHB_Pulverizer extends UpgradableOrundumWirelessMultiMachineBase<EOHB_Pulverizer>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_Pulverizer> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainPulverizer";
    private static final int OffsetsX = 3;
    private static final int OffsetsY = 10;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;
    private int glassTier = -1;

    public EOHB_Pulverizer(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_Pulverizer(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.Pulverizer;
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
        {"       "," DBBBD ","DDBBBDD","DDBBBDD","DDDBDDD"," DDBDD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DAAAD ","DCCCCCD","DCCCCCD","DCCCCCD"," DCCCD ","  DBD  "},
        {"       "," DDDDD ","DDDDDDD","DDDDDDD","DDDDDDD"," DDDDD ","  DBD  "},
        {"BBB~BBB","BBBBBBB","BBBBBBB","BBBBBBB","BBBBBBB","BBBBBBB","BBBBBBB"}
    };

    @Override
    public IStructureDefinition<EOHB_Pulverizer> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_Pulverizer>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    chainAllGlasses(-1, (te, t) -> te.glassTier = t, te -> te.glassTier)
                )
                .addElement(
                    'B',
                    buildHatchAdder(EOHB_Pulverizer.class)
                        .atLeast(InputBus,OutputBus)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockCasings8, 7)
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_Pulverizer_MachineType)
            .addInfo(Tooltip_Pulverizer_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_Pulverizer_00)
            .addInfo(Tooltip_Pulverizer_01)
            .addInfo(Tooltip_Pulverizer_02)
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
        return new EOHB_Pulverizer(this.mName);
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
