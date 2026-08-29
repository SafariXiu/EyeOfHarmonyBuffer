package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemZiJingKuang extends Item {

    public ItemZiJingKuang() {
        super();

        this.setUnlocalizedName("ZiJingKuang");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/ZiJingKuang");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
