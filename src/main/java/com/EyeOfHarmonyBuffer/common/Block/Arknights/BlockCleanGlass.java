package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import com.EyeOfHarmonyBuffer.common.Block.CTMHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

/**
 * 水下纯净玻璃（Underwater Pure Glass）：
 * 材质恢复正常（玻璃），由 WaterGlassMixin 跳过相邻水体的侧面渲染，
 * 因此水下不会画侧面水墙，看起来干净、可透视，且放置行为正常。
 *
 * 连接材质（CTM）：相邻同类玻璃自动合并边框，只有外沿保留边框。
 * 16 个变体按掩码选择，掩码位定义（相对贴图本身）：
 *   bit0 = 上（贴图顶部边）已连接
 *   bit1 = 下（贴图底部边）已连接
 *   bit2 = 左（贴图左边）已连接
 *   bit3 = 右（贴图右边）已连接
 *
 * 面的世界方向 -> 贴图边 的映射由 RenderBlocks 各面 UV 顶点顺序推导得出
 * （注意 vanilla 的北面(side=2)和东面(side=5)贴图是水平镜像的）。
 */
public class BlockCleanGlass extends BlockBreakable {

    @SideOnly(Side.CLIENT)
    private IIcon[] connectionIcons;

    public BlockCleanGlass() {
        super("eyeofharmonybuffer:Arknights/CleanGlass", Material.glass, false);
        setHardness(0.3F);
        setStepSound(soundTypeGlass);
        setLightOpacity(0);
    }

    @Override
    public boolean renderAsNormalBlock() {
        // 按透明方块渲染（与原版玻璃一致），否则会当实体方块画、看起来挡光
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        connectionIcons = new IIcon[16];
        for (int i = 0; i < 16; i++) {
            connectionIcons[i] = reg.registerIcon("eyeofharmonybuffer:Arknights/CleanGlass_conn_" + i);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        // 物品栏/默认：无连接，显示完整边框
        return connectionIcons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int mask = CTMHelper.getConnectionMask(world, x, y, z, side, BlockCleanGlass::isConnected);
        return connectionIcons[mask];
    }

    private static boolean isConnected(IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockCleanGlass;
    }
}
