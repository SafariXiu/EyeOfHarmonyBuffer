package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.UpgradableOrundumWirelessMultiMachineBase;
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
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
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
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_FluidPumpMK2 extends UpgradableOrundumWirelessMultiMachineBase<EOHB_FluidPumpMK2>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_FluidPumpMK2> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainFluidPumpMK2";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 14;
    private static final int OffsetsZ = 2;
    private static final int CASING_INDEX1 = 183;

    private PumpFluidConfig currentConfig = null;
    private BigInteger currentCycleOrundumCost = BigInteger.ZERO;

    public EOHB_FluidPumpMK2(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_FluidPumpMK2(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    private static class PumpFluidConfig {
        final Fluid fluid; // 对应的流体对象
        final int mbPerCycle; // 每次循环抽取的流体量（mB）
        final int durationTicks; // 每次循环耗时（tick）
        final BigInteger orundumPerCycle; // 每次循环消耗的 Orundum

        PumpFluidConfig(Fluid fluid, int mbPerCycle, int durationTicks, long orundumPerCycle) {
            this.fluid = fluid;
            this.mbPerCycle = mbPerCycle;
            this.durationTicks = durationTicks;
            this.orundumPerCycle = BigInteger.valueOf(orundumPerCycle);
        }
    }

    private static final List<PumpFluidConfig> SUPPORTED_FLUIDS = new ArrayList<>();

    static {
        Fluid water = FluidRegistry.WATER;
        if (water != null) {
            SUPPORTED_FLUIDS.add(new PumpFluidConfig(
                water,
                10000,
                200,
                20000L
            ));
        }

        Fluid precipitationAcid = EOHBMaterialPool.PrecipitationAcid
            .getFluidOrGas(1)
            .getFluid();
        if (precipitationAcid != null) {
            SUPPORTED_FLUIDS.add(new PumpFluidConfig(
                precipitationAcid,
                5000,
                400,
                50000L
            ));
        }

        Fluid lava = FluidRegistry.LAVA;
        if (lava != null)
            SUPPORTED_FLUIDS.add(new PumpFluidConfig(
                lava,
                10000,
                200,
                40000L
            ));
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.FluidPumpMK2;
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        recalcControllerUpgrades();

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

        Fluid fluid = findSourceFluid(world, cx, cy, cz, front);
        if (fluid == null) {
            return SimpleCheckRecipeResult.ofFailure("NoFluidSource");
        }

        PumpFluidConfig cfg = null;
        for (PumpFluidConfig c : SUPPORTED_FLUIDS) {
            if (c.fluid != null && c.fluid.equals(fluid)) {
                cfg = c;
                break;
            }
        }
        if (cfg == null) {
            return SimpleCheckRecipeResult.ofFailure("UnsupportedFluid");
        }

        int parallel = Math.max(1, getMaxParallelRecipes());

        this.currentConfig = cfg;

        this.currentCycleOrundumCost =
            cfg.orundumPerCycle.multiply(BigInteger.valueOf(parallel));

        this.mProgresstime = 0;

        int baseDuration = cfg.durationTicks;
        int scaledDuration = (int) Math.max(
            1L,
            (long) baseDuration * getWirelessModeProcessingTime() / DEFAULT_BASE_WIRELESS_TIME
        );
        this.mMaxProgresstime = scaledDuration;

        this.mEfficiency = 10000;
        this.mEUt = 0;

        int totalMbPerCycle = cfg.mbPerCycle * parallel;
        this.mOutputFluids = new FluidStack[]{
            new FluidStack(fluid, totalMbPerCycle)
        };

        int cycles = Math.max(1, getWirelessCycleNum());

        BigInteger totalCost = this.currentCycleOrundumCost.multiply(BigInteger.valueOf(cycles));

        if (!consumeOrundumForOwner(ownerUUID, totalCost)) {
            this.mOutputFluids = null;
            return CheckRecipeResultRegistry.insufficientPower(
                safeToLong(totalCost)
            );
        }

        this.costingEU = this.costingEU.add(totalCost);
        this.costingEUText = NumberFormatUtil.formatNumber(this.costingEU);

        this.mOutputFluids[0].amount *= cycles;

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
    public IStructureDefinition<EOHB_FluidPumpMK2> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_FluidPumpMK2>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings2, 0))
                .addElement('B', ofBlock(sBlockCasings2, 13))
                .addElement('C', ofBlock(sBlockCasings8, 7))
                .addElement(
                    'D',
                    buildHatchAdder(EOHB_FluidPumpMK2.class)
                        .atLeast(InputBus)
                        .casingIndex(CASING_INDEX1)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_FluidPumpMK2.class)
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

    @Nullable
    private Fluid findSourceFluid(World world, int cx, int cy, int cz, ForgeDirection front) {
        ForgeDirection back = front.getOpposite();

        int tx = cx + back.offsetX * 10;
        int ty = cy - 1;
        int tz = cz + back.offsetZ * 10;

        if (ty < 0 || ty >= world.getHeight()) {
            return null;
        }

        Block block = world.getBlock(tx, ty, tz);
        int meta = world.getBlockMetadata(tx, ty, tz);

        if (block == null) return null;

        Fluid fluid = null;

        if (block instanceof IFluidBlock) {
            fluid = ((IFluidBlock) block).getFluid();
        } else {
            fluid = FluidRegistry.lookupFluidForBlock(block);
        }

        if (fluid == null) {
            return null;
        }

        if ((block == Blocks.water || block == Blocks.flowing_water
            || block == Blocks.lava || block == Blocks.flowing_lava)) {
            if (meta != 0) {
                return null;
            }
        }

        return fluid;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_FluidPumpMK2_MachineType)
            .addInfo(Tooltip_FluidPumpMK2_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_FluidPumpMK2_00)
            .addInfo(Tooltip_FluidPumpMK2_01)
            .addInfo(Tooltip_FluidPumpMK2_02)
            .addInfo(Tooltip_FluidPumpMK2_03)
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
        return new EOHB_FluidPumpMK2(this.mName);
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
