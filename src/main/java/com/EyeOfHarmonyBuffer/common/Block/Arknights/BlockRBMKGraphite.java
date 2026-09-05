package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import com.EyeOfHarmonyBuffer.common.Block.CTMHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

public class BlockRBMKGraphite extends BlockBreakable {

    @SideOnly(Side.CLIENT)
    private IIcon[] connectionIcons;

    public BlockRBMKGraphite() {
        super("eyeofharmonybuffer:Arknights/RBMK_Graphite/RBMK_Graphite_conn_0", Material.rock, false);
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
        connectionIcons = new IIcon[47];
        for (int i = 0; i < 47; i++) {
            connectionIcons[i] = reg.registerIcon("eyeofharmonybuffer:Arknights/RBMK_Graphite/RBMK_Graphite_conn_" + i);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return connectionIcons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        int bits = CTMHelper.getNeighborBits(world, x, y, z, side, BlockRBMKGraphite::isConnected);
        return connectionIcons[CTMHelper.NEIGHBOR_MAP[bits]];
    }

    private static boolean isConnected(IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockRBMKGraphite;
    }
}
