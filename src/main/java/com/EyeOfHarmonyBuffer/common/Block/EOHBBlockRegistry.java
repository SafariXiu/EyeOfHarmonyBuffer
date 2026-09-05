package com.EyeOfHarmonyBuffer.common.Block;

import com.EyeOfHarmonyBuffer.common.Block.Arknights.*;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.botany.BlockIntermediateResources;
import com.EyeOfHarmonyBuffer.common.Block.Arknights.fluids.EOHBFluidBlockRegistry;
import com.EyeOfHarmonyBuffer.common.Block.BlockClass.Casings.SingularityStabilizationRing.SingularityStabilizationRingCasingsItemBlocks;
import com.EyeOfHarmonyBuffer.common.Block.BlockClass.Casings.SingularityStabilizationRingCasings;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.item.items.ItemBlockForgeOfTheSkyCore;
import com.EyeOfHarmonyBuffer.common.item.items.ItemBlockRBMKGraphite;
import com.EyeOfHarmonyBuffer.common.item.items.ItemBlockRBMKShield;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.ArknightsProject_Block;
import static com.EyeOfHarmonyBuffer.common.Block.BasicBlocks.SingularityStabilizationRingCasingsUpgrade;

/**
 * EOHB 方块统一注册入口（GT 系机器外壳除外，见 {@link EOHBMachineBlocks}）。
 * <p>
 * 按类别分方法注册；方块实例不对外暴露 public 字段，一律通过
 * {@code GTCMItemList.Xxx.getBlock()} / {@code getItem()} 取用。
 */
public final class EOHBBlockRegistry {

    private EOHBBlockRegistry() {
    }

    /**
     * preInit 阶段注册的方块（不依赖物品/材质）。
     * 植物（依赖种子物品）与流体（依赖 Werkstoff 材质）分别在
     * {@link #registerPlantBlocks()} / {@link #registerFluidBlocks()} 中按各自时序注册。
     */
    public static void registryBlocks() {
        registerNormalBlocks();
        registerCTMBlocks();
        registerModelBlocks();
        registerRBMKBlocks();
        registerMetaBlocks();
    }

    /** 植物方块：依赖种子物品，须在物品注册后调用。 */
    public static void registerPlantBlocks() {
        registerPlantBlocksInternal();
    }

    /** 流体方块：依赖 Werkstoff 材质，须在材质就绪后（postInit）调用。 */
    public static void registerFluidBlocks() {
        registerFluidBlocksInternal();
    }

    // ==================== 统一注册辅助 ====================

    /** 默认 ItemBlock 注册（无贴图名，CTM/模型方块用）。 */
    private static Block reg(Block block, String name) {
        block.setBlockName(name);
        block.setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(block, name);
        return block;
    }

    /** 默认 ItemBlock 注册（带贴图名）。 */
    private static Block reg(Block block, String name, String textureName) {
        block.setBlockName(name);
        if (textureName != null) {
            block.setBlockTextureName(textureName);
        }
        block.setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(block, name);
        return block;
    }

    /** 自定义 ItemBlock 注册（带 tooltip 的方块）。 */
    private static Block reg(Block block, Class<? extends ItemBlock> itemClass, String name) {
        block.setBlockName(name);
        block.setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(block, itemClass, name);
        return block;
    }

    /** 自定义 ItemBlock 注册 + 贴图名。 */
    private static Block reg(Block block, Class<? extends ItemBlock> itemClass, String name, String textureName) {
        block.setBlockName(name);
        if (textureName != null) {
            block.setBlockTextureName(textureName);
        }
        block.setCreativeTab(ArknightsProject_Block);
        GameRegistry.registerBlock(block, itemClass, name);
        return block;
    }

    /** 统一 GTCMItemList 挂接。 */
    private static void link(GTCMItemList entry, Block block, int meta) {
        entry.set(new ItemStack(block, 1, meta));
    }

    // ==================== 普通方块（矿石 / 纯净方块） ====================

