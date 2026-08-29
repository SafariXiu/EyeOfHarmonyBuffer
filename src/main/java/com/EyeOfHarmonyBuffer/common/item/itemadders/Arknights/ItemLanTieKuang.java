package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemLanTieKuang extends Item {

    public ItemLanTieKuang() {
        super();

        this.setUnlocalizedName("LanTieKuang");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/LanTieKuang");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
