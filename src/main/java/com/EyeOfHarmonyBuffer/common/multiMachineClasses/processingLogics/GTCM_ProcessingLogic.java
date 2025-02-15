package com.EyeOfHarmonyBuffer.common.multiMachineClasses.processingLogics;

import com.EyeOfHarmonyBuffer.common.misc.OverclockType;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.ParallelHelper;

import javax.annotation.Nonnull;

public class GTCM_ProcessingLogic extends ProcessingLogic {
    public GTCM_ProcessingLogic() {
    }

    @Nonnull
    protected ParallelHelper createParallelHelper(@Nonnull GTRecipe recipe) {
        return (new GTCM_ParallelHelper()).setRecipe(recipe).setItemInputs(this.inputItems).setFluidInputs(this.inputFluids).setAvailableEUt(this.availableVoltage * this.availableAmperage).setMachine(this.machine, this.protectItems, this.protectFluids).setRecipeLocked(this.recipeLockableMachine, this.isRecipeLocked).setMaxParallel(this.maxParallel).setEUtModifier(this.euModifier).enableBatchMode(this.batchSize).setConsumption(true).setOutputCalculation(true);
    }

    public com.EyeOfHarmonyBuffer.common.multiMachineClasses.processingLogics.GTCM_ProcessingLogic setOverclockType(OverclockType t) {
        this.setOverclock((double)t.timeReduction, (double)t.powerIncrease);
        return this;
    }
}
