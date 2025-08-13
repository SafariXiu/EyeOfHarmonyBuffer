package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

@Mixin(value = TileEntityModuleBase.class,remap = false)
public abstract class ModuleAssemblerPowerMixin extends TTMultiblockBase {

    protected ModuleAssemblerPowerMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "onPostTick", at = @At("HEAD"))
    private void refillEnergy(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if(MainConfig.SpaceElevatorMiningParallelsEnable || MainConfig.SpaceElevatorMiningTicksTrue || MainConfig.SpaceElevatorAssemblerParallelEnable){
            if (aBaseMetaTileEntity.isServerSide()) {
                long currentEnergy = aBaseMetaTileEntity.getStoredEU();
                long maxEnergy = aBaseMetaTileEntity.getEUCapacity();

                if (currentEnergy < maxEnergy) {
                    aBaseMetaTileEntity.increaseStoredEnergyUnits(maxEnergy - currentEnergy, true);
                }
            }
        }
    }

}
