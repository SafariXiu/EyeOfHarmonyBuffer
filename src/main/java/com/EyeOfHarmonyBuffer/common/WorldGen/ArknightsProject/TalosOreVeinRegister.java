package com.EyeOfHarmonyBuffer.common.WorldGen.ArknightsProject;

import java.util.Collections;

import com.EyeOfHarmonyBuffer.common.material.EOHBGTMaterials;
import gregtech.api.enums.StoneType;
import gregtech.api.interfaces.IOreMaterial;
import gregtech.common.OreMixBuilder;
import gregtech.common.WorldgenGTOreLayer;
import gtneioreplugin.util.DimensionHelper;

/**
 * Talos2 专属 GT 原生矿脉注册。
 * 构造 WorldgenGTOreLayer 即会加入 WorldgenGTOreLayer.sList，
 * GTWorldgenerator 会按权重/高度在 Talos2 维度中生成。
 */
public final class TalosOreVeinRegister {

    /**
     * DimensionDef.getDimensionName(Talos2) 解析为天体名，
     * Galacticraft 会把天体名转为小写，因此这里是 "talos2"。
     */
    public static final String DIM_TALOS2 = "talos2";

    private TalosOreVeinRegister() {}

    public static void register() {
        // 让 NEI 矿脉插件 / BartWorks 虚空采矿机掉落表能识别 Talos2 维度，
        // 否则它们在扫描已注册矿脉时会因查不到该维度而直接抛异常。
        DimensionHelper.register(
            "EyeOfHarmonyBuffer_Talos2",
            DIM_TALOS2,
            "Talos2",
            "Ta",
            "gtnop.tier.4",
            Collections.singletonList(StoneType.Stone));

        // 轻锰矿：最常见，浅层
        registerVein(
            "ore.mix.talos.qingmengkuang",
            EOHBGTMaterials.QingMengKuang,
            110,
            20, 100,
            5, 28);

        // 异铁：中等，较深
        registerVein(
            "ore.mix.talos.yitie",
            EOHBGTMaterials.YiTie,
            80,
            10, 70,
            4, 24);

        // 转质盐：稀有，中高层
        registerVein(
            "ore.mix.talos.zhuanzhiyan",
            EOHBGTMaterials.ZhuanZhiYan,
            50,
            40, 130,
            3, 20);
    }

    private static void registerVein(String name, IOreMaterial material, int weight, int minY, int maxY,
        int density, int size) {
        new WorldgenGTOreLayer(
            new OreMixBuilder()
                .name(name)
                .enableInDim(DIM_TALOS2)
                .heightRange(minY, maxY)
                .weight(weight)
                .density(density)
                .size(size)
                .primary(material)
                .secondary(material)
                .inBetween(material)
                .sporadic(material));
    }
}