    private static void registerNormalBlocks() {
        Block b;

        b = reg(new BlockYuanShiMain(), "yuan_shi_main_block", "eyeofharmonybuffer:Arknights/yuanshi_main_block");
        link(GTCMItemList.YuanShiMainBlock, b, 0);

        b = reg(new BlockYuanShi(), "yuan_shi_block", "eyeofharmonybuffer:Arknights/yuanshi_block");
        link(GTCMItemList.YuanShiBlock, b, 0);

        b = reg(new BlcokOverdomainErosion(), "overdomain_erosion", "eyeofharmonybuffer:Arknights/overdomain_erosion_portal");
        link(GTCMItemList.OverdomainErosion, b, 0);

        b = reg(new BlockLanTie(), "lan_tie_block", "eyeofharmonybuffer:Arknights/lantie_block");
        link(GTCMItemList.LanTieBlock, b, 0);

        b = reg(new BlockLanTieMain(), "lan_tie_main_block", "eyeofharmonybuffer:Arknights/lantie_main_block");
        link(GTCMItemList.LanTieMainBlock, b, 0);

        b = reg(new BlockZiJing(), "zi_jing_block", "eyeofharmonybuffer:Arknights/zijing_block");
        link(GTCMItemList.ZiJingBlock, b, 0);

        b = reg(new BlockZiJingMain(), "zi_jing_main_block", "eyeofharmonybuffer:Arknights/zijing_main_block");
        link(GTCMItemList.ZiJingMainBlock, b, 0);

        b = reg(new BlockChiTong(), "chi_tong_block", "eyeofharmonybuffer:Arknights/chitong_block");
        link(GTCMItemList.ChiTongBlock, b, 0);

        b = reg(new BlockChiTongMain(), "chi_tong_main_block", "eyeofharmonybuffer:Arknights/chitong_main_block");
        link(GTCMItemList.ChiTongMainBlock, b, 0);

        b = reg(new BlockDuoQiMain(), "duo_qi_main_block", "eyeofharmonybuffer:Arknights/duoqi_main_block");
        link(GTCMItemList.DuoQiMainBlock, b, 0);

        b = reg(new BlockXiRangQiMain(), "xi_rang_qi_main_block", "eyeofharmonybuffer:Arknights/xirangqi_main_block");
        link(GTCMItemList.XiRangQiMainBlock, b, 0);

        b = reg(new BlockXieYiYuanShi(), "xie_yi_yuan_shi", "eyeofharmonybuffer:Arknights/xieyiyuanshi_block");
        link(GTCMItemList.XieYiYuanShiKuai, b, 0);
    }

    // ==================== CTM 连接方块 ====================

    private static void registerCTMBlocks() {
        Block b;

        b = reg(new BlockCleanGlass(), "CleanGlass");
        link(GTCMItemList.CleanGlass, b, 0);

        b = reg(new BlockCleanGlassGlow(), "CleanGlassGlow");
        link(GTCMItemList.CleanGlassGlow, b, 0);

        b = reg(new BlockRBMKShield(), ItemBlockRBMKShield.class, "RBMKShield");
        link(GTCMItemList.RBMKShield, b, 0);

        b = reg(new BlockRBMKGraphite(), ItemBlockRBMKGraphite.class, "RBMKGraphite");
        link(GTCMItemList.RBMKGraphite, b, 0);
    }

    // ==================== 模型方块（TESR：风车 / 天穹炉心） ====================

    private static void registerModelBlocks() {
        Block b;

        // 原 eohb:windmill 指向不存在的资源域，物品栏会紫黑；这里用真实存在的模型贴图作物品图标占位
        b = reg(new BlockWindmill(), "blockWindmill", "eyeofharmonybuffer:textures/models/textureTrubine");
        GameRegistry.registerTileEntity(com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityWindmill.class, "Windmill_TE");
        link(GTCMItemList.TrubineBlock, b, 0);

        b = reg(new BlcokForgeOfTheSkyCore(), ItemBlockForgeOfTheSkyCore.class, "ForgeOfTheSkyCore",
            com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.MODID + ":Arknights/ForgeOfTheSky_Core");
        GameRegistry.registerTileEntity(com.EyeOfHarmonyBuffer.common.Block.TileEntity.TileEntityForgeOfTheSkyCore.class, "ForgeOfTheSkyCore_TE");
        link(GTCMItemList.ForgeOfTheSkyCore, b, 0);
    }

    // ==================== RBMK 通道方块 ====================

    private static void registerRBMKBlocks() {
        Block b;

        b = reg(new BlockRBMKRod("KongZhiBang", "Bang_DiMian", "Bang_CeMian", BlockRBMKRod.Role.CONTROL_ROD_TOP,
            "eyeofharmonybuffer:textures/blocks/Arknights/rbmk/FuelTube_StandardControlRod.png"), "rbmk_standard_control_rod");
        link(GTCMItemList.StandardControlRod, b, 0);

        b = reg(new BlockRBMKRod("DuanXiShouBang", "Bang_DiMian", "Bang_CeMian", BlockRBMKRod.Role.CONTROL_ROD_TOP,
            "eyeofharmonybuffer:textures/blocks/Arknights/rbmk/FuelTube_ShortAbsorbingRod.png"), "rbmk_short_absorbing_rod");
        link(GTCMItemList.ShortAbsorbingRod, b, 0);

        b = reg(new BlockRBMKRod("ZiDongKongZhiBang", "Bang_DiMian", "Bang_CeMian", BlockRBMKRod.Role.CONTROL_ROD_TOP,
            "eyeofharmonybuffer:textures/blocks/Arknights/rbmk/FuelTube_AutoControlRod.png"), "rbmk_auto_control_rod");
        link(GTCMItemList.AutoControlRod, b, 0);

        b = reg(new BlockRBMKRod("TeShuTiaoJieBang", "Bang_DiMian", "Bang_CeMian", BlockRBMKRod.Role.CONTROL_ROD_TOP,
            "eyeofharmonybuffer:textures/blocks/Arknights/rbmk/FuelTube_SpecialAdjustmentRod.png"), "rbmk_special_adjustment_rod");
        link(GTCMItemList.SpecialAdjustmentRod, b, 0);

        b = reg(new BlockRBMKRod("Bang_DingMian", "Bang_DiMian", "Bang_CeMian", BlockRBMKRod.Role.FUEL_CHANNEL_TOP,
            "eyeofharmonybuffer:textures/models/FuelTube.png"), "rbmk_fuel_tube");
        link(GTCMItemList.FuelTube, b, 0);

        b = reg(new BlockRBMKRod("Bang_DiMian", "Bang_DingMian", "Bang_CeMian", BlockRBMKRod.Role.FUEL_CHANNEL_BASE), "rbmk_fuel_tube_base");
        link(GTCMItemList.FuelTubeBase, b, 0);

        b = reg(new BlockRBMKRod("Bang_ShiMo_DiMian", "Bang_ShiMo_DiMian", "Bang_ShiMo_CeMian", BlockRBMKRod.Role.FUEL_CHANNEL_PIPE), "rbmk_graphite_pipe");
        link(GTCMItemList.ReactorGraphitePipe, b, 0);
    }

