package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import kubatech.tileentity.gregtech.multiblock.MTEMegaIndustrialApiary;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = MTEMegaIndustrialApiary.BeeSimulator.class, remap = false)
public abstract class MegaIndustrialApiaryMixin {

    @Inject(
        method = "getDrops(Lkubatech/tileentity/gregtech/multiblock/MTEMegaIndustrialApiary;D)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void onGetDrops(MTEMegaIndustrialApiary mte, double timePassed,
                            CallbackInfoReturnable<List<ItemStack>> cir) {
        int multiplier = Math.max(1, MainConfig.MegaIndustrialApiaryOutPut);

        if (!MainConfig.MegaIndustrialApiaryOutPutEnable) return;

        List<ItemStack> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        List<ItemStack> boosted = new ArrayList<>();

        for (ItemStack stack : original) {
            if (stack == null) continue;
            ItemStack copy = stack.copy();

            int total = copy.stackSize * multiplier;
            while (total > copy.getMaxStackSize()) {
                ItemStack split = copy.copy();
                split.stackSize = copy.getMaxStackSize();
                boosted.add(split);
                total -= copy.getMaxStackSize();
            }

            copy.stackSize = total;
            boosted.add(copy);
        }

        cir.setReturnValue(boosted);
    }
}
