package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemZiJingXianWei extends Item {

    public ItemZiJingXianWei() {
        super();

        this.setUnlocalizedName("ZiJingXianWei");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/ZiJingXianWei");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
