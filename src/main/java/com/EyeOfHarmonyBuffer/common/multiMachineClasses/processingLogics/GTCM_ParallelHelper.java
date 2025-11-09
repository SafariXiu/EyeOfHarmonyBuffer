package com.EyeOfHarmonyBuffer.common.multiMachineClasses.processingLogics;

import com.EyeOfHarmonyBuffer.utils.Utils;

import com.EyeOfHarmonyBuffer.utils.rewrites.EOHB_ItemID;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IRecipeLockable;
import gregtech.api.interfaces.tileentity.IVoidable;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SingleRecipeCheck;
import gregtech.api.util.*;
import ic2.core.Ic2Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;

public class GTCM_ParallelHelper extends ParallelHelper {
    private static final double MAX_BATCH_MODE_TICK_TIME = 128.0;
    private IVoidable machine;
    private IRecipeLockable singleRecipeMachine;
    private boolean isRecipeLocked;
    private GTRecipe recipe;
    private long availableEUt;
    private int currentParallel = 0;
    private int maxParallel = 1;
    private int batchModifier = 1;
    private ItemStack[] itemInputs;
    private ItemStack[] itemOutputs;
    private FluidStack[] fluidInputs;
    private FluidStack[] fluidOutputs;
    private boolean protectExcessItem;
    private boolean protectExcessFluid;
    private boolean consume;
    private boolean batchMode;
    private boolean calculateOutputs;
    private boolean built;
    private double durationMultiplier;
    private float eutModifier = 1.0F;
    private ParallelHelper.MaxParallelCalculator maxParallelCalculator = com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper::maxParallelCalculatedByInputs;
    private ParallelHelper.InputConsumer inputConsumer = com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.processingLogics.GTCM_ParallelHelper::consumeInput;
    private OverclockCalculator calculator;
    private CheckRecipeResult result;
    private Function<Integer, ItemStack[]> customItemOutputCalculation;
    private Function<Integer, FluidStack[]> customFluidOutputCalculation;

    public GTCM_ParallelHelper() {
        this.result = CheckRecipeResultRegistry.NONE;
    }

    public ParallelHelper setMachine(IVoidable machine) {
        return this.setMachine(machine, machine.protectsExcessItem(), machine.protectsExcessFluid());
    }

    public ParallelHelper setMachine(IVoidable machine, boolean protectExcessItem, boolean protectExcessFluid) {
        this.protectExcessItem = protectExcessItem;
        this.protectExcessFluid = protectExcessFluid;
        this.machine = machine;
        return this;
    }

    public ParallelHelper setRecipe(@Nonnull GTRecipe aRecipe) {
        this.recipe = (GTRecipe) Objects.requireNonNull(aRecipe);
        return this;
    }

    public ParallelHelper setRecipeLocked(IRecipeLockable singleRecipeMachine, boolean isRecipeLocked) {
        this.singleRecipeMachine = singleRecipeMachine;
        this.isRecipeLocked = isRecipeLocked;
        return this;
    }

    public ParallelHelper setItemInputs(ItemStack... aItemInputs) {
        this.itemInputs = aItemInputs;
        return this;
    }

    public ParallelHelper setFluidInputs(FluidStack... aFluidInputs) {
        this.fluidInputs = aFluidInputs;
        return this;
    }

    public ParallelHelper setAvailableEUt(long aAvailableEUt) {
        this.availableEUt = aAvailableEUt;
        return this;
    }

    public ParallelHelper setEUtModifier(float aEUtModifier) {
        this.eutModifier = aEUtModifier;
        return this;
    }

    public ParallelHelper setCalculator(OverclockCalculator calculator) {
        this.calculator = calculator;
        return this;
    }

    /** @deprecated */
    @Deprecated
    public ParallelHelper enableConsumption() {
        return this.setConsumption(true);
    }

    public ParallelHelper setConsumption(boolean consume) {
        this.consume = consume;
        return this;
    }

    public ParallelHelper setMaxParallel(int maxParallel) {
        this.maxParallel = maxParallel;
        return this;
    }

