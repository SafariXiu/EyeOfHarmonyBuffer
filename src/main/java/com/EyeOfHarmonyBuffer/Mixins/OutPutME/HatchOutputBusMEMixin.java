package com.EyeOfHarmonyBuffer.Mixins.OutPutME;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTModHandler;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEHatchOutputBusME.class, remap = false)
public abstract class HatchOutputBusMEMixin {

    private static final ItemStack UNIVERSE_CELL;

    static {
        UNIVERSE_CELL = GTModHandler.getModItem(
            "appliedenergistics2",
            "item.ItemExtremeStorageCell.Universe",
            1,
            0
        );
    }

    @Inject(method = "getCellStack", at = @At("HEAD"), cancellable = true)
    private void gg$alwaysReturnUniverseCell(CallbackInfoReturnable<ItemStack> cir) {
        if (!MainConfig.OutPutHatchMEEnable) {
            return;
        }

        if (UNIVERSE_CELL == null) {
            return;
        }

        ItemStack fake = UNIVERSE_CELL.copy();

        MTEHatchOutputBus self = (MTEHatchOutputBus) (Object) this;
        ItemStack real = self.getStackInSlot(0);
        if (real != null) {
            fake.stackSize = real.stackSize;
        }

        cir.setReturnValue(fake);
        cir.cancel();
    }
}
