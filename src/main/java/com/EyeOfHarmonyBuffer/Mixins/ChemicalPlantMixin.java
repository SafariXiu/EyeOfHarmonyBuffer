package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gtPlusPlus.core.util.math.MathUtils;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.chemplant.MTEChemicalPlant;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nonnull;

@Mixin(value = MTEChemicalPlant.class, remap = false)
public abstract class ChemicalPlantMixin extends GTPPMultiBlockBase<MTEChemicalPlant> implements ISurvivalConstructable {

    public ChemicalPlantMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    protected abstract void setDamage(@Nonnull ItemStack aStack, int aAmount);

    @Shadow
    public abstract int getMaxCatalystDurability();

    @Shadow
    protected abstract int getDamage(@Nonnull ItemStack aStack);

    @Inject(method = "damageCatalyst", at = @At("HEAD"), cancellable = true)
    private void onDamageCatalyst(ItemStack aStack, int minParallel, CallbackInfoReturnable<Boolean> cir) {
        if (MainConfig.ChemicalPlantEnable) {
            cir.setReturnValue(false);
            return;
        }
    }
}