    public ParallelHelper enableBatchMode(int batchModifier) {
        this.batchMode = batchModifier > 1;
        this.batchModifier = batchModifier;
        return this;
    }

    /** @deprecated */
    @Deprecated
    public ParallelHelper enableOutputCalculation() {
        return this.setOutputCalculation(true);
    }

    public ParallelHelper setOutputCalculation(boolean calculateOutputs) {
        this.calculateOutputs = calculateOutputs;
        return this;
    }

    public ParallelHelper setCustomItemOutputCalculation(Function<Integer, ItemStack[]> custom) {
        this.customItemOutputCalculation = custom;
        return this;
    }

    public ParallelHelper setCustomFluidOutputCalculation(Function<Integer, FluidStack[]> custom) {
        this.customFluidOutputCalculation = custom;
        return this;
    }

    public ParallelHelper build() {
        if (this.built) {
            throw new IllegalStateException("Tried to build twice");
        } else if (this.recipe == null) {
            throw new IllegalStateException("Recipe is not set");
        } else {
            this.built = true;
            this.determineParallel();
            return this;
        }
    }

    public int getCurrentParallel() {
        if (!this.built) {
            throw new IllegalStateException("Tried to get parallels before building");
        } else {
            return this.currentParallel;
        }
    }

    public double getDurationMultiplierDouble() {
        if (!this.built) {
            throw new IllegalStateException("Tried to get duration multiplier before building");
        } else {
            return this.batchMode && this.durationMultiplier > 0.0 ? this.durationMultiplier : 1.0;
        }
    }

    /** @deprecated */
    @Deprecated
    public float getDurationMultiplier() {
        return (float)this.getDurationMultiplierDouble();
    }

    @Nonnull
    public ItemStack[] getItemOutputs() {
        if (this.built && this.calculateOutputs) {
            return this.itemOutputs;
        } else {
            throw new IllegalStateException("Tried to get item outputs before building or without enabling calculation of outputs");
        }
    }

    @Nonnull
    public FluidStack[] getFluidOutputs() {
        if (this.built && this.calculateOutputs) {
            return this.fluidOutputs;
        } else {
            throw new IllegalStateException("Tried to get fluid outputs before building or without enabling calculation of outputs");
        }
    }

    @Nonnull
    public CheckRecipeResult getResult() {
        if (!this.built) {
            throw new IllegalStateException("Tried to get recipe result before building");
        } else {
            return this.result;
        }
    }

    /** @deprecated */
    @Deprecated
    protected boolean tryConsumeRecipeInputs(GTRecipe recipe, FluidStack[] fluids, ItemStack[] items) {
        return this.tryConsumeRecipeInputs(recipe, fluids, items, 1);
    }

    protected boolean tryConsumeRecipeInputs(GTRecipe recipe, FluidStack[] fluids, ItemStack[] items, int minParallel) {
        return recipe.isRecipeInputEqual(true, false, minParallel, fluids, items);
    }

