package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemLanTieFenMo extends Item {

    public ItemLanTieFenMo() {
        super();

        this.setUnlocalizedName("LanTieFenMo");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/LanTieFenMo");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
