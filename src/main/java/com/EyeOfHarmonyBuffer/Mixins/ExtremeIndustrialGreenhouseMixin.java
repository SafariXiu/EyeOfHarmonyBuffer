package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.recipe.check.CheckRecipeResult;
import kubatech.api.eig.EIGDropTable;
import kubatech.api.implementations.KubaTechGTMultiBlockBase;
import kubatech.tileentity.gregtech.multiblock.MTEExtremeIndustrialGreenhouse;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEExtremeIndustrialGreenhouse.class, remap = false)
public abstract class ExtremeIndustrialGreenhouseMixin extends KubaTechGTMultiBlockBase<MTEExtremeIndustrialGreenhouse> {

    protected ExtremeIndustrialGreenhouseMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Final
    @Shadow
    public EIGDropTable dropTracker;

    @Inject(
        method = "checkProcessing",
        at = @At(
            value = "FIELD",
            target = "Lkubatech/tileentity/gregtech/multiblock/MTEExtremeIndustrialGreenhouse;mOutputItems:[Lnet/minecraft/item/ItemStack;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        )
    )
    private void onCheckProcessing_BoostDrops(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (!MainConfig.ExtremeIndustrialGreenhouseOutPutEnable) return;

        double multiplier = Math.max(1.0, MainConfig.ExtremeIndustrialGreenhouseOutPut);

        if (mOutputItems == null) return;

        for (ItemStack stack : mOutputItems) {
            if (stack == null) continue;
            stack.stackSize = (int) Math.round(stack.stackSize * multiplier);
        }
    }
}
