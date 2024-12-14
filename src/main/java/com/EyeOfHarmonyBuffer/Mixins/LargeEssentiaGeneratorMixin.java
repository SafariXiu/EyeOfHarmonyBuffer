package com.EyeOfHarmonyBuffer.Mixins;

import com.EyeOfHarmonyBuffer.Mixins.Accessor.TTMultiblockBaseAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import goodgenerator.blocks.tileEntity.MTELargeEssentiaGenerator;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MTELargeEssentiaGenerator.class,remap = false)
public abstract class LargeEssentiaGeneratorMixin extends MTETooltipMultiBlockBaseEM
    implements IConstructable, ISurvivalConstructable {
    protected LargeEssentiaGeneratorMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    /**
     * 修改 checkMachine_EM 方法，允许激光仓兼容
     */
    @Inject(
        method = "checkMachine_EM",
        at = @At("HEAD"),
        cancellable = true
    )
    private void modifyCheckMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, CallbackInfoReturnable<Boolean> cir) {
        MTELargeEssentiaGenerator self = (MTELargeEssentiaGenerator) (Object) this;

        TTMultiblockBaseAccessor accessor = (TTMultiblockBaseAccessor) self;
        int dynamoHatchCount = self.mDynamoHatches.size() + accessor.getEDynamoMulti().size();

        boolean structureValid = self.structureCheck_EM(self.mName, 4, 0, 4);
        boolean hatchTierValid = self.checkHatchTier();
        boolean essentiaHatchStateValid = self.updateEssentiaHatchState();

        if (structureValid && dynamoHatchCount >= 1 && hatchTierValid && essentiaHatchStateValid) {
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }

}
