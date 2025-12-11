package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class ItemHeChengYu extends Item {

    public ItemHeChengYu() {
        super();

        this.setUnlocalizedName("HeChengYu");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":Arknights/HeChengYu");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_HeChengYu_Tooltip_00);
        list.add(EOHB_HeChengYu_Tooltip_01);
    }
}
