package com.EyeOfHarmonyBuffer.utils;

import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FoodHelper {

    private static List<ItemStack> CACHED_FOODS = null;

    public static List<ItemStack> getAllFoods() {
        if (CACHED_FOODS == null) {
            CACHED_FOODS = collectAllFoods();
        }
        return CACHED_FOODS;
    }

    private static List<ItemStack> collectAllFoods() {
        List<ItemStack> result = new ArrayList<ItemStack>();

        @SuppressWarnings("unchecked")
        Iterable<Item> allItems = (Iterable<Item>) Item.itemRegistry;

        for (Item item : allItems) {
            if (item instanceof ItemFood) {
                result.add(new ItemStack(item, 1, 0));
            }
        }

        return result;
    }

    public static void reload() {
        CACHED_FOODS = null;
    }
}
