package com.EyeOfHarmonyBuffer.common.Block.Arknights.botany;

import com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.ItemIntermediateProducts;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 实验性水下植物（1.13 海草风格）：
 * 材质伪装成水，相邻水不会在它周围画“水框”；
 * 十字渲染 + 无碰撞，外观接近高版本水下植物。
 * 仅用于测试，后续可替换/删除。
 */
public final class BlockUnderwaterShrub extends BlockBush {

    private static BlockUnderwaterShrub instance;

    private BlockUnderwaterShrub() {
        super(Material.water);
        setBlockName("UnderwaterShrub");
        setBlockTextureName("eyeofharmonybuffer:Arknights/TongHuaGuanMu");
        setCreativeTab(CreativeTabs.tabDecorations);
        setHardness(0.2F);
        setStepSound(soundTypeGrass);
        setBlockBounds(0.2F, 0.0F, 0.2F, 0.8F, 0.7F, 0.8F);
        setTickRandomly(true);
    }

    public static BlockUnderwaterShrub register(String modid) {
        if (instance == null) {
            instance = new BlockUnderwaterShrub();
            GameRegistry.registerBlock(instance, "UnderwaterShrub");
        }
        return instance;
    }

    public static Block getRegistered() {
        return instance;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 1; // 草类十字渲染
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world,
                                                         int x, int y, int z) {
        return null;
    }

    @Override
    protected boolean canPlaceBlockOn(Block ground) {
        return ground == Blocks.sand || ground == Blocks.dirt
            || ground == Blocks.gravel || ground == Blocks.clay
            || ground == Blocks.stone;
    }

    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        if (!canPlaceBlockOn(world.getBlock(x, y - 1, z))) {
            return false;
        }
        // 至少上方或某一侧是水，维持“水下”观感
        return isWater(world, x + 1, y, z)
            || isWater(world, x - 1, y, z)
            || isWater(world, x, y, z + 1)
            || isWater(world, x, y, z - 1)
            || isWater(world, x, y + 1, z);
    }

    private static boolean isWater(World world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        return b == Blocks.water || b == Blocks.flowing_water;
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        Item seed = ItemIntermediateProducts.TongHuaShuZhong;
        return seed != null ? seed : Item.getItemFromBlock(this);
    }
}
