package com.EyeOfHarmonyBuffer.utils;

import gregtech.api.util.MethodsReturnNonnullByDefault;
import gregtech.nei.RecipeDisplayInfo;
import gregtech.nei.formatter.INEISpecialInfoFormatter;
import net.minecraft.util.StatCollector;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleStringSpecialFormatter implements INEISpecialInfoFormatter {

    @Nullable
    private final String translationKey;

    private static final String[] TIER_NAMES = {
        "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UMV", "UXV", "MAX"
    };

    public SimpleStringSpecialFormatter(@Nullable String translationKey) {
        this.translationKey = translationKey;
    }

    @Override
    public List<String> format(RecipeDisplayInfo recipeInfo) {
        int specialValue = recipeInfo.recipe.mSpecialValue;

        int tierIndex = specialValue - 1;

        if (tierIndex < 0 || tierIndex >= TIER_NAMES.length) {
            return Collections.singletonList("Unknown Tier (" + specialValue + ")");
        }

        String tierName = TIER_NAMES[tierIndex];

        if (translationKey != null) {
            return Collections.singletonList(
                StatCollector.translateToLocalFormatted(translationKey, tierName)
            );
        }

        return Collections.singletonList(tierName);
    }
}
