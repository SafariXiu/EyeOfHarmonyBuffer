package com.EyeOfHarmonyBuffer.Mixins.TST;

import com.Nxer.TwistSpaceTechnology.common.machine.GTCM_LightningSpire;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.TT_MultiMachineBase_EM;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static gregtech.api.recipe.check.CheckRecipeResultRegistry.NO_RECIPE;

@Mixin(value = GTCM_LightningSpire.class, remap = false)
public abstract class LightningSpireMixin extends TT_MultiMachineBase_EM implements IConstructable, ISurvivalConstructable {

    public LightningSpireMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int tRods;

    @Shadow
    private long tStored;

    @Shadow
    private long tProduct;

    @Shadow
    private long tMaxStored;

    @Shadow
    protected abstract void lightOnWorld();

    @Inject(method = "checkProcessing_EM", at = @At("HEAD"), cancellable = true)
    private void modifyCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (tRods > 0) {
            tStored = Math.min(tStored + tProduct, tMaxStored);
            GTCM_LightningSpire instance = (GTCM_LightningSpire) (Object) this;
            lightOnWorld();
            instance.mMaxProgresstime = 256;
            instance.updateSlots();

            cir.setReturnValue(CheckRecipeResultRegistry.GENERATING);
        } else {
            cir.setReturnValue(NO_RECIPE);
        }
    }
}
