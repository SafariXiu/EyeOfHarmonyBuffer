package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import com.EyeOfHarmonyBuffer.example.material.ModMaterials;
import com.EyeOfHarmonyBuffer.example.tile.TileEntityOverdomainErosion;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class BlcokOverdomainErosion extends BlockContainer {

    public BlcokOverdomainErosion() {
        super(ModMaterials.portalLiquid);
        setBlockName("overdomain_erosion");
        setLightLevel(0.8F);
        setBlockBounds(0F, 0F, 0F, 1F, 0.875F, 1F);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityOverdomainErosion();
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
        return -1;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        return AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 0.875, z + 1);
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        if (world.isRemote) return;
    }

    // @Override
    // public boolean onBlockActivated(World world, int x, int y, int z,
    //         EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
    //     ItemStack held = player.getHeldItem();
    //     if (held != null && held.getItem() instanceof ItemBucket) {
    //         return true;
    //     }
    //     return false;
    // }
}
