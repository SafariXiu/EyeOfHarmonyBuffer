package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * RBMK 控制棒 / 燃料管方块（多面贴图）。
 * <p>
 * 顶面按类型使用独立纹理（控制棒端头 / 燃料管顶部），
 * 四边共用 {@code Bang_CeMian}，底面共用 {@code Bang_DiMian}。
 * 同一种方块只需用不同的顶部纹理名构造即可得到不同种类。
 * <p>
 * 燃料通道三件套（顶部燃料管 / 中段石墨管道 / 底部燃料管底座）构成
 * 8 格竖直结构，成型后不再按普通方块渲染，而是由基座 TileEntity 的
 * TESR 绘制整根 {@code FuelTube} 模型（见 RbmkFuelChannelHelper / RbmkFuelChannelBlockRenderer）。
 */
public class BlockRBMKRod extends Block {

    /** 自定义渲染 ID（客户端初始化时由 ClientProxy 设置）。 */
    public static int renderId = -1;

    /** 方块在燃料通道中的角色。 */
    public enum Role {
        /** 普通方块（无结构）：始终按普通方块渲染 */
        NORMAL,
        /** 通道顶部（普通燃料管） */
        FUEL_CHANNEL_TOP,
        /** 通道顶部（控制棒：标准/自动/特殊调节等），成型后由基座 TESR 绘制对应贴图的管子 */
        CONTROL_ROD_TOP,
        /** 通道中段（反应堆内部石墨管道） */
        FUEL_CHANNEL_PIPE,
        /** 通道底部（燃料管底座，持有 TE + TESR） */
        FUEL_CHANNEL_BASE
    }

    /** 顶面纹理资源名（相对 textures/blocks/Arknights/rbmk/） */
    protected final String topTexture;
    /** 底面纹理资源名（相对 textures/blocks/Arknights/rbmk/），默认共用 Bang_DiMian */
    protected final String bottomTexture;
    /** 侧面纹理资源名（相对 textures/blocks/Arknights/rbmk/），默认共用 Bang_CeMian */
    protected final String sideTexture;
    /** 燃料通道角色 */
    protected final Role role;
    /** 作为通道顶部时，整根管子的模型贴图完整资源路径（空串则 TESR 回退 FuelTube.png） */
    protected final String modelTexture;

    protected IIcon iconTop;
    protected IIcon iconSide;
    protected IIcon iconBottom;

    public BlockRBMKRod(String topTexture) {
        this(topTexture, "Bang_DiMian", "Bang_CeMian", Role.NORMAL);
    }

    public BlockRBMKRod(String topTexture, String bottomTexture) {
        this(topTexture, bottomTexture, "Bang_CeMian", Role.NORMAL);
    }

    public BlockRBMKRod(String topTexture, String bottomTexture, String sideTexture) {
        this(topTexture, bottomTexture, sideTexture, Role.NORMAL);
    }

    public BlockRBMKRod(String topTexture, String bottomTexture, String sideTexture, Role role) {
        this(topTexture, bottomTexture, sideTexture, role, "");
    }

    public BlockRBMKRod(String topTexture, String bottomTexture, String sideTexture, Role role, String modelTexture) {
        super(Material.iron);
        this.topTexture = topTexture;
        this.bottomTexture = bottomTexture;
        this.sideTexture = sideTexture;
        this.role = role;
        this.modelTexture = modelTexture;
        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
    }

    public Role getRole() {
        return role;
    }

    /** 作为通道顶部时的整根管子模型贴图（完整 ResourceLocation 字符串）；空串回退 FuelTube.png。 */
    public String getModelTexture() {
        return modelTexture;
    }

    // ==================== 通道结构公共接口（客户端渲染 & 服务端逻辑共用） ====================

    /**
     * 若 (x,y,z) 属于一个已成型通道（燃料管/控制棒顶部 + 6 石墨管道 + 底座），返回该通道基座（底部）的 Y；
     * 否则返回 -1。可用于服务端物理代码定位通道。
     */
    public static int channelBottom(IBlockAccess world, int x, int y, int z) {
        Role role = roleOf(world, x, y, z);
        if (role == null) {
            return -1;
        }
        int candidate;
        switch (role) {
            case FUEL_CHANNEL_BASE:
                candidate = y;
                break;
            case FUEL_CHANNEL_TOP:
            case CONTROL_ROD_TOP:
                candidate = y - 7;
                break;
            case FUEL_CHANNEL_PIPE: {
                int yy = y;
                while (yy > 0 && roleOf(world, x, yy - 1, z) == Role.FUEL_CHANNEL_PIPE) {
                    yy--;
                }
                candidate = yy - 1; // 最下方石墨管道再往下一格，必须是底座
                break;
            }
            default:
                return -1;
        }
        return isChannel(world, x, candidate, z) ? candidate : -1;
    }

    /** 是否为合法的通道顶部（普通燃料管 / 各类控制棒）。 */
    public static boolean isChannelTop(Role role) {
        return role == Role.FUEL_CHANNEL_TOP || role == Role.CONTROL_ROD_TOP;
    }

    /**
     * 取 (x,y,z) 所属已成型通道的基座 TileEntity（用于控制模型的 Y 轴偏移等）。
     * 未成型或不是通道返回 null。
     */
    public static TileEntityRbmkFuelChannel channelTE(net.minecraft.world.World world, int x, int y, int z) {
        int bottom = channelBottom(world, x, y, z);
        if (bottom < 0) {
            return null;
        }
        TileEntity te = world.getTileEntity(x, bottom, z);
        return (te instanceof TileEntityRbmkFuelChannel) ? (TileEntityRbmkFuelChannel) te : null;
    }

    private static boolean isChannel(IBlockAccess world, int x, int by, int z) {
        if (roleOf(world, x, by, z) != Role.FUEL_CHANNEL_BASE) {
            return false;
        }
        for (int i = 1; i <= 6; i++) {
            if (roleOf(world, x, by + i, z) != Role.FUEL_CHANNEL_PIPE) {
                return false;
            }
        }
        return isChannelTop(roleOf(world, x, by + 7, z));
    }

    private static Role roleOf(IBlockAccess world, int x, int y, int z) {
        Block b = world.getBlock(x, y, z);
        return (b instanceof BlockRBMKRod) ? ((BlockRBMKRod) b).role : null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        switch (side) {
            case 1:  return iconTop;    // 顶面 (Y+)
            case 0:  return iconBottom; // 底面 (Y-)
            default: return iconSide;   // 四周 2~5
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.iconTop = reg.registerIcon("eyeofharmonybuffer:Arknights/rbmk/" + topTexture);
        this.iconSide = reg.registerIcon("eyeofharmonybuffer:Arknights/rbmk/" + sideTexture);
        this.iconBottom = reg.registerIcon("eyeofharmonybuffer:Arknights/rbmk/" + bottomTexture);
    }

    // ---- 燃料通道渲染相关 ----

    @Override
    public int getRenderType() {
        return role == Role.NORMAL ? 0 : renderId;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return role == Role.NORMAL;
    }

    @Override
    public boolean isOpaqueCube() {
        return role == Role.NORMAL;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        // 仅基座持有 TE：TileEntityRbmkFuelChannel.getRenderBoundingBox() 已把
        // Angelica 的渲染包围盒扩成整条 8 格通道，任意部分可见都会绘制模型，
        // 因此无需在每一格都挂 TE（整堆 1661 通道只需 1661 个 TE）。
        return role == Role.FUEL_CHANNEL_BASE;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return role == Role.FUEL_CHANNEL_BASE ? new TileEntityRbmkFuelChannel() : null;
    }
}