    protected void determineParallel() {
        if (this.itemInputs == null) {
            this.itemInputs = new ItemStack[0];
        }

        if (this.fluidInputs == null) {
            this.fluidInputs = new FluidStack[0];
        }

        if (!this.consume) {
            this.copyInputs();
        }

        if (this.calculator == null) {
            this.calculator = (new OverclockCalculator()).setEUt(this.availableEUt).setRecipeEUt((long)this.recipe.mEUt).setDuration(this.recipe.mDuration).setEUtDiscount((double)this.eutModifier);
        }

        int tRecipeEUt = (int)Math.ceil((double)((float)this.recipe.mEUt * this.eutModifier));
        if (this.availableEUt < (long)tRecipeEUt) {
            this.result = CheckRecipeResultRegistry.insufficientPower((long)tRecipeEUt);
        } else {
            int originalMaxParallel = this.maxParallel;
            double multiplier = this.calculator.setParallel(originalMaxParallel).calculateMultiplierUnderOneTick();
            maxParallel = GTUtility.safeInt((long) (maxParallel * multiplier), 0);

            int maxParallelBeforeBatchMode = this.maxParallel;
            if (this.batchMode) {
                this.maxParallel = Utils.safeInt((long)this.maxParallel * (long)this.batchModifier, 1);
            }

            ItemStack[] truncatedItemOutputs = this.recipe.mOutputs != null ? (ItemStack[]) Arrays.copyOfRange(this.recipe.mOutputs, 0, Math.min(this.machine.getItemOutputLimit(), this.recipe.mOutputs.length)) : new ItemStack[0];
            FluidStack[] truncatedFluidOutputs = this.recipe.mFluidOutputs != null ? (FluidStack[])Arrays.copyOfRange(this.recipe.mFluidOutputs, 0, Math.min(this.machine.getFluidOutputLimit(), this.recipe.mFluidOutputs.length)) : new FluidStack[0];
            SingleRecipeCheck recipeCheck = null;
            SingleRecipeCheck.Builder tSingleRecipeCheckBuilder = null;
            if (this.isRecipeLocked && this.singleRecipeMachine != null) {
                recipeCheck = this.singleRecipeMachine.getSingleRecipeCheck();
                if (recipeCheck == null) {
                    RecipeMap<?> recipeMap = this.singleRecipeMachine.getRecipeMap();
                    if (recipeMap != null) {
                        tSingleRecipeCheckBuilder = SingleRecipeCheck.builder(recipeMap).setBefore(this.itemInputs, this.fluidInputs);
                    }
                }
            }

            if (this.protectExcessItem || this.protectExcessFluid) {
                if (this.machine == null) {
                    throw new IllegalStateException("Tried to calculate void protection, but machine is not set");
                }

                VoidProtectionHelper voidProtectionHelper = new VoidProtectionHelper();
                voidProtectionHelper.setMachine(this.machine).setItemOutputs(truncatedItemOutputs).setFluidOutputs(truncatedFluidOutputs).setMaxParallel(this.maxParallel).build();
                this.maxParallel = Math.min(voidProtectionHelper.getMaxParallel(), this.maxParallel);
                if (this.maxParallel <= 0) {
                    this.result = CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
                    return;
                }
            }

            maxParallelBeforeBatchMode = Math.min(this.maxParallel, maxParallelBeforeBatchMode);
            int actualMaxParallel = tRecipeEUt > 0 ? (int)Math.min((long)maxParallelBeforeBatchMode, this.availableEUt / (long)tRecipeEUt) : maxParallelBeforeBatchMode;
            if (recipeCheck != null) {
                this.currentParallel = recipeCheck.checkRecipeInputs(true, actualMaxParallel, this.itemInputs, this.fluidInputs);
            } else {
                this.currentParallel = (int)this.maxParallelCalculator.calculate(this.recipe, actualMaxParallel, this.fluidInputs, this.itemInputs);
                if (this.currentParallel > 0) {
                    if (tSingleRecipeCheckBuilder != null) {
                        this.inputConsumer.consume(this.recipe, 1, this.fluidInputs, this.itemInputs);
                        SingleRecipeCheck builtCheck = tSingleRecipeCheckBuilder.setAfter(this.itemInputs, this.fluidInputs).setRecipe(this.recipe).build();
                        this.singleRecipeMachine.setSingleRecipeCheck(builtCheck);
                        this.inputConsumer.consume(this.recipe, this.currentParallel - 1, this.fluidInputs, this.itemInputs);
                    } else {
                        this.inputConsumer.consume(this.recipe, this.currentParallel, this.fluidInputs, this.itemInputs);
                    }
                }
            }

            if (this.currentParallel <= 0) {
                this.result = CheckRecipeResultRegistry.INTERNAL_ERROR;
            } else {
                long eutUseAfterOC = this.calculator.setCurrentParallel(this.currentParallel).setParallel(Math.min(this.currentParallel, originalMaxParallel)).calculate().getConsumption();
                if (this.currentParallel > originalMaxParallel) {
                    this.calculator.setRecipeEUt(eutUseAfterOC);
                }

                if (this.batchMode && this.currentParallel > 0 && (double)this.calculator.getDuration() < 128.0) {
                    double batchMultiplierMax = 128.0 / (double)this.calculator.getDuration();
                    int maxExtraParallels = (int)Math.floor(Math.min((double)this.currentParallel * Math.min(batchMultiplierMax - 1.0, (double)(this.batchModifier - 1)), (double)(this.maxParallel - this.currentParallel)));
                    int tExtraParallels;
                    if (recipeCheck != null) {
                        tExtraParallels = recipeCheck.checkRecipeInputs(true, maxExtraParallels, this.itemInputs, this.fluidInputs);
                    } else {
                        tExtraParallels = (int)this.maxParallelCalculator.calculate(this.recipe, maxExtraParallels, this.fluidInputs, this.itemInputs);
                        this.inputConsumer.consume(this.recipe, tExtraParallels, this.fluidInputs, this.itemInputs);
                    }

                    this.durationMultiplier = (double)(1.0F + (float)tExtraParallels / (float)this.currentParallel);
                    this.currentParallel += tExtraParallels;
                }

                if (this.calculateOutputs && this.currentParallel > 0) {
                    if (this.recipe.mOutputs != null) {
                        this.calculateItemOutputs();
                    }

                    if (this.recipe.mFluidOutputs != null) {
                        this.calculateFluidOutputs();
                    }
                }

                this.result = CheckRecipeResultRegistry.SUCCESSFUL;
            }
        }
    }

