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
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
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
    List<ItemStack> mStored = new ArrayList();

    @Shadow
    protected abstract void lightOnWorld();

    @Inject(method = "checkProcessing_EM", at = @At("HEAD"), cancellable = true)
    private void modifyCheckProcessing(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if (MainConfig.LightningSpireEnable) {
            int canAdd;
            ArrayList cryotheums;

            if (this.OperatingMode == 0) {
                if (this.tRods > 0) {
                    this.tProduct = (long) this.tRods * 28000000L;
                    this.tMaxStored = (long) this.tRods * 280000000L;
                    this.tStored = Math.min(this.tStored + this.tProduct, this.tMaxStored);

                    GTCM_LightningSpire instance = (GTCM_LightningSpire) (Object) this;
                    lightOnWorld();

                    instance.mMaxProgresstime = MainConfig.LightningSpireTime;

                    instance.updateSlots();

                    cir.setReturnValue(CheckRecipeResultRegistry.GENERATING);
                    cir.cancel();
                    return;
                } else {
                    cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
                    cir.cancel();
                    return;
                }
            }

            if (this.OperatingMode == 1 && this.tRods < MainConfig.LightningSpireMaxRods) {
                TST_ItemID LightningRod = TST_ItemID.createNoNBT(ItemList.Machine_HV_LightningRod.get(1L, new Object[0]));
                canAdd = MainConfig.LightningSpireMaxRods - this.tRods;
                cryotheums = this.getStoredInputs();

                if (cryotheums.isEmpty()) {
                    cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
                    cir.cancel();
                    return;
                }

                Iterator var4 = cryotheums.iterator();
                while (var4.hasNext()) {
                    ItemStack machine = (ItemStack) var4.next();
                    if (machine != null && machine.stackSize >= 1 && LightningRod.equalItemStack(machine)) {
                        if (canAdd <= machine.stackSize) {
                            this.mStored.add(GTUtility.copyAmount(MainConfig.LightningSpireMaxRods - this.tRods, machine));
                            machine.stackSize -= canAdd;
                            this.tRods = MainConfig.LightningSpireMaxRods;
                            break;
                        }

                        this.mStored.add(GTUtility.copy(machine));
                        this.tRods += machine.stackSize;
                        canAdd -= machine.stackSize;
                        machine.stackSize = 0;
                    }
                }

                this.tProduct = (long) this.tRods * 28000000L;
                this.tMaxStored = (long) this.tRods * 280000000L;
                this.mMaxProgresstime = 20;
                this.updateSlots();
                cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
                cir.cancel();
                return;
            }

            if (this.OperatingMode == 2 && this.tRods > 0 && this.tStored == 0L) {
                this.mOutputItems = (ItemStack[])this.mStored.toArray(new ItemStack[0]);
                this.mStored.clear();
                this.updateSlots();
                this.tRods = 0;
                this.tProduct = 0L;
                this.tMaxStored = 0L;
                this.mMaxProgresstime = 20;
                this.updateSlots();
                cir.setReturnValue(CheckRecipeResultRegistry.SUCCESSFUL);
            }

            cir.setReturnValue(CheckRecipeResultRegistry.NO_RECIPE);
            cir.cancel();
        }
    }
}
