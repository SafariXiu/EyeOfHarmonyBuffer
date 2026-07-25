package com.EyeOfHarmonyBuffer.Mixins.OutPutME;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.util.GTModHandler;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static fox.spiteful.avaritia.Mods.AE2FluidCraft;

@Mixin(value = MTEHatchOutputME.class, remap = false)
public abstract class HatchOutputMEMixin {

    private static ItemStack UNIVERSE_FLUID_CELL;

    private static ItemStack gg$getUniverseFluidCell() {
        if (UNIVERSE_FLUID_CELL == null) {
            UNIVERSE_FLUID_CELL = GTModHandler.getModItem(
                AE2FluidCraft.ID,
                "fluid_storage.Universe",
                1,
                0
            );
        }
        return UNIVERSE_FLUID_CELL;
    }

    @Inject(method = "getCellStack", at = @At("HEAD"), cancellable = true)
    private void gg$alwaysReturnUniverseFluidCell(CallbackInfoReturnable<ItemStack> cir) {
        if (!MainConfig.OutPutHatchMEEnable) return;

        ItemStack universe = gg$getUniverseFluidCell();
        if (universe == null) return;

        cir.setReturnValue(universe.copy());
        cir.cancel();
    }
}
