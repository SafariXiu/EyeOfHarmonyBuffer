package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.modularui.IAddUIWidgets;
import gregtech.api.interfaces.modularui.IGetTitleColor;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.multi.MTEBrickedBlastFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTEBrickedBlastFurnace.class,remap = false)
public abstract class PrimitiveBlastFurnaceMixin extends MetaTileEntity
    implements IAlignment, ISurvivalConstructable, RecipeMapWorkable, IAddUIWidgets, IGetTitleColor {

    public PrimitiveBlastFurnaceMixin(int aID, String aBasicName, String aRegionalName, int aInvSlotCount) {
        super(aID, aBasicName, aRegionalName, aInvSlotCount);
    }

    @Inject(method = "checkRecipe", at = @At("RETURN"))
    private void forceFixedProcessingTime(CallbackInfoReturnable<Boolean> cir) {
        if(MainConfig.PrimitiveBlastFurnaceEnable){
            if (cir.getReturnValue()) {
                ((MTEBrickedBlastFurnace) (Object) this).mMaxProgresstime = 10;
            }
        }
    }
}
