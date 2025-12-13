package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemZiJingFenMo extends Item {

    public ItemZiJingFenMo() {
        super();

        this.setUnlocalizedName("ZiJingFenMo");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/ZiJingFenMo");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
