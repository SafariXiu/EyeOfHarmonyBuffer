package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.common.item.items.ItemBlockCasingsEOH;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.common.blocks.BlockCasingsAbstract;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import java.util.List;

public class BlockCasingsEOH extends BlockCasingsAbstract {

    @SideOnly(Side.CLIENT)
    private IIcon[] mIcons = new IIcon[16];

    public static final int META_XIRANG_WAIKE = 0;
    public static final int META_ZHONG_XIRANG_WAIKE = 1;

    public static final int CASING_INDEX_XIRANG =
        (30 << 7) | BlockCasingsEOH.META_XIRANG_WAIKE;

    public static final int CASING_INDEX_ZHONG_XIRANG =
        (30 << 7) | BlockCasingsEOH.META_ZHONG_XIRANG_WAIKE;

    public BlockCasingsEOH() {
        super(ItemBlockCasingsEOH.class, "eoh.blockcasings", Material.iron, 2);
    }

    @Override
    public int getTextureIndex(int meta) {
        meta = Math.max(0, Math.min(meta, 1));

        int page = 30;
        int baseSlot = 0;

        return (page << 7) | (baseSlot + meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, META_XIRANG_WAIKE));
        list.add(new ItemStack(item, 1, META_ZHONG_XIRANG_WAIKE));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        mIcons[META_XIRANG_WAIKE] =
            reg.registerIcon("eyeofharmonybuffer:Arknights/xirangwaike");

        mIcons[META_ZHONG_XIRANG_WAIKE] =
            reg.registerIcon("eyeofharmonybuffer:Arknights/zhongxirangwaike");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        meta = Math.max(0, Math.min(meta, 1));
        IIcon icon = mIcons[meta];
        if (icon == null) {
            icon = mIcons[META_XIRANG_WAIKE];
        }
        return icon;
    }
}
