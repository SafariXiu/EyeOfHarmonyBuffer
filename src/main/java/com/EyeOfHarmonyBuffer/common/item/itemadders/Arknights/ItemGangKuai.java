package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemGangKuai extends Item {

    public ItemGangKuai() {
        super();

        this.setUnlocalizedName("GangKuai");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/GangKuai");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
