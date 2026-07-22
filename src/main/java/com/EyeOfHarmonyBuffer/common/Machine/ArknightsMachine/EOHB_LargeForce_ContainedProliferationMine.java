package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeHelper;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.*;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.common.blocks.BlockCasings10;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import gregtech.api.casing.Casings;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tectech.thing.casing.BlockGTCasingsTT;
import tectech.thing.casing.TTCasingsContainer;
import tectech.thing.metaTileEntity.multi.base.render.TTRenderedExtendedFacingTexture;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_2;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_LargeForce_ContainedProliferationMine extends OrundumWirelessMultiMachineBase<EOHB_LargeForce_ContainedProliferationMine>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_LargeForce_ContainedProliferationMine> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainLargeForce_ContainedProliferationMine";
    private static final int OffsetsX = 25;
    private static final int OffsetsY = 14;
    private static final int OffsetsZ = 24;
    private static IIconContainer ScreenOFF;
    private static IIconContainer ScreenON;

    private static final Block INACTIVE_VEIN_BLOCK = sBlockCasings10;
    private static final int INACTIVE_VEIN_META = 3;

    private final List<MTEHatchOutputBus> mOutputBusesForM = new ArrayList<>();
    private final List<MTEHatchOutputBus> mOutputBusesForP = new ArrayList<>();
    private final List<MTEHatchOutputBus> mOutputBusesForQ = new ArrayList<>();
    private final List<MTEHatchOutputBus> mOutputBusesForT = new ArrayList<>();

    private VeinType veinM = null;
    private VeinType veinP = null;
    private VeinType veinQ = null;
    private VeinType veinT = null;

    private ItemStack pendingOutputM;
    private ItemStack pendingOutputP;
    private ItemStack pendingOutputQ;
    private ItemStack pendingOutputT;

    public EOHB_LargeForce_ContainedProliferationMine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_LargeForce_ContainedProliferationMine(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    private static final class VeinCost {
        final ItemStack output;
        final long orundumCost;

        VeinCost(ItemStack output, long orundumCost) {
            this.output = output;
            this.orundumCost = orundumCost;
        }
    }

    @Nullable
    private VeinCost makeVeinOutput(VeinType vein) {
        int amount;
        long cost;

        switch (vein) {
            case LANTIE:
            case ZIJING:
            case YUANSHI:
            case CHITONG:
                amount = 1_000_000;
                cost = 100_000_000L;
                break;
            default:
                return null;
        }

        ItemStack out = vein.newOreSample(amount);
        if (out == null) return null;
        out.stackSize = amount;
        return new VeinCost(out, cost);
    }

    public boolean addOutputBusForM(IGregTechTileEntity aBaseMetaTileEntity, int aBaseCasingIndex) {
        if (aBaseMetaTileEntity == null) return false;
        IMetaTileEntity meta = aBaseMetaTileEntity.getMetaTileEntity();
        if (meta instanceof MTEHatchOutputBus) {
            MTEHatchOutputBus hatch = (MTEHatchOutputBus) meta;
            hatch.updateTexture(aBaseCasingIndex);
            mOutputBusesForM.add(hatch);
            return true;
        }
        return false;
    }

    public boolean addOutputBusForP(IGregTechTileEntity aBaseMetaTileEntity, int aBaseCasingIndex) {
        if (aBaseMetaTileEntity == null) return false;
        IMetaTileEntity meta = aBaseMetaTileEntity.getMetaTileEntity();
        if (meta instanceof MTEHatchOutputBus) {
            MTEHatchOutputBus hatch = (MTEHatchOutputBus) meta;
            hatch.updateTexture(aBaseCasingIndex);
            mOutputBusesForP.add(hatch);
            return true;
        }
        return false;
    }

    public boolean addOutputBusForQ(IGregTechTileEntity aBaseMetaTileEntity, int aBaseCasingIndex) {
        if (aBaseMetaTileEntity == null) return false;
        IMetaTileEntity meta = aBaseMetaTileEntity.getMetaTileEntity();
        if (meta instanceof MTEHatchOutputBus) {
            MTEHatchOutputBus hatch = (MTEHatchOutputBus) meta;
            hatch.updateTexture(aBaseCasingIndex);
            mOutputBusesForQ.add(hatch);
            return true;
        }
        return false;
    }

    public boolean addOutputBusForT(IGregTechTileEntity aBaseMetaTileEntity, int aBaseCasingIndex) {
        if (aBaseMetaTileEntity == null) return false;
        IMetaTileEntity meta = aBaseMetaTileEntity.getMetaTileEntity();
        if (meta instanceof MTEHatchOutputBus) {
            MTEHatchOutputBus hatch = (MTEHatchOutputBus) meta;
            hatch.updateTexture(aBaseCasingIndex);
            mOutputBusesForT.add(hatch);
            return true;
        }
        return false;
    }

    private enum VeinType {
        INACTIVE(null, null),

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

        public boolean isActive() {
            return this != INACTIVE;
        }

        @Nullable
        public Block getMainBlock() {
            return mainBlockEntry == null ? null : mainBlockEntry.getBlock();
        }

        @Nullable
        public ItemStack newOreSample(int amount) {
            return oreEntry == null ? null : oreEntry.get(amount);
        }

        @Nullable
        public static VeinType fromBlock(Block block, int meta) {
            if (block == INACTIVE_VEIN_BLOCK && meta == INACTIVE_VEIN_META) {
                return INACTIVE;
            }

            if (meta != 0) {
                return null;
            }

            for (VeinType type : values()) {
                if (type == INACTIVE) continue;

                if (type.getMainBlock() == block) {
                    return type;
                }
            }

            return null;
        }
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 1200;
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
        this.costingEU = BigInteger.ZERO;
        this.costingEUText = ZERO_STRING;


        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        World world = base.getWorld();
        if (world == null || world.isRemote) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        if (!isActiveVein(veinM)
            && !isActiveVein(veinP)
            && !isActiveVein(veinQ)
            && !isActiveVein(veinT)) {

            return SimpleCheckRecipeResult.ofFailure("NoMainVeinBlock");
        }

        if (isActiveVein(veinM) && mOutputBusesForM.isEmpty()) {
            return SimpleCheckRecipeResult.ofFailure("NoOutputBusForM");
        }
        if (isActiveVein(veinP) && mOutputBusesForP.isEmpty()) {
            return SimpleCheckRecipeResult.ofFailure("NoOutputBusForP");
        }
        if (isActiveVein(veinQ) && mOutputBusesForQ.isEmpty()) {
            return SimpleCheckRecipeResult.ofFailure("NoOutputBusForQ");
        }
        if (isActiveVein(veinT) && mOutputBusesForT.isEmpty()) {
            return SimpleCheckRecipeResult.ofFailure("NoOutputBusForT");
        }

        final int duration = getWirelessModeProcessingTime();

        pendingOutputM = null;
        pendingOutputP = null;
        pendingOutputQ = null;
        pendingOutputT = null;

        long totalOrundumCost = 0L;

        if (isActiveVein(veinM)) {
            VeinCost vc = makeVeinOutput(veinM);
            if (vc == null) {
                return SimpleCheckRecipeResult.ofFailure("UnsupportedVeinType");
            }

            pendingOutputM = vc.output;
            totalOrundumCost += vc.orundumCost;
        }

        if (isActiveVein(veinP)) {
            VeinCost vc = makeVeinOutput(veinP);
            if (vc == null) {
                return SimpleCheckRecipeResult.ofFailure("UnsupportedVeinType");
            }

            pendingOutputP = vc.output;
            totalOrundumCost += vc.orundumCost;
        }

        if (isActiveVein(veinQ)) {
            VeinCost vc = makeVeinOutput(veinQ);
            if (vc == null) {
                return SimpleCheckRecipeResult.ofFailure("UnsupportedVeinType");
            }

            pendingOutputQ = vc.output;
            totalOrundumCost += vc.orundumCost;
        }

        if (isActiveVein(veinT)) {
            VeinCost vc = makeVeinOutput(veinT);
            if (vc == null) {
                return SimpleCheckRecipeResult.ofFailure("UnsupportedVeinType");
            }

            pendingOutputT = vc.output;
            totalOrundumCost += vc.orundumCost;
        }

        if (totalOrundumCost <= 0) {
            pendingOutputM = pendingOutputP = pendingOutputQ = pendingOutputT = null;
            return SimpleCheckRecipeResult.ofFailure("NoOutputOrCost");
        }

        BigInteger demand = getRequiredComputeForCurrentRecipe();
        if (demand != null && demand.signum() > 0 && ownerUUID != null) {
            WirelessComputeHelper.updateConsumer(this);

            boolean satisfied = WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
            if (!satisfied) {
                pendingOutputM = pendingOutputP = pendingOutputQ = pendingOutputT = null;
                return SimpleCheckRecipeResult.ofFailure("InsufficientCompute");
            }
        }

        if (!consumeOrundumForOwner(ownerUUID, totalOrundumCost)) {
            pendingOutputM = pendingOutputP = pendingOutputQ = pendingOutputT = null;
            return CheckRecipeResultRegistry.insufficientPower(totalOrundumCost);
        }

        this.costingEU = this.costingEU.add(BigInteger.valueOf(totalOrundumCost));
        this.costingEUText = NumberFormatUtil.formatNumber(this.costingEU);

        this.mOutputItems = null;
        this.mOutputFluids = null;

        this.mProgresstime = 0;
        this.mMaxProgresstime = duration;
        this.mEUt = 0;
        this.mEfficiency = 10000;
        this.mEfficiencyIncrease = 0;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.LargeForce_ContainedProliferationMine;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {

        mOutputBusesForM.clear();
        mOutputBusesForP.clear();
        mOutputBusesForQ.clear();
        mOutputBusesForT.clear();

        veinM = null;
        veinP = null;
        veinQ = null;
        veinT = null;

        pendingOutputM = null;
        pendingOutputP = null;
        pendingOutputQ = null;
        pendingOutputT = null;

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
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                         F                         ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                         F                         ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                         F                         ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                         F                         ","                        FFF                        ","                        FF                         ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                         F                         ","                        FFF                        ","                        FF                         ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                        FFF                        ","                        FFF                        ","                        FF                         ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                        FFF                        ","                        FFF                        ","                        FF                         ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                        FFF                        ","                        FFF                        ","                        FFF                        ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                        FFF                        ","                        FFF                        ","                        FFF                        ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                       JJJJJ                       ","                      JBBBBBJ                      ","                      JBFFFBJ                      ","                      JBFFFBJ                      ","                      JBFFFBJ                      ","                      JBBBBBJ                      ","                       JJJJJ                       ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                      JJJJJJJ                      ","                     JJ     JJ                     ","                     J       J                     ","                     J  FFF  J                     ","                     J  FFF  J                     ","                     J  FFF  J                     ","                     J       J                     ","                     JJ     JJ                     ","                      JJJJJJJ                      ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","              BBB                 BBB              ","             BDDDB               BDDDB             ","            BDDDDDB             BDDDDDB            ","           BDDDDDDDB           BDDDDDDDB           ","           BDDDDDDDB           BDDDDDDDB           ","           BDDDDDDDB           BDDDDDDDB           ","            BDDDDDB             BDDDDDB            ","             BDDDB               BDDDB             ","              BBB                 BBB              ","                     JJJJJJJJJ                     ","                    JJ       JJ                    ","                    J         J                    ","                    J         J                    ","                    J   FGF   J                    ","                    J   GFG   J                    ","                    J   FGF   J                    ","                    J         J                    ","                    J         J                    ","                    JJ       JJ                    ","                     JJJJJJJJJ                     ","              BBB                 BBB              ","             BDDDB               BDDDB             ","            BDDDDDB             BDDDDDB            ","           BDDDDDDDB           BDDDDDDDB           ","           BDDDDDDDB           BDDDDDDDB           ","           BDDDDDDDB           BDDDDDDDB           ","            BDDDDDB             BDDDDDB            ","             BDDDB               BDDDB             ","              BBB                 BBB              ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","              JJJ                 JJJ              ","             J   J               J   J             ","            J     J             J     J            ","           J       J           J       J           ","           J       J           J       J           ","           J       J           J       J           ","            J     J             J     J            ","             J   J               J   J             ","              JJJ    BBBBBBBBB    JJJ              ","                    B         B                    ","                   B           B                   ","                   B           B                   ","                   B           B                   ","                   B    FGF    B                   ","                   B    GFG    B                   ","                   B    FGF    B                   ","                   B           B                   ","                   B           B                   ","                   B           B                   ","                    B         B                    ","              JJJ    BBBBBBBBB    JJJ              ","             J   J               J   J             ","            J     J             J     J            ","           J       J           J       J           ","           J       J           J       J           ","           J       J           J       J           ","            J     J             J     J            ","             J   J               J   J             ","              JJJ                 JJJ              ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","              JJJ                 JJJ              ","             J   J               J   J             ","            J     J             J     J            ","           J       J           J       J           ","           J       J           J       J           ","           J       J           J       J           ","            J     J             J     J            ","             J   J               J   J             ","              JJJ    JJB   BJJ    JJJ              ","                    J         J                    ","                   J HG     FH J                   ","                   J GG     GF J                   ","                   B           B                   ","                        FGF                        ","                        GFG                        ","                        FGF                        ","                   B           B                   ","                   J FG     GF J                   ","                   J HF     FH J                   ","                    J         J                    ","              JJJ    JJB   BJJ    JJJ              ","             J   J               J   J             ","            J     J             J     J            ","           J       J           J       J           ","           J       J           J       J           ","           J       J           J       J           ","            J     J             J     J            ","             J   J               J   J             ","              JJJ                 JJJ              ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","              JJJ                 JJJ              ","             J   J               J   J             ","            J     J             J     J            ","           J       J           J       J           ","           J       J           J       J           ","           J       J           J       J           ","            J     J             J     J            ","             J   J               J   J             ","              JJJ    JJB   BJJ    JJJ              ","                    J         J                    ","                   J HG     FH J                   ","                   J GG     GF J                   ","                   B           B                   ","                        F~F                        ","                        GFG                        ","                        FGF                        ","                   B           B                   ","                   J FG     GF J                   ","                   J HF     FH J                   ","                    J         J                    ","              JJJ    JJB   BJJ    JJJ              ","             J   J               J   J             ","            J     J             J     J            ","           J       J           J       J           ","           J       J           J       J           ","           J       J           J       J           ","            J     J             J     J            ","             J   J               J   J             ","              JJJ                 JJJ              ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                                                   ","                                                   ","                                                   ","                                                   ","                                                   ","                     III   III                     ","                  III  I   I  III                  ","                II     I   I     II                ","              II       I   I       II              ","             I         I   I         I             ","            I          I   I          I            ","           I  JJJ      I   I      JJJ  I           ","          I  J   J     I   I     J   J  I          ","         I  J     J    I   I    J     J  I         ","        I  J       J   I   I   J       J  I        ","        I  J   M   N   I   I   O   P   J  I        ","       I   J       J   I   I   J       J   I       ","       I    J     J    I   I    J     J    I       ","      I      J   J     I   I     J   J      I      ","      I       JJJ    JJB   BJJ    JJJ       I      ","      I             J         J             I      ","     I             J HG     FH J             I     ","     I             J GG     GF J             I     ","     IIIIIIIIIIIIIIB           BIIIIIIIIIIIIII     ","                        HHH                        ","                        HHH                        ","                        HHH                        ","     IIIIIIIIIIIIIIB           BIIIIIIIIIIIIII     ","     I             J FG     GF J             I     ","     I             J HF     FH J             I     ","      I             J         J             I      ","      I       JJJ    JJB   BJJ    JJJ       I      ","      I      J   J     I   I     J   J      I      ","       I    J     J    I   I    J     J    I       ","       I   J       J   I   I   J       J   I       ","        I  J   Q   R   I   I   S   T   J  I        ","        I  J       J   I   I   J       J  I        ","         I  J     J    I   I    J     J  I         ","          I  J   J     I   I     J   J  I          ","           I  JJJ      I   I      JJJ  I           ","            I          I   I          I            ","             I         I   I         I             ","              II       I   I       II              ","                II     I   I     II                ","                  III  I   I  III                  ","                     III   III                     ","                                                   ","                                                   ","                                                   ","                                                   ","                                                   "},
        {"                    BBBBBBBBBBB                    ","                 BBBAAAAAAAAAAABBB                 ","              BBBAAAAAAAAAAAAAAAAABBB              ","             BAAAAAAAAAAAAAAAAAAAAAAAB             ","           BBAAAAAAAAAAAAAAAAAAAAAAAAABB           ","          BAAAAAAAAAABBBDCDBBBAAAAAAAAAAB          ","        BBAAAAAAAABBBBBBDCDBBBBBBAAAAAAAABB        ","       BAAAAAAAABBBBBBBBDCDBBBBBBBBAAAAAAAAB       ","      BAAAAAAABBBBBBBBBBDCDBBBBBBBBBBAAAAAAAB      ","      BAAAAAABBBBBBBBBBBDCDBBBBBBBBBBBAAAAAAB      ","     BAAAAAABBBBBBBBBBBBDCDBBBBBBBBBBBBAAAAAAB     ","    BAAAAAABBBBBBBBBBBBBDCDBBBBBBBBBBBBBAAAAAAB    ","    BAAAAABBBBEEEBBBBBBBDCDBBBBBBBEEEBBBBAAAAAB    ","   BAAAAABBBBEEEEEBBBBBBDCDBBBBBBEEEEEBBBBAAAAAB   ","  BAAAAABBBBEEAAAEEBBBBBDCDBBBBBEEAAAEEBBBBAAAAAB  ","  BAAAAABBBBEEAAAEEBBBBBDCDBBBBBEEAAAEEBBBBAAAAAB  ","  BAAAABBBBBEEAAAEEBBBBBDCDBBBBBEEAAAEEBBBBBAAAAB  "," BAAAAABBBBBBEEEEEBBBBBBDCDBBBBBBEEEEEBBBBBBAAAAAB "," BAAAABBBBBBBBEEEBBBBBBDDCDDBBBBBBEEEBBBBBBBBAAAAB "," BAAAABBBBBBBBBBBBBBBDDEDCDEDDBBBBBBBBBBBBBBBAAAAB ","BAAAAABBBBBBBBBBBBBBDEEEDCDEEEDBBBBBBBBBBBBBBAAAAAB","BAAAABBBBBBBBBBBBBBDEEEEDCDEEEEDBBBBBBBBBBBBBBAAAAB","BAAAABBBBBBBBBBBBBBDEEEEDCDEEEEDBBBBBBBBBBBBBBAAAAB","BAAAABBBBBBBBBBBBBDEEEEEDCDEEEEEDBBBBBBBBBBBBBAAAAB","BAAAADDDDDDDDDDDDDDDDDDDDCDDDDDDDDDDDDDDDDDDDDAAAAB","BAAAACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCAAAAB","BAAAADDDDDDDDDDDDDDDDDDDDCDDDDDDDDDDDDDDDDDDDDAAAAB","BAAAABBBBBBBBBBBBBDEEEEEDCDEEEEEDBBBBBBBBBBBBBAAAAB","BAAAABBBBBBBBBBBBBBDEEEEDCDEEEEDBBBBBBBBBBBBBBAAAAB","BAAAABBBBBBBBBBBBBBDEEEEDCDEEEEDBBBBBBBBBBBBBBAAAAB","BAAAAABBBBBBBBBBBBBBDEEEDCDEEEDBBBBBBBBBBBBBBAAAAAB"," BAAAABBBBBBBBBBBBBBBDDEDCDEDDBBBBBBBBBBBBBBBAAAAB "," BAAAABBBBBBBBEEEBBBBBBDDCDDBBBBBBEEEBBBBBBBBAAAAB "," BAAAAABBBBBBEEEEEBBBBBBDCDBBBBBBEEEEEBBBBBBAAAAAB ","  BAAAABBBBBEEAAAEEBBBBBDCDBBBBBEEAAAEEBBBBBAAAAB  ","  BAAAAABBBBEEAAAEEBBBBBDCDBBBBBEEAAAEEBBBBAAAAAB  ","  BAAAAABBBBEEAAAEEBBBBBDCDBBBBBEEAAAEEBBBBAAAAAB  ","   BAAAAABBBBEEEEEBBBBBBDCDBBBBBBEEEEEBBBBAAAAAB   ","    BAAAAABBBBEEEBBBBBBBDCDBBBBBBBEEEBBBBAAAAAB    ","    BAAAAAABBBBBBBBBBBBBDCDBBBBBBBBBBBBBAAAAAAB    ","     BAAAAAABBBBBBBBBBBBDCDBBBBBBBBBBBBAAAAAAB     ","      BAAAAAABBBBBBBBBBBDCDBBBBBBBBBBBAAAAAAB      ","      BAAAAAAABBBBBBBBBBDCDBBBBBBBBBBAAAAAAAB      ","       BAAAAAAAABBBBBBBBDCDBBBBBBBBAAAAAAAAB       ","        BBAAAAAAAABBBBBBDCDBBBBBBAAAAAAAABB        ","          BAAAAAAAAAABBBDCDBBBAAAAAAAAAAB          ","           BBAAAAAAAAAAAAAAAAAAAAAAAAABB           ","             BAAAAAAAAAAAAAAAAAAAAAAAB             ","              BBBAAAAAAAAAAAAAAAAABBB              ","                 BBBAAAAAAAAAAABBB                 ","                    BBBBBBBBBBB                    "}
    };

    @Override
    public IStructureDefinition<EOHB_LargeForce_ContainedProliferationMine> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_LargeForce_ContainedProliferationMine>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement(
                    'A',
                    Casings.FieldRestrictionCasing.asElement()
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings10, 3)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings13, 0)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockCasings13, 2)
                )
                .addElement(
                    'E',
                    ofBlock(sBlockCasings2, 6)
                )
                .addElement(
                    'F',
                    ofBlock(TTCasingsContainer.sBlockCasingsTT, 1)
                )
                .addElement(
                    'G',
                    ofBlock(TTCasingsContainer.sBlockCasingsTT, 2)
                )
                .addElement(
                    'H',
                    ofBlock(TTCasingsContainer.sBlockCasingsTT, 3)
                )
                .addElement(
                    'I',
                    ofBlock(sBlockFrames, 81)
                )
                .addElement(
                    'J',
                    ofBlock(sBlockGlass1, 1)
                )
                .addElement(
                    'M',
                    createVeinElementForM()
                )
                .addElement(
                    'P',
                    createVeinElementForP()
                )
                .addElement(
                    'Q',
                    createVeinElementForQ()
                )
                .addElement(
                    'T',
                    createVeinElementForT()
                )
                .addElement(
                    'N',
                    buildHatchAdder(EOHB_LargeForce_ContainedProliferationMine.class)
                        .atLeast(OutputBus)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(1)
                        .adder(EOHB_LargeForce_ContainedProliferationMine::addOutputBusForM)
                        .buildAndChain(
                            ofBlock(sBlockCasings10, 3)
                        )
                )
                .addElement(
                    'O',
                    buildHatchAdder(EOHB_LargeForce_ContainedProliferationMine.class)
                        .atLeast(OutputBus)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(1)
                        .adder(EOHB_LargeForce_ContainedProliferationMine::addOutputBusForP)
                        .buildAndChain(
                            ofBlock(sBlockCasings10, 3)
                        )
                )
                .addElement(
                    'R',
                    buildHatchAdder(EOHB_LargeForce_ContainedProliferationMine.class)
                        .atLeast(OutputBus)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(1)
                        .adder(EOHB_LargeForce_ContainedProliferationMine::addOutputBusForQ)
                        .buildAndChain(
                            ofBlock(sBlockCasings10, 3)
                        )
                )
                .addElement(
                    'S',
                    buildHatchAdder(EOHB_LargeForce_ContainedProliferationMine.class)
                        .atLeast(OutputBus)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(1)
                        .adder(EOHB_LargeForce_ContainedProliferationMine::addOutputBusForT)
                        .buildAndChain(
                            ofBlock(sBlockCasings10, 3)
                        )
                )

                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_LargeForce_ContainedProliferationMine_MachineType)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_00)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_01)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_02)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_03)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_04)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_05)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_06)
            .addInfo(Tooltip_LargeForce_ContainedProliferationMine_07)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addOutputBus("4+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_LargeForce_ContainedProliferationMine(this.mName);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister aBlockIconRegister) {
        ScreenOFF = Textures.BlockIcons.custom("iconsets/EM_COMPUTER");
        ScreenON = Textures.BlockIcons.custom("iconsets/EM_COMPUTER_ACTIVE");
        super.registerIcons(aBlockIconRegister);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int colorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { Textures.BlockIcons.casingTexturePages[BlockGTCasingsTT.texturePage][3],
                new TTRenderedExtendedFacingTexture(aActive ? ScreenON : ScreenOFF) };
        }
        return new ITexture[] { Textures.BlockIcons.casingTexturePages[BlockGTCasingsTT.texturePage][3] };
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<EOHB_LargeForce_ContainedProliferationMine> createVeinElementForM() {

        ITierConverter<VeinType> converter = new ITierConverter<VeinType>() {
            @Override
            public VeinType convert(Block b, int meta) {
                return VeinType.fromBlock(b, meta);
            }
        };

        List<Pair<Block, Integer>> knownTiers = new java.util.ArrayList<>();

        knownTiers.add(Pair.of(INACTIVE_VEIN_BLOCK, INACTIVE_VEIN_META));

        for (VeinType type : VeinType.values()) {
            knownTiers.add(Pair.of(type.getMainBlock(), 0));
        }

        return StructureUtility.ofBlocksTiered(
            converter,
            knownTiers,
            null,
            new BiConsumer<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public void accept(EOHB_LargeForce_ContainedProliferationMine t, VeinType v) {
                    t.veinM = v;
                }
            },
            new Function<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public VeinType apply(EOHB_LargeForce_ContainedProliferationMine t) {
                    return t.veinM;
                }
            }
        );
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<EOHB_LargeForce_ContainedProliferationMine> createVeinElementForP() {

        ITierConverter<VeinType> converter = new ITierConverter<VeinType>() {
            @Override
            public VeinType convert(Block b, int meta) {
                return VeinType.fromBlock(b, meta);
            }
        };

        List<Pair<Block, Integer>> knownTiers = new java.util.ArrayList<>();
        knownTiers.add(Pair.of(INACTIVE_VEIN_BLOCK, INACTIVE_VEIN_META));
        for (VeinType type : VeinType.values()) {
            knownTiers.add(Pair.of(type.getMainBlock(), 0));
        }

        return StructureUtility.ofBlocksTiered(
            converter,
            knownTiers,
            null,
            new BiConsumer<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public void accept(EOHB_LargeForce_ContainedProliferationMine t, VeinType v) {
                    t.veinP = v;
                }
            },
            new Function<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public VeinType apply(EOHB_LargeForce_ContainedProliferationMine t) {
                    return t.veinP;
                }
            }
        );
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<EOHB_LargeForce_ContainedProliferationMine> createVeinElementForQ() {

        ITierConverter<VeinType> converter = new ITierConverter<VeinType>() {
            @Override
            public VeinType convert(Block b, int meta) {
                return VeinType.fromBlock(b, meta);
            }
        };

        List<Pair<Block, Integer>> knownTiers = new java.util.ArrayList<>();
        knownTiers.add(Pair.of(INACTIVE_VEIN_BLOCK, INACTIVE_VEIN_META));
        for (VeinType type : VeinType.values()) {
            knownTiers.add(Pair.of(type.getMainBlock(), 0));
        }

        return StructureUtility.ofBlocksTiered(
            converter,
            knownTiers,
            null,
            new BiConsumer<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public void accept(EOHB_LargeForce_ContainedProliferationMine t, VeinType v) {
                    t.veinQ = v;
                }
            },
            new Function<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public VeinType apply(EOHB_LargeForce_ContainedProliferationMine t) {
                    return t.veinQ;
                }
            }
        );
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<EOHB_LargeForce_ContainedProliferationMine> createVeinElementForT() {

        ITierConverter<VeinType> converter = new ITierConverter<VeinType>() {
            @Override
            public VeinType convert(Block b, int meta) {
                return VeinType.fromBlock(b, meta);
            }
        };

        List<Pair<Block, Integer>> knownTiers = new java.util.ArrayList<>();
        knownTiers.add(Pair.of(INACTIVE_VEIN_BLOCK, INACTIVE_VEIN_META));
        for (VeinType type : VeinType.values()) {
            knownTiers.add(Pair.of(type.getMainBlock(), 0));
        }

        return StructureUtility.ofBlocksTiered(
            converter,
            knownTiers,
            null,
            new BiConsumer<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public void accept(EOHB_LargeForce_ContainedProliferationMine t, VeinType v) {
                    t.veinT = v;
                }
            },
            new Function<EOHB_LargeForce_ContainedProliferationMine, VeinType>() {
                @Override
                public VeinType apply(EOHB_LargeForce_ContainedProliferationMine t) {
                    return t.veinT;
                }
            }
        );
    }

    @Override
    protected void outputAfterRecipe() {
        if (pendingOutputM != null) {
            pushToGroup(mOutputBusesForM, pendingOutputM);
            pendingOutputM = null;
        }
        if (pendingOutputP != null) {
            pushToGroup(mOutputBusesForP, pendingOutputP);
            pendingOutputP = null;
        }
        if (pendingOutputQ != null) {
            pushToGroup(mOutputBusesForQ, pendingOutputQ);
            pendingOutputQ = null;
        }
        if (pendingOutputT != null) {
            pushToGroup(mOutputBusesForT, pendingOutputT);
            pendingOutputT = null;
        }
    }

    private void pushToGroup(List<MTEHatchOutputBus> group, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        if (group == null || group.isEmpty()) return;

        ItemStack remaining = stack.copy();

        for (MTEHatchOutputBus bus : group) {
            if (remaining.stackSize <= 0) break;
            if (bus == null || !bus.isValid() || bus.getBaseMetaTileEntity() == null) continue;

            bus.storePartial(remaining, false);
        }
    }

    @Override
    protected BigInteger getRequiredComputeForCurrentRecipe() {
        int activeVeins = 0;

        if (isActiveVein(veinM)) activeVeins++;
        if (isActiveVein(veinP)) activeVeins++;
        if (isActiveVein(veinQ)) activeVeins++;
        if (isActiveVein(veinT)) activeVeins++;

        if (activeVeins == 0) {
            return BigInteger.ZERO;
        }

        return BigInteger.valueOf(150L * activeVeins);
    }

    private static boolean isActiveVein(@Nullable VeinType vein) {
        return vein != null && vein.isActive();
    }
}
