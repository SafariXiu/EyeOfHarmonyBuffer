package com.EyeOfHarmonyBuffer.Mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import tectech.thing.metaTileEntity.multi.MTEForgeOfGods;

@Mixin(value = MTEForgeOfGods.class, remap = false)
public interface FOGAccessor {

    @Accessor("upgrades")
    boolean[] getUpgrades();

    @Accessor("currentUpgradeID")
    int getCurrentUpgradeID();

}