    protected void copyInputs() {
        ItemStack[] itemInputsToUse = new ItemStack[this.itemInputs.length];

        int i;
        for(i = 0; i < this.itemInputs.length; ++i) {
            itemInputsToUse[i] = this.itemInputs[i].copy();
        }

        FluidStack[] fluidInputsToUse = new FluidStack[this.fluidInputs.length];

        for(i = 0; i < this.fluidInputs.length; ++i) {
            fluidInputsToUse[i] = this.fluidInputs[i].copy();
        }

        this.itemInputs = itemInputsToUse;
        this.fluidInputs = fluidInputsToUse;
    }

    protected void calculateItemOutputs() {
        if (this.customItemOutputCalculation != null) {
            this.itemOutputs = (ItemStack[])this.customItemOutputCalculation.apply(this.currentParallel);
        } else {
            ArrayList<ItemStack> toOutput = new ArrayList();

            for(int i = 0; i < this.recipe.mOutputs.length; ++i) {
                int outputChance = this.recipe.getOutputChance(i);
                ItemStack origin = this.recipe.getOutput(i).copy();
                long outputs;
                if (outputChance < 10000) {
                    outputs = (long)this.currentParallel * (long)origin.stackSize * (long)outputChance / 10000L;
                    long remain = (long)this.currentParallel * (long)origin.stackSize * (long)outputChance % 10000L;
                    if (remain > 0L && remain > (long) XSTR.XSTR_INSTANCE.nextInt(10000)) {
                        outputs += (long)origin.stackSize;
                    }

                    while(outputs >= 2147483647L) {
                        toOutput.add(Utils.setStackSize(origin.copy(), Integer.MAX_VALUE));
                        outputs -= 2147483647L;
                    }

                    if (outputs > 0L) {
                        toOutput.add(Utils.setStackSize(origin.copy(), (int)outputs));
                    }
                } else {
                    for(outputs = (long)this.currentParallel * (long)origin.stackSize; outputs > 2147483647L; outputs -= 2147483647L) {
                        toOutput.add(Utils.setStackSize(origin.copy(), Integer.MAX_VALUE));
                    }

                    toOutput.add(Utils.setStackSize(origin.copy(), (int)outputs));
                }
            }

            this.itemOutputs = (ItemStack[])toOutput.toArray(new ItemStack[0]);
        }
    }

