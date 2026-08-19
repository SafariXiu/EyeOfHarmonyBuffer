package com.EyeOfHarmonyBuffer.Mixins.GodOfForgeModuleMixin;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.ItemEjectionHelper;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;
import tectech.thing.metaTileEntity.multi.godforge.MTEForgeOfGods;
import tectech.thing.metaTileEntity.multi.godforge.util.ForgeOfGodsData;

@Mixin(value = MTEForgeOfGods.class, remap = false)
public abstract class GravitonShardMixin extends TTMultiblockBase implements IConstructable, ISurvivalConstructable {

    protected GravitonShardMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private ForgeOfGodsData data;

    @Inject(method = "ejectGravitonShards", at = @At("HEAD"), cancellable = true)
    private void modifyGravitonShardEjection(CallbackInfo ci) {
        if (!MainConfig.FOGGravitonShardEnable) {
            return;
        }

        if (this.mOutputBusses.size() == 1) {
            int available = this.data.getGravitonShardsAvailable();
            if (available <= 0) {
                ci.cancel();
                return;
            }

            ItemStack shard = GTOreDictUnificator.get(OrePrefixes.gem, Materials.GravitonShard, 1L);
            shard.stackSize = available;

            ItemEjectionHelper ejectionHelper = new ItemEjectionHelper(this.getOutputBusses(), true);
            ejectionHelper.ejectStack(shard);
            ejectionHelper.commit();

        }

        ci.cancel();
    }
}
