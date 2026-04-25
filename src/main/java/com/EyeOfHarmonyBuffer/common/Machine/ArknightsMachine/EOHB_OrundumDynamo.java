package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.*;
import static gregtech.api.util.GTStructureUtility.ofCoil;
import static gregtech.api.util.GTUtility.validMTEList;

public class EOHB_OrundumDynamo extends OrundumWirelessMultiMachineBase<EOHB_OrundumDynamo> implements IConstructable, ISurvivalConstructable {

    private static final BigInteger ORUNDUM_PER_PURE_ORIGINIUM = BigInteger.valueOf(100_000L);
    private static final int TICKS_PER_CYCLE = 20 * 5;
    private BigInteger pendingOrundum = BigInteger.ZERO;
    private static IStructureDefinition<EOHB_OrundumDynamo> STRUCTURE_DEFINITION = null;
    protected static final String STRUCTURE_PIECE_MAIN = "mainOrundumDynamo";
    private int glassTier = -1;
    private int lastItemOutput = 0;
    private ItemStack lastInputType = null;
    private InputRecipe lastRecipeConfig = null;
    private HeatingCoilLevel mCoilLevel;
    private int mOrundumEfficiency;
    private static final int OffsetsX = 9;
    private static final int OffsetsY = 29;
    private static final int OffsetsZ = 1;
    private static final int CASING_INDEX = 16;
    private int lastParallelCount = 1;

    private static final Map<ItemStack, InputRecipe> INPUT_RECIPES = new HashMap<>();

    static {
        INPUT_RECIPES.put(GTCMItemList.YuanShi.get(1),
            new InputRecipe(GTCMItemList.HeChengYu.get(1), 180, 100_000L));

        INPUT_RECIPES.put(GTCMItemList.DiRongLiangDianChi.get(1),
            new InputRecipe(GTCMItemList.PoSuiYuanShi.get(1), 25, 500_000L));

        INPUT_RECIPES.put(GTCMItemList.ZhongRongLiangDianChi.get(1),
            new InputRecipe(GTCMItemList.PoSuiYuanShi.get(1), 50, 1_000_000L));

        INPUT_RECIPES.put(GTCMItemList.GaoRongLiangDianChi.get(1),
            new InputRecipe(GTCMItemList.PoSuiYuanShi.get(1), 200, 5_000_000L));
    }

