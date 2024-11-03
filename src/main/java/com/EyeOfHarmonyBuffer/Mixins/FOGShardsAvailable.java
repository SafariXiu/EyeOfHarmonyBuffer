package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tectech.thing.metaTileEntity.multi.MTEForgeOfGods;

@Mixin(value = MTEForgeOfGods.class, remap = false)
public class FOGShardsAvailable {

    @Inject(method = "completeUpgrade", at = @At("HEAD"), cancellable = true)
    private void injectCompleteUpgrade(CallbackInfo ci) {

        FOGAccessor accessor = (FOGAccessor) this;

        boolean[] upgrades = accessor.getUpgrades();
        int currentUpgradeID = accessor.getCurrentUpgradeID();

        upgrades[currentUpgradeID] = true;

        ci.cancel();
    }
}
