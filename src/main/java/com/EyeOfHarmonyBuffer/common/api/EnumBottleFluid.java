package com.EyeOfHarmonyBuffer.common.api;

public enum EnumBottleFluid {
    EMPTY(0, ""),// 0 = 空
    CHEN_JI_SUAN(1, "ChenJiSuan"),
    CHI_TONG_RONG_YE(2, "ChiTongRongYe"),
    DUO_XING_RANG_JING_FEI_YE(3, "DuoXingRangJingFeiYe"),
    HE_TONG_RONG_YE(4, "HeTongRongYe"),
    JIN_CAO_RONG_YE(5, "JinCaoRongYe"),
    QING_SHUI(6, "QingShui"),
    RANG_JING_FEI_YE(7, "RangJingFeiYe"),
    WU_SHUI(8, "WuShui"),
    YA_ZHEN_RONG_YE(9, "YaZhenRongYe"),
    YE_HUA_XI_RANG(10, "YeHuaXiRang"),
    YE_HUA_ZHONG_XI_RANG(11, "YeHuaZhongXiRang"),

    SUAN_QI(12, "SuanQi"),
    QI_TAI_CHI_TONG(13, "QiTaiChiTong"),
    QI_TAI_HE_TONG(14, "QiTaiHeTong"),
    QI_TAI_ZHUO_TONG(15, "QiTaiZhuoTong"),
    DUO_QI(16, "DuoQi"),
    SHUI_ZHENG_QI(17, "ShuiZhengQi"),
    XI_RANG_QI(18, "XiRangQi"),
    ZHONG_XI_RANG_QI(19, "ZhongXiRangQi");

    public final int meta;
    public final String texture;

    EnumBottleFluid(int meta, String texture) {
        this.meta = meta;
        this.texture = texture;
    }

    public static EnumBottleFluid fromMeta(int meta) {
        if (meta < 0 || meta >= values().length) return EMPTY;
        return values()[meta];
    }
}
