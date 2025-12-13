package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemLanTieKuai extends Item {

    public ItemLanTieKuai() {
        super();

        this.setUnlocalizedName("LanTieKuai");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/LanTieKuai");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
