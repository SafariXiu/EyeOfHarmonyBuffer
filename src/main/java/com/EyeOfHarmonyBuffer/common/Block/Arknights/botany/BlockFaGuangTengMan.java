package com.EyeOfHarmonyBuffer.common.Block.Arknights.botany;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 发光藤蔓：洞厅风格化用植物，从天花板倒挂向下生长（多段）。
 * - 十字渲染 + 无碰撞；自带 7 级光（发光藤蔓碎片为打碎掉落物）。
 * - 支持块在上方（天花板），可长在任何石头上；下方为空气时随机向下延伸一段。
 * - 断根（上方支撑消失 / 被敲掉）时：下方所有剩余段级联碎掉并掉落为发光藤蔓碎片。
 */
public class BlockFaGuangTengMan extends BlockBush {

    /** 单串藤蔓最长段数（防止无限生长）。 */
    private static final int MAX_SEGMENTS = 6;
    /** 每个随机刻向下生长的概率。 */
    private static final float GROW_CHANCE = 0.25F;

    public BlockFaGuangTengMan() {
        super(Material.plants);
        setHardness(0.2F);
        setStepSound(soundTypeGrass);
        setBlockBounds(0.2F, 0.0F, 0.2F, 0.8F, 0.8F, 0.8F);
        setLightLevel(7.0F / 15.0F); // 7 级光
        setTickRandomly(true);       // 驱动向下生长
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

    /** 支撑块（上方）：任意石头 / 沙 / 泥土 / 沙砾等，或另一段发光藤蔓。 */
    @Override
    protected boolean canPlaceBlockOn(Block ground) {
        return ground == Blocks.stone || ground == Blocks.cobblestone
            || ground == Blocks.mossy_cobblestone
            || ground == Blocks.stonebrick
            || ground == Blocks.sandstone
            || ground == Blocks.dirt || ground == Blocks.grass
            || ground == Blocks.gravel || ground == Blocks.sand
            || ground == Blocks.netherrack;
    }

    /** 挂在天花板（上方）上：上方是石头或另一段藤蔓即可。 */
    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        Block above = world.getBlock(x, y + 1, z);
        return above == this || canPlaceBlockOn(above);
    }

    @Override
    public void updateTick(World world, int x, int y, int z, Random rand) {
        // 1) 支撑丢失：整串碎掉掉落
        if (!this.canBlockStay(world, x, y, z)) {
            this.dropBlockAsItem(world, x, y, z,
                world.getBlockMetadata(x, y, z), 0);
            world.setBlockToAir(x, y, z);
            return;
        }
        // 2) 向下生长：下方为空气、未超过最长、随机触发
        if (world.isAirBlock(x, y - 1, z)
            && countDown(world, x, y, z) < MAX_SEGMENTS
            && rand.nextFloat() < GROW_CHANCE) {
            world.setBlock(x, y - 1, z, this, 0, 2);
        }
    }

    /** 统计该串藤蔓已占用的向下段数（含本段）。 */
    private int countDown(World world, int x, int y, int z) {
        int n = 1;
        int yy = y;
        while (world.getBlock(x, yy - 1, z) == this) {
            n++;
            yy--;
            if (n > MAX_SEGMENTS) {
                break;
            }
        }
        return n;
    }

    /**
     * 段被敲掉 / 被移除时：下方所有剩余段级联碎掉并掉落为发光藤蔓碎片。
     * 注意：本格的掉落由 harvestBlock / BlockBush.checkAndDropBlock 负责
     * （避免玩家敲掉的当前格掉两次碎片），这里只级联处理下方段；
     * setBlockToAir 会再次触发本方法，天然向下级联。
     */
    @Override
    public void breakBlock(World world, int x, int y, int z,
                           Block block, int meta) {
        super.breakBlock(world, x, y, z, block, meta);
        if (world.isRemote) {
            return;
        }
        breakBelow(world, x, y - 1, z);
    }

    private void breakBelow(World world, int x, int y, int z) {
        if (world.getBlock(x, y, z) != this) {
            return;
        }
        // 下方段不是玩家直接敲掉的，需要手动掉落后再移除
        // （移除会触发下一段的 breakBlock 继续向下级联）。
        dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
        world.setBlockToAir(x, y, z);
    }

    @Override
    public Item getItemDropped(int meta, Random rand, int fortune) {
        Item frag = GTCMItemList.FaGuangTengManSuPian.getItem();
        return frag != null ? frag : Item.getItemFromBlock(this);
    }
}
