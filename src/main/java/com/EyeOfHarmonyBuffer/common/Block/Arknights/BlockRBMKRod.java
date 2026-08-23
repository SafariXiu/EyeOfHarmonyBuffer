package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityRbmkFuelChannel;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
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
        /** 普通方块（控制棒等）：始终按普通方块渲染 */
        NORMAL,
        /** 燃料通道顶部（普通燃料管） */
        FUEL_CHANNEL_TOP,
        /** 燃料通道中段（反应堆内部石墨管道） */
        FUEL_CHANNEL_PIPE,
        /** 燃料通道底部（燃料管底座，持有 TE + TESR） */
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
        super(Material.iron);
        this.topTexture = topTexture;
        this.bottomTexture = bottomTexture;
        this.sideTexture = sideTexture;
        this.role = role;
        this.setHardness(5.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeMetal);
    }

    public Role getRole() {
        return role;
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
