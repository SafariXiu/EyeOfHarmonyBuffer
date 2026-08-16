package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 纯色发光方块的物品：保留 meta（0-15），每个变体独立显示名与图标，
 * 保证 NEI/创造页拿取时 meta 正确。
 */
public class ItemBlockPureGlow extends ItemBlock {

    public ItemBlockPureGlow(Block block) {
        super(block);
        this.setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int damage) {
        return damage & 15;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + (stack.getItemDamage() & 15);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return this.field_150939_a.getIcon(0, stack.getItemDamage() & 15);
    }
}
