package com.EyeOfHarmonyBuffer.common.Block.Arknights.botany;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum ResourceClusterDef {

    GAN_SHI("GanShi", "GanShiZhongZi", 0.25F, 8, Blocks.grass, Blocks.dirt),
    JIN_CAO("JinCao", "JinCaoZhongZi", 0.35F, 6, Blocks.grass, Blocks.dirt),
    QIAO_HUA("QiaoHua", "QiaoHuaZhongZi", 0.30F, 5, Blocks.grass, Blocks.dirt, Blocks.sand),
    SHA_YE("ShaYe", "ShaYeZhongZi", 0.30F, 5, Blocks.grass, Blocks.dirt, Blocks.sand),
    TONG_HUA_GUAN_MU("TongHuaGuanMu", "TongHuaShuZhong", 0.30F, 5, Blocks.grass, Blocks.dirt, Blocks.sand),
    YA_ZHEN("YaZhen", "YaZhenZhongZi", 0.30F, 5, Blocks.grass, Blocks.dirt, Blocks.sand);

    public final String blockName;
    public final String dropItemField;
    public final float extraDropChance; // 掉落第二个种子的概率
    public final int triesPerChunk; // 每区块尝试次数
    public final Set<Block> validGround;

    ResourceClusterDef(String blockName,
                       String dropItemField,
                       float extraDropChance,
                       int triesPerChunk,
                       Block... validGround) {
        this.blockName = blockName;
        this.dropItemField = dropItemField;
        this.extraDropChance = extraDropChance;
        this.triesPerChunk = triesPerChunk;
        this.validGround = new HashSet<>(Arrays.asList(validGround));
    }
}
