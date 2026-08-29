package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemTanFenMo extends Item {

    public ItemTanFenMo() {
        super();

        this.setUnlocalizedName("TanFenMo");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/TanFenMo");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
