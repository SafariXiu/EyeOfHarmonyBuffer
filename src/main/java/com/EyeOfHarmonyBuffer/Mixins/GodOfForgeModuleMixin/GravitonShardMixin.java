package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.EyeOfHarmonyBuffer.Mixins.Accessor.FOGAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.MaterialsUEVplus;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;
import tectech.thing.metaTileEntity.multi.godforge.MTEForgeOfGods;

@Mixin(value = MTEForgeOfGods.class, remap = false)
public abstract class GravitonShardMixin extends TTMultiblockBase implements IConstructable, ISurvivalConstructable {

    protected GravitonShardMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int gravitonShardsAvailable;

    @Inject(method = "ejectGravitonShards", at = @At("HEAD"), cancellable = true)
    private void modifyGravitonShardEjection(CallbackInfo ci) {
        if(MainConfig.FOGGravitonShardEnable){
            MTEForgeOfGods self = (MTEForgeOfGods) (Object) this;

            int gravitonShardsAvailable = ((FOGAccessor) self).getGravitonShardsAvailable();

            if (self.mOutputBusses.size() == 1) {
                while (gravitonShardsAvailable >= 64) {
                    self.addOutput(GTOreDictUnificator.get(OrePrefixes.gem, MaterialsUEVplus.GravitonShard, 64));
                }
                self.addOutput(GTOreDictUnificator.get(OrePrefixes.gem, MaterialsUEVplus.GravitonShard, gravitonShardsAvailable));
            }

            ci.cancel();
        }
    }
}
