package com.EyeOfHarmonyBuffer.Mixins.TreatedWater;

import com.EyeOfHarmonyBuffer.Config.MainConfig;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitBase;
import gregtech.common.tileentities.machines.multi.purification.MTEPurificationUnitOzonation;
import journeymap.shadow.org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = MTEPurificationUnitOzonation.class, remap = false)
public abstract class Grade2WaterPurificationMixin extends MTEPurificationUnitBase<MTEPurificationUnitOzonation>
    implements ISurvivalConstructable {
    protected Grade2WaterPurificationMixin(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    /**
     * @author eyeofharmonybuffer
     * @reason 修改判断逻辑，移除臭氧检查确保每次运行成功
     */
    @Overwrite
    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        if(MainConfig.Grade2WaterPurificationEnabled){
            CheckRecipeResult result = super.checkProcessing();
            if (!result.wasSuccessful()) {
                return result;
            }
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }
        return super.checkProcessing();
    }
}
