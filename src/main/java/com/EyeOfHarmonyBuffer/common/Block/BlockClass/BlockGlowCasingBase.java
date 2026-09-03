package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.common.Block.CTMHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.GTValues;
import gregtech.common.blocks.BlockCasingsAbstract;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.List;

/**
 * 可点亮机械外壳基类（GT5U 机械外壳模式 + 自研 CTM）。
 *
 * <p>meta 布局：低 3 位 = 变体（0~7），第 3 位（+8）= 点亮标志。
 * meta 0~7 为熄灯态，meta 8~15 为点亮态（点亮 = 替换为发光贴图 + 真实光照 15）。
 * 点亮状态直接存服务端 meta（0~15 合法），由 vanilla 方块更新自动同步客户端。
 *
 * <p>CTM：静态块把本类加入 GT5U 的 CTM 方块名单（GTValues.mCTMEnabledBlock），
 * 使 GT 纹理构建器对本方块走 GTCopiedCTMBlockTexture（世界感知 getIcon）。
 * 正交连接与 BlockCleanGlass 共用 CTMHelper 的 4 方向掩码；
 * 对角连接决定"角吸收"：角并入屏幕仅当两条相邻边都连接 &amp;&amp; 对角也有方块
 * （楼梯形缺少对角时角保留边框，2x2 完整时角变黑）。
 * 贴图按变体放在独立文件夹（getIconBasePath 返回其路径，以 / 结尾），文件夹内命名：
 *   conn_&lt;N&gt;              N=0..15 正交掩码（其中 5,6,7,9,10,11,13,14,15 为"全保留角"态）
 *   conn_&lt;M&gt;_&lt;bits&gt;       角吸收变体：M 同上；bits = 被吸收角（TL=1,TR=2,BL=4,BR=8）
 *   Ligth_conn_*          点亮版同名
 * 子类只需提供 getIconBasePath(variant) 与贴图文件，逻辑全部共享。
 *
 * <p>结构检查请使用接受任意 meta 的元素（如 StructureUtility.ofBlockAnyMeta(block)），
 * 或自行判断 (meta &amp; 7) == 期望变体，否则点亮后会因 meta 不匹配导致结构失效。
 */
public abstract class BlockGlowCasingBase extends BlockCasingsAbstract {

    /** 点亮标志位（meta 第 3 位） */
    public static final int LIT_META_BIT = 8;
    /** 变体掩码（低 3 位） */
    public static final int META_MASK = 7;
    /** 点亮时的方块光照值 */
    public static final int LIGHT_LEVEL_LIT = 15;

    /** GT 方块纹理索引页（EOHB 自用页，避免与 GT/息壤外壳的页冲突） */
    private static final int CASING_PAGE = 31;

    static {
        // 基类入名单：所有子类方块经 GT 纹理构建器自动走世界坐标 getIcon（CTM 生效）
        GTValues.mCTMEnabledBlock.add(BlockGlowCasingBase.class.getName());
    }

    /** 47 格 CTM 贴图数量（16 基础正交 + 31 角吸收变体） */
    private static final int CTM_TILE_COUNT = 47;

    /**
     * 每个正交掩码的可吸收角位（TL=1, TR=2, BL=4, BR=8）。
     * 0 = 该掩码没有相邻边对，不存在角吸收。
     */
    // 角位：TL=1, TR=2, BL=4, BR=8；掩码位：U=1, D=2, L=4, R=8
    // 5=U|L→TL, 6=D|L→BL, 7=U|D|L→TL+BL, 9=U|R→TR, 10=D|R→BR,
    // 11=U|D|R→TR+BR, 13=U|L|R→TL+TR, 14=D|L|R→BL+BR, 15→全
    private static final int[] ABSORBABLE = { 0, 0, 0, 0, 0, 1, 4, 5, 0, 2, 8, 10, 0, 3, 12, 15 };

    /** 变体槽位表：[正交掩码][角吸收位] -&gt; 贴图槽位 0~46，-1 = 非法组合 */
    private static final int[][] SLOT = new int[16][16];

    /** 槽位对应的正交掩码（用于注册贴图名） */
    private static final int[] SLOT_MASK = new int[CTM_TILE_COUNT];
    /** 槽位对应的角吸收位（0 = 全保留态） */
    private static final int[] SLOT_CORNERS = new int[CTM_TILE_COUNT];