    protected void calculateFluidOutputs() {
        if (this.customFluidOutputCalculation != null) {
            this.fluidOutputs = (FluidStack[])this.customFluidOutputCalculation.apply(this.currentParallel);
        } else {
            ArrayList<FluidStack> toOutput = new ArrayList();
            this.fluidOutputs = new FluidStack[this.recipe.mFluidOutputs.length];

            for(int i = 0; i < this.recipe.mFluidOutputs.length; ++i) {
                if (this.recipe.getFluidOutput(i) != null) {
                    FluidStack tFluid = this.recipe.getFluidOutput(i).copy();

                    long amount;
                    FluidStack fluid;
                    for(amount = (long)tFluid.amount * (long)this.currentParallel; amount >= 2147483647L; amount -= 2147483647L) {
                        fluid = tFluid.copy();
                        fluid.amount = Integer.MAX_VALUE;
                        toOutput.add(fluid);
                    }

                    fluid = tFluid.copy();
                    fluid.amount = (int)amount;
                    toOutput.add(fluid);
                }
            }

            this.fluidOutputs = (FluidStack[])toOutput.toArray(new FluidStack[0]);
        }
    }

    public static void consumeInput(GTRecipe recipe, int amountMultiplier, FluidStack[] aFluidInputs, ItemStack... aInputs) {
        if (amountMultiplier > 0) {
            long remainingCost;
            int var7;
            int var8;
            int var12;
            if (aFluidInputs != null) {
                FluidStack[] var6 = recipe.mFluidInputs;
                var7 = var6.length;

                for(var8 = 0; var8 < var7; ++var8) {
                    FluidStack recipeFluidCost = var6[var8];
                    if (recipeFluidCost != null) {
                        remainingCost = (long)recipeFluidCost.amount * (long)amountMultiplier;
                        FluidStack[] var10 = aFluidInputs;
                        int var11 = aFluidInputs.length;

                        for(var12 = 0; var12 < var11; ++var12) {
                            FluidStack providedFluid = var10[var12];
                            if (providedFluid != null && providedFluid.isFluidEqual(recipeFluidCost)) {
                                if ((long)providedFluid.amount >= remainingCost) {
                                    providedFluid.amount -= (int)remainingCost;
                                    break;
                                }

                                remainingCost -= (long)providedFluid.amount;
                                providedFluid.amount = 0;
                            }
                        }
                    }
                }
            }

            if (aInputs != null) {
                ItemStack[] var15 = recipe.mInputs;
                var7 = var15.length;

                for(var8 = 0; var8 < var7; ++var8) {
                    ItemStack recipeItemCost = var15[var8];
                    if (recipeItemCost.stackSize != 0) {
                        ItemStack unifiedItemCost = GTOreDictUnificator.get_nocopy(recipeItemCost);
                        if (unifiedItemCost != null) {
                            remainingCost = (long)recipeItemCost.stackSize * (long)amountMultiplier;
                            ItemStack[] var18 = aInputs;
                            var12 = aInputs.length;

                            for(int var19 = 0; var19 < var12; ++var19) {
                                ItemStack providedItem = var18[var19];
                                if ((!recipe.isNBTSensitive || GTUtility.areStacksEqual(providedItem, unifiedItemCost, false)) && (recipe.isNBTSensitive || GTOreDictUnificator.isInputStackEqual(providedItem, unifiedItemCost)) && (!GTRecipe.GTppRecipeHelper || !GTUtility.areStacksEqual(providedItem, Ic2Items.FluidCell.copy(), true) && !GTUtility.areStacksEqual(providedItem, ItemList.Tool_DataStick.get(1L, new Object[0]), true) && !GTUtility.areStacksEqual(providedItem, ItemList.Tool_DataOrb.get(1L, new Object[0]), true) || GTUtility.areStacksEqual(providedItem, recipeItemCost, false))) {
                                    if ((long)providedItem.stackSize >= remainingCost) {
                                        providedItem.stackSize -= (int)remainingCost;
                                        break;
                                    }

                                    remainingCost -= (long)providedItem.stackSize;
                                    providedItem.stackSize = 0;
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    public static double maxParallelCalculatedByInputs(GTRecipe recipe, int maxParallel, FluidStack[] aFluidInputs, ItemStack... aInputs) {
        if (recipe.mInputs.length > 0 && aInputs == null) {
            return 0.0;
        } else if (recipe.mFluidInputs.length > 0 && aFluidInputs == null) {
            return 0.0;
        } else {
            double currentParallel = (double)maxParallel;
            if (recipe.mFluidInputs.length > 0) {
                Map<Fluid, Long> fluidMap = new HashMap();
                Map<Fluid, Long> fluidCost = new HashMap();
                FluidStack[] var8 = aFluidInputs;
                int var9 = aFluidInputs.length;

                int var10;
                FluidStack fluidStack;
                for(var10 = 0; var10 < var9; ++var10) {
                    fluidStack = var8[var10];
                    if (fluidStack != null) {
                        fluidMap.merge(fluidStack.getFluid(), (long)fluidStack.amount, Long::sum);
                    }
                }

                var8 = recipe.mFluidInputs;
                var9 = var8.length;

                for(var10 = 0; var10 < var9; ++var10) {
                    fluidStack = var8[var10];
                    if (fluidStack != null) {
                        fluidCost.merge(fluidStack.getFluid(), (long)fluidStack.amount, Long::sum);
                    }
                }

                Iterator var21 = fluidCost.entrySet().iterator();

                while(var21.hasNext()) {
                    Map.Entry<Fluid, Long> costEntry = (Map.Entry)var21.next();
                    if ((Long)costEntry.getValue() > 0L) {
                        currentParallel = Math.min(currentParallel, (double)(Long)fluidMap.getOrDefault(costEntry.getKey(), 0L) / (double)(Long)costEntry.getValue());
                    }

                    if (currentParallel <= 0.0) {
                        return 0.0;
                    }
                }
            }

            boolean isNBTSensitive = recipe.isNBTSensitive;
            if (recipe.mInputs.length > 0) {
                Map<EOHB_ItemID, Long> itemCost = new HashMap();
                ItemStack[] var12;
                int var13;
                int var14;
                ItemStack recipeItemCost;
                if (isNBTSensitive) {
                    var12 = recipe.mInputs;
                    var13 = var12.length;

                    for(var14 = 0; var14 < var13; ++var14) {
                        recipeItemCost = var12[var14];
                        if (recipeItemCost.stackSize != 0) {
                            itemCost.merge(EOHB_ItemID.create(recipeItemCost), (long)recipeItemCost.stackSize, Long::sum);
                        }
                    }
                } else {
                    var12 = recipe.mInputs;
                    var13 = var12.length;

                    for(var14 = 0; var14 < var13; ++var14) {
                        recipeItemCost = var12[var14];
                        if (recipeItemCost.stackSize != 0) {
                            itemCost.merge(EOHB_ItemID.createNoNBT(recipeItemCost), (long)recipeItemCost.stackSize, Long::sum);
                        }
                    }
                }

                Iterator var25 = itemCost.entrySet().iterator();

                while(true) {
                    label100:
                    while(var25.hasNext()) {
                        Map.Entry<EOHB_ItemID, Long> itemID = (Map.Entry)var25.next();
                        ItemStack unifiedItemCost;
                        if (isNBTSensitive) {
                            unifiedItemCost = GTOreDictUnificator.get_nocopy(((EOHB_ItemID)itemID.getKey()).getItemStackWithNBT());
                        } else {
                            unifiedItemCost = GTOreDictUnificator.get_nocopy(((EOHB_ItemID)itemID.getKey()).getItemStack());
                        }

                        double remainingCost = (double)(Long)itemID.getValue() * currentParallel;
                        long providedAmount = 0L;
                        ItemStack[] var28 = aInputs;
                        int var16 = aInputs.length;

                        for(int var17 = 0; var17 < var16; ++var17) {
                            ItemStack providedItem = var28[var17];
                            if ((!isNBTSensitive || GTUtility.areStacksEqual(providedItem, unifiedItemCost, false)) && (isNBTSensitive || GTOreDictUnificator.isInputStackEqual(providedItem, unifiedItemCost))) {
                                providedAmount += (long)providedItem.stackSize;
                                if ((double)providedAmount >= remainingCost) {
                                    continue label100;
                                }
                            }
                        }

                        if (providedAmount == 0L) {
                            return 0.0;
                        }

                        currentParallel = Math.min(currentParallel, (double)providedAmount / (double)(Long)itemID.getValue());
                    }

                    return currentParallel;
                }
            } else {
                return currentParallel;
            }
        }
    }
}
