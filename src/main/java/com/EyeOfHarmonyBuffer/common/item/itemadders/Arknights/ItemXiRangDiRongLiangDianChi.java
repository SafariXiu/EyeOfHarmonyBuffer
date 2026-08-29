package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemXiRangDiRongLiangDianChi extends Item {

    public ItemXiRangDiRongLiangDianChi() {
        this.setUnlocalizedName("XiRangDiRongLiangDianChi");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/XiRangDiRongLiangDianChi");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
