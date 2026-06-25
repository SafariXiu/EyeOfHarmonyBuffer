package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.*;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.ArknightsProject_Block;

public class ArknightsBlockRegister {

    public static Block YuanShiMainBlock;
    public static Block YuanShiBlock;
    public static Block OverdomainErosion;
    public static Block LanTieBlock;
    public static Block LanTieMainBlock;
    public static Block ZiJinghBlock;
    public static Block ZiJingMainBlock;
    public static Block ChiTongBlock;
    public static Block ChiTongMainBlock;

    public static void registryBlocks(){

        YuanShiMainBlock = new BlockYuanShiMain()
            .setBlockName("yuan_shi_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/yuanshi_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(YuanShiMainBlock, "yuan_shi_main_block");

        YuanShiBlock = new BlockYuanShi()
            .setBlockName("yuan_shi_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/yuanshi_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(YuanShiBlock, "yuan_shi_block");

        OverdomainErosion = new BlcokOverdomainErosion()
            .setBlockName("overdomain_erosion")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/overdomain_erosion_portal")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(OverdomainErosion, "overdomain_erosion");

        LanTieBlock = new BlockLanTie()
            .setBlockName("lan_tie_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/lantie_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(LanTieBlock, "lan_tie_block");

        LanTieMainBlock = new BlockLanTieMain()
            .setBlockName("lan_tie_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/lantie_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(LanTieMainBlock, "lan_tie_main_block");

        ZiJinghBlock = new BlockZiJing()
            .setBlockName("zi_jing_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/zijing_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ZiJinghBlock, "zi_jing_block");

        ZiJingMainBlock = new BlockZiJingMain()
            .setBlockName("zi_jing_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/zijing_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ZiJingMainBlock, "zi_jing_main_block");

        ChiTongBlock = new BlockZiJing()
            .setBlockName("chi_tong_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/chitong_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ChiTongBlock, "chi_tong_block");

        ChiTongMainBlock = new BlockZiJingMain()
            .setBlockName("chi_tong_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/chitong_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ChiTongMainBlock, "chi_tong_main_block");
    }

    public static void registry() {
        registryBlocks();
    }
}
