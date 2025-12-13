package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemXiRang extends Item {

    public ItemXiRang() {
        super();

        this.setUnlocalizedName("XiRang");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/XiRang");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
