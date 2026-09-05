package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.utils.TextLocalization;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_YaZhenZhenJi_00;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_YaZhenZhenJi_01;

public class ItemYaZhenZhenJi extends Item {

    public ItemYaZhenZhenJi() {
        this.setUnlocalizedName("YaZhenZhenJi");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/YaZhenZhenJi");
        this.setMaxStackSize(16);
        this.setCreativeTab(tabMetaItem01);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_YaZhenZhenJi_00);
        list.add(EOHB_YaZhenZhenJi_01);
    }
}
