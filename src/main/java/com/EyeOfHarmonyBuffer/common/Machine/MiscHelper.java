package com.EyeOfHarmonyBuffer.common.Machine;

import cpw.mods.fml.common.Loader;
import net.minecraft.item.ItemStack;

import static com.newmaa.othtech.common.OTHItemList.ingotHotDog;
import static tectech.thing.CustomItemList.astralArrayFabricator;

public class MiscHelper {

    public static ItemStack ASTRAL_ARRAY_FABRICATOR;
    public static ItemStack BlueDogItem;

    public static void initStatics(){
        ASTRAL_ARRAY_FABRICATOR = astralArrayFabricator.get(1);
        if(Loader.isModLoaded("123Technology")){
            BlueDogItem = ingotHotDog.get(1);
        }

    }
}
