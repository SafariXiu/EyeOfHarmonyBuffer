package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;

public class BlockDuoQiMain extends Block {

    public BlockDuoQiMain() {
        super(Material.glass);

        this.setBlockUnbreakable();
        this.setResistance(6000000.0F);
        this.setStepSound(soundTypeGlass);
        this.setLightOpacity(0);
        this.setLightLevel(0.8F);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getRenderBlockPass() {
        return 1;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return true;
    }

    @Override
    public int quantityDropped(java.util.Random random) {
        return 0;
    }

    @Override
    public Item getItemDropped(int meta, java.util.Random random, int fortune) {
        return null;
    }

    @Override
    protected boolean canSilkHarvest() {
        return false;
    }

    @Override
    public boolean shouldSideBeRendered(net.minecraft.world.IBlockAccess world,
                                        int x, int y, int z, int side) {
        Block block = world.getBlock(x, y, z);
        return block != this && super.shouldSideBeRendered(world, x, y, z, side);
    }
}
