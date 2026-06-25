package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.world.IBlockAccess;

import static com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemIntermediateProducts.ChiTongKuang;

public class BlockChiTong extends Block {

    public BlockChiTong() {
        super(Material.glass);

        this.setHardness(0.3F);
        this.setStepSound(soundTypeGlass);
        this.setLightOpacity(0);
        this.setLightLevel(0.2F);
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
    public int quantityDroppedWithBonus(int fortune, java.util.Random random) {
        if (fortune > 0) {
            int extra = random.nextInt(fortune + 2) - 1;
            if (extra < 0) {
                extra = 0;
            }
            return this.quantityDropped(random) * (extra + 1);
        } else {
            return this.quantityDropped(random);
        }
    }

    @Override
    public Item getItemDropped(int meta, java.util.Random random, int fortune) {
        return ChiTongKuang;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockAccess world,
                                        int x, int y, int z, int side) {
        Block block = world.getBlock(x, y, z);
        return block != this && super.shouldSideBeRendered(world, x, y, z, side);
    }
}