    static {
        for (int[] row : SLOT) {
            Arrays.fill(row, -1);
        }
        int slot = 0;
        for (int m = 0; m < 16; m++) {
            SLOT[m][0] = slot;
            SLOT_MASK[slot] = m;
            SLOT_CORNERS[slot] = 0;
            slot++;
        }
        for (int m = 5; m <= 15; m++) {
            int a = ABSORBABLE[m];
            if (a == 0) {
                continue;
            }
            for (int c = 1; c <= 15; c++) {
                if ((c & a) == c) {
                    SLOT[m][c] = slot;
                    SLOT_MASK[slot] = m;
                    SLOT_CORNERS[slot] = c;
                    slot++;
                }
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private IIcon[][] mConnIcons;    // [变体][47] 熄灯连接贴图
    @SideOnly(Side.CLIENT)
    private IIcon[][] mLitConnIcons; // [变体][47] 点亮连接贴图

    protected BlockGlowCasingBase(Class<? extends ItemBlock> aItemClass, String aName) {
        // aMaxMeta=16：为 8 个熄灯变体 + 8 个点亮变体各注册一个纹理索引槽
        super(aItemClass, aName, Material.iron, 16);
    }

    /** 变体数量（1~8），用于创造栏 */
    protected abstract int getVariantCount();

    /**
     * 第 v 个变体的贴图文件夹路径（以 / 结尾），如 "Arknights/HunNingTuDaoXian/"。
     * 基类按约定自动加载文件夹内的 47 张连接贴图（见类注释的命名约定）。
     */
    protected abstract String getIconBasePath(int variant);

    @Override
    public int getTextureIndex(int meta) {
        return (CASING_PAGE << 7) | (meta & 15);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        int count = Math.max(1, Math.min(getVariantCount(), 8));
        mConnIcons = new IIcon[count][CTM_TILE_COUNT];
        mLitConnIcons = new IIcon[count][CTM_TILE_COUNT];
        for (int v = 0; v < count; v++) {
            String base = getIconBasePath(v);
            for (int slot = 0; slot < CTM_TILE_COUNT; slot++) {
                String suffix = SLOT_CORNERS[slot] == 0
                    ? String.valueOf(SLOT_MASK[slot])
                    : SLOT_MASK[slot] + "_" + SLOT_CORNERS[slot];
                mConnIcons[v][slot] = reg.registerIcon("eyeofharmonybuffer:" + base + "conn_" + suffix);
                mLitConnIcons[v][slot] = reg.registerIcon("eyeofharmonybuffer:" + base + "Ligth_conn_" + suffix);
            }
        }
    }

    /** 物品栏/默认：无连接，显示完整边框（conn_0） */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (mConnIcons == null) {
            return null;
        }
        int variant = Math.min(meta & META_MASK, mConnIcons.length - 1);
        IIcon[][] table = (meta & LIT_META_BIT) != 0 ? mLitConnIcons : mConnIcons;
        IIcon icon = table[variant][0];
        return icon != null ? icon : mConnIcons[0][0];
    }

    /** 世界感知取图标（CTM）：经 GT CTM 名单由 GTRendererCasing 调用此重载 */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        if (mConnIcons == null) {
            return null;
        }
        // GTRenderingWorld 会把渲染位置的 meta override 成真实值，邻居位置照常
        int meta = world.getBlockMetadata(x, y, z);
        int variant = Math.min(meta & META_MASK, mConnIcons.length - 1);
        IIcon[][] table = (meta & LIT_META_BIT) != 0 ? mLitConnIcons : mConnIcons;
        int bits = CTMHelper.getNeighborBits(world, x, y, z, side, BlockGlowCasingBase::isConnected);
        int ortho = CTMHelper.getOrthoFromBits(bits);
        int diag = CTMHelper.getCornersFromBits(bits);
        int corners = ABSORBABLE[ortho] & diag;
        int slot = SLOT[ortho][corners];
        if (slot < 0) {
            slot = ortho;
        }
        IIcon icon = table[variant][slot];
        if (icon == null) {
            icon = mConnIcons[0][slot];
        }
        return icon != null ? icon : mConnIcons[0][0];
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return (world.getBlockMetadata(x, y, z) & LIT_META_BIT) != 0 ? LIGHT_LEVEL_LIT : 0;
    }

    @Override
    public int damageDropped(int metadata) {
        return metadata & META_MASK;
    }

    @Override
    public int getDamageValue(World aWorld, int aX, int aY, int aZ) {
        return aWorld.getBlockMetadata(aX, aY, aZ) & META_MASK;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        int count = Math.max(1, Math.min(getVariantCount(), 8));
        for (int i = 0; i < count; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    /** GT 的 CTM 路径用 colorMultiplier 上色：覆写为白色，避免被机器金属灰染色 */
    @Override
    public int colorMultiplier(IBlockAccess aWorld, int aX, int aY, int aZ) {
        return 0xFFFFFF;
    }

    /** 该坐标处是否为点亮态（仅对自身方块有效，其他方块一律 false） */
    public static boolean isLit(World world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockGlowCasingBase
            && (world.getBlockMetadata(x, y, z) & LIT_META_BIT) != 0;
    }

    /**
     * 开关点亮态（仅对自身方块生效）。
     * 状态未变化时不会触碰 world；变化时走完整 setBlock 路径：
     * 1.7.10 的 setBlockMetadataWithNotify 不会触发光照重算（func_147451_t 只在
     * setBlock 完整路径里无条件调用），会导致发光/熄灭时周围光照不更新，
     * 必须用 {@link World#setBlock(int, int, int, Block, int, int)}（flag=2 只发包，
     * 不通知邻居；同方块只改 meta，不会触发放置/破坏钩子）。
     */
    public static void setLit(World world, int x, int y, int z, boolean lit) {
        Block block = world.getBlock(x, y, z);
        if (!(block instanceof BlockGlowCasingBase)) {
            return;
        }
        int meta = world.getBlockMetadata(x, y, z);
        int target = lit ? (meta | LIT_META_BIT) : (meta & META_MASK);
        if (meta != target) {
            world.setBlock(x, y, z, block, target, 2);
        }
    }

    private static boolean isConnected(IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockGlowCasingBase;
    }
}
