package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemMiZhiJingTi extends Item {

    public ItemMiZhiJingTi() {
        super();

        this.setUnlocalizedName("MiZhiJingTi");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/MiZhiJingTi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
