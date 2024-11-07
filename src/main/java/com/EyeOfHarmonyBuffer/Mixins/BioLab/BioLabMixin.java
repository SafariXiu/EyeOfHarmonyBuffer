package com.EyeOfHarmonyBuffer.Mixins.BioLab;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

import bartworks.common.tileentities.tiered.MTEBioLab;
import bartworks.util.BioData;
import gregtech.api.objects.XSTR;

@Mixin(value = MTEBioLab.class, remap = false)
public class BioLabMixin {

    @Redirect(method = "checkRecipe(Z)I", at = @At(value = "INVOKE", target = "Lbartworks/util/BioData;getChance()I"))
    private int redirectGetChance(BioData instance) {
        if (MainConfig.BioLabMixin) {
            return 10000;
        }
        return instance.getChance();
    }

    @Redirect(method = "checkRecipe(Z)I", at = @At(value = "INVOKE", target = "Lgregtech/api/objects/XSTR;nextInt(I)I"))
    private int redirectNextInt(XSTR instance, int bound) {
        if (MainConfig.BioLabMixin) {
            return 0;
        }
        return instance.nextInt(bound);
    }
}
