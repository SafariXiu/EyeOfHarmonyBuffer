package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.TreatedWater.Grade4WaterPurificationAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitPhAdjustment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPurificationUnitPhAdjustment.class, remap = false)
public abstract class Grade4WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitPhAdjustment>
    implements ISurvivalConstructable {

    protected Grade4WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    private static final float SAFE_PH_MIN = 6.95f; // 安全范围下限
    private static final float SAFE_PH_MAX = 7.05f; // 安全范围上限

    @Shadow
    private float currentpHValue = 0.0f;

    @Inject(method = "calculateFinalSuccessChance", at = @At("HEAD"), cancellable = true)
    private void forceSuccessChance(CallbackInfoReturnable<Float> cir) {
        if(MainConfig.Grade4WaterPurificationEnabled){
            cir.setReturnValue(100.0f);
        }
    }

    @Inject(method = "runMachine", at = @At("HEAD"), cancellable = true)
    private void forceSafePH(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if(MainConfig.Grade4WaterPurificationEnabled){
            MTEPurificationUnitPhAdjustment instance = (MTEPurificationUnitPhAdjustment) (Object) this;
            Grade4WaterPurificationAccessor accessor = (Grade4WaterPurificationAccessor) instance;

            float currentpHValue = accessor.getCurrentpHValue();

            if (currentpHValue < SAFE_PH_MIN) {
                accessor.setCurrentpHValue(SAFE_PH_MIN);
            } else if (currentpHValue > SAFE_PH_MAX) {
                accessor.setCurrentpHValue(SAFE_PH_MAX);
            }

            ci.cancel();
        }
    }
}
