package com.EyeOfHarmonyBuffer.Mixins.SpaceElevator;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.GT_MetaTileEntity_EnhancedMultiBlockBase_EM;
import com.gtnewhorizons.gtnhintergalactic.tile.multi.elevatormodules.TileEntityModuleBase;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileEntityModuleBase.class,remap = false)
public abstract class ModuleAssemblerPowerMixin extends GT_MetaTileEntity_EnhancedMultiBlockBase_EM {

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
