package com.EyeOfHarmonyBuffer.Mixins.TST;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.Nxer.TwistSpaceTechnology.common.machine.GTCM_LightningSpire;
import com.Nxer.TwistSpaceTechnology.common.machine.multiMachineClasses.TT_MultiMachineBase_EM;
import com.Nxer.TwistSpaceTechnology.util.rewrites.TST_ItemID;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.ItemList;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = GTCM_LightningSpire.class, remap = false)
public abstract class LightningSpireMixin extends TT_MultiMachineBase_EM implements IConstructable, ISurvivalConstructable {

    public LightningSpireMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int tRods;

    @Shadow
    private long tStored;

    @Shadow
    private long tProduct;

    @Shadow
    private long tMaxStored;

    @Shadow
    private int OperatingMode = 0;

    @Shadow
    protected abstract void lightOnWorld();

    @Inject(method = "checkProcessing_EM", at = @At("HEAD"), cancellable = true)
    private void modifyCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (MainConfig.LightningSpireEnable) {
            if (OperatingMode == 0 && tRods <= 0) {
                List<ItemStack> inputs = this.getStoredInputs();
                if (inputs != null && !inputs.isEmpty()) {
                    TST_ItemID LightningRod = TST_ItemID.createNoNBT(ItemList.Machine_HV_LightningRod.get(1L, new Object[0]));
                    int rodsToAdd = 0;

                    for (ItemStack stack : inputs) {
                        if (stack != null && LightningRod.equalItemStack(stack)) {
                            rodsToAdd += stack.stackSize;
                            stack.stackSize = 0;
                        }
                    }

                    tRods = Math.min(tRods + rodsToAdd, MainConfig.LightningSpireMaxRods);
                }
            }

            if (tRods > 0) {
                tProduct = (long) tRods * 28000000L;
                tMaxStored = (long) tRods * 280000000L;

                tStored = Math.min(tStored + tProduct, tMaxStored);

                GTCM_LightningSpire instance = (GTCM_LightningSpire) (Object) this;
                lightOnWorld();
                instance.mMaxProgresstime = MainConfig.LightningSpireTime;
                instance.updateSlots();

                cir.setReturnValue(CheckRecipeResultRegistry.GENERATING);
            } else {
                cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            }
        }
    }
}
