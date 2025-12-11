package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemZhongRongLiangDianChi extends Item {

    public ItemZhongRongLiangDianChi() {
        this.setUnlocalizedName("ZhongRongLiangDianChi");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/ZhongRongLiangDianChi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
