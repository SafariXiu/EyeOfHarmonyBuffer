package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemYuanShiKuang extends Item {

    public ItemYuanShiKuang() {
        super();

        this.setUnlocalizedName("YuanShiKuang");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/YuanShiKuang");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
