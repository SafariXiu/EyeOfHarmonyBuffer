package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.common.tileentities.machines.multi.compressor.MTEHIPCompressor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(value = MTEHIPCompressor.class, remap = false)
public abstract class HIPCompressorMixin extends MTEExtendedPowerMultiBlockBase<MTEHIPCompressor>
    implements ISurvivalConstructable {

    protected HIPCompressorMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Unique
    private static final float LOCKED_HEAT = 50.0f;

    @Inject(method = "onPostTick", at = @At("HEAD"))
    private void disableHeatSystem(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if(MainConfig.HIPCompressorEnable){
            try {
                Field heatField = MTEHIPCompressor.class.getDeclaredField("heat");
                heatField.setAccessible(true);
                heatField.set(this, LOCKED_HEAT);

                Field coolingField = MTEHIPCompressor.class.getDeclaredField("cooling");
                coolingField.setAccessible(true);
                coolingField.set(this, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Inject(method = "getWailaNBTData", at = @At("HEAD"), cancellable = true)
    private void lockHeatForWaila(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y, int z, CallbackInfo ci) {
        if(MainConfig.HIPCompressorEnable){
            tag.setInteger("heat", Math.round(LOCKED_HEAT));
            tag.setBoolean("cooling", false);
            ci.cancel();
        }
    }

    @Inject(method = "saveNBTData", at = @At("TAIL"))
    private void lockHeatForSave(NBTTagCompound aNBT, CallbackInfo ci) {
        if(MainConfig.HIPCompressorEnable){
            aNBT.setFloat("heat", LOCKED_HEAT);
            aNBT.setBoolean("cooling", false);
        }
    }

    @Inject(method = "loadNBTData", at = @At("TAIL"))
    private void lockHeatForLoad(NBTTagCompound aNBT, CallbackInfo ci) {
        if(MainConfig.HIPCompressorEnable){
            try {
                Field heatField = MTEHIPCompressor.class.getDeclaredField("heat");
                heatField.setAccessible(true);
                heatField.set(this, LOCKED_HEAT);

                Field coolingField = MTEHIPCompressor.class.getDeclaredField("cooling");
                coolingField.setAccessible(true);
                coolingField.set(this, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Inject(method = "onRunningTick", at = @At("HEAD"))
    private void disableHeatImpactOnRunning(ItemStack aStack, CallbackInfoReturnable<Boolean> cir) {
        if(MainConfig.HIPCompressorEnable){
            try {
                Field doingHIPField = MTEHIPCompressor.class.getDeclaredField("doingHIP");
                doingHIPField.setAccessible(true);
                doingHIPField.set(this, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
