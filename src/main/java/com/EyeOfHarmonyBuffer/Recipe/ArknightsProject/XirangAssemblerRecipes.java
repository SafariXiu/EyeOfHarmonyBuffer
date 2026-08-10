package com.EyeOfHarmonyBuffer.Recipe.ArknightsProject;

import bartworks.system.material.WerkstoffLoader;
import com.dreammaster.item.NHItemList;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.utils.IRecipePool;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTUtility;

import static com.EyeOfHarmonyBuffer.Recipe.RecipeMaps.XirangAssembler;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

public class XirangAssemblerRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTUtility.copyAmount(0, GTCMItemList.FenLiXin.get(1)),
                GTCMItemList.HuaHeQieXiaoYe.get(16),
                GTCMItemList.JingTiYuanJian.get(16),
                GTCMItemList.RMA70_12.get(16),
                GTCMItemList.GaoJingLingJian.get(128),
                GTCMItemList.WenDingTanKuai.get(128)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                EOHBMaterialPool.ChunHuaNiuZhuanChunJiang.getFluidOrGas(8000),
                EOHBMaterialPool.JingZhiQieXiaoYe.getFluidOrGas(32000)
            )
            .itemOutputs(
                GTCMItemList.QieXiaoYuanYe.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.GaiLiangZhuangZhi.get(16),
                GTCMItemList.YeHuaGaoNengQiTi.get(16),
                GTCMItemList.DianJiDanYuan.get(16),
                GTCMItemList.YiTieKuai.get(8),
                GTCMItemList.GangZhiLingJian.get(128),
                GTCMItemList.ZhiMiJingTiFenMo.get(128),
                GTCMItemList.TongHuaGuanMuFenMo.get(128),
                NHItemList.CircuitUV.get(8)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                EOHBMaterialPool.WenDingGaoNengQiTi.getFluidOrGas(32000),
                EOHBMaterialPool.GaoNengYeTi.getFluidOrGas(32000)
            )
            .itemOutputs(
                GTCMItemList.JuNengDongLiDanYuan.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.TongNingJiZu.get(16),
                GTCMItemList.LeiNingJieHe.get(16),
                GTCMItemList.YeHuaGaoNengQiTi.get(16),
                GTCMItemList.JuSuanZhiKuai.get(64),
                GTCMItemList.LanTiePing.get(64),
                GTCMItemList.XiMoQiaoHuaFenMo.get(128)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                EOHBMaterialPool.HuanTingDanTi.getFluidOrGas(16000),
                EOHBMaterialPool.NaiSuanJianRongJi.getFluidOrGas(48000)
            )
            .itemOutputs(
                GTCMItemList.YeHuaMiXiJuTi.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.LeiNingJieHe.get(16),
                GTCMItemList.HuanTingJuZhi.get(16),
                GTCMItemList.TangZu.get(16),
                WerkstoffLoader.RedZircon.get(OrePrefixes.lens, 16),
                GTCMItemList.GaoJingXianWei.get(128),
                GTCMItemList.ZiJingLingJian.get(128),
                GTCMItemList.MiZhiJingTi.get(64)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                EOHBMaterialPool.FuHeJuZhiJiang.getFluidOrGas(32000),
                EOHBMaterialPool.Inergen.getFluidOrGas(32000)
            )
            .itemOutputs(
                GTCMItemList.ShouXingQuGuangTi.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HuanTingJuZhi.get(16),
                GTCMItemList.HeSuXianWei.get(16),
                GTCMItemList.ZhuanZhiYanZu.get(16),
                GTCMItemList.ZiJingXianWei.get(128),
                GTCMItemList.GaoJingXianWei.get(128),
                GTCMItemList.RangJing.get(64)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                EOHBMaterialPool.JiaoLianNingJiao.getFluidOrGas(16000),
                EOHBMaterialPool.JuZhiRongJiang.getFluidOrGas(16000),
                EOHBMaterialPool.ShiKeJingTiJiang.getFluidOrGas(64000)
            )
            .itemOutputs(
                GTCMItemList.HuanTingYuZhiTi.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.HeSuXianWei.get(16),
                GTCMItemList.JuSuanZhiZu.get(32),
                GTCMItemList.GuYuanYanZu.get(16),
                ItemList.KevlarFiber.get(128),
                ItemList.Circuit_Parts_GlassFiber.get(128),
                GTCMItemList.GaoJingXianWei.get(64),
                GTCMItemList.ZhiMiLanTieFenMo.get(64),
                GTCMItemList.GangZhiLingJian.get(128)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                Materials.Epoxid.getMolten(32000)
            )
            .itemOutputs(
                GTCMItemList.GuHuaXianWeiBan.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.ZhuanZhiYanZu.get(16),
                GTCMItemList.BanZiRanRongJi.get(16),
                GTCMItemList.TangZu.get(16),
                GTCMItemList.ZiJingFenMo.get(128),
                GTCMItemList.WenDingTanKuai.get(128)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                EOHBMaterialPool.JingTiJiang.getFluidOrGas(32000),
                EOHBMaterialPool.NingJieJiang.getFluidOrGas(32000),
                EOHBMaterialPool.ChunHuaNiuZhuanChunJiang.getFluidOrGas(32000)
            )
            .itemOutputs(
                GTCMItemList.ZhuanZhiYanJuKuai.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);

        GTValues.RA.stdBuilder()
            .itemInputs(
                GTCMItemList.BanZiRanRongJi.get(16),
                GTCMItemList.HuaHeQieXiaoYe.get(16),
                GTCMItemList.NingJiao.get(16),
                GTCMItemList.GaoJingXianWei.get(128),
                GTCMItemList.ZiJingZhiPing.get(64),
                GTCMItemList.WenDingTanKuai.get(256)
            )
            .fluidInputs(
                EOHBMaterialPool.GaoJieJingLianYe.getFluidOrGas(8000),
                EOHBMaterialPool.Inergen.getFluidOrGas(64000),
                EOHBMaterialPool.HuanTingJuHeWu.getFluidOrGas(32000)
            )
            .itemOutputs(
                GTCMItemList.JingLianRongJi.get(8)
            )
            .eut(500000)
            .duration(10 * SECONDS)
            .addTo(XirangAssembler);
    }
}
