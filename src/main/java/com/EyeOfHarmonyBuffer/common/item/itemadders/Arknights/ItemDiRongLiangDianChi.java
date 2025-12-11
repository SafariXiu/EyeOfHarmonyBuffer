package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemDiRongLiangDianChi extends Item {

    public ItemDiRongLiangDianChi() {
        this.setUnlocalizedName("DiRongLiangDianChi");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/DiRongLiangDianChi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
