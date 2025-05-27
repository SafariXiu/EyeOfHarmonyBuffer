package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.util.ExoticEnergyInputHelper;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.util.GTUtility.filterValidMTEs;

@SuppressWarnings("UnusedMixin")
@Mixin(value = GTPPMultiBlockBase.class, remap = false)
public abstract class GTPPMultiBlockBaseMixin<T extends MTEExtendedPowerMultiBlockBase<T>>
    extends MTEExtendedPowerMultiBlockBase<T> {

    @Shadow
    public ArrayList<MTEHatch> mAllEnergyHatches;

    protected GTPPMultiBlockBaseMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Override
    public List<MTEHatch> getExoticAndNormalEnergyHatchList() {
        if (MainConfig.GTPPMachineExoEnergyHatchFixEnable) {
            return new ArrayList<>(filterValidMTEs(this.mAllEnergyHatches));
        } else {
            return super.getExoticAndNormalEnergyHatchList();
        }
    }

    @Override
    public long getMaxInputVoltage() {
        if (MainConfig.GTPPMachineExoEnergyHatchFixEnable) {
        return ExoticEnergyInputHelper.getMaxInputVoltageMulti(getExoticAndNormalEnergyHatchList());
        } else {
            return super.getMaxInputVoltage();
        }
    }
}
