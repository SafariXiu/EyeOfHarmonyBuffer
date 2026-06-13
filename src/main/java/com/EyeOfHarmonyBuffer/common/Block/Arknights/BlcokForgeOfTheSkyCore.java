package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityForgeOfTheSkyCore;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import static com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.MODID;

public class BlcokForgeOfTheSkyCore extends BlockContainer {

    public BlcokForgeOfTheSkyCore() {
        super(Material.iron);
        setBlockName("ForgeOfTheSkyCore");
        setBlockTextureName(MODID + ":Arknights/ForgeOfTheSky_Core");
        setCreativeTab(CreativeTabs.tabMisc);

        setHardness(-1.0F);
        setResistance(6000000.0F);
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityForgeOfTheSkyCore();
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean canHarvestBlock(EntityPlayer player, int meta) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }
}
