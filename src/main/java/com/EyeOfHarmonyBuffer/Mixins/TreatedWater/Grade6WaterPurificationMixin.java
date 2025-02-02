package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.machines.multi.purification.*;
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

    @Shadow
    private MTEHatchLensIndicator lensIndicator;

    @Shadow
    private boolean removedTooEarly = false;

    @Shadow
    protected abstract int generateNextSwapTime();

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
        super.runMachine(aBaseMetaTileEntity, aTick);

        if (mMaxProgresstime <= 0) return;

        if (this.lensCycle == null) {
            return;
        }

        ItemStack currentLens = getCurrentlyInsertedLens();

        if (timeUntilNextSwap > 0) {
            timeUntilNextSwap -= 1;
            lensIndicator.updateRedstoneOutput(false);

            if (currentLens == null || !currentLens.isItemEqual(lensCycle.current())) {
                removedTooEarly = true;
            }

            if (timeUntilNextSwap == 0) {
                boolean advanced = lensCycle.advance();
                if (!advanced) {
                    timeUntilNextSwap = mMaxProgresstime + 1;
                }
            }
        }

        else if (timeUntilNextSwap == 0) {
            lensIndicator.updateRedstoneOutput(true);

            if (currentLens != null && currentLens.isItemEqual(lensCycle.current())) {
                numSwapsPerformed += 1;
                timeUntilNextSwap = generateNextSwapTime();
            }
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
