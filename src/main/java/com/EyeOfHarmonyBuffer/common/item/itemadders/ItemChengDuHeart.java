package com.EyeOfHarmonyBuffer.common.item.itemadders;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;

public class ItemChengDuHeart extends Item {

    public ItemChengDuHeart() {
        super();

        this.setUnlocalizedName(EOHB_ChengDuHeart);
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":ChengDuHeart");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(64);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_ChengDuHeart_Tooltip_01);
        list.add(EOHB_ChengDuHeart_Tooltip_02);
    }
}
