package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.core.util.math.MathUtils;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchTurbine;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.turbines.MTELargerTurbineBaseLegacy;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.turbines.MTELargerTurbinePlasmaLegacy;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(value = MTELargerTurbinePlasmaLegacy.class, remap = false)
public abstract class LargerTurbinePlasmaMixin extends MTELargerTurbineBaseLegacy {

    public LargerTurbinePlasmaMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    abstract long fluidIntoPower(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine);

    @Shadow
    private int getFuelValue(FluidStack aLiquid) {
        return 0;
    }

    @Inject(method = "checkProcessing", at = @At("HEAD"), cancellable = true)
    private void injectedCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!MainConfig.LargerTurbinePlasmaEnable) {
            return;
        }

        try {
            ArrayList<MTEHatchTurbine> emptyHatches = this.getEmptyTurbineAssemblies();
            if (!emptyHatches.isEmpty()) {
                for (MTEHatchTurbine hatch : emptyHatches) {
                    ArrayList<ItemStack> turbines = this.getAllBufferedTurbines();
                    for (ItemStack turbineItem : turbines) {
                        if (turbineItem != null && hatch.insertTurbine(turbineItem.copy())) {
                            this.depleteTurbineFromStock(turbineItem);
                            break;
                        }
                    }
                }
            }

            if (this.getEmptyTurbineAssemblies().isEmpty() && this.areAllTurbinesTheSame()) {
                ItemStack turbineItem = this.mTurbineRotorHatches.get(0).getTurbine();
                TurbineStatCalculator turbine = new TurbineStatCalculator(
                    (MetaGeneratedTool) turbineItem.getItem(),
                    turbineItem
                );

                ArrayList<FluidStack> tFluids = this.getStoredFluids();

                if (!tFluids.isEmpty()) {
                    boolean needRecalc =
                        this.baseEff == 0
                            || this.optFlow == 0L
                            || this.counter >= 512
                            || this.getBaseMetaTileEntity().hasWorkJustBeenEnabled()
                            || this.getBaseMetaTileEntity().hasInventoryBeenModified();

                    if (!needRecalc) {
                        ++this.counter;
                    } else {
                        this.counter = 0;

                        float aTotalBaseEff = 0.0F;
                        float aTotalOptimalFlow = 0.0F;

                        ItemStack aStack = this.getFullTurbineAssemblies().get(0).getTurbine();

                        aTotalBaseEff += turbine.getPlasmaEfficiency() * 10000.0F;
                        aTotalOptimalFlow += turbine.getOptimalPlasmaFlow();

                        double aEUPerTurbine = (double) turbine.getOptimalPlasmaEUt();
                        aTotalOptimalFlow *= (float) this.getSpeedMultiplier();

                        if (aTotalOptimalFlow < 0.0F) {
                            aTotalOptimalFlow = 100.0F;
                        }

                        this.flowMultipliers[0] = MetaGeneratedTool.getPrimaryMaterial(aStack).mSteamMultiplier;
                        this.flowMultipliers[1] = MetaGeneratedTool.getPrimaryMaterial(aStack).mGasMultiplier;
                        this.flowMultipliers[2] = MetaGeneratedTool.getPrimaryMaterial(aStack).mPlasmaMultiplier;
                        this.baseEff = MathUtils.roundToClosestInt((double) aTotalBaseEff);
                        this.optFlow = (long) MathUtils.roundToClosestInt((double) aTotalOptimalFlow);
                        this.euPerTurbine = (long) MathUtils.roundToClosestInt(aEUPerTurbine);

                        if (this.optFlow <= 0L || this.baseEff <= 0) {
                            this.stopMachine(ShutDownReasonRegistry.NONE);
                            cir.setReturnValue(CheckRecipeResultRegistry.NO_FUEL_FOUND);
                            cir.cancel();
                            return;
                        }
                    }
                }

                long newPower = this.fluidIntoPower(tFluids, turbine);

                int fuelValue = 0;
                if (!tFluids.isEmpty()) {
                    fuelValue = this.getFuelValue(new FluidStack(tFluids.get(0), 0));
                }

                float magicValueBase = (float) fuelValue * 0.005F * (float) fuelValue * 0.005F;
                float efficiencyLossBase = 0.0F;
                if (this.euPerTurbine > 0L) {
                    efficiencyLossBase = Math.min(1.0F, magicValueBase / (float) this.euPerTurbine);
                }

                float lowFuelBoost = 1.0F;

                int lowFuelThreshold = 300000;
                float maxExtraMultiplier = 2.0F;

                if (fuelValue > 0 && fuelValue < lowFuelThreshold) {
                    float ratio = (lowFuelThreshold - fuelValue) / (float) lowFuelThreshold; // 0~1
                    lowFuelBoost = 1.0F + ratio * maxExtraMultiplier;
                }

                float efficiencyLoss = Math.min(1.0F, efficiencyLossBase * lowFuelBoost);

                newPower = (long) ((float) newPower * efficiencyLoss);

                long difference = newPower - this.lEUt;
                int maxChangeAllowed = Math.max(200, GTUtility.safeInt(Math.abs(difference) / 5L));
                if (Math.abs(difference) > (long) maxChangeAllowed) {
                    int change = maxChangeAllowed * (difference > 0L ? 1 : -1);
                    this.lEUt += (long) change;
                } else {
                    this.lEUt = newPower;
                }

                if (this.lEUt <= 0L) {
                    this.lEUt = 0L;
                    this.mEfficiency = 0;
                    cir.setReturnValue(CheckRecipeResultRegistry.NO_FUEL_FOUND);
                    cir.cancel();
                    return;
                } else {
                    this.mMaxProgresstime = 20;
                    this.mEfficiencyIncrease = 200;
                    this.enableAllTurbineHatches();
                    cir.setReturnValue(CheckRecipeResultRegistry.GENERATING);
                    cir.cancel();
                    return;
                }
            } else {
                this.stopMachine(ShutDownReasonRegistry.NO_TURBINE);
                cir.setReturnValue(CheckRecipeResultRegistry.NO_TURBINE_FOUND);
                cir.cancel();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            cir.setReturnValue(CheckRecipeResultRegistry.NO_FUEL_FOUND);
            cir.cancel();
        }
    }
}
