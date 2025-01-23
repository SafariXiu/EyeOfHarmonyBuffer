package com.EyeOfHarmonyBuffer.Mixins.FuelFactory;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import goodgenerator.blocks.tileEntity.MTEMultiNqGenerator;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.recipe.check.CheckRecipeResult;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEMultiNqGenerator.class, remap = false)
public abstract class NaquadahFuelRefineryMixin extends MTETooltipMultiBlockBaseEM implements IConstructable, ISurvivalConstructable {

    protected NaquadahFuelRefineryMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "checkProcessing_EM", at = @At("RETURN"), cancellable = true)
    private void modifyOutputMultiplier(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if(MainConfig.NaquadahFuelOutPutMagnificationTrue){
            MTEMultiNqGenerator instance = (MTEMultiNqGenerator) (Object) this;
            if (instance.mOutputFluids != null && instance.mOutputFluids.length > 0) {
                for (FluidStack fluid : instance.mOutputFluids) {
                    fluid.amount *= MainConfig.NaquadahFuelOutPutMagnification;
                }

                instance.mMaxProgresstime = Math.max(1, instance.mMaxProgresstime / MainConfig.NaquadahFuelOutPutMagnification);
            }
        }
    }
}
