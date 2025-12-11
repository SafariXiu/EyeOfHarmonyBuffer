package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemGaoRongLiangDianChi extends Item {

    public ItemGaoRongLiangDianChi() {
        this.setUnlocalizedName("GaoRongLiangDianChi");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/GaoRongLiangDianChi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
