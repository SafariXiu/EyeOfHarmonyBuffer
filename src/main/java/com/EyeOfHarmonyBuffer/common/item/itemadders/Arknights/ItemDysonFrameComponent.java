package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

import net.minecraft.item.Item;

/** 戴森框架组件（占位物品）。 */
public class ItemDysonFrameComponent extends Item {

    public ItemDysonFrameComponent() {
        setUnlocalizedName("DysonFrameComponent");
        setCreativeTab(tabMetaItem01);
        setMaxStackSize(64);
    }
}
