package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.FOGAccessor;

import tectech.thing.metaTileEntity.multi.godforge.MTEForgeOfGods;
import tectech.thing.metaTileEntity.multi.godforge.upgrade.ForgeOfGodsUpgrade;
import tectech.thing.metaTileEntity.multi.godforge.upgrade.UpgradeStorage;

@Mixin(value = MTEForgeOfGods.class, remap = false)
public class FOGShardsAvailable {

    /**
     * 强制启用升级，无视任何前置条件
     */
    @Inject(method = "completeUpgrade", at = @At("HEAD"), cancellable = true)
    private void forceCompleteUpgrade(ForgeOfGodsUpgrade upgrade, CallbackInfo ci) {
        if(MainConfig.FOGUpDate){
            UpgradeStorage upgrades = ((FOGAccessor) (Object) this).getUpgrades();
            upgrades.unlockUpgrade(upgrade);
            ci.cancel();
        }
    }
}
