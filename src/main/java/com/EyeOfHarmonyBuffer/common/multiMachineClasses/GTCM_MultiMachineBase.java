package com.EyeOfHarmonyBuffer.common.multiMachineClasses;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.common.Block.BlockClass.BlockGlowCasingBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.processingLogics.GTCM_ProcessingLogic;
import com.EyeOfHarmonyBuffer.common.misc.OverclockType;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.*;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.EyeOfHarmonyBuffer.utils.TextHandler.texter;
import static com.EyeOfHarmonyBuffer.utils.Utils.filterValidMTEs;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlockAnyMeta;

public abstract class GTCM_MultiMachineBase<T extends GTCM_MultiMachineBase<T>>
    extends MTEExtendedPowerMultiBlockBase<T> implements IConstructable, ISurvivalConstructable {

    // region Class Constructor
    public GTCM_MultiMachineBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public GTCM_MultiMachineBase(String aName) {
        super(aName);
    }

    // endregion

    // region Glow Casing（发光外壳自动系统）
    /**
     * 结构中发光外壳坐标（由 {@link #glowCasing(Block)} 元素在结构检查时自动收集）。
     * 列表为空时整套系统零开销；机器侧只需在结构定义里加一个元素：
     * {@code .addElement('B', glowCasing(EOHBMachineBlocks.sBlockCasingsDysonFlow))}
     */
    protected final List<Long> mGlowBlocks = new ArrayList<>();
    /** 目标点亮态（true = 要亮，false = 要灭），与实际切换进度解耦 */
    private boolean mGlowState = false;
    /** 分帧渐变游标：每 tick 只切换 GLOW_TOGGLE_BUDGET 个方块，避免大量光照重算卡顿 */
    private int mGlowToggleIndex = 0;
    /**
     * 每 tick 切换的发光外壳数量上限。
     * setLit 走完整 setBlock 路径会触发每方块一次光照重算（BFS），
     * DysonCore 结构约 1124 块：14/ tick ≈ 80 tick ≈ 4 秒渐变点亮/熄灭，平滑无卡顿。
     */
    private static final int GLOW_TOGGLE_BUDGET = 14;

    /**
     * 发光外壳结构元素：接受该方块任意变体（点亮态 meta|8 同样通过），
     * 结构检查通过时自动把坐标收集进 {@link #mGlowBlocks}。
     */
    protected final <T extends GTCM_MultiMachineBase<T>> IStructureElement<T> glowCasing(Block block) {
        return glowCasing(block, -1);
    }

    /**
     * 发光外壳结构元素：限定变体（点亮态同样通过）。
     *
     * @param variant 0~7，-1 = 任意变体
     */
    protected final <T extends GTCM_MultiMachineBase<T>> IStructureElement<T> glowCasing(Block block, int variant) {
        return new GTStructureUtility.ProxyStructureElement<T, IStructureElement<T>>(ofBlockAnyMeta(block)) {
            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                if (!super.check(t, world, x, y, z)) {
                    return false;
                }
                if (variant >= 0
                    && (world.getBlockMetadata(x, y, z) & BlockGlowCasingBase.META_MASK) != variant) {
                    return false;
                }
                long pos = CoordinatePacker.pack(x, y, z);
                // 去重：clearHatches 不再清列表（见其注释），结构重检会重复收集
                if (!t.mGlowBlocks.contains(pos)) {
                    t.mGlowBlocks.add(pos);
                }
                return true;
            }
        };
    }

    /**
     * 发光条件：默认 GT 原生"正在加工"语义（结构成型 &amp;&amp; 正在加工），机器可覆写。
     */
    protected boolean shouldGlowBlocksBeLit() {
        return mMachine && mMaxProgresstime > 0;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) {
            return;
        }
        if (mGlowBlocks.isEmpty()) {
            return;
        }

        boolean shouldLit = shouldGlowBlocksBeLit();
        if (shouldLit != mGlowState) {
            // 目标翻转：重启分帧渐变（从 0 重新扫，已处于目标态的方块被 setLit 的 meta 判等直接跳过）
            mGlowState = shouldLit;
            mGlowToggleIndex = 0;
        }
        if (mGlowToggleIndex >= mGlowBlocks.size()) {
            return;
        }

        World world = aBaseMetaTileEntity.getWorld();
        if (world == null) {
            return;
        }
        int end = Math.min(mGlowBlocks.size(), mGlowToggleIndex + GLOW_TOGGLE_BUDGET);
        for (int i = mGlowToggleIndex; i < end; i++) {
            long pos = mGlowBlocks.get(i);
            BlockGlowCasingBase.setLit(
                world,
                CoordinatePacker.unpackX(pos),
                CoordinatePacker.unpackY(pos),
                CoordinatePacker.unpackZ(pos),
                mGlowState
            );
        }
        mGlowToggleIndex = end;
    }

    /**
     * 结构重检（GT 每 ~50 tick 一次）也会走到这里：这里不再清列表/不关灯，
     * 否则配合 setBlock 的光照重算会周期性全量闪烁。
     * 亮/灭完全由 onPostTick 的 {@link #shouldGlowBlocksBeLit()} 目标驱动，
     * 结构失效时 mMachine=false 目标翻转为灭，分帧渐变自然关灯。
     * 列表只在机器被拆毁（{@link #onRemoval}）时清空。
     */
    @Override
    public void clearHatches() {
        super.clearHatches();
    }

    /**
     * 机器被拆毁：立即全量关灯（罕见事件，可接受一次性光照重算开销），
     * 之后不再有 tick，无法依赖分帧渐变。
     */
    @Override
    public void onRemoval() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide() && !mGlowBlocks.isEmpty()) {
            World world = base.getWorld();
            if (world != null) {
                for (long pos : mGlowBlocks) {
                    BlockGlowCasingBase.setLit(
                        world,
                        CoordinatePacker.unpackX(pos),
                        CoordinatePacker.unpackY(pos),
                        CoordinatePacker.unpackZ(pos),
                        false
                    );
                }
            }
        }
        mGlowBlocks.clear();
        mGlowState = false;
        mGlowToggleIndex = 0;
        super.onRemoval();
    }
    // endregion

    // region new methods
    public void repairMachine() {
        mHardHammer = true;
        mScrewdriver = true;
        mCrowbar = true;
        mSolderingTool = true;
        mWrench = true;
    }

    // endregion

    // region Processing Logic

    /**
     * Creates logic to run recipe check based on recipemap. This runs only once, on class instantiation.
     * <p>
     * If this machine doesn't use recipemap or does some complex things, override {@link #checkProcessing()}.
     */
    @ApiStatus.OverrideOnly
    protected ProcessingLogic createProcessingLogic() {
        return new GTCM_ProcessingLogic() {

            @NotNull
            @Override
            public CheckRecipeResult process() {

                setEuModifier(getEuModifier());
                setSpeedBonus(getSpeedBonus());
                setOverclockType(
                    isEnablePerfectOverclock() ? OverclockType.PerfectOverclock : OverclockType.NormalOverclock);
                return super.process();
            }

        }.setMaxParallelSupplier(this::getLimitedMaxParallel);
    }

    /**
     * Proxy Perfect Overclock Supplier.
     *
     * @return If true, enable Perfect Overclock.
     */
    protected abstract boolean isEnablePerfectOverclock();

    /**
     * Proxy Standard Eu Modifier Supplier.
     *
     * @return The value (or a method to get the value) of Eu Modifier (dynamically) .
     */
    @ApiStatus.OverrideOnly
    protected float getEuModifier() {
        return 1.0F;
    }

    /**
     * Proxy Standard Speed Multiplier Supplier.
     *
     * @return The value (or a method to get the value) of Speed Multiplier (dynamically) .
     */
    @ApiStatus.OverrideOnly
    protected abstract float getSpeedBonus();

    /**
     * Proxy Standard Parallel Supplier.
     *
     * @return The value (or a method to get the value) of Max Parallel (dynamically) .
     */
    @ApiStatus.OverrideOnly
    public abstract int getMaxParallelRecipes();

    /**
     * Limit the max parallel to prevent overflow.
     *
     * @return Limited parallel.
     */
    protected int getLimitedMaxParallel() {
        return getMaxParallelRecipes();
    }

    /**
     * Prevent overflow during power consumption calculation.
     *
     * @return Eu consumption per tick.
     */
    @Override
    protected long getActualEnergyUsage() {
        return (long) (-this.lEUt * (10000.0 / Math.max(1000, mEfficiency)));
    }

    /**
     * Checks recipe and setup machine if it's successful.
     * <p>
     * For generic machine working with recipemap, use {@link #createProcessingLogic()} to make use of shared codebase.
     */
    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        // If no logic is found, try legacy checkRecipe
        if (processingLogic == null) {
            return checkRecipe(mInventory[1]) ? CheckRecipeResultRegistry.SUCCESSFUL
                : CheckRecipeResultRegistry.NO_RECIPE;
        }

        setupProcessingLogic(processingLogic);

        CheckRecipeResult result = doCheckRecipe();
        result = postCheckRecipe(result, processingLogic);
        // inputs are consumed at this point
        updateSlots();
        if (!result.wasSuccessful()) return result;

        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        mMaxProgresstime = processingLogic.getDuration();
        setEnergyUsage(processingLogic);

        mOutputItems = processingLogic.getOutputItems();
        mOutputFluids = processingLogic.getOutputFluids();

        return result;
    }

    /**
     * <p>
     * Get inputting items without DualInputHatch, and no separation mode.
     * <p>
     * Always used to get some special input items.
     *
     * @return The inputting items.
     */
    public ArrayList<ItemStack> getStoredInputsWithoutDualInputHatch() {

        ArrayList<ItemStack> rList = new ArrayList<>();
        for (MTEHatchInputBus tHatch : filterValidMTEs(mInputBusses)) {
            tHatch.mRecipeMap = getRecipeMap();
            IGregTechTileEntity tileEntity = tHatch.getBaseMetaTileEntity();
            for (int i = tileEntity.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack itemStack = tileEntity.getStackInSlot(i);
                if (itemStack != null) {
                    rList.add(itemStack);
                }
            }
        }

        if (getStackInSlot(1) != null && getStackInSlot(1).getUnlocalizedName()
            .startsWith("gt.integrated_circuit")) rList.add(getStackInSlot(1));
        return rList;
    }

    public ArrayList<ItemStack> getStoredInputItemsWithDualInputHatch() {

        if (supportsCraftingMEBuffer() && !mDualInputHatches.isEmpty()) {
            for (IDualInputHatch dualInputHatch : mDualInputHatches) {
                Iterator<? extends IDualInputInventory> inventoryIterator = dualInputHatch.inventories();
                while (inventoryIterator.hasNext()) {
                    ItemStack[] items = inventoryIterator.next()
                        .getItemInputs();
                    if (items == null || items.length == 0) continue;

                    ArrayList<ItemStack> rList = new ArrayList<>();
                    for (int i = 0; i < items.length; i++) {
                        if (items[i] != null) {
                            rList.add(items[i]);
                        }
                    }
                    return rList;
                }
            }
        }

        ArrayList<ItemStack> rList = new ArrayList<>();
        for (MTEHatchInputBus tHatch : filterValidMTEs(mInputBusses)) {
            tHatch.mRecipeMap = getRecipeMap();
            IGregTechTileEntity tileEntity = tHatch.getBaseMetaTileEntity();
            for (int i = tileEntity.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack itemStack = tileEntity.getStackInSlot(i);
                if (itemStack != null) {
                    rList.add(itemStack);
                }
            }
        }

        if (getStackInSlot(1) != null && getStackInSlot(1).getUnlocalizedName()
            .startsWith("gt.integrated_circuit")) rList.add(getStackInSlot(1));
        return rList;
    }

    /**
     * Forced get all input items, include all Dual Input Hatch slot.
     *
     * @return The items list.
     */
    public ArrayList<ItemStack> getStoredInputsNoSeparation() {
        ArrayList<ItemStack> rList = new ArrayList<>();

        if (supportsCraftingMEBuffer()) {
            for (IDualInputHatch dualInputHatch : mDualInputHatches) {
                Iterator<? extends IDualInputInventory> inventoryIterator = dualInputHatch.inventories();
                while (inventoryIterator.hasNext()) {
                    ItemStack[] items = inventoryIterator.next()
                        .getItemInputs();
                    if (items == null || items.length == 0) continue;

                    for (int i = 0; i < items.length; i++) {
                        if (items[i] != null) {
                            rList.add(items[i]);
                        }
                    }

                }
            }
        }

        Map<GTUtility.ItemId, ItemStack> inputsFromME = new HashMap<>();
        for (MTEHatchInputBus tHatch : GTUtility.filterValidMTEs(mInputBusses)) {
            tHatch.mRecipeMap = getRecipeMap();
            IGregTechTileEntity tileEntity = tHatch.getBaseMetaTileEntity();
            boolean isMEBus = tHatch instanceof MTEHatchInputBusME;
            for (int i = tileEntity.getSizeInventory() - 1; i >= 0; i--) {
                ItemStack itemStack = tileEntity.getStackInSlot(i);
                if (itemStack != null) {
                    if (isMEBus) {
                        // Prevent the same item from different ME buses from being recognized
                        inputsFromME.put(GTUtility.ItemId.createNoCopy(itemStack), itemStack);
                    } else {
                        rList.add(itemStack);
                    }
                }
            }
        }

        if (getStackInSlot(1) != null && getStackInSlot(1).getUnlocalizedName()
            .startsWith("gt.integrated_circuit")) rList.add(getStackInSlot(1));
        if (!inputsFromME.isEmpty()) {
            rList.addAll(inputsFromME.values());
        }
        return rList;
    }

    /**
     * Forced get all input fluids, include all Dual Input Hatch slot.
     *
     * @return ArrayList of all fluid stacks, contains fluid stacks in Crafting Input Hatch.
     */
    public ArrayList<FluidStack> getStoredFluidsWithDualInput() {
        ArrayList<FluidStack> rList = new ArrayList<>();
        Map<Fluid, FluidStack> inputsFromME = new HashMap<>();
        for (MTEHatchInput tHatch : GTUtility.filterValidMTEs(mInputHatches)) {
            setHatchRecipeMap(tHatch);
            if (tHatch instanceof MTEHatchMultiInput multiInputHatch) {
                for (FluidStack tFluid : multiInputHatch.getStoredFluid()) {
                    if (tFluid != null) {
                        rList.add(tFluid);
                    }
                }
            } else if (tHatch instanceof MTEHatchInputME meHatch) {
                for (FluidStack fluidStack : meHatch.getStoredFluids()) {
                    if (fluidStack != null) {
                        // Prevent the same fluid from different ME hatches from being recognized
                        inputsFromME.put(fluidStack.getFluid(), fluidStack);
                    }
                }
            } else {
                if (tHatch.getFillableStack() != null) {
                    rList.add(tHatch.getFillableStack());
                }
            }
        }

        if (!inputsFromME.isEmpty()) {
            rList.addAll(inputsFromME.values());
        }

        // get all fluids from Dual input
        if (supportsCraftingMEBuffer()) {
            for (IDualInputHatch dualInputHatch : mDualInputHatches) {
                Iterator<? extends IDualInputInventory> inventoryIterator = dualInputHatch.inventories();
                while (inventoryIterator.hasNext()) {
                    FluidStack[] fluids = inventoryIterator.next()
                        .getFluidInputs();
                    if (fluids == null || fluids.length == 0) continue;

                    for (int i = 0; i < fluids.length; i++) {
                        if (fluids[i] != null && fluids[i].amount > 0) {
                            rList.add(fluids[i]);
                        }
                    }

                }
            }
        }

        return rList;
    }

    // region Overrides
    @Override
    public String[] getInfoData() {
        String dSpeed = String.format("%.3f", this.getSpeedBonus() * 100) + "%";
        String dEUMod = String.format("%.3f", this.getEuModifier() * 100) + "%";

        String[] origin = super.getInfoData();
        String[] ret = new String[origin.length + 3];
        System.arraycopy(origin, 0, ret, 0, origin.length);
        ret[origin.length] = EnumChatFormatting.AQUA + texter("Parallels", "MachineInfoData.Parallels")
            + ": "
            + EnumChatFormatting.GOLD
            + this.getLimitedMaxParallel();
        ret[origin.length + 1] = EnumChatFormatting.AQUA + texter("Speed multiplier", "MachineInfoData.SpeedMultiplier")
            + ": "
            + EnumChatFormatting.GOLD
            + dSpeed;
        ret[origin.length + 2] = EnumChatFormatting.AQUA + texter("EU Modifier", "MachineInfoData.EuModifier")
            + ": "
            + EnumChatFormatting.GOLD
            + dEUMod;
        return ret;
    }

    @Override
    public boolean addToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return super.addToMachineList(aTileEntity, aBaseCasingIndex)
            || addExoticEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
    }

    public boolean addEnergyHatchOrExoticEnergyHatchToMachineList(IGregTechTileEntity aTileEntity,
        int aBaseCasingIndex) {
        return addEnergyInputToMachineList(aTileEntity, aBaseCasingIndex)
            || addExoticEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
    }

    public boolean addInputBusOrOutputBusToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return addInputBusToMachineList(aTileEntity, aBaseCasingIndex)
            || addOutputBusToMachineList(aTileEntity, aBaseCasingIndex);
    }

    public boolean addInputHatchOrOutputHatchToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        return addInputHatchToMachineList(aTileEntity, aBaseCasingIndex)
            || addOutputHatchToMachineList(aTileEntity, aBaseCasingIndex);
    }

    public boolean addFluidInputToMachineList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchInput) {
            ((MTEHatch) aMetaTileEntity).updateTexture(aBaseCasingIndex);
            ((MTEHatchInput) aMetaTileEntity).mRecipeMap = getRecipeMap();
            return mInputHatches.add((MTEHatchInput) aMetaTileEntity);
        } else if (aMetaTileEntity instanceof MTEHatchMuffler) {
            ((MTEHatch) aMetaTileEntity).updateTexture(aBaseCasingIndex);
            return mMufflerHatches.add((MTEHatchMuffler) aMetaTileEntity);
        }
        return false;
    }

    @Override
    public boolean addEnergyOutput(long aEU) {
        if (aEU <= 0) {
            return true;
        }
        if (!mDynamoHatches.isEmpty()) {
            return addEnergyOutputMultipleDynamos(aEU, true);
        }
        return false;
    }

    @Override
    public boolean addEnergyOutputMultipleDynamos(long aEU, boolean aAllowMixedVoltageDynamos) {
        int injected = 0;
        long totalOutput = 0;
        long aFirstVoltageFound = -1;
        boolean aFoundMixedDynamos = false;
        for (MTEHatchDynamo aDynamo : filterValidMTEs(mDynamoHatches)) {
            long aVoltage = aDynamo.maxEUOutput();
            long aTotal = aDynamo.maxAmperesOut() * aVoltage;
            // Check against voltage to check when hatch mixing
            if (aFirstVoltageFound == -1) {
                aFirstVoltageFound = aVoltage;
            } else {
                if (aFirstVoltageFound != aVoltage) {
                    aFoundMixedDynamos = true;
                }
            }
            totalOutput += aTotal;
        }

        /*
         * disable explosion
         * if (totalOutput < aEU || (aFoundMixedDynamos && !aAllowMixedVoltageDynamos)) {
         * explodeMultiblock();
         * return false;
         * }
         */

        long actualOutputEU;
        if (totalOutput < aEU) {
            actualOutputEU = totalOutput;
        } else {
            actualOutputEU = aEU;
        }

        long leftToInject;
        long aVoltage;
        int aAmpsToInject;
        int aRemainder;
        int ampsOnCurrentHatch;
        for (MTEHatchDynamo aDynamo : filterValidMTEs(mDynamoHatches)) {
            leftToInject = actualOutputEU - injected;
            aVoltage = aDynamo.maxEUOutput();
            aAmpsToInject = (int) (leftToInject / aVoltage);
            aRemainder = (int) (leftToInject - (aAmpsToInject * aVoltage));
            ampsOnCurrentHatch = (int) Math.min(aDynamo.maxAmperesOut(), aAmpsToInject);
            for (int i = 0; i < ampsOnCurrentHatch; i++) {
                aDynamo.getBaseMetaTileEntity()
                    .increaseStoredEnergyUnits(aVoltage, false);
            }
            injected += aVoltage * ampsOnCurrentHatch;
            if (aRemainder > 0 && ampsOnCurrentHatch < aDynamo.maxAmperesOut()) {
                aDynamo.getBaseMetaTileEntity()
                    .increaseStoredEnergyUnits(aRemainder, false);
                injected += aRemainder;
            }
        }
        return injected > 0;
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    /**
     * No more machine error
     */
    @Override
    public boolean doRandomMaintenanceDamage() {
        return true;
    }

    /**
     * No more machine error
     */
    @Override
    public void checkMaintenance() {}

    /**
     * No more machine error
     */
    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    /**
     * No more machine error
     */
    @Override
    public final boolean shouldCheckMaintenance() {
        return false;
    }

    /**
     * Gets the maximum Efficiency that spare Part can get (0 - 10000)
     *
     * @param aStack
     */
    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    /**
     * Gets the damage to the ItemStack, usually 0 or 1.
     *
     * @param aStack
     */
    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    /**
     * no longer afraid of rain
     */
    @Override
    public boolean willExplodeInRain() {
        return false;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsInputSeparation() {
        return true;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    @Override
    public boolean getDefaultBatchMode() {
        if (!supportsBatchMode()) return false;
        return MainConfig.DEFAULT_BATCH_MODE;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return true;
    }

    @Override
    public int getRecipeCatalystPriority() {
        return -1;
    }

    // endregion
}
