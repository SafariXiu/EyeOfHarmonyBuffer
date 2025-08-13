package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import gregtech.api.interfaces.IChunkLoader;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gtnhintergalactic.tile.multi.TileEntityPlanetaryGasSiphon;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntityPlanetaryGasSiphon.class,remap = false)
public abstract class PlanetaryGasSiphonMixin extends MTEEnhancedMultiBlockBase<TileEntityPlanetaryGasSiphon>
    implements IChunkLoader {

    protected PlanetaryGasSiphonMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Inject(method = "checkProcessing", at = @At("TAIL"))
    private void modifyOutputFluid(CallbackInfoReturnable<CheckRecipeResult> cir) {
        if(MainConfig.PlanetaryGasSiphonEnable){
            TileEntityPlanetaryGasSiphon siphon = (TileEntityPlanetaryGasSiphon) (Object) this;

            if (siphon.mOutputFluids != null && siphon.mOutputFluids.length > 0) {
                for (FluidStack fluid : siphon.mOutputFluids) {
                    if (fluid != null) {
                        fluid.amount *= MainConfig.PlanetaryGasSiphonParallel;
                    }
                }
            }
        }
    }
}