    // ==================== meta 方块（纯彩光方块 / 奇异稳定环外壳） ====================

    private static void registerMetaBlocks() {
        Block b;

        // PureGlowBlock：16 个变体，一个 GTCMItemList 对应一个 meta（MC 标准染料色序）
        b = reg(new BlockPureGlow(), com.EyeOfHarmonyBuffer.common.Block.Arknights.ItemBlockPureGlow.class, "pure_glow_block");
        link(GTCMItemList.PureGlowBlock, b, 0);
        link(GTCMItemList.PureGlowBlock_White, b, 0);
        link(GTCMItemList.PureGlowBlock_Orange, b, 1);
        link(GTCMItemList.PureGlowBlock_Magenta, b, 2);
        link(GTCMItemList.PureGlowBlock_LightBlue, b, 3);
        link(GTCMItemList.PureGlowBlock_Yellow, b, 4);
        link(GTCMItemList.PureGlowBlock_Lime, b, 5);
        link(GTCMItemList.PureGlowBlock_Pink, b, 6);
        link(GTCMItemList.PureGlowBlock_Gray, b, 7);
        link(GTCMItemList.PureGlowBlock_LightGray, b, 8);
        link(GTCMItemList.PureGlowBlock_Cyan, b, 9);
        link(GTCMItemList.PureGlowBlock_Purple, b, 10);
        link(GTCMItemList.PureGlowBlock_Blue, b, 11);
        link(GTCMItemList.PureGlowBlock_Brown, b, 12);
        link(GTCMItemList.PureGlowBlock_Green, b, 13);
        link(GTCMItemList.PureGlowBlock_Red, b, 14);
        link(GTCMItemList.PureGlowBlock_Black, b, 15);

        // 奇异稳定环外壳：GT 机器方块，构造器已自注册；这里补 ItemBlock 注册 + 14 个层级变体。
        // 必须走 SingularityStabilizationRingCasingsMeta() —— 它会填充 SingularityStabilizationRingCasingsSet
        // （meta 0..13，供 getSubBlocks/registerBlockIcons 使用）并 texter 注册各层级 i18n 显示名。
        GameRegistry.registerBlock(
            SingularityStabilizationRingCasingsUpgrade,
            SingularityStabilizationRingCasingsItemBlocks.class,
            SingularityStabilizationRingCasingsUpgrade.getUnlocalizedName());
        GTCMItemList.SingularityStabilizationRingCasingsLV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings LV Tier", 0));
        GTCMItemList.SingularityStabilizationRingCasingsMV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings MV Tier", 1));
        GTCMItemList.SingularityStabilizationRingCasingsHV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings HV Tier", 2));
        GTCMItemList.SingularityStabilizationRingCasingsEV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings EV Tier", 3));
        GTCMItemList.SingularityStabilizationRingCasingsIV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings IV Tier", 4));
        GTCMItemList.SingularityStabilizationRingCasingsLuV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings LuV Tier", 5));
        GTCMItemList.SingularityStabilizationRingCasingsZPM.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings ZPM Tier", 6));
        GTCMItemList.SingularityStabilizationRingCasingsUV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UV Tier", 7));
        GTCMItemList.SingularityStabilizationRingCasingsUHV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UHV Tier", 8));
        GTCMItemList.SingularityStabilizationRingCasingsUEV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UEV Tier", 9));
        GTCMItemList.SingularityStabilizationRingCasingsUIV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UIV Tier", 10));
        GTCMItemList.SingularityStabilizationRingCasingsUMV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UMV Tier", 11));
        GTCMItemList.SingularityStabilizationRingCasingsUXV.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings UXV Tier", 12));
        GTCMItemList.SingularityStabilizationRingCasingsMAX.set(
            SingularityStabilizationRingCasings.SingularityStabilizationRingCasingsMeta("Singularity Stabilization Ring Casings MAX Tier", 13));
    }

    // ==================== 植物（枚举批量） ====================

    private static void registerPlantBlocksInternal() {
        BlockIntermediateResources.registerAll(com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer.MODID);
    }

    // ==================== 液体 ====================

    private static void registerFluidBlocksInternal() {
        EOHBFluidBlockRegistry.registerFluidBlocks();
    }
}
