package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemTanKuai extends Item {

    public ItemTanKuai() {
        super();

        this.setUnlocalizedName("TanKuai");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/TanKuai");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
