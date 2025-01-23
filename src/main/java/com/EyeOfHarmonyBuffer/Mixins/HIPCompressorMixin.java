package com.EyeOfHarmonyBuffer.Mixins;

import bartworks.util.MathUtils;
import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.common.tileentities.machines.multi.compressor.MTEHIPCompressor;
import gregtech.common.tileentities.machines.multi.compressor.MTEHeatSensor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.ArrayList;

@Mixin(value = MTEHIPCompressor.class, remap = false)
public abstract class HIPCompressorMixin extends MTEExtendedPowerMultiBlockBase<MTEHIPCompressor>
    implements ISurvivalConstructable {

    protected HIPCompressorMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private int coilTier = 0;

    @Shadow
    private int coolingTimer = 0;

    @Shadow
    private float heat = 0;

    @Shadow
    private boolean overheated = false;

    @Shadow
    private final ArrayList<MTEHeatSensor> sensorHatches = new ArrayList<>();

    @Inject(method = "onPostTick", at = @At("HEAD"), cancellable = true)
    private void injectOnPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick, CallbackInfo ci) {
        if(MainConfig.HIPCompressorEnable){
            if (aTick % 20 == 0 && !aBaseMetaTileEntity.isClientSide()) {
                heat = 0;
                coolingTimer = 0;
                overheated = false;

                for (MTEHeatSensor hatch : sensorHatches) {
                    hatch.updateRedstoneOutput(heat);
                }

                ci.cancel();
            }
        }
    }
}
