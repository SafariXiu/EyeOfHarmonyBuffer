package com.EyeOfHarmonyBuffer.common.item.items;

import com.EyeOfHarmonyBuffer.common.Block.BlockClass.BlockGlowCasingBase;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * 可点亮机械外壳的 ItemBlock：放置与命名时归一化掉点亮位（meta &amp; 7），
 * 玩家永远无法通过物品放置/获得点亮态。
 */
public class ItemBlockGlowCasing extends ItemBlockCasingsEOH {

    public ItemBlockGlowCasing(Block block) {
        super(block);
    }

    @Override
    public int getMetadata(int damage) {
        return damage & BlockGlowCasingBase.META_MASK;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + (stack.getItemDamage() & BlockGlowCasingBase.META_MASK);
    }
}
