package com.EyeOfHarmonyBuffer.Mixins.BioLab;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.modularui.IAddGregtechLogo;
import gregtech.api.interfaces.modularui.IAddUIWidgets;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.IOverclockDescriptionProvider;
import gregtech.api.interfaces.tileentity.RecipeMapWorkable;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.api.metatileentity.implementations.MTEBasicTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MTEBasicMachine.class, remap = false)
public abstract class BasicMachineMixin extends MTEBasicTank implements RecipeMapWorkable, IConfigurationCircuitSupport,
    IOverclockDescriptionProvider, IAddGregtechLogo, IAddUIWidgets {

    public BasicMachineMixin(int aID, String aName, String aNameRegional, int aTier, int aInvSlotCount, String aDescription, ITexture... aTextures) {
        super(aID, aName, aNameRegional, aTier, aInvSlotCount, aDescription, aTextures);
    }

    @Redirect(
        method = "checkRecipe(Z)I",
        at = @At(
            value = "INVOKE",
            target = "Lgregtech/api/interfaces/tileentity/IGregTechTileEntity;getRandomNumber(I)I"
        )
    )
    private int redirectRandomNumber(IGregTechTileEntity instance, int i) {
        if(MainConfig.BioLabMixin){
            return 0;
        }
        return instance.getRandomNumber(i);
    }
}
