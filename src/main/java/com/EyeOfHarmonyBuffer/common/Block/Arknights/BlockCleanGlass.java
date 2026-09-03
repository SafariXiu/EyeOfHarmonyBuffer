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
 * <p>
 * 完整版 CTM（47 张连接贴图，含角吸收）：
 * 相邻同类玻璃自动合并边框，只有外沿保留边框；2x2 完整块的四角并入屏幕
 * （楼梯形缺角时角保留边框）。贴图放 Arknights/CleanGlass/：
 *   CleanGlass_conn_0..46
 * 槽位映射复用 {@link CTMHelper#NEIGHBOR_MAP}（8 方向邻接组合 -&gt; 47 tile 索引）。
 */
public class BlockCleanGlass extends BlockBreakable {

    @SideOnly(Side.CLIENT)
    private IIcon[] connectionIcons;

    public BlockCleanGlass() {
        // BlockBreakable 基底纹理指向 Arknights/CleanGlass（基础图，仅兜底；实际渲染全走 connectionIcons）
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
        // NEIGHBOR_MAP 值域即 47 格 tile 索引（0~46）
        connectionIcons = new IIcon[47];
        for (int i = 0; i < connectionIcons.length; i++) {
            connectionIcons[i] = reg.registerIcon("eyeofharmonybuffer:Arknights/CleanGlass/CleanGlass_conn_" + i);
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
        int bits = CTMHelper.getNeighborBits(world, x, y, z, side, BlockCleanGlass::isConnected);
        return connectionIcons[CTMHelper.NEIGHBOR_MAP[bits]];
    }

    private static boolean isConnected(IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockCleanGlass;
    }
}
