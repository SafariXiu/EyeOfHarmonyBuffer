package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemWenDingTanKuai extends Item {

    public ItemWenDingTanKuai() {
        super();

        this.setUnlocalizedName("WenDingTanKuai");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/WenDingTanKuai");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
