package com.EyeOfHarmonyBuffer.Mixins.LargeFusion;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;
import gregtech.api.enums.GTValues;

@Mixin(value = MTELargeFusionComputer.class, remap = false)
public abstract class LargeFusionMixin extends MTETooltipMultiBlockBaseEM
    implements IConstructable, ISurvivalConstructable, IOverclockDescriptionProvider {

    @Shadow
    public abstract int tier();

    @Shadow
    public abstract int getMaxPara();

    @Shadow
    public abstract int extraPara(long startEnergy);

    protected LargeFusionMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "getSingleHatchPower", at = @At("RETURN"), cancellable = true)
    private void injectGetSingleHatchPower(CallbackInfoReturnable<Long> cir) {
        if (MainConfig.LargeFusionMixin) {
            MTELargeFusionComputer fusionComputer = (MTELargeFusionComputer) (Object) this;
            long customPower = GTValues.V[fusionComputer.tier()] * fusionComputer.getMaxPara() * 32L;
            cir.setReturnValue(customPower);
        }
    }

    @Inject(method = "maxEUStore", at = @At("RETURN"), cancellable = true)
    private void modifyMaxEUStore(CallbackInfoReturnable<Long> cir) {
        if (MainConfig.LargeFusionMixin) {
            long maxEnergyStore = 10000000000000L;
            cir.setReturnValue(maxEnergyStore);
        }
    }
}
