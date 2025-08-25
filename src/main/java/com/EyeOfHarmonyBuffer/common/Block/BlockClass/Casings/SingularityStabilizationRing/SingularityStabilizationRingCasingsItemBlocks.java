package com.EyeOfHarmonyBuffer.common.Block.BlockClass.Casings.SingularityStabilizationRing;

import com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs;
import com.EyeOfHarmonyBuffer.common.Block.BlockClass.ItemBlockBase01;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.List;

public class SingularityStabilizationRingCasingsItemBlocks extends ItemBlockBase01 {

    public SingularityStabilizationRingCasingsItemBlocks(Block aBlock) {
        super(aBlock);
        this.setCreativeTab(EOHBCreativeTabs.TAB_META_BLOCKS);
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings({ "unchecked" })
    public void addInformation(ItemStack aItemStack, EntityPlayer p_77624_2_, List theTooltipsList,
                               boolean p_77624_4_) {
        if (null == aItemStack) return;

        theTooltipsList.add(mNoMobsToolTip);
        theTooltipsList.add(mNoTileEntityToolTip);
    }
}
