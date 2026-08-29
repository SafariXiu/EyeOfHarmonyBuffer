package com.EyeOfHarmonyBuffer.utils;

import gregtech.api.util.MethodsReturnNonnullByDefault;
import gregtech.nei.RecipeDisplayInfo;
import gregtech.nei.formatter.INEISpecialInfoFormatter;
import net.minecraft.util.StatCollector;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FixedStringSpecialFormatter implements INEISpecialInfoFormatter {

    @Nullable
    private final String translationKeyOrLiteral;

    public FixedStringSpecialFormatter(@Nullable String translationKeyOrLiteral) {
        this.translationKeyOrLiteral = translationKeyOrLiteral;
    }

    @Override
    public List<String> format(RecipeDisplayInfo recipeInfo) {
        if (translationKeyOrLiteral == null || translationKeyOrLiteral.isEmpty()) {
            return Collections.emptyList();
        }

        String localized = StatCollector.translateToLocal(translationKeyOrLiteral);
        if (localized.equals(translationKeyOrLiteral)) {
            return Collections.singletonList(translationKeyOrLiteral);
        } else {
            return Collections.singletonList(localized);
        }
    }
}
