package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.GTMod;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.modularui.IAddUIWidgets;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.objects.overclockdescriber.OverclockDescriber;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;
import gregtech.api.util.ParallelHelper;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.tileentities.machines.multi.MTEFusionComputer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

import static gregtech.api.util.GTUtility.validMTEList;

@Mixin(value = MTEFusionComputer.class, remap = false)
public abstract class FusionComputerMixin extends MTEEnhancedMultiBlockBase<MTEFusionComputer>
    implements ISurvivalConstructable, IAddUIWidgets, IOverclockDescriptionProvider {

    protected FusionComputerMixin(int aID, String aName, String aNameRegional, OverclockDescriber overclockDescriber) {
        super(aID, aName, aNameRegional);
        this.overclockDescriber = overclockDescriber;
    }

    @Shadow
    public long mEUStore;

    @Shadow
    public abstract int tier();

    @Shadow
    public GTRecipe mLastRecipe;

    @Mutable
    @Final
    @Shadow
    private final OverclockDescriber overclockDescriber;

    @Shadow
    public abstract boolean turnCasingActive(boolean status);

    @Inject(method = "onPostTick",at = @At("HEAD"),cancellable = true)
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if(MainConfig.FusionComputerEnable){
            if (aBaseMetaTileEntity.isServerSide()) {
                mTotalRunTime++;
                if (mEfficiency < 0) mEfficiency = 0;
                if (mRunningOnLoad && checkMachine(aBaseMetaTileEntity, mInventory[1])) {
                    this.mEUStore = aBaseMetaTileEntity.getStoredEU();
                    checkRecipe();
                }
                if (mUpdated) {
                    mUpdate = 50;
                    mUpdated = false;
                }
                if (--mUpdate == 0 || --mStartUpCheck == 0) {
                    checkStructure(true, aBaseMetaTileEntity);
                }
                if (mStartUpCheck < 0) {
                    if (mMachine) {
                        this.mEUStore = aBaseMetaTileEntity.getStoredEU();
                        if (this.mEnergyHatches != null) {
                            for (MTEHatchEnergy tHatch : validMTEList(mEnergyHatches)) {
                                long energyToMove = GTValues.V[tier()] * 16;
                                if (aBaseMetaTileEntity.getStoredEU() + energyToMove < maxEUStore()
                                    && tHatch.getBaseMetaTileEntity()
                                    .decreaseStoredEnergyUnits(energyToMove, false)) {
                                    aBaseMetaTileEntity.increaseStoredEnergyUnits(energyToMove, true);
                                }
                            }
                        }
                        if (this.mEUStore <= 0 && mMaxProgresstime > 0) {
                            stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                        }
                        if (mMaxProgresstime > 0) {
                            this.getBaseMetaTileEntity()
                                .decreaseStoredEnergyUnits(-mEUt, true);
                            if (mMaxProgresstime > 0 && ++mProgresstime >= mMaxProgresstime) {
                                if (mOutputItems != null)
                                    for (ItemStack tStack : mOutputItems) if (tStack != null) addOutput(tStack);
                                if (mOutputFluids != null)
                                    for (FluidStack tStack : mOutputFluids) if (tStack != null) addOutput(tStack);
                                mEfficiency = Math
                                    .max(0, Math.min(mEfficiency + mEfficiencyIncrease, getMaxEfficiency(mInventory[1])));
                                mOutputItems = null;
                                mProgresstime = 0;
                                mMaxProgresstime = 0;
                                mEfficiencyIncrease = 0;
                                mLastWorkingTick = mTotalRunTime;
                                if (mOutputFluids != null && mOutputFluids.length > 0) {
                                    try {
                                        GTMod.achievements.issueAchivementHatchFluid(
                                            aBaseMetaTileEntity.getWorld()
                                                .getPlayerEntityByName(aBaseMetaTileEntity.getOwnerName()),
                                            mOutputFluids[0]);
                                    } catch (Exception ignored) {}
                                }
                                this.mEUStore = aBaseMetaTileEntity.getStoredEU();
                                if (aBaseMetaTileEntity.isAllowedToWork()) checkRecipe();
                            }
                        } else {
                            if (aTick % 100 == 0 || aBaseMetaTileEntity.hasWorkJustBeenEnabled()
                                || aBaseMetaTileEntity.hasInventoryBeenModified()) {
                                turnCasingActive(mMaxProgresstime > 0);
                                if (aBaseMetaTileEntity.isAllowedToWork()) {
                                    this.mEUStore = aBaseMetaTileEntity.getStoredEU();
                                    if (checkRecipe()) {
                                        if (this.mEUStore < this.mLastRecipe.mSpecialValue + this.mEUt) {
                                            stopMachine(ShutDownReasonRegistry.POWER_LOSS);
                                        }
                                        aBaseMetaTileEntity
                                            .decreaseStoredEnergyUnits(this.mLastRecipe.mSpecialValue + this.mEUt, true);
                                    }
                                }
                                if (mMaxProgresstime <= 0) mEfficiency = Math.max(0, mEfficiency - 1000);
                            }
                        }
                    } else if (aBaseMetaTileEntity.isAllowedToWork()) {
                        this.mLastRecipe = null;
                        stopMachine(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
                    }
                }
                aBaseMetaTileEntity
                    .setErrorDisplayID((aBaseMetaTileEntity.getErrorDisplayID() & ~127) | (mMachine ? 0 : 64));
                aBaseMetaTileEntity.setActive(mMaxProgresstime > 0);
            } else {
                doActivitySound(getActivitySoundLoop());
            }
            ci.cancel();
        }
    }

    @Inject(method = "createProcessingLogic",at = @At("HEAD"),cancellable = true)
    public void createProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.FusionComputerEnable){
            ProcessingLogic customLogic = new ProcessingLogic(){

                @NotNull
                @Override
                protected ParallelHelper createParallelHelper(@NotNull GTRecipe recipe){
                    return super.createParallelHelper(recipe).setConsumption(!mRunningOnLoad);
                }

                @NotNull
                @Override
                protected OverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                    return overclockDescriber.createCalculator(super.createOverclockCalculator(recipe), recipe);
                }

                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    if (!mRunningOnLoad && recipe.mSpecialValue > maxEUStore()) {
                        return CheckRecipeResultRegistry.insufficientStartupPower(recipe.mSpecialValue);
                    }
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @NotNull
                @Override
                public CheckRecipeResult process() {
                    CheckRecipeResult result = super.process();
                    if (mRunningOnLoad) mRunningOnLoad = false;
                    turnCasingActive(result.wasSuccessful());
                    if (result.wasSuccessful()) {
                        mLastRecipe = lastRecipe;
                    } else {
                        mLastRecipe = null;
                    }
                    return result;
                }

                @Override
                protected double calculateDuration(@Nonnull GTRecipe recipe, @Nonnull ParallelHelper helper,
                                                   @Nonnull OverclockCalculator calculator) {
                    return 10;
                }

            };

            customLogic
                .setEuModifier(0.0F)
                .setMaxParallelSupplier(() -> Integer.MAX_VALUE);

            cir.setReturnValue(customLogic);
            cir.cancel();
        }
    }
}
