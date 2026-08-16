package com.EyeOfHarmonyBuffer.space;

import micdoodle8.mods.galacticraft.api.GalacticraftRegistry;
import micdoodle8.mods.galacticraft.api.galaxies.*;
import micdoodle8.mods.galacticraft.api.recipe.SpaceStationRecipe;
import micdoodle8.mods.galacticraft.api.vector.Vector3;
import micdoodle8.mods.galacticraft.api.world.SpaceStationType;

import java.util.HashMap;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.space.talos.WorldProviderTalos1;
import com.EyeOfHarmonyBuffer.space.talos.WorldProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.station.WorldProviderTalos2Station;

import galaxyspace.core.dimension.TeleportTypeSpaceStationGS;

import static com.EyeOfHarmonyBuffer.space.talos.client.resources.ResourcesDimensions.*;

public class RegisterDimensions {

    public static SolarSystem talosSystem;
    public static Star talosStar;
    public static Planet talosGasGiant;
    public static Moon talos2;
    /** 塔罗斯-1 空间站：挂在塔罗斯气态巨行星（塔罗斯-1）名下，维度 -52。 */
    public static Satellite talos1Station;

    /** 塔罗斯-1（气态巨行星 Talos）维度：有维度但不可登录，同 GS 木星/土星模式。 */
    public static final int ID_TALOS1_DIM = 14000;
    public static final int ID_TALOS2_DIM = 14001;
    /** 塔罗斯-1 空间站维度：固定 ID（-52，避开 GC -27 与 GalaxySpace -40~-51 区间）。 */
    public static final int ID_TALOS2_STATION_DIM = -52;
    public static final int tier_Talos1 = 5;
    public static final int tier_Talos2 = 4;

    public static void init() {

        talosSystem = new SolarSystem("talosSystem", "milkyWay")
            .setMapPosition(new Vector3(1.2, -0.6, 0.0));
        GalaxyRegistry.registerSolarSystem(talosSystem);

        talosStar = (Star) new Star("talosStar")
            .setParentSolarSystem(talosSystem)
            .setTierRequired(-1);
        talosStar.setBodyIcon(TalosStar);
        talosSystem.setMainStar(talosStar);

        talosGasGiant = new Planet("Talos")
            .setParentSolarSystem(talosSystem);
        talosGasGiant.setRingColorRGB(0.9F, 0.7F, 0.3F);
        talosGasGiant.setTierRequired(tier_Talos1);
        talosGasGiant.setBodyIcon(TalosGasGiant);
        talosGasGiant.setRelativeDistanceFromCenter(new CelestialBody.ScalableDistance(0.8F, 0.8F));
        talosGasGiant.setRelativeOrbitTime(2.0F);
        // 塔罗斯-1：有维度但不可登录（同 GS 木星/土星模式）——
        // setDimensionInfo 自动注册 14000 的 provider 并置 reachable（星系图可选中、可建空间站）；
        // 不注册传送类型：星系图 Travel 无反应，配合 WorldProviderTalos1 的 canSpaceshipTierPass=false
        // 与太空世界（无地表 + IHostileBody 高压），玩家无法登录塔罗斯-1。
        talosGasGiant.setDimensionInfo(ID_TALOS1_DIM, WorldProviderTalos1.class);
        GalaxyRegistry.registerPlanet(talosGasGiant);

        talos2 = new Moon("Talos2")
            .setParentPlanet(talosGasGiant);
        talos2.setRingColorRGB(0.6F, 0.9F, 1.0F);
        talos2.setTierRequired(tier_Talos2);
        talos2.setBodyIcon(Talos2Moon);
        talos2.setRelativeDistanceFromCenter(new CelestialBody.ScalableDistance(10.0F, 10.0F));
        talos2.setRelativeOrbitTime(100.0F);
        talos2.setDimensionInfo(ID_TALOS2_DIM, WorldProviderTalos2.class);

        GalaxyRegistry.registerMoon(talos2);
        GalacticraftRegistry.registerTeleportType(WorldProviderTalos2.class, new WorldProviderTalos2());

        // ---- 塔罗斯-1 空间站（T5，参考 GS 土星空间站模式） ----
        // 卫星挂靠权限开在父行星（塔罗斯-1）上：GC 星系图按父行星的
        // allowSatellite 显示“创建空间站”入口
        talosGasGiant.setAllowSatellite(true);

        // 塔罗斯-1 空间站：父体 = 塔罗斯气态巨行星（塔罗斯-1）。
        // 创建流程与 GS 木星/土星一致：客户端选中塔罗斯-1（14000）→ 发送 S_BIND_SPACE_STATION_ID(14000) →
        // 服务端按配方 worldToOrbitID==14000 匹配并绑定（parentPlanet.getDimensionID()==14000）。
        // 玩家可在任何地方（如塔罗斯-2 表面）打开星系图为塔罗斯-1 建站，无需站在塔罗斯-1 上。
        talos1Station = (Satellite) new Satellite("spaceStation.talos")
            .setParentBody(talosGasGiant)
            .setRelativeSize(0.2667F)
            // 轨道比塔罗斯-2（10.0）更靠内，避免星系图里两者轨道重合
            .setRelativeDistanceFromCenter(new CelestialBody.ScalableDistance(6.0F, 6.0F))
            .setRelativeOrbitTime(20.0F)
            .setTierRequired(tier_Talos2)
            .setBodyIcon(SpaceStation);
        talos1Station.setDimensionInfo(
            ID_TALOS2_STATION_DIM,
            ID_TALOS2_STATION_DIM,
            WorldProviderTalos2Station.class);
        GalaxyRegistry.registerSatellite(talos1Station);
        // Satellite 新版 setDimensionInfo 默认 autoRegisterDimension=false（不自动注册 provider 类型），
        // 必须手动注册，否则“行星维度列表”与 GC 的 provider 注册表长度不一致，
        // 客户端按序号对齐查询时会越界崩溃（GS 对自家空间站同样手动注册）。
        GalacticraftRegistry.registerProvider(ID_TALOS2_STATION_DIM, WorldProviderTalos2Station.class, true);
        GalacticraftRegistry.registerTeleportType(WorldProviderTalos2Station.class, new TeleportTypeSpaceStationGS());

        // 空间站配方（占位材料：源石 + 息壤，后续可调整）
        HashMap<Object, Integer> stationCost = new HashMap<Object, Integer>();
        stationCost.put(GTCMItemList.YuanShi.get(1), 64);
        stationCost.put(GTCMItemList.XiRang.get(1), 64);
        GalacticraftRegistry.registerSpaceStation(
            new SpaceStationType(
                ID_TALOS2_STATION_DIM,
                ID_TALOS1_DIM,
                new SpaceStationRecipe(stationCost)));

        GalaxyRegistry.refreshGalaxies();
    }
}
