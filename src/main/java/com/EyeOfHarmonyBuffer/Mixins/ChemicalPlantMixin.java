package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gtPlusPlus.core.item.chemistry.general.ItemGenericChemBase;
import gtPlusPlus.core.recipe.common.CI;
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

    @Inject(method = "damageCatalyst",at = @At("HEAD"),cancellable = true)
    private void damageCatalyst(ItemStack aStack, int minParallel, CallbackInfoReturnable<Boolean> cir){
        if(MainConfig.ChemicalPlantEnable){
            float damageChance = 0.0f;
            for (int i = 0; i < minParallel; i++) {

                if (MathUtils.randFloat(0, 1.0f) < damageChance) {
                    int damage = getDamage(aStack) + 1;
                    if (damage >= getMaxCatalystDurability()) {
                        addOutput(CI.getEmptyCatalyst(1));
                        aStack.stackSize -= 1;
                        cir.setReturnValue(aStack.stackSize == 0);
                        return;
                    } else {
                        setDamage(aStack, damage);
                    }
                }
            }

            cir.setReturnValue(false);
        }
    }
}
