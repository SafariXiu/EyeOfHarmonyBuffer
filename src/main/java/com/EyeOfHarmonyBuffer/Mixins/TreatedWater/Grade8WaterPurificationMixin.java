package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBaryonicPerfection;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEPurificationUnitBaryonicPerfection.class, remap = false)
public abstract class Grade8WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitBaryonicPerfection> implements ISurvivalConstructable {

    @Shadow
    private static final long CATALYST_BASE_COST = 144L;

    @Shadow
    private int correctStartIndex = -1;

    protected Grade8WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "checkSequence", at = @At("HEAD"), cancellable = true)
    private void skipSequenceCheck(CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.Grade8WaterPurificationEnabled){
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "calculateCatalystCost", at = @At("HEAD"), cancellable = true)
    private void disableDynamicCost(ItemStack newCatalyst, CallbackInfoReturnable<Integer> cir) {
        if (MainConfig.Grade8WaterPurificationEnabled) {
            cir.setReturnValue(0);
        }
    }
}
