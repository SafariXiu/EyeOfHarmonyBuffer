package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.purification.MTEHatchLensHousing;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitUVTreatment;
import gregtech.common.tileentities.machines.multi.purification.UVTreatmentLensCycle;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import static gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitUVTreatment.LENS_ITEMS;

@Mixin(value = MTEPurificationUnitUVTreatment.class, remap = false)
public abstract class Grade6WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitUVTreatment>
    implements ISurvivalConstructable {
    protected Grade6WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Shadow
    private UVTreatmentLensCycle lensCycle = null;

    @Shadow
    private int timeUntilNextSwap = 0;

    @Shadow
    private int numSwapsPerformed = 0;

    @Shadow
    private MTEHatchLensHousing lensInputBus;

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 修改镜片逻辑
     */
    @Overwrite
    protected void runMachine(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if(MainConfig.Grade6WaterPurificationEnabled){
            super.runMachine(aBaseMetaTileEntity, aTick);
            if (mMaxProgresstime <= 0) return;
            if (this.lensCycle == null) {
                this.lensCycle = new UVTreatmentLensCycle(LENS_ITEMS);
            }
            this.timeUntilNextSwap = 0;
            this.numSwapsPerformed += 1;
        }
    }

    /**
     * @author EyeOfHarmonyBuffer
     * @reason 镜片槽为空也可以正常运行
     */
    @Overwrite
    private ItemStack getCurrentlyInsertedLens() {
        if(MainConfig.Grade6WaterPurificationEnabled){
            ItemStack actualLens = this.lensInputBus != null ? this.lensInputBus.getStackInSlot(0) : null;
            return actualLens != null ? actualLens : (!LENS_ITEMS.isEmpty() ? LENS_ITEMS.get(0) : null);
        }
        return this.lensInputBus != null ? this.lensInputBus.getStackInSlot(0) : null;
    }

}
