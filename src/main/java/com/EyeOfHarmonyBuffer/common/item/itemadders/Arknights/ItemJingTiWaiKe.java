package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemJingTiWaiKe extends Item {

    public ItemJingTiWaiKe() {
        super();

        this.setUnlocalizedName("JingTiWaiKe");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/JingTiWaiKe");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
