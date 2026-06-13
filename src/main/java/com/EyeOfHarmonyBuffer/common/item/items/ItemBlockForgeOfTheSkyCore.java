package com.EyeOfHarmonyBuffer.common.item.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_ForgeOfTheSkyCore_Tooltip_00;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_ForgeOfTheSkyCore_Tooltip_01;

public class ItemBlockForgeOfTheSkyCore extends ItemBlock {

    public ItemBlockForgeOfTheSkyCore(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_ForgeOfTheSkyCore_Tooltip_00);
        list.add(EOHB_ForgeOfTheSkyCore_Tooltip_01);
    }
}
