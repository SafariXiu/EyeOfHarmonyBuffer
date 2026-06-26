package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
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
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_InputBus;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_MaintenanceHatch;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.add_OutputBus;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_ElectricTypeTwoMiningMachine extends OrundumWirelessMultiMachineBase<EOHB_ElectricTypeTwoMiningMachine>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_ElectricTypeTwoMiningMachine> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainElectricTypeTwoMiningMachine";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 10;
    private static final int OffsetsZ = 3;
    private static final int CASING_INDEX1 = 183;

    private enum VeinType {
        LANTIE(GTCMItemList.LanTieMainBlock, GTCMItemList.LanTieKuang),
        ZIJING(GTCMItemList.ZiJingMainBlock, GTCMItemList.ZiJingKuang),
        YUANSHI(GTCMItemList.YuanShiMainBlock, GTCMItemList.YuanShiKuang);

        private final GTCMItemList mainBlockEntry;
        private final GTCMItemList oreEntry;

        VeinType(GTCMItemList mainBlockEntry, GTCMItemList oreEntry) {
            this.mainBlockEntry = mainBlockEntry;
            this.oreEntry = oreEntry;
        }

        public Block getMainBlock() {
            return mainBlockEntry.getBlock();
        }

        public ItemStack newOreSample(int amount) {
            return oreEntry.get(amount);
        }

        @Nullable
        public static VeinType fromMainBlock(Block block) {
            if (block == null) return null;
            for (VeinType type : values()) {
                if (type.getMainBlock() == block) {
                    return type;
                }
            }
            return null;
        }
    }

    public EOHB_ElectricTypeTwoMiningMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_ElectricTypeTwoMiningMachine(String aName) {
        super(aName);
        setWirelessCycleNum(1);
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

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        VeinType veinType = scanMainVeinAndCleanOthers();
        if (veinType == null) {
            return SimpleCheckRecipeResult.ofFailure("NoMainVeinBlock");
        }

        int duration;
        int amount;
        long orundumCost;

        switch (veinType) {
            case LANTIE:
                duration = 200;
                amount = 160;
                orundumCost = 40_000L;
                break;
            case ZIJING:
                duration = 200;
                amount = 160;
                orundumCost = 10_000L;
                break;
            case YUANSHI:
                duration = 200;
                amount = 160;
                orundumCost = 10_000L;
                break;
            default:
                return SimpleCheckRecipeResult.ofFailure("UnsupportedMainVeinBlock");
        }

        ItemStack out = veinType.newOreSample(amount);
        out.stackSize = amount;
        this.mOutputItems = new ItemStack[]{out};

        this.mProgresstime = 0;
        this.mMaxProgresstime = duration;
        this.mEUt = 0;
        this.mEfficiency = 10000;

        if (!consumeOrundumForOwner(ownerUUID, orundumCost)) {
            this.mOutputItems = null;
            return CheckRecipeResultRegistry.insufficientPower(orundumCost);
        }
        this.costingEU = this.costingEU.add(BigInteger.valueOf(orundumCost));
        this.costingEUText = NumberFormatUtil.formatNumber(this.costingEU);

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {
        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);

        if (!ok) return;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.ElectricTypeTwoMiningMachine;
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
    public IStructureDefinition<EOHB_ElectricTypeTwoMiningMachine> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_ElectricTypeTwoMiningMachine>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockGlass1, 0))
                .addElement(
                    'B',
                    buildHatchAdder(EOHB_ElectricTypeTwoMiningMachine.class)
                        .atLeast(InputBus, OutputBus)
                        .casingIndex(CASING_INDEX1)
                        .hint(1)
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
        tt.addMachineType(Tooltip_ElectricTypeTwoMiningMachine_MachineType)
            .addInfo(Tooltip_ElectricTypeTwoMiningMachine_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_ElectricTypeTwoMiningMachine_00)
            .addInfo(Tooltip_ElectricTypeTwoMiningMachine_01)
            .addInfo(Tooltip_ElectricTypeTwoMiningMachine_02)
            .addInfo(Tooltip_ElectricTypeTwoMiningMachine_03)
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
        return new EOHB_ElectricTypeTwoMiningMachine(this.mName);
    }

    @Nullable
    private VeinType scanMainVeinAndCleanOthers() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return null;

        World world = base.getWorld();
        if (world == null) return null;

        int cx = base.getXCoord();
        int cy = base.getYCoord();
        int cz = base.getZCoord();
        ForgeDirection front = base.getFrontFacing();
        ForgeDirection back = front.getOpposite();

        int bx = cx + back.offsetX;
        int by = cy - 2;
        int bz = cz + back.offsetZ;

        VeinType firstVeinType = null;

        for (int dy = 0; dy <= 3; dy++) {
            int y = by - dy;
            if (y < 0 || y >= world.getHeight()) continue;

            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int x = bx + dx;
                    int z = bz + dz;

                    Block block = world.getBlock(x, y, z);
                    if (block == null) continue;

                    VeinType thisType = VeinType.fromMainBlock(block);
                    if (thisType == null) {
                        continue;
                    }

                    if (firstVeinType == null) {
                        firstVeinType = thisType;
                    } else {
                        if (thisType != firstVeinType) {
                            world.setBlockToAir(x, y, z);
                        }
                    }
                }
            }
        }

        return firstVeinType;
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
