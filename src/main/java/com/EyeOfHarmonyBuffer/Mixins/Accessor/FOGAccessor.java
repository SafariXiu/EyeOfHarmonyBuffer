package com.EyeOfHarmonyBuffer.Mixins.Accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import tectech.thing.metaTileEntity.multi.godforge.MTEForgeOfGods;
import tectech.thing.metaTileEntity.multi.godforge.upgrade.UpgradeStorage;

@Mixin(value = MTEForgeOfGods.class, remap = false)
public interface FOGAccessor {

    @Accessor("upgrades")
    UpgradeStorage getUpgrades();

}
