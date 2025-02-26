package com.EyeOfHarmonyBuffer.common.Machine;

import net.minecraft.item.ItemStack;

import static tectech.thing.CustomItemList.astralArrayFabricator;

public class MiscHelper {

    public static ItemStack ASTRAL_ARRAY_FABRICATOR;

    public static void initStatics(){
        ASTRAL_ARRAY_FABRICATOR = astralArrayFabricator.get(1);
    }
}
