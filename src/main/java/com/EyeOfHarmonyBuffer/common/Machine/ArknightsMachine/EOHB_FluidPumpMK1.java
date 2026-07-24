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
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_InputBus;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_MaintenanceHatch;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_outputHatch;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings2;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_FluidPumpMK1 extends UpgradableOrundumWirelessMultiMachineBase<EOHB_FluidPumpMK1>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_FluidPumpMK1> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainFluidPumpMK1";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 14;
    private static final int OffsetsZ = 2;
    private static final int CASING_INDEX1 = 183;

    public EOHB_FluidPumpMK1(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_FluidPumpMK1(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.FluidPumpMK1;
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            return SimpleCheckRecipeResult.ofFailure("GT_MetaTileEntity_Null");
        }

        World world = base.getWorld();
        if (world == null) {
            return SimpleCheckRecipeResult.ofFailure("WorldNull");
        }

        int cx = base.getXCoord();
        int cy = base.getYCoord();
        int cz = base.getZCoord();
        ForgeDirection front = base.getFrontFacing();

        if (!hasWaterBehindDown(world, cx, cy, cz, front)) {
            return SimpleCheckRecipeResult.ofFailure("NoWaterSourceBehindDown");
        }

        return super.checkProcessing();
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
        {"         ","         ","   CCC   ","  CCCCC  ","  CCCCC  ","  CCCCC  ","   CCC   ","         ","         ","         ","         ","         ","         ","         "},
        {"         ","         ","         ","   BBB   ","   BBB   ","   BBB   ","         ","         ","         ","         ","         ","         ","         ","         "},
        {"         ","         ","   CCC   ","  C   C  ","  C   C  ","  C   C  ","   CCC   ","         ","         ","         ","         ","         ","         ","         "},
        {"         ","         ","   CCC   ","  C   C  ","  C   C  ","  C   C  ","   CCC   ","         ","         ","         ","         ","         ","         ","         "},
        {"         ","         ","   CCC   ","  C   C  ","  A   A  ","  C   C  ","   CCC   ","         ","         ","         ","         ","         ","         ","         "},
        {"         ","         ","   CCC   ","  CCCCC  ","  CCCCC  ","  CCCCC  ","   CCC   ","         ","         ","         ","         ","         ","         ","         "},
        {"         ","         ","  CCCCC  "," CC   CC "," CC   CC "," CC   CC ","  CCCCC  ","    A    ","    A    ","    A    ","    A    ","    A    ","    A    ","    A    "},
        {"   EEE   ","   BBB   ","  ACCCA  "," CC   CC "," CC   CC "," CC   CC ","  ACCCA  ","   AAA   ","   AAA   ","   AAA   ","   AAA   ","   AAA   ","   AAA   ","    A    "},
        {"   EEE   ","   BBB   ","  ACCCA  ","  AAAAA  ","  AAAAA  ","  AAAAA  ","  ACCCA  ","    A    ","    A    ","    A    ","    A    ","    A    ","   AAA   ","    A    "},
        {"         ","         ","  A   A  ","   AAA   ","   AAA   ","   AAA   ","  A   A  ","         ","         ","         ","         ","    A    ","   AAA   ","    A    "},
        {"         ","         ","  A   A  ","    A    ","   AAA   ","    A    ","  A   A  ","         ","         ","         ","         ","    A    ","   AAA   ","    A    "},
        {"         "," A     A ","  A   A  ","         ","    A    ","         ","  A   A  "," A     A ","         ","         ","         ","    A    ","   AAA   ","    A    "},
        {"         "," A     A ","         ","         ","    C    ","         ","         "," A     A ","         ","         ","         ","    A    ","   AAA   ","    A    "},
        {"         "," A     A ","         ","         ","         ","         ","         "," A     A ","         ","         ","         ","    A    ","   AAA   ","    A    "},
        {"AA     AA","AAA   AAA"," ADD~DDA ","  DDDDD  ","  DDDDD  ","  DDDDD  "," ADDDDDA ","AAA   AAA","AA     AA","         ","         ","    A    ","   AAA   ","    A    "}
    };

    @Override
    public IStructureDefinition<EOHB_FluidPumpMK1> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_FluidPumpMK1>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings2, 0))
                .addElement('B', ofBlock(sBlockCasings2, 13))
                .addElement('C', ofBlock(sBlockCasings8, 7))
                .addElement(
                    'D',
                    buildHatchAdder(EOHB_FluidPumpMK1.class)
                        .atLeast(InputBus)
                        .casingIndex(CASING_INDEX1)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_FluidPumpMK1.class)
                        .atLeast(OutputHatch)
                        .casingIndex(CASING_INDEX1)
                        .hint(2)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private boolean hasWaterBehindDown(World world, int cx, int cy, int cz, ForgeDirection front) {
        ForgeDirection back = front.getOpposite();

        int tx = cx + back.offsetX * 10;
        int ty = cy - 1;
        int tz = cz + back.offsetZ * 10;

        if (ty < 0 || ty >= world.getHeight()) {
            return false;
        }

        return isWaterSourceBlock(world, tx, ty, tz);
    }

    private boolean isWaterSourceBlock(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);

        if (block == Blocks.water || block == Blocks.flowing_water) {
            int meta = world.getBlockMetadata(x, y, z);
            return meta == 0;
        }

        return false;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_FluidPumpMK1_MachineType)
            .addInfo(Tooltip_FluidPumpMK1_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_FluidPumpMK1_00)
            .addInfo(Tooltip_FluidPumpMK1_01)
            .addInfo(Tooltip_FluidPumpMK1_02)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addOutputHatch("1+", EOHB_MachineType_2)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_FluidPumpMK1(this.mName);
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
