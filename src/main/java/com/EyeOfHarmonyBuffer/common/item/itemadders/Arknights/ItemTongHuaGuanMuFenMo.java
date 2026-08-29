package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.item.Item;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemTongHuaGuanMuFenMo extends Item {

    public ItemTongHuaGuanMuFenMo() {
        super();

        this.setUnlocalizedName("TongHuaGuanMuFenMo");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/TongHuaGuanMuFenMo");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }
}
