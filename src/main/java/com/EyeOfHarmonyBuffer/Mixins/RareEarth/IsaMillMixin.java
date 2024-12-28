package com.EyeOfHarmonyBuffer.Mixins.RareEarth;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.util.GTRecipe;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.processing.MTEIsaMill;
import journeymap.shadow.org.jetbrains.annotations.NotNull;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEIsaMill.class, remap = false)
public abstract class IsaMillMixin extends GTPPMultiBlockBase<MTEIsaMill> implements ISurvivalConstructable {

    public IsaMillMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    protected abstract void damageMillingBall(ItemStack aStack);

    @Shadow
    protected abstract ItemStack findMillingBall(ItemStack[] aItemInputs);

    @Inject(
        method = "createProcessingLogic",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.IsaMillEnable){
            ProcessingLogic customProcessingLogic = new ProcessingLogic() {

                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }

                @NotNull
                @Override
                public CheckRecipeResult process() {
                    return super.process();
                }

            }.enablePerfectOverclock();

            customProcessingLogic
                .setSpeedBonus(1F / 0.02F)
                .setEuModifier(0.0F)
                .setMaxParallelSupplier(() -> 2000000);

            cir.setReturnValue(customProcessingLogic);
        }
    }
}
