package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemYuanShiFenMo extends Item {

    public ItemYuanShiFenMo() {
        super();

        this.setUnlocalizedName("YuanShiFenMo");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/YuanShiFenMo");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
