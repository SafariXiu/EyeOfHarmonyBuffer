package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import java.util.List;

public class ItemXiRangQi extends Item {

    public ItemXiRangQi() {
        this.setUnlocalizedName("XiRangQi");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/XiRangQi");
        this.setCreativeTab(null);
        this.setMaxStackSize(64);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, @SuppressWarnings("rawtypes") List list) {
    }
}
