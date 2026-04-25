package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessEnergyMultiMachineBase;
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
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import static com.EyeOfHarmonyBuffer.common.Block.ArknightsBlockRegister.YuanShiMainBlock;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_ElectricTypeOneMiningMachine extends OrundumWirelessMultiMachineBase<EOHB_ElectricTypeOneMiningMachine>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_ElectricTypeOneMiningMachine> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainOrundumDynamo";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 10;
    private static final int OffsetsZ = 3;
    private static final int CASING_INDEX1 = 183;

    public EOHB_ElectricTypeOneMiningMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_ElectricTypeOneMiningMachine(String aName) {
        super(aName);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 200;
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
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.ElectricTypeOneMiningMachine;
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
        {"         ","         ","         ","   CCC   ","   CBC   ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","         ","    B    ","         ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","   CBC   ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","         ","    B    ","         ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","   CBC   ","   CCC   ","         ","         ","         "},
        {"         "," CCCCCCC "," CBBBBBC "," CBBBBBC "," CBB BBC "," CBBBBBC "," CBBBBBC "," CCCCCCC ","         "},
        {"         ","         ","  C   C  ","   BBB   ","   B B   ","   BBB   ","  C   C  ","         ","         "},
        {"         ","         ","  C   C  ","   BAB   ","   A A   ","   BAB   ","  C   C  ","         ","         "},
        {"         ","         ","  C   C  ","   BAB   ","   A A   ","   BAB   ","  C   C  ","         ","         "},
        {"         "," CCCCCCC "," CC   CC "," C BAB C "," C A A C "," C BAB C "," CC   CC "," CCCCCCC ","         "},
        {"         "," C     C ","         ","   B~B   ","   A A   ","   BAB   ","         "," C     C ","         "},
        {"         "," C     C ","         ","   BBB   ","   BAB   ","   BBB   ","         "," C     C ","         "},
        {"         "," C     C ","  BBBBB  ","  BBBBB  ","  BB BB  ","  BBBBB  ","  BBBBB  "," C     C ","         "},
        {"CC     CC","CCBBBBBCC"," B     B "," B     B "," B     B "," B     B "," B     B ","CCBBBBBCC","CC     CC"}
    };

    @Override
    public IStructureDefinition<EOHB_ElectricTypeOneMiningMachine> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_ElectricTypeOneMiningMachine>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockGlass1, 0))
                .addElement(
                    'B',
                    buildHatchAdder(EOHB_ElectricTypeOneMiningMachine.class)
                        .atLeast(InputBus, OutputBus, Energy.or(ExoticEnergy))
                        .casingIndex(CASING_INDEX1)
                        .dot(1)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .addElement('C', ofBlock(sBlockFrames, 305))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_ElectricTypeOneMiningMachine_MachineType)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_00)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_01)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_02)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_03)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_04)
            .addInfo(Tooltip_ElectricTypeOneMiningMachine_05)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputHatch(add_inputHatch)
            .addInputBus(add_InputBus)
            .addOutputHatch(add_outputHatch)
            .addOutputBus(add_OutputBus)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_ElectricTypeOneMiningMachine(this.mName);
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        return super.checkProcessing();
    }

    /**
     * 7×7×4 区域检测：在机器背后、向下 4 层的盒子里找 YuanShiMainBlock。
     */
    private boolean hasYuanShiInMiningArea(World world, int cx, int cy, int cz, ForgeDirection front) {
        ForgeDirection back = front.getOpposite();
        int bx = cx + back.offsetX;
        int by = cy - 2;
        int bz = cz + back.offsetZ;

        for (int dy = 0; dy <= 3; dy++) {
            int y = by - dy;
            if (y < 0 || y >= world.getHeight()) continue;

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int x = bx + dx;
                    int z = bz + dz;

                    Block block = world.getBlock(x, y, z);
                    if (block == YuanShiMainBlock) {
                        return true;
                    }
                }
            }
        }

        return false;
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
