package com.EyeOfHarmonyBuffer.Mixins.EOH;

import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.MTEHatchOutputME;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

import static gregtech.common.tileentities.machines.multi.MTEFusionComputer.STRUCTURE_PIECE_MAIN;

@Mixin(value = MTEEyeOfHarmony.class, remap = false)
public abstract class EyeOfHarmonyBus extends TTMultiblockBase implements IConstructable, ISurvivalConstructable {

    @Shadow
    private int stabilisationFieldMetadata;

    @Shadow
    private int timeAccelerationFieldMetadata;

    @Shadow
    private int spacetimeCompressionFieldMetadata;

    protected EyeOfHarmonyBus(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 覆写EOH的舱室检测逻辑
     */
    @Overwrite
    public boolean checkMachine_EM(IGregTechTileEntity iGregTechTileEntity, ItemStack itemStack) {

        spacetimeCompressionFieldMetadata = -1;
        timeAccelerationFieldMetadata = -1;
        stabilisationFieldMetadata = -1;

        if (!structureCheck_EM(STRUCTURE_PIECE_MAIN, 16, 16, 0)) {
            return false;
        }

        if (!mDualInputHatches.isEmpty()) {
            return false;
        }

        {
            if (mOutputBusses.size() != 1) {
                return false;
            }

            if (!(mOutputBusses.get(0) instanceof MTEHatchOutputBusME)) {
                return false;
            }
        }

        {
            if (mOutputHatches.size() != 1) {
                return false;
            }

            if (!(mOutputHatches.get(0) instanceof MTEHatchOutputME)) {
                return false;
            }
        }

        {
            if (mInputBusses.size() != 1) {
                return false;
            }

            if (mInputBusses.get(0) instanceof MTEHatchInputBusME) {
                return MainConfig.EOHinputBusMe;
            }
        }

        {
            if (!mEnergyHatches.isEmpty()) {
                return false;
            }

            if (!mExoticEnergyHatches.isEmpty()) {
                return false;
            }
        }
        if(MainConfig.EOHinputHatchEnable){
            return mInputHatches.size() <= 2;
        }
        return mInputHatches.size() == 2;

    }
}
