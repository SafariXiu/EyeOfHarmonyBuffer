package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemGaoJingXianWei extends Item {

    public ItemGaoJingXianWei() {
        super();

        this.setUnlocalizedName("GaoJingXianWei");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/GaoJingXianWei");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
