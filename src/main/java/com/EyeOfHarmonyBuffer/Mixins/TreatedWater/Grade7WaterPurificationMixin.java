package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitDegasser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@Mixin(value = MTEPurificationUnitDegasser.class, remap = false)
public abstract class Grade7WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitDegasser>
    implements ISurvivalConstructable {

    protected Grade7WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "startCycle", at = @At("HEAD"))
    private void forceControlSignal(int cycleTime, int progressTime, CallbackInfo ci) {
        if(MainConfig.Grade7WaterPurificationEnabled){
            try {
                Field controlSignalField = MTEPurificationUnitDegasser.class.getDeclaredField("controlSignal");
                controlSignalField.setAccessible(true);
                Class<?> controlSignalClass = Class.forName("gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitDegasser$ControlSignal");
                Constructor<?> constructor = controlSignalClass.getDeclaredConstructor(byte.class);
                constructor.setAccessible(true);
                Object newControlSignal = constructor.newInstance((byte) 4);

                controlSignalField.set(this, newControlSignal);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
