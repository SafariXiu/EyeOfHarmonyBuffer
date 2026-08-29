package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.Materials;
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
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_HydroMiningRig extends OrundumWirelessMultiMachineBase<EOHB_HydroMiningRig>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_HydroMiningRig> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainHydroMiningRig";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 11;
    private static final int OffsetsZ = 2;
    private static final int CASING_INDEX = 16;
    private static final int CASING_INDEX1 = 183;

    private enum VeinType {
        LANTIE(GTCMItemList.LanTieMainBlock, GTCMItemList.LanTieKuang),
        ZIJING(GTCMItemList.ZiJingMainBlock, GTCMItemList.ZiJingKuang),
        YUANSHI(GTCMItemList.YuanShiMainBlock, GTCMItemList.YuanShiKuang),
        CHITONG(GTCMItemList.ChiTongMainBlock, GTCMItemList.ChiTongKuang);

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

    private static final int WATER_PER_RUN_LANTIE = 6000;
    private static final int WATER_PER_RUN_ZIJING = 6000;
    private static final int WATER_PER_RUN_YUANSHI = 6000;
    private static final int WATER_PER_RUN_CHITONG = 12000;

    private int getWaterCostForVein(VeinType type) {
        switch (type) {
            case LANTIE:  return WATER_PER_RUN_LANTIE;
            case ZIJING:  return WATER_PER_RUN_ZIJING;
            case YUANSHI: return WATER_PER_RUN_YUANSHI;
            case CHITONG: return WATER_PER_RUN_CHITONG;
            default:      return 0;
        }
    }

    public EOHB_HydroMiningRig(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_HydroMiningRig(String aName) {
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

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {

        VeinType veinType = scanMainVeinAndCleanOthers();
        if (veinType == null) {
            return SimpleCheckRecipeResult.ofFailure("NoMainVeinBlock");
        }

        int duration;
        int amount;

        switch (veinType) {
            case LANTIE:
            case ZIJING:
            case YUANSHI:
                duration = 200;
                amount = 320;
                break;
            case CHITONG:
                duration = 200;
                amount = 160;
                break;
            default:
                return SimpleCheckRecipeResult.ofFailure("UnsupportedMainVeinBlock");
        }

        if (!tryConsumeWaterForVein(veinType)) {
            return SimpleCheckRecipeResult.ofFailure("NotEnoughWater");
        }

        ItemStack out = veinType.newOreSample(amount);
        out.stackSize = amount;
        this.mOutputItems = new ItemStack[]{out};

        this.mProgresstime = 0;
        this.mMaxProgresstime = duration;
        this.mEUt = 0;
        this.mEfficiency = 10000;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @Override
    protected boolean shouldRequireOrundumField() {
        return false;
    }

    @Override
    protected boolean usesOrundumCost() {
        return false;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.HydroMiningRig;
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
        {"         ","         ","  DDDDD  ","  D   D  ","  DAAAD  ","  D   D  ","  DDDDD  ","         ","         "},
        {"         ","         ","         ","         ","  C A C  ","         ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","  CCCCC  ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","  CC CC  ","   CBC   ","         ","         ","         "},
        {"         ","         ","    F    ","   CAC   ","  CC CC  ","   CBC   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","  CC CC  ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   "," CCC CCC ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   "," C C C C ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   "," C AAA C ","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   "," C AAA C ","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","  C   C  "," C  A  C ","  C   C  ","         ","         ","         "},
        {"         ","         ","   C~C   ","  C   C  "," CC A CC ","  C   C  ","   CCC   ","         ","         "},
        {"A       A"," A     A ","  AEEEA  ","  AAAAA  "," CAAAAAC ","  AAAAA  ","  AAAAA  "," A     A ","A       A"},
        {"B       B","         ","   EEE   ","  AAAAA  "," CAAAAAC ","  AAAAA  ","   AAA   ","         ","B       B"}
    };

    @Override
    public IStructureDefinition<EOHB_HydroMiningRig> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_HydroMiningRig>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement(
                    'A',
                    ofBlock(sBlockCasings2, 0))
                .addElement(
                    'B',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings8, 7)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockFrames, 305)
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_HydroMiningRig.class)
                        .atLeast(OutputBus)
                        .casingIndex(CASING_INDEX1)
                        .hint(2)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .addElement(
                    'F',
                    buildHatchAdder(EOHB_HydroMiningRig.class)
                        .atLeast(InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings2, 0
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_HydroMiningRig_MachineType)
            .addInfo(Tooltip_HydroMiningRig_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_HydroMiningRig_00)
            .addInfo(Tooltip_HydroMiningRig_01)
            .addInfo(Tooltip_HydroMiningRig_02)
            .addInfo(Tooltip_HydroMiningRig_03)
            .addInfo(Tooltip_HydroMiningRig_04)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputHatch("1+", EOHB_MachineType_1)
            .addOutputBus("1+", EOHB_MachineType_2)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_HydroMiningRig(this.mName);
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

        int bx = cx + back.offsetX * 2;
        int by = cy - 1;
        int bz = cz + back.offsetZ * 2;

        VeinType firstVeinType = null;

        for (int dy = 0; dy < 3; dy++) {
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
                    } else if (thisType != firstVeinType) {
                        world.setBlockToAir(x, y, z);
                    }
                }
            }
        }

        return firstVeinType;
    }

    private boolean tryConsumeWaterForVein(VeinType veinType) {
        int amount = getWaterCostForVein(veinType);
        if (amount <= 0) return true;

        FluidStack req = Materials.Water.getFluid(amount);
        if (req == null || req.getFluid() == null) {
            return false;
        }

        if (!depleteInput(req, true)) {
            return false;
        }

        return depleteInput(req, false);
    }
}
