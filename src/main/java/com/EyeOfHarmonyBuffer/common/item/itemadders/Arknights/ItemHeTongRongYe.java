package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import java.util.List;

public class ItemHeTongRongYe extends Item {

    public ItemHeTongRongYe() {
        super();

        setUnlocalizedName("HeTongRongYe");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/HeTongRongYe");
        this.setCreativeTab(null);
        this.setMaxStackSize(64);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, @SuppressWarnings("rawtypes") List list) {
    }
}
