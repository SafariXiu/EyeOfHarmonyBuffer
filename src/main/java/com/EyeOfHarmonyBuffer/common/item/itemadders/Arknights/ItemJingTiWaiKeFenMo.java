package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemJingTiWaiKeFenMo extends Item {

    public ItemJingTiWaiKeFenMo() {
        super();

        this.setUnlocalizedName("JingTiWaiKeFenMo");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/JingTiWaiKeFenMo");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
