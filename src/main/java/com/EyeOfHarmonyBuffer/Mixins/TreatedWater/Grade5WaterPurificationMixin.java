package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitPlasmaHeater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = MTEPurificationUnitPlasmaHeater.class, remap = false)
public abstract class Grade5WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitPlasmaHeater>
    implements ISurvivalConstructable {
    protected Grade5WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "runMachine", at = @At("HEAD"), cancellable = true)
    private void injectRunMachine(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if(MainConfig.Grade5WaterPurificationEnabled){
            try {
                Field stateField = MTEPurificationUnitPlasmaHeater.class.getDeclaredField("state");
                stateField.setAccessible(true);

                Object state = stateField.get(this);

                if (state.toString().equals("Heating")) {
                    stateField.set(this, Enum.valueOf(
                        (Class<Enum>) state.getClass(),
                        "Cooling"
                    ));
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
