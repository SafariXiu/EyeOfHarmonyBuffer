package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.items.MetaGeneratedTool;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gtPlusPlus.core.util.math.MathUtils;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.MTEHatchTurbine;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.turbines.MTELargerTurbineBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.turbines.MTELargerTurbinePlasma;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(value = MTELargerTurbinePlasma.class, remap = false)
public abstract class LargerTurbinePlasmaMixin extends MTELargerTurbineBase {

    public LargerTurbinePlasmaMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    abstract long fluidIntoPower(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine);

    @Inject(method = "checkProcessing",at = @At("HEAD"),cancellable = true)
    private void newCheckProcessing(CallbackInfoReturnable<Object> cir) {
        if(MainConfig.LargerTurbinePlasmaEnable){
            try {
                ArrayList<MTEHatchTurbine> aEmptyTurbineRotorHatches = getEmptyTurbineAssemblies();
                if (!aEmptyTurbineRotorHatches.isEmpty()) {
                    hatch: for (MTEHatchTurbine aHatch : aEmptyTurbineRotorHatches) {
                        ArrayList<ItemStack> aTurbines = getAllBufferedTurbines();
                        for (ItemStack aTurbineItem : aTurbines) {
                            if (aTurbineItem == null) {
                                continue;
                            }
                            if (aHatch.insertTurbine(aTurbineItem.copy())) {
                                depleteTurbineFromStock(aTurbineItem);
                                continue hatch;
                            }
                        }
                    }
                }

                if (!getEmptyTurbineAssemblies().isEmpty() || !areAllTurbinesTheSame()) {
                    stopMachine(ShutDownReasonRegistry.NO_TURBINE);
                    cir.setReturnValue(CheckRecipeResultRegistry.NO_TURBINE_FOUND);
                }

                ItemStack turbineItem = mTurbineRotorHatches.get(0)
                    .getTurbine();
                TurbineStatCalculator turbine = new TurbineStatCalculator(
                    (MetaGeneratedTool) turbineItem.getItem(),
                    turbineItem);

                ArrayList<FluidStack> tFluids = getStoredFluids();

                if (!tFluids.isEmpty()) {
                    if (baseEff == 0 || optFlow == 0
                        || counter >= 512
                        || this.getBaseMetaTileEntity()
                        .hasWorkJustBeenEnabled()
                        || this.getBaseMetaTileEntity()
                        .hasInventoryBeenModified()) {
                        counter = 0;

                        float aTotalBaseEff = 0;
                        float aTotalOptimalFlow = 0;

                        ItemStack aStack = getFullTurbineAssemblies().get(0)
                            .getTurbine();
                        aTotalBaseEff += turbine.getPlasmaEfficiency() * 10000;
                        aTotalOptimalFlow += turbine.getOptimalPlasmaFlow();

                        double aEUPerTurbine = turbine.getOptimalPlasmaEUt();
                        aTotalOptimalFlow *= getSpeedMultiplier();

                        if (aTotalOptimalFlow < 0) {
                            aTotalOptimalFlow = 100;
                        }

                        flowMultipliers[0] = MetaGeneratedTool.getPrimaryMaterial(aStack).mSteamMultiplier;
                        flowMultipliers[1] = MetaGeneratedTool.getPrimaryMaterial(aStack).mGasMultiplier;
                        flowMultipliers[2] = MetaGeneratedTool.getPrimaryMaterial(aStack).mPlasmaMultiplier;
                        baseEff = MathUtils.roundToClosestInt(aTotalBaseEff);
                        optFlow = MathUtils.roundToClosestInt(aTotalOptimalFlow);
                        euPerTurbine = MathUtils.roundToClosestInt(aEUPerTurbine);
                        if (optFlow <= 0 || baseEff <= 0) {
                            stopMachine(ShutDownReasonRegistry.NONE);
                            cir.setReturnValue(CheckRecipeResultRegistry.NO_FUEL_FOUND);
                        }
                    } else {
                        counter++;
                    }
                }

                long newPower = fluidIntoPower(tFluids, turbine);

                int fuelValue = 0;
                if (!tFluids.isEmpty()) {
                    fuelValue = getFuelValue(new FluidStack(tFluids.get(0), 0));
                }

                float magicValue = (fuelValue * 0.01f) * (fuelValue * 0.001f);
                float efficiencyLoss;

                if (fuelValue < 300000) {
                    efficiencyLoss = Math.min(1.0f, (magicValue + 0.2f) / euPerTurbine);
                } else {
                    efficiencyLoss = Math.min(1.0f, magicValue / euPerTurbine);
                }

                newPower *= efficiencyLoss;

                long difference = newPower - this.lEUt;
                int maxChangeAllowed = Math.max(200, GTUtility.safeInt(Math.abs(difference) / 5));

                if (Math.abs(difference) > maxChangeAllowed) {
                    int change = maxChangeAllowed * (difference > 0 ? 1 : -1);
                    this.lEUt += change;
                } else {
                    this.lEUt = newPower;
                }
                if (this.lEUt <= 0) {
                    this.lEUt = 0;
                    this.mEfficiency = 0;
                    cir.setReturnValue(CheckRecipeResultRegistry.NO_FUEL_FOUND);
                } else {
                    this.mMaxProgresstime = 20;
                    this.mEfficiencyIncrease = 200;
                    enableAllTurbineHatches();
                    cir.setReturnValue(CheckRecipeResultRegistry.GENERATING);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            cir.setReturnValue(CheckRecipeResultRegistry.NO_FUEL_FOUND);

            cir.cancel();
        }
    }
}
