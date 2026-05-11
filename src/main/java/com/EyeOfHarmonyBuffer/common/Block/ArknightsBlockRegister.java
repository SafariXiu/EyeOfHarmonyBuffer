package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockYuanShi;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.BlockYuanShiMain;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.ArknightsProject_Block;

public class ArknightsBlockRegister {

    public static Block YuanShiMainBlock;
    public static Block YuanShiBlock;

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
    }

    public static void registry() {
        registryBlocks();
    }
}
