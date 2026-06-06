package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityOverdomainErosion;
import com.EyeOfHarmonyBuffer.example.material.ModMaterials;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.BlockContainer;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import java.util.Random;

import static com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.MODID;

public class BlcokOverdomainErosion extends BlockContainer {

    public BlcokOverdomainErosion() {
        super(ModMaterials.portalLiquid);
        setBlockName("overdomain_erosion");
        setLightLevel(0.8F);
        setBlockBounds(0F, 0F, 0F, 1F, 0.875F, 1F);
        setBlockUnbreakable();
        setResistance(6000000.0F);
    }

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.icon = reg.registerIcon(MODID + ":Arknights/overdomain_erosion_portal");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.icon;
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
    public Item getItemDropped(int meta, Random random, int fortune) {
        return null;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    protected boolean canSilkHarvest() {
        return false;
    }

    @Override
    public float getPlayerRelativeBlockHardness(EntityPlayer player, World world, int x, int y, int z) {
        if (!player.capabilities.isCreativeMode) {
            return 0.0F;
        }
        return super.getPlayerRelativeBlockHardness(player, world, x, y, z);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean addHitEffects(World world, MovingObjectPosition target, EffectRenderer effectRenderer) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean addDestroyEffects(World world, int x, int y, int z, int meta, EffectRenderer effectRenderer) {
        return true;
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

        if (entity instanceof EntityItem) {
            entity.setDead();
            return;
        }

        if (entity instanceof EntityXPOrb) {
            entity.setDead();
            return;
        }
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
