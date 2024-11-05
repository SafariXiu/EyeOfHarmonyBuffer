package com.EyeOfHarmonyBuffer.Mixins;

import java.lang.reflect.Field;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import goodgenerator.blocks.tileEntity.base.MTELargeFusionComputer;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;

@Mixin(value = MTELargeFusionComputer.class, remap = false)
public class LargeFusionMixin {

    @Inject(method = "getSingleHatchPower", at = @At("RETURN"), cancellable = true)
    private void modifyGetSingleHatchPower(CallbackInfoReturnable<Long> cir) {
        if (MainConfig.LargeFusionMixin) {
            MTELargeFusionComputer fusionComputer = (MTELargeFusionComputer) (Object) this;

            List<MTEHatchEnergy> energyHatches = fusionComputer.mEnergyHatches;

            List<MTEHatchEnergyMulti> energyMultiHatches = getEnergyMultiHatches(fusionComputer);

            long totalPower = 0;

            for (MTEHatchEnergy energyHatch : energyHatches) {
                long amperage = energyHatch.maxAmperesOut();
                long voltage = energyHatch.getBaseMetaTileEntity()
                    .getInputVoltage();
                totalPower += amperage * voltage;
            }

            if (energyMultiHatches != null) {
                for (MTEHatchEnergyMulti energyMultiHatch : energyMultiHatches) {
                    long amperage = energyMultiHatch.maxAmperesOut();
                    long voltage = energyMultiHatch.getBaseMetaTileEntity()
                        .getInputVoltage();
                    totalPower += amperage * voltage;
                }
            }

            cir.setReturnValue(totalPower);
        }
    }

    @Inject(method = "maxEUStore", at = @At("RETURN"), cancellable = true)
    private void modifyMaxEUStore(CallbackInfoReturnable<Long> cir) {
        if (MainConfig.LargeFusionMixin) {
            MTELargeFusionComputer fusionComputer = (MTELargeFusionComputer) (Object) this;
            long maxEnergyStore = fusionComputer.capableStartupCanonical() * 32L / 32L;
            cir.setReturnValue(maxEnergyStore);
        }
    }

    @SuppressWarnings("unchecked")
    private List<MTEHatchEnergyMulti> getEnergyMultiHatches(MTELargeFusionComputer fusionComputer) {
        try {
            Field energyMultiField = MTELargeFusionComputer.class.getSuperclass()
                .getDeclaredField("eEnergyMulti");
            energyMultiField.setAccessible(true);
            return (List<MTEHatchEnergyMulti>) energyMultiField.get(fusionComputer);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}
