package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;

public class ItemIntermediateProducts {

    public static Item ChunJingYuanShiFenMo;
    public static Item DiChunYuanShiFenMo;
    public static Item YuanShiJingHe;

    public static void initAndRegister(String modid) {
        String textureFolder = "Arknights/";

        try {
            for (Field field : ItemIntermediateProducts.class.getDeclaredFields()) {
                if (Item.class.isAssignableFrom(field.getType())) {
                    String name = field.getName();
                    Item item = (Item) field.getType().newInstance();

                    item.setUnlocalizedName(name)
                        .setTextureName(modid + ":" + textureFolder + name);

                    GameRegistry.registerItem(item, name);
                    field.set(null, item);

                    try {
                        GTCMItemList enumEntry = GTCMItemList.valueOf(name);
                        enumEntry.set(new ItemStack(item));
                        System.out.println("[ItemIntermediateProducts] 已注册到 GTCMItemList: " + name);
                    } catch (IllegalArgumentException ignore) {
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
