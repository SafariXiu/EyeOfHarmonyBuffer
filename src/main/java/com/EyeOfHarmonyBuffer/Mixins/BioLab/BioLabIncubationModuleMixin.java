package com.EyeOfHarmonyBuffer.Mixins.BioLab;

import bartworks.common.tileentities.tiered.MTEBioLab;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.GTMod;
import gregtech.api.interfaces.ICleanroom;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.metadata.CompressionTierKey;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTRecipe;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static gregtech.api.enums.GTValues.V;
import static gregtech.api.enums.GTValues.debugCleanroom;
import static gregtech.api.util.GTRecipeConstants.EXPLODE;
import static gregtech.api.util.GTRecipeConstants.ON_FIRE;

@Mixin(value = MTEBioLab.class,remap = false)
public abstract class BioLabIncubationModuleMixin extends MTEBasicMachine {
    public BioLabIncubationModuleMixin(int aID, String aName, String aNameRegional, int aTier, int aAmperage, String aDescription, int aInputSlotCount, int aOutputSlotCount, ITexture... aOverlays) {
        super(aID, aName, aNameRegional, aTier, aAmperage, aDescription, aInputSlotCount, aOutputSlotCount, aOverlays);
    }

    @Final
    @Shadow private static int INCUBATION_MODULE;

    @Inject(method = "checkRecipe", at = @At("HEAD"), cancellable = true)
    private void injectIncubationModule(boolean skipOC, CallbackInfoReturnable<Integer> cir) {
        if(MainConfig.BioLabMixin){
            if (this.getSpecialSlot() != null && this.getSpecialSlot().getItemDamage() == INCUBATION_MODULE) {

                RecipeMap<?> tMap = getRecipeMap();
                if (tMap == null) {
                    cir.setReturnValue(DID_NOT_FIND_RECIPE);
                    return;
                }

                GTRecipe tRecipe = tMap.findRecipeQuery()
                    .items(getAllInputs())
                    .fluids(getFillableStack())
                    .specialSlot(getSpecialSlot())
                    .voltage(V[mTier])
                    .cachedRecipe(mLastRecipe)
                    .find();
                if (tRecipe == null) {
                    cir.setReturnValue(DID_NOT_FIND_RECIPE);
                    return;
                }

                if (tRecipe.getMetadataOrDefault(EXPLODE, false) && getBaseMetaTileEntity() != null) {
                    getBaseMetaTileEntity().doExplosion(V[mTier] * 4);
                    cir.setReturnValue(DID_NOT_FIND_RECIPE);
                    return;
                }

                if (tRecipe.getMetadataOrDefault(ON_FIRE, false) && getBaseMetaTileEntity() != null) {
                    getBaseMetaTileEntity().setOnFire();
                    cir.setReturnValue(DID_NOT_FIND_RECIPE);
                    return;
                }

                if (tRecipe.getMetadataOrDefault(CompressionTierKey.INSTANCE, 0) > 0) {
                    cir.setReturnValue(FOUND_RECIPE_BUT_DID_NOT_MEET_REQUIREMENTS);
                    return;
                }

                if (GTMod.proxy.mLowGravProcessing && (tRecipe.mSpecialValue == -100 || tRecipe.mSpecialValue == -300)
                    && !isValidForLowGravity(tRecipe, getBaseMetaTileEntity().getWorld().provider.dimensionId)) {
                    cir.setReturnValue(FOUND_RECIPE_BUT_DID_NOT_MEET_REQUIREMENTS);
                    return;
                }

                if (tRecipe.mCanBeBuffered) mLastRecipe = tRecipe;

                if (!canOutput(tRecipe)) {
                    mOutputBlocked++;
                    cir.setReturnValue(FOUND_RECIPE_BUT_DID_NOT_MEET_REQUIREMENTS);
                    return;
                }

                ICleanroom cleanroom = cleanroomReference.getCleanroom();
                if (tRecipe.mSpecialValue == -200 || tRecipe.mSpecialValue == -300) {
                    if (cleanroom == null || !cleanroom.isValidCleanroom() || cleanroom.getCleanness() == 0) {
                        cir.setReturnValue(FOUND_RECIPE_BUT_DID_NOT_MEET_REQUIREMENTS);
                        return;
                    }
                }

                if (!tRecipe.isRecipeInputEqual(true, new FluidStack[] { getFillableStack() }, getAllInputs())) {
                    cir.setReturnValue(FOUND_RECIPE_BUT_DID_NOT_MEET_REQUIREMENTS);
                    return;
                }

                for (int i = 0; i < mOutputItems.length; i++) {
                    mOutputItems[i] = tRecipe.getOutput(i);
                }

                if (tRecipe.mSpecialValue == -200 || tRecipe.mSpecialValue == -300) {
                    assert cleanroom != null;
                    for (int i = 0; i < mOutputItems.length; i++) {
                        if (mOutputItems[i] != null
                            && getBaseMetaTileEntity().getRandomNumber(10000) > cleanroom.getCleanness()) {
                            if (debugCleanroom) {
                                GTLog.out.println("BasicMachine: Voiding output due to cleanness failure. Cleanness = "
                                    + cleanroom.getCleanness());
                            }
                            mOutputItems[i] = null;
                        }
                    }
                }

                mOutputFluid = tRecipe.getFluidOutput(0);

                if (!skipOC) {
                    calculateCustomOverclock(tRecipe);
                    if (mMaxProgresstime == Integer.MAX_VALUE - 1 && mEUt == Integer.MAX_VALUE - 1) {
                        cir.setReturnValue(FOUND_RECIPE_BUT_DID_NOT_MEET_REQUIREMENTS);
                        return;
                    }
                }

                cir.setReturnValue(FOUND_AND_SUCCESSFULLY_USED_RECIPE);
                cir.cancel();
            }
        }
    }
}
