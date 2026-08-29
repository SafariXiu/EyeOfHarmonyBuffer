package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemUnactivatedYuanShi extends Item {

    public static Item instance;

    public ItemUnactivatedYuanShi() {
        super();

        this.setUnlocalizedName("UnactivatedYuanShi");
        //this.setTextureName(EyeOfHarmonyBuffer.MODID + ":YuanShi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);

        instance = this;
    }
}
