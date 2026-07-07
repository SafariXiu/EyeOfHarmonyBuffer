package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBaryonicPerfection;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPurificationUnitBaryonicPerfection.class, remap = false)
public abstract class Grade8WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitBaryonicPerfection> implements ISurvivalConstructable {

    protected Grade8WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(
        method = "calculateFinalSuccessChance",
        at = @At("HEAD"),
        cancellable = true
    )
    private void alwaysSuccess(CallbackInfoReturnable<Float> cir) {
        if (MainConfig.Grade8WaterPurificationEnabled) {
            cir.setReturnValue(100.0f);
        }
    }

    @Inject(
        method = "runMachine",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/common/tileentities/machines/multi/purification/MTEPurificationUnitBase;runMachine(Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;J)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void skipPuzzle(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if (MainConfig.Grade8WaterPurificationEnabled) {
            ci.cancel();
        }
    }
}
