package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.*;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.ArknightsProject_Block;

public class ArknightsBlockRegister {

    public static Block YuanShiMainBlock;
    public static Block YuanShiBlock;
    public static Block OverdomainErosion;
    public static Block LanTieBlock;
    public static Block LanTieMainBlock;
    public static Block ZiJingBlock;
    public static Block ZiJingMainBlock;
    public static Block ChiTongBlock;
    public static Block ChiTongMainBlock;
    public static Block DuoQiMainBlock;
    public static Block XiRangQiMainBlock;
    public static Block CleanGlass;
    public static Block CleanGlassGlow;
    public static Block XieYiYuanShiKuai;
    public static Block PureGlowBlock;

    public static void registryBlocks(){

        YuanShiMainBlock = new BlockYuanShiMain()
            .setBlockName("yuan_shi_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/yuanshi_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(YuanShiMainBlock, "yuan_shi_main_block");
        GTCMItemList.YuanShiMainBlock.set(new ItemStack(YuanShiMainBlock, 1, 0));

        YuanShiBlock = new BlockYuanShi()
            .setBlockName("yuan_shi_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/yuanshi_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(YuanShiBlock, "yuan_shi_block");
        GTCMItemList.YuanShiBlock.set(new ItemStack(YuanShiBlock, 1, 0));

        OverdomainErosion = new BlcokOverdomainErosion()
            .setBlockName("overdomain_erosion")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/overdomain_erosion_portal")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(OverdomainErosion, "overdomain_erosion");
        GTCMItemList.OverdomainErosion.set(new ItemStack(OverdomainErosion, 1, 0));

        LanTieBlock = new BlockLanTie()
            .setBlockName("lan_tie_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/lantie_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(LanTieBlock, "lan_tie_block");
        GTCMItemList.LanTieBlock.set(new ItemStack(LanTieBlock, 1, 0));

        LanTieMainBlock = new BlockLanTieMain()
            .setBlockName("lan_tie_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/lantie_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(LanTieMainBlock, "lan_tie_main_block");
        GTCMItemList.LanTieMainBlock.set(new ItemStack(LanTieMainBlock, 1, 0));

        ZiJingBlock = new BlockZiJing()
            .setBlockName("zi_jing_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/zijing_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ZiJingBlock, "zi_jing_block");
        GTCMItemList.ZiJingBlock.set(new ItemStack(ZiJingBlock, 1, 0));

        ZiJingMainBlock = new BlockZiJingMain()
            .setBlockName("zi_jing_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/zijing_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ZiJingMainBlock, "zi_jing_main_block");
        GTCMItemList.ZiJingMainBlock.set(new ItemStack(ZiJingMainBlock, 1, 0));

        ChiTongBlock = new BlockChiTong()
            .setBlockName("chi_tong_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/chitong_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ChiTongBlock, "chi_tong_block");
        GTCMItemList.ChiTongBlock.set(new ItemStack(ChiTongBlock, 1, 0));

        ChiTongMainBlock = new BlockChiTongMain()
            .setBlockName("chi_tong_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/chitong_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(ChiTongMainBlock, "chi_tong_main_block");
        GTCMItemList.ChiTongMainBlock.set(new ItemStack(ChiTongMainBlock, 1, 0));

        DuoQiMainBlock = new BlockDuoQiMain()
            .setBlockName("duo_qi_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/duoqi_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(DuoQiMainBlock, "duo_qi_main_block");
        GTCMItemList.DuoQiMainBlock.set(new ItemStack(DuoQiMainBlock, 1, 0));

        XiRangQiMainBlock = new BlockXiRangQiMain()
            .setBlockName("xi_rang_qi_main_block")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/xirangqi_main_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(XiRangQiMainBlock, "xi_rang_qi_main_block");
        GTCMItemList.XiRangQiMainBlock.set(new ItemStack(XiRangQiMainBlock, 1, 0));

        CleanGlass = new BlockCleanGlass()
            .setBlockName("CleanGlass")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(CleanGlass, "CleanGlass");
        GTCMItemList.CleanGlass.set(new ItemStack(CleanGlass, 1, 0));

        CleanGlassGlow = new BlockCleanGlassGlow()
            .setBlockName("CleanGlassGlow")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(CleanGlassGlow, "CleanGlassGlow");
        GTCMItemList.CleanGlassGlow.set(new ItemStack(CleanGlassGlow, 1, 0));

        PureGlowBlock = new BlockPureGlow()
            .setBlockName("pure_glow_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(PureGlowBlock, com.EyeOfHarmonyBuffer.common.Block.Arknights.ItemBlockPureGlow.class,
            "pure_glow_block");
        // 16 个变体条目：一个 GTCMItemList 对应一个 meta（按 MC 标准染料色序）
        GTCMItemList.PureGlowBlock.set(new ItemStack(PureGlowBlock, 1, 0));
        GTCMItemList.PureGlowBlock_White.set(new ItemStack(PureGlowBlock, 1, 0));
        GTCMItemList.PureGlowBlock_Orange.set(new ItemStack(PureGlowBlock, 1, 1));
        GTCMItemList.PureGlowBlock_Magenta.set(new ItemStack(PureGlowBlock, 1, 2));
        GTCMItemList.PureGlowBlock_LightBlue.set(new ItemStack(PureGlowBlock, 1, 3));
        GTCMItemList.PureGlowBlock_Yellow.set(new ItemStack(PureGlowBlock, 1, 4));
        GTCMItemList.PureGlowBlock_Lime.set(new ItemStack(PureGlowBlock, 1, 5));
        GTCMItemList.PureGlowBlock_Pink.set(new ItemStack(PureGlowBlock, 1, 6));
        GTCMItemList.PureGlowBlock_Gray.set(new ItemStack(PureGlowBlock, 1, 7));
        GTCMItemList.PureGlowBlock_LightGray.set(new ItemStack(PureGlowBlock, 1, 8));
        GTCMItemList.PureGlowBlock_Cyan.set(new ItemStack(PureGlowBlock, 1, 9));
        GTCMItemList.PureGlowBlock_Purple.set(new ItemStack(PureGlowBlock, 1, 10));
        GTCMItemList.PureGlowBlock_Blue.set(new ItemStack(PureGlowBlock, 1, 11));
        GTCMItemList.PureGlowBlock_Brown.set(new ItemStack(PureGlowBlock, 1, 12));
        GTCMItemList.PureGlowBlock_Green.set(new ItemStack(PureGlowBlock, 1, 13));
        GTCMItemList.PureGlowBlock_Red.set(new ItemStack(PureGlowBlock, 1, 14));
        GTCMItemList.PureGlowBlock_Black.set(new ItemStack(PureGlowBlock, 1, 15));

        XieYiYuanShiKuai = new BlockXieYiYuanShi()
            .setBlockName("xie_yi_yuan_shi")
            .setBlockTextureName("eyeofharmonybuffer:Arknights/xieyiyuanshi_block")
            .setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(XieYiYuanShiKuai, "xie_yi_yuan_shi");
        GTCMItemList.XieYiYuanShiKuai.set(new ItemStack(XieYiYuanShiKuai, 1, 0));
    }

    public static void registry() {
        registryBlocks();
    }
}
