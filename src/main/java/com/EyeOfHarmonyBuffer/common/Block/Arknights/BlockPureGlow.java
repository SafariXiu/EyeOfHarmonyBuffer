package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;

/**
 * 纯色自发光方块：16 种变体以 meta 区分（0-15），颜色对应 MC 标准染料 16 色，
 * 每个 meta 一张纯色纹理（无任何杂质），自发光 15 级（满亮度）。
 *
 * <p>继承普通 {@link Block}（实心方块），非玻璃等特殊方块。
 */
public class BlockPureGlow extends Block {

    /**
     * 16 种纯色（RGB，对应 MC 标准染料色序：白橙品红浅蓝黄亮绿粉灰浅灰青紫蓝棕绿红黑）。
     * 整体提亮版：保留色相，各通道向高亮方向拉（黑→深灰、灰→亮灰等）。
     */
    public static final int[] COLORS = {
        0xFFFFFF, 0xF2A55C, 0xE08CF2, 0x8CC2F2,
        0xF7F766, 0xB8F24D, 0xFFB8D1, 0x7A7A7A,
        0xC9C9C9, 0x8CB8D1, 0xB88CE0, 0x738CE0,
        0x9C7A5C, 0x9CB86B, 0xE06666, 0x454545
    };

    public static final int VARIANT_COUNT = COLORS.length; // 16

    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public BlockPureGlow() {
        super(Material.rock);
        this.setHardness(2.0F);
        this.setResistance(10.0F);
        this.setStepSound(soundTypeStone);
        this.setLightLevel(1.0F); // 自发光 15 级（15/15）
    }

    // ---- 变体（meta 0-15） ----

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < VARIANT_COUNT; i++) {
            list.add(new ItemStack(item, 1, i));
        }
    }

    // ---- 纹理（每 meta 一张纯色图） ----

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return this.icons[meta % VARIANT_COUNT];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.icons = new IIcon[VARIANT_COUNT];
        for (int i = 0; i < VARIANT_COUNT; i++) {
            this.icons[i] = register.registerIcon(EyeOfHarmonyBuffer.MODID + ":pure_glow/" + i);
        }
    }

}
