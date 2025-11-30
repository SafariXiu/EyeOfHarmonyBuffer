package com.EyeOfHarmonyBuffer.utils;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import squeek.applecore.api.AppleCoreAPI;
import squeek.applecore.api.food.FoodValues;

import java.util.ArrayList;
import java.util.List;

public class FoodHelper {

    private static List<ItemStack> CACHED_FOODS = null;

    public static List<ItemStack> getAllFoods() {
        if (CACHED_FOODS == null)
        {
            CACHED_FOODS = collectAllFoods();
        }
        return CACHED_FOODS;
    }

    private static List<ItemStack> collectAllFoods() {
        List<ItemStack> result = new ArrayList<ItemStack>();

        @SuppressWarnings("unchecked")
        Iterable<Item> allItems = (Iterable<Item>) Item.itemRegistry;

        for (Item item : allItems)
        {
            if (item == null)
                continue;

            List<ItemStack> subItems = new ArrayList<ItemStack>();

            try
            {
                item.getSubItems(item, (CreativeTabs) null, subItems);
            }
            catch (Throwable t)
            {
                subItems.clear();
            }

            if (subItems.isEmpty())
            {
                ItemStack defaultStack = new ItemStack(item, 1, 0);
                if (isFood(defaultStack))
                {
                    result.add(defaultStack);
                }
            }
            else
            {
                for (ItemStack sub : subItems)
                {
                    if (sub == null)
                        continue;

                    if (isFood(sub))
                    {
                        result.add(sub);
                    }
                }
            }
        }

        return result;
    }

    public static void reload() {
        CACHED_FOODS = null;
    }

    public static boolean isFood(ItemStack stack) {
        if (stack == null)
            return false;

        try
        {
            return AppleCoreAPI.accessor.isFood(stack);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    public static int getHunger(ItemStack stack) {
        if (!isFood(stack))
            return 0;

        FoodValues values = AppleCoreAPI.accessor.getFoodValues(stack);
        return values.hunger;
    }

    public static float getSaturation(ItemStack stack) {
        if (!isFood(stack))
            return 0F;

        FoodValues values = AppleCoreAPI.accessor.getFoodValues(stack);
        return values.hunger * values.saturationModifier * 2F;
    }

    public static float getSaturationModifier(ItemStack stack) {
        if (!isFood(stack))
            return 0F;

        FoodValues values = AppleCoreAPI.accessor.getFoodValues(stack);
        return values.saturationModifier;
    }
}