    public EOHB_OrundumDynamo(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_OrundumDynamo(String aName) {
        super(aName);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 0;
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
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.OrundumDynamo;
    }

    private int getParallelCount() {
        int tier = Math.max(0, this.glassTier);
        return 1 << tier;
    }

    private static class InputRecipe {
        final ItemStack output;
        final int baseItemOutput;
        final BigInteger baseOrundum;

        InputRecipe(ItemStack output, int itemOut, long orundumOut) {
            this.output = output;
            this.baseItemOutput = itemOut;
            this.baseOrundum = BigInteger.valueOf(orundumOut);
        }
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        List<ItemStack> inputs = getStoredInputs();
        ItemStack foundType = null;
        InputRecipe recipeConfig = null;
        int availableCount = 0;

        for (ItemStack stack : inputs) {
            if (stack == null) continue;

            for (Map.Entry<ItemStack, InputRecipe> entry : INPUT_RECIPES.entrySet()) {
                if (stack.isItemEqual(entry.getKey())) {
                    foundType = entry.getKey();
                    recipeConfig = entry.getValue();
                    availableCount += stack.stackSize;
                    break;
                }
            }
            if (foundType != null) break;
        }

        if (foundType == null || recipeConfig == null || availableCount <= 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        int maxParallel = getParallelCount();
        int usedParallel = Math.min(availableCount, maxParallel);

        int remaining = usedParallel;
        for (MTEHatchInputBus bus : validMTEList(mInputBusses)) {
            IGregTechTileEntity base = bus.getBaseMetaTileEntity();
            for (int i = bus.getSizeInventory() - 1; i >= 0 && remaining > 0; i--) {
                ItemStack stack = base.getStackInSlot(i);
                if (stack == null) continue;
                if (stack.isItemEqual(foundType)) {
                    int take = Math.min(stack.stackSize, remaining);
                    base.decrStackSize(i, take);
                    remaining -= take;
                }
            }
            if (remaining <= 0) break;
        }

        if (remaining > 0) {
            for (MTEHatchInput hatch : validMTEList(mInputHatches)) {
                IGregTechTileEntity base = hatch.getBaseMetaTileEntity();
                ItemStack slot0 = base.getStackInSlot(0);
                if (slot0 != null && slot0.isItemEqual(foundType)) {
                    int take = Math.min(slot0.stackSize, remaining);
                    base.decrStackSize(0, take);
                    remaining -= take;
                }
                if (remaining <= 0) break;
            }
        }

        mMaxProgresstime = TICKS_PER_CYCLE;
        mProgresstime = 0;
        mEfficiency = 10000;

        this.lastParallelCount = usedParallel;
        this.lastInputType = foundType;
        this.lastRecipeConfig = recipeConfig;

        int coilHeat = (int) this.getCoilLevel().getHeat();
        double baseHeat = 1800.0;
        double coilFactor = Math.pow(coilHeat / baseHeat, 1.2);
        double glassFactor = 1.0 + (this.glassTier / (double) VoltageIndex.UHV) * 1.5;
        double effFactor = this.mOrundumEfficiency / 100.0;

        double totalMultiplier = coilFactor * glassFactor * effFactor * usedParallel;

        BigInteger orundumGain = recipeConfig.baseOrundum.multiply(BigInteger.valueOf((long) totalMultiplier));
        this.pendingOrundum = orundumGain;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void endRecipeProcessing() {
        if (this.lastRecipeConfig == null) {
            super.endRecipeProcessing();
            return;
        }

        int usedParallel = Math.max(1, this.lastParallelCount);
        int coilHeat = (int) this.getCoilLevel().getHeat();
        double baseHeat = 1800.0;
        double coilFactor = Math.pow(coilHeat / baseHeat, 1.2);
        double glassFactor = 1.0 + (this.glassTier / (double) VoltageIndex.UHV) * 1.5;
        double effFactor = this.mOrundumEfficiency / 100.0;

        double totalEnergyMul = coilFactor * glassFactor * effFactor * usedParallel;
        BigInteger totalOrundum = this.lastRecipeConfig.baseOrundum.multiply(BigInteger.valueOf((long) totalEnergyMul));

        if (ownerUUID != null) {
            OrundumEnergyService.changeOrundumForUser(ownerUUID, totalOrundum);
        }

        double itemMultiplier = (coilFactor * 0.9 + glassFactor * 0.1) * effFactor;
        int outputPerThread = (int) (this.lastRecipeConfig.baseItemOutput * itemMultiplier);
        int totalOutput = outputPerThread * usedParallel;

        this.lastItemOutput = totalOutput;

        ItemStack outTemplate = this.lastRecipeConfig.output.copy();
        outTemplate.stackSize = totalOutput;
        addOutput(outTemplate);

        pendingOrundum = BigInteger.ZERO;
        super.endRecipeProcessing();
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        this.glassTier = -1;
        this.setCoilLevel(HeatingCoilLevel.None);

        if (!checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ))
            return false;

        if (this.getCoilLevel() == HeatingCoilLevel.None)
            return false;

        int baseHeat = (int) this.getCoilLevel().getHeat();
        this.mOrundumEfficiency = 100 + (baseHeat / 1000);

        return true;
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

    @Override
    public int getMaxParallelRecipes() {
        return 0;
    }

    protected static final String[][] shapeMain = new String[][]{
        {"                   ","                   ","                   "," C               C "," C               C "," C               C "," C               C "," E               E "," C               C "," C               C "," E               E "," C               C "," C               C "," C               C "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  C             C  "," CC             CC "," CC             CC "," CC             CC "," CC             CC "," EEEEEEEEEEEEEEEEE "," CC             CC "," CC             CC "," EEEEEEEEEEEEEEEEE "," CC             CC "," CC             CC "," CC             CC "," CC             CC ","                   ","                   ","                   "},
        {"                   ","                   ","  CCCCCCCCCCCCCCC  "," CCCCCCCCCCCCCCCCC "," CCCCCCCCCCCCCCCCC "," CCCCCCCCCCCCCCCCC "," CCCCCCCCCCCCCCCCC "," ECCCCCCCCCCCCCCCE "," CCCCCCCCCCCCCCCCC "," CCCCCCCCCCCCCCCCC "," ECCCCCCCCCCCCCCCE "," CCCCCCCCCCCCCCCCC "," CCCCCCCCCCCCCCCCC "," CCCCCCCCCCCCCCCCC "," CCCCCCCCCCCCCCCCC ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C               C "," C               C "," C      GGG      C "," E     G   G     E "," C     G   G     C "," C     G   G     C "," E      GGG      E "," C               C "," C               C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C               C "," C               C "," C      GGG      C "," E     G   G     E "," C     G   G     C "," C     G   G     C "," E      GGG      E "," C               C "," C               C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C               C "," C      CCC      C "," C     CCCCC     C "," E    CC   CC    E "," C    CC   CC    C "," C    CC   CC    C "," E     CCCCC     E "," C      CCC      C "," C               C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    CC   CC    C "," C   CC     CC   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   CC     CC   E "," C    CC   CCC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     CCCCC     C "," C    C     C    C "," C   C       C   C "," E   C       C   E "," C   C       C   C "," C   C       C   C "," E   C       C   E "," C    C     CC   C "," C     CCCCCCC   C "," CFFFFFFFFFFFFFFFC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," C               C "," C     EEEEE     C "," C    E     E    C "," C   E       E   C "," ECCDE       EDCCE "," CCCDE       EDCCC "," CCCDE       EDCCC "," E   E       E   E "," C    E     E    C "," C     EEEEE     C "," CCCCCCCCCCCCCCCCC "," C               C ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," B               B "," C     CCCCC     C "," C    C     C    C "," CCCDC       CDCCC "," E   C       C   E ","CC   C       C   CC","CC   C       C   CC"," ECCDC       CDCCE "," C    C     C    C "," C     CCCCC     C "," CCCCCCCCCCCCCCCCC "," B               B ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," B               B "," C               C "," C     GGGGG     C "," CCCDDGG   GGDDCCC ","CE   DG     G    EC","FC   DG     G    CF","FC   DG     G    CF","CECCDDGG   GGDDCCEC"," C     GGGGG     C "," C               C "," CCCCCCCCCCCCCCCCC "," B               B ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," B               B "," C               C "," C     GGGGG     C "," CCCDDGG   GGDDCCC ","CE   DG     G    EC","FC   DG     G    CF","FC   DG     G    CF","CECCDDGG   GGDDCCEC"," C     GGGGG     C "," C               C "," CCCCCCCCCCCCCCCCC "," B               B ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," B               B "," C     CCCCC     C "," C    C     C    C "," CCCDC       CDCCC "," E   C       C   E ","CC   C       C   CC","CC   C       C   CC"," ECCDC       CDCCE "," C    C     C    C "," C     CCCCC     C "," CCCCCCCCCCCCCCCCC "," B               B ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," B               B "," C     EEEEE     C "," C    E     E    C "," C   E       E   C "," ECCDE       EDCCE "," CCCDE       EDCCC "," CCCDE       EDCCC "," E   E       E   E "," C    E     E    C "," C     EEEEE     C "," CCCCCCCCCCCCCCCCC "," B               B ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," CCC           CCC "," CCC   CCCCC   CCC "," C    CCCCCCC    C "," C   CCCCCCCCC   C "," E   CCC   CCC   E "," C   CCC   CCC   C "," C   CCC   CCC   C "," E   CCCCCCCCC   E "," C    CCCCCCC    C "," CCCCCCCCCCCCCCCCC "," CCC           CCC "," CCC           CCC ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," CCC           CCC "," CCC           CCC "," C               C "," C      GGG      C "," E     G   G     E "," C     G   G     C "," C     G   G     C "," E      GGG      E "," CCCCCCCCCCCCCCCCC "," C               C "," CCC           CCC "," CCC           CCC ","                   ","                   ","                   "},
        {"                   ","                   ","  CCAAAAAAAAAAACC  "," CCC           CCC "," CCC           CCC "," C               C "," C      GGG      C "," E     G   G     E "," C     G   G     C "," C     G   G     C "," E      GGG      E "," CCCCCCCCCCCCCCCCC "," C               C "," CCC           CCC "," CCC           CCC ","                   ","                   ","                   "},
        {"                   ","                   ","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC"," CCCCCCCCCCCCCCCCC ","  CCCCCCCCCCCCCCC  ","   CCCC     CCCC   "},
        {"                   ","                   ","C        C        C","C        C        C","CCEEEEEECCCEEEEEECC","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C"," C               C ","  C    CCCCC    C  ","   CCCC     CCCC   "},
        {"                   ","C        ~        C","C        C        C","C        C        C","CCEEEEEECCCEEEEEECC","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C"," C               C ","  C    CCCCC    C  ","   CCCC     CCCC   "},
        {"C        C        C","C        C        C","C        C        C","C        C        C","CCEEEEEECCCEEEEEECC","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C","C                 C"," C               C ","  C    CCCCC    C  ","   CCCC     CCCC   "},
        {"CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC","CCCCCCCCCCCCCCCCCCC"," CCCCCCCCCCCCCCCCC ","  CCCCCCCCCCCCCCC  ","   CCCC     CCCC   "}
    };

    @Override
    public IStructureDefinition<EOHB_OrundumDynamo> getStructureDefinition(){
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_OrundumDynamo>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    chainAllGlasses(-1, (te, t) -> te.glassTier = t, te -> te.glassTier)
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings10,10)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings2,0)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockCasings2,3)
                )
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_OrundumDynamo.class)
                        .atLeast(InputBus,InputHatch,OutputHatch,OutputBus)
                        .casingIndex(CASING_INDEX)
                        .dot(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,13)
                        )
                )
                .addElement(
                    'F',
                    ofBlock(sBlockCasings3,14)
                )
                .addElement(
                    'G',
                    GTStructureChannels.HEATING_COIL
                        .use(activeCoils(ofCoil(EOHB_OrundumDynamo::setCoilLevel, EOHB_OrundumDynamo::getCoilLevel)))
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_OrundumDynamo_MachineType)
            .addInfo(Tooltip_OrundumDynamo_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_OrundumDynamo_00)
            .addInfo(Tooltip_OrundumDynamo_01)
            .addInfo(Tooltip_OrundumDynamo_02)
            .addInfo(Tooltip_OrundumDynamo_03)
            .addInfo(Tooltip_OrundumDynamo_04)
            .addInfo(Tooltip_OrundumDynamo_05)
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
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag,
                                World world, int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);

        if (this.mCoilLevel != null) {
            tag.setInteger("coilHeat", (int) this.mCoilLevel.getHeat());
        }
        tag.setInteger("glassTier", this.glassTier);
        tag.setInteger("orundumEff", this.mOrundumEfficiency);

        tag.setInteger("itemOut", this.lastItemOutput);

        tag.setInteger("parallelMax", getParallelCount());
        tag.setInteger("parallelUsed", this.lastParallelCount);

        if (this.lastRecipeConfig != null) {
            tag.setLong("baseOrundum", this.lastRecipeConfig.baseOrundum.longValue());
        } else {
            tag.setLong("baseOrundum", ORUNDUM_PER_PURE_ORIGINIUM.longValue());
        }
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip,
                             IWailaDataAccessor accessor, IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);

        final NBTTagCompound tag = accessor.getNBTData();

        if (tag.getBoolean("wirelessMode")) {
            String wirelessText = TextLocalization.Waila_WirelessMode;
            String euCostText = TextLocalization.Waila_CurrentEuCost;
            String orundumCostKey = "Current Orundum Cost";

            currentTip.removeIf(raw -> {
                String line = EnumChatFormatting.getTextWithoutFormattingCodes(raw);
                if (line == null) line = raw;
                return line.startsWith(wirelessText)
                    || line.startsWith(euCostText)
                    || line.startsWith(orundumCostKey);
            });
        }

        int coilHeat = tag.getInteger("coilHeat");
        int glassTierValue = tag.getInteger("glassTier");
        int effValue = tag.getInteger("orundumEff");
        int itemOutValue = tag.hasKey("itemOut") ? tag.getInteger("itemOut") : 0;
        int parallelMax = tag.getInteger("parallelMax");
        int parallelUsed = tag.getInteger("parallelUsed");

        double baseHeat = 1800.0;
        double coilFactor = Math.pow(coilHeat / baseHeat, 1.2);
        double glassFactor = 1.0 + (glassTierValue / (double) VoltageIndex.UHV) * 1.5;
        double effBonus = effValue / 100.0;

        double totalEnergyMul = coilFactor * glassFactor * effBonus * parallelUsed;

        long baseOrundumL = tag.hasKey("baseOrundum") ? tag.getLong("baseOrundum") : ORUNDUM_PER_PURE_ORIGINIUM.longValue();

        BigInteger dynamicEnergy = BigInteger.valueOf(baseOrundumL)
            .multiply(BigInteger.valueOf((long)(totalEnergyMul)));
        BigInteger orundumPerTick = dynamicEnergy.divide(BigInteger.valueOf(TICKS_PER_CYCLE));
        BigInteger orundumPerSecond = orundumPerTick.multiply(BigInteger.valueOf(20L));

        currentTip.add(EnumChatFormatting.YELLOW + "【线圈信息】" + EnumChatFormatting.RESET);
        currentTip.add(EnumChatFormatting.GRAY + "线圈温度:" + EnumChatFormatting.WHITE + " " + coilHeat + " K");
        currentTip.add(EnumChatFormatting.GRAY + "能量加成:" + EnumChatFormatting.GOLD +
            String.format(" ×%.2f", coilFactor) + EnumChatFormatting.RESET);

        currentTip.add(EnumChatFormatting.AQUA + "【玻璃信息】" + EnumChatFormatting.RESET);
        currentTip.add(EnumChatFormatting.GRAY + "玻璃等级:" + EnumChatFormatting.WHITE + " " + glassTierValue);
        currentTip.add(EnumChatFormatting.GRAY + "玻璃加成:" + EnumChatFormatting.GOLD +
            String.format(" ×%.2f", glassFactor) + EnumChatFormatting.RESET);
        currentTip.add(EnumChatFormatting.GRAY + "并行线程:" + EnumChatFormatting.GOLD +
            " " + parallelUsed + "/" + parallelMax + EnumChatFormatting.RESET);

        currentTip.add(EnumChatFormatting.GREEN + "【综合效率】" + EnumChatFormatting.RESET);
        currentTip.add(EnumChatFormatting.GRAY + "机器效率:" + EnumChatFormatting.WHITE +
            " " + effValue + "%" + EnumChatFormatting.RESET);
        currentTip.add(EnumChatFormatting.GRAY + "总输出倍率(含并行):" + EnumChatFormatting.GOLD +
            String.format(" ×%.2f", totalEnergyMul) + EnumChatFormatting.RESET);

        currentTip.add(EnumChatFormatting.BLUE + "【当前性能】" + EnumChatFormatting.RESET);
        currentTip.add(EnumChatFormatting.GRAY + "Orundum 输出:" + EnumChatFormatting.GOLD +
            " " + orundumPerTick + EnumChatFormatting.RESET + " /t");
        currentTip.add(EnumChatFormatting.GRAY + "折算:" + EnumChatFormatting.GOLD +
            " " + orundumPerSecond + EnumChatFormatting.RESET + " /s");

        if (itemOutValue > 0) {
            currentTip.add(EnumChatFormatting.GREEN + "每循环产出:" + EnumChatFormatting.GOLD +
                " " + itemOutValue + EnumChatFormatting.RESET + " 个产物");
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);

        if (this.mCoilLevel != null) {
            aNBT.setInteger("coilHeat", (int) this.mCoilLevel.getHeat());
        }

        if (this.lastInputType != null)
            aNBT.setString("lastInputType", this.lastInputType.getUnlocalizedName());

        aNBT.setInteger("glassTier", this.glassTier);
        aNBT.setInteger("orundumEff", this.mOrundumEfficiency);

        aNBT.setInteger("itemOut", this.lastItemOutput);

        aNBT.setInteger("parallelUsed", this.lastParallelCount);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        this.glassTier = aNBT.getInteger("glassTier");
        this.mOrundumEfficiency = aNBT.getInteger("orundumEff");

        if (aNBT.hasKey("coilHeat")) {
            int heat = aNBT.getInteger("coilHeat");
            this.mCoilLevel = getByHeat(heat);
        }

        if (aNBT.hasKey("itemOut")) {
            this.lastItemOutput = aNBT.getInteger("itemOut");
        }

        if (aNBT.hasKey("lastInputType")) {
            String name = aNBT.getString("lastInputType");
            for (ItemStack key : INPUT_RECIPES.keySet()) {
                if (key.getUnlocalizedName().equals(name)) {
                    this.lastInputType = key;
                    this.lastRecipeConfig = INPUT_RECIPES.get(key);
                    break;
                }
            }
        }
    }

    private static HeatingCoilLevel getByHeat(int heat) {
        for (gregtech.api.enums.HeatingCoilLevel level : gregtech.api.enums.HeatingCoilLevel.values()) {
            if (level.getHeat() == heat) {
                return level;
            }
        }
        return gregtech.api.enums.HeatingCoilLevel.None;
    }

    public void setCoilLevel(HeatingCoilLevel aCoilLevel) {
        this.mCoilLevel = aCoilLevel;
    }

    public HeatingCoilLevel getCoilLevel() {
        return this.mCoilLevel;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_OrundumDynamo(this.mName);
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
