package com.EyeOfHarmonyBuffer.common.Block.Arknights;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.BlockBreakable;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

/**
 * 实验性“干净玻璃”：
 * 材质恢复正常（玻璃），由 WaterGlassMixin 跳过相邻水体的侧面渲染，
 * 因此水下不会画侧面水墙，看起来干净、可透视，且放置行为正常；
 * 贴图为无边框可平铺的透明玻璃面，相邻方块天然无缝。
 */
public final class BlockCleanGlass extends BlockBreakable {

    private static BlockCleanGlass instance;

    private BlockCleanGlass() {
        super("eyeofharmonybuffer:Arknights/CleanGlass", Material.glass, false);
        setBlockName("CleanGlass");
        setCreativeTab(CreativeTabs.tabBlock);
        setHardness(0.3F);
        setStepSound(soundTypeGlass);
        setLightOpacity(0);
    }

    public static BlockCleanGlass register(String modid) {
        if (instance == null) {
            instance = new BlockCleanGlass();
            GameRegistry.registerBlock(instance, "CleanGlass");
        }
        return instance;
    }

    public static BlockCleanGlass getRegistered() {
        return instance;
    }

    @Override
    public boolean renderAsNormalBlock() {
        // 按透明方块渲染（与原版玻璃一致），否则会当实体方块画、看起来挡光
        return false;
    }
}
