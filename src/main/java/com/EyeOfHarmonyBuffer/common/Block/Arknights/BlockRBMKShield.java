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
 * RBMK 反应堆生物屏蔽层（RBMK Reactor Biological Shield）。
 * <p>
 * 简单版 CTM（16 张连接贴图，正交连接掩码，无角吸收），逻辑与水下纯净玻璃
 * {@link BlockCleanGlass} 一致。贴图放 Arknights/RBMK_Schema_E/：
 *   RBMK_Schema_E_conn_0..15
 * <p>
 * 掩码位定义见 {@link CTMHelper}。
 */
public class BlockRBMKShield extends BlockBreakable {

    @SideOnly(Side.CLIENT)
    private IIcon[] connectionIcons;

    public BlockRBMKShield() {
        // BlockBreakable: (纹理基础路径, Material, 是否是对方块本身也渲染邻接面的薄方块)
        super("eyeofharmonybuffer:Arknights/RBMK_Schema_E/RBMK_Schema_E_conn_0", Material.rock, false);
        setHardness(2.0F);
        setResistance(5.0F);
        setStepSound(soundTypeStone);
        setLightOpacity(0);
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        connectionIcons = new IIcon[16];
        for (int i = 0; i < 16; i++) {
            connectionIcons[i] = reg.registerIcon("eyeofharmonybuffer:Arknights/RBMK_Schema_E/RBMK_Schema_E_conn_" + i);
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
        int mask = CTMHelper.getConnectionMask(world, x, y, z, side, BlockRBMKShield::isConnected);
        return connectionIcons[mask];
    }

    private static boolean isConnected(IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockRBMKShield;
    }
}
