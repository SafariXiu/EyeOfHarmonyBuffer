package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemShaYeFenMo extends Item {

    public ItemShaYeFenMo() {
        super();

        this.setUnlocalizedName("ShaYeFenMo");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/ShaYeFenMo");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
