package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import tectech.thing.metaTileEntity.multi.godforge.upgrade.ForgeOfGodsUpgrade;
import tectech.thing.metaTileEntity.multi.godforge.upgrade.UpgradeStorage;
import tectech.thing.metaTileEntity.multi.godforge.util.ForgeOfGodsData;

@Mixin(value = ForgeOfGodsData.class, remap = false)
public class FOGShardsAvailable {

    @Inject(method = "unlockUpgrade", at = @At("HEAD"), cancellable = true)
    private void forceUnlockUpgrade(ForgeOfGodsUpgrade upgrade, CallbackInfo ci) {
        if (!MainConfig.FOGUpDate) {
            return;
        }

        ForgeOfGodsData self = (ForgeOfGodsData) (Object) this;

        if (self.isUpgradeActive(upgrade)) {
            ci.cancel();
            return;
        }

        UpgradeStorage upgrades = self.getUpgrades();
        upgrades.unlockUpgrade(upgrade);

        ci.cancel();
    }
}
