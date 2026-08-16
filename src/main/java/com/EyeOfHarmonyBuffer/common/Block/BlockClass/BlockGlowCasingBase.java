package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.common.Block.CTMHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.GTValues;
import gregtech.common.blocks.BlockCasingsAbstract;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/**
 * 可点亮机械外壳基类（GT5U 机械外壳模式 + 自研 CTM）。
 *
 * <p>meta 布局：低 3 位 = 变体（0~7），第 3 位（+8）= 点亮标志。
 * meta 0~7 为熄灯态，meta 8~15 为点亮态（点亮 = 替换为发光贴图 + 真实光照 15）。
 * 点亮状态直接存服务端 meta（0~15 合法），由 vanilla 方块更新自动同步客户端。
 *
 * <p>CTM：静态块把本类加入 GT5U 的 CTM 方块名单（GTValues.mCTMEnabledBlock），
 * 使 GT 纹理构建器对本方块走 GTCopiedCTMBlockTexture（世界感知 getIcon），
 * 与 BlockCleanGlass 共用 CTMHelper 的 4 方向掩码机制。
 * 贴图命名约定（每变体 32 张）：
 *   &lt;base&gt;_conn_0..15       熄灯 16 张连接变体（0=无连接全边框，15=全连接无边框）
 *   &lt;base&gt;_Ligth_conn_0..15  点亮 16 张
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

    @SideOnly(Side.CLIENT)
    private IIcon[][] mConnIcons;    // [变体][16] 熄灯连接贴图
    @SideOnly(Side.CLIENT)
    private IIcon[][] mLitConnIcons; // [变体][16] 点亮连接贴图

    protected BlockGlowCasingBase(Class<? extends ItemBlock> aItemClass, String aName) {
        // aMaxMeta=16：为 8 个熄灯变体 + 8 个点亮变体各注册一个纹理索引槽
        super(aItemClass, aName, Material.iron, 16);
    }

    /** 变体数量（1~8），用于创造栏 */
    protected abstract int getVariantCount();

    /**
     * 第 v 个变体的贴图基础路径（不含 _conn 后缀），如 "Arknights/HunNingTuDaoXian"。
     * 基类按约定自动加载 &lt;base&gt;_conn_0..15 与 &lt;base&gt;_Ligth_conn_0..15。
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
        mConnIcons = new IIcon[count][16];
        mLitConnIcons = new IIcon[count][16];
        for (int v = 0; v < count; v++) {
            String base = getIconBasePath(v);
            for (int i = 0; i < 16; i++) {
                mConnIcons[v][i] = reg.registerIcon("eyeofharmonybuffer:" + base + "_conn_" + i);
                mLitConnIcons[v][i] = reg.registerIcon("eyeofharmonybuffer:" + base + "_Ligth_conn_" + i);
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
        int mask = CTMHelper.getConnectionMask(world, x, y, z, side, BlockGlowCasingBase::isConnected);
        IIcon icon = table[variant][mask];
        return icon != null ? icon : mConnIcons[0][mask];
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
     * 状态未变化时不会触碰 world；变化时以 flag=2 同步客户端并触发光照重算。
     */
    public static void setLit(World world, int x, int y, int z, boolean lit) {
        if (!(world.getBlock(x, y, z) instanceof BlockGlowCasingBase)) {
            return;
        }
        int meta = world.getBlockMetadata(x, y, z);
        int target = lit ? (meta | LIT_META_BIT) : (meta & META_MASK);
        if (meta != target) {
            world.setBlockMetadataWithNotify(x, y, z, target, 2);
        }
    }

    private static boolean isConnected(IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockGlowCasingBase;
    }
}
