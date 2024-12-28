package com.EyeOfHarmonyBuffer.Mixins.RareEarth;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTRecipe;
import gtPlusPlus.core.material.Material;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GTPPMultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.helpers.FlotationRecipeHandler;
import gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production.MTEFrothFlotationCell;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = MTEFrothFlotationCell.class, remap = false)
public abstract class FrothFlotationCellMixin extends GTPPMultiBlockBase<MTEFrothFlotationCell> implements ISurvivalConstructable {

    public FrothFlotationCellMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private String lockedMaterialName = null;

    @Inject(
        method = "createProcessingLogic",
        at = @At("HEAD"),
        cancellable = true
    )
    private void injectCreateProcessingLogic(CallbackInfoReturnable<ProcessingLogic> cir) {
        if(MainConfig.FrothFlotationCellEnable){
            ProcessingLogic customLogic = new ProcessingLogic() {

                @NotNull
                @Override
                protected CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                    Material foundMaterial = FlotationRecipeHandler.getMaterialOfMilledProduct(
                        FlotationRecipeHandler.findMilledStack(recipe)
                    );
                    String foundMaterialName = null;
                    if (foundMaterial != null) {
                        foundMaterialName = foundMaterial.getUnlocalizedName();
                    }

                    if (foundMaterialName == null) {
                        return CheckRecipeResultRegistry.NO_RECIPE;
                    }

                    if (lockedMaterialName == null) {
                        lockedMaterialName = foundMaterialName;
                    }

                    if (!Objects.equals(lockedMaterialName, foundMaterialName)) {
                        return SimpleCheckRecipeResult.ofFailure("machine_locked_to_different_recipe");
                    }
                    return CheckRecipeResultRegistry.SUCCESSFUL;
                }
            };

            customLogic.setSpeedBonus(1F / 0.02F)
                .setEuModifier(0.0F)
                .setMaxParallelSupplier(() -> 200000);

            cir.setReturnValue(customLogic);
        }
    }
}
