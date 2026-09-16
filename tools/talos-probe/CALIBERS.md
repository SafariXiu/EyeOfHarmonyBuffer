# 物理量口径登记表（CALIBERS）

> **这张表是目标第 (2) 条的交付物。** 它由 `tools/talos-probe/calibers_check.ps1` **机器核对**：
> 表里每一个符号必须在落地源里真实存在（否则报 stale）；
> `extract_symbols.ps1` 抽出的每一个 `public static` 成员必须**要么**在本表的「物理量」表里、
> **要么**在「非物理量」表里，否则进「未分类」清单。
>
> **为什么不是凭记忆写**：口径漂移（D67 / D75 / D78 三次同族）都是「有一处没被列出来」。
> 所以**分类是被机器逼出来的**，不是靠作者想起来。

## 0. 口径字段的含义

| 字段 | 取值 | 说明 |
|---|---|---|
| **单位** | `m` `km` `K` `K/deg` `Pa` `Pa/K` `m/s` `m/s²` `s⁻¹` `kg/kg` `kg/m²` `mm/day` `mm/s` `Sv` `m²/s` \[0,1\] \[−1,1\] `bool` | **量纲本身就出过错**（E59 用错单价、E64 把弧度当角度）⇒ 必须写出来 |
| **口径** | `年平` / `单相位 θ` / `四相位平均`；`\|lat\|`（偶）/ `带符号 lat`；`洋面表` / `全球表`；`应力` / `速度`；`海平面等效` / `含海拔` | 「同一个物理量两套口径」是本项目最大的缺陷来源 |
| **生产者** | 唯一的产生者（若有第二个，就是缺陷） | |
| **消费者** | **全部**已知消费者；写「无」必须是真的无 | |

⚠ **两条纪律**（来自目标原文）：

1. **分解类仪器自检必须覆盖求导之后的量** —— 探针把一个量拆成几项时，**不许只核对拆分项**，
   必须把**导数/二阶导**这类由拆分项算出来的量也一起自检（E63/E64 就是栽在这里：量的是导数）。
2. **未复现的子代理读数一律标「未对账」，不许进验收表。**

---

## 1. 世界几何（`sim.world.WorldContract`）

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `WorldContract.latOf` | 纬度 | rad（带符号） | **帐篷函数**：\|lat\| 对 z=MAX_D 对称；z 非周期 | 契约 | `Atmosphere.*`、`PrecipField.*`、`OceanField`、`GyreRow.latOf`、地形/群系 |
| `WorldContract.bandD` | 带距（赤道→极点） | m | 0..MAX_D | 契约 | 群系 `bandD` 字段 |
| `WorldContract.coriolis` | 科氏参数 f | s⁻¹（带符号） | = 2Ω sin lat | 契约 | `Atmosphere.wind`、`GyreRow`、`CoastalLayer` |
| `WorldContract.betaForLatitude` | β = df/dy | m⁻¹s⁻¹ | 带符号纬度 | 契约 | `GyreRow`（Munk 方程）、`Atmosphere`? |
| `WorldContract.R_EFF` | 有效半径 | m | 6,366,198 | 契约 | 全部「度↔米」换算 |
| `WorldContract.Z_CYCLE` / `MAX_D` | z 周期 / 半周期 | m | 20,000,000 / 10,000,000 | 契约 | 全部 |

---

## 2. 岩石圈 / 海陆（`sim.litho.PlateField`）

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `PlateField.isLandWithCell` | 海陆 | `bool` | **逐点**；`cell` = 板块量化格（PLATE_CELL = 2.4e6 m） | 唯一 | 方块层、群系 LUT、`Atmosphere.kappaAt`、`SimClimate`、`CoastalLayer.coastTangent` |
| `PlateField.landScoreWithCell` | 大陆度评分 | \[−1,1\] | 逐点，`cell` 量化 | 唯一 | `kappaAt`、`eastness` |
| `PlateField.landFractionWithCell` | 半径 R 内的陆地占比 | \[0,1\] | **圆盘平均**（**不是**距离！D18-(c2)） | 唯一 | `kappaAt` 的历史路径 |
| `PlateField.elevationWithCell` | 地面/海床高程 | m | 逐点；海为负 | 唯一 | 方块层、`surfaceTemp`（−Γhκ）、`SimTerrain.compose` |
| `PlateField.coastDistanceNew` | 到海岸的距离 | m（带符号） | **陆负 / 海正 / 岸线 0**；窗内无岸线 ⇒ **±2·maxSearch**（哨兵，**不是 0**） | 唯一 | `SimClimate.coastD`（COAST_FINE=40 km）/ `coastFar`（COAST_FAR=1.6e6 m） |
| `PlateField.PLATE_CELL` | 板块格边长 | m | 2,400,000 | | ⚠ 改变它必须重测 `Atmosphere.KAPPA_MEAN`（P420/P491） |

---

## 3. 大气（`sim.atmos`）

### 3.1 `ZonalTables` —— 外生纬向表

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `ZonalTables.tOceanK` | **洋面**纬向年均气温 | K | **\|lat\|**（偶）；ERA5 观测；5° 表 + 线性 | `gen_zonal_tables.py` | `Atmosphere.oceanBaseK` |
| `ZonalTables.tLandK` | **陆面**纬向年均气温 | K | **\|lat\|**；**含地球自己的海拔** | 同上 | `Atmosphere.landMinusOceanSLK` |
| `ZonalTables.landMeanElev` | 陆面平均高程 | m | \|lat\|；ETOPO1 | 同上 | 同上（剥海拔） |
| `ZonalTables.tZmSym` | 地球**全表面**纬向年均气温 | K | \|lat\|；**只作参照** | 同上 | `Atmosphere.tZonalMean` |
| `ZonalTables.zfEarth` | 地球纬向陆地占比 | \[0,1\] | \|lat\|；**只作参照** | 同上 | 探针 |
| `ZonalTables.aLand` / `aSea` ← `A_LAND_K` / `A_SEA_K` | 季节振幅 A(φ) | K | \|lat\|；**10° 分段线性** ⚠ 进 `zonalSlTemp` 的二阶导。⚠⚠ **D83：`aSea` 的 60~90° 段是「开阔水面」口径（2.8/1.5 K @70°/80°），而消费者需要的是「纬向平均（含冰盖海洋）」口径（实测 10.76/11.49 K）—— 差 2.6~7.7 倍** | ERA5 | `Atmosphere.seasonalAnomaly` |
| `Atmosphere.CELL_LAG` | 气压带滞后 | rad | 30 天 | 手定 | `cellPressure` |
| `Atmosphere.DELTA_PHI0` | 旧的季节平移幅度 | rad | **@Deprecated**，生产已不用（只旧探针） | | |
| `PrecipField.deformRadius` | 罗斯贝变形半径 | m | 诊断/涡动项 | 本类 | `eadyGrowth` 等 |
| `ZonalTables.pRef` / `carrier` | 纬向平均海平面气压 | Pa | \|lat\|；10° 分段线性 | 观测 | `seaLevelPressure`、`cellPressure` |
| `ZonalTables.pRefSlopePerRad` | dp_ref/dlat | Pa/rad（**带符号**） | **中心差分 ±5°**（D1 修复；赤道自然给 0） | 本类 | `Atmosphere.wind`（只进 v） |
| `ZonalTables.uZm` / `uZmSea` / `uZmBlend` | 纬向平均纬向风 | m/s | **单相位 θ**；\|lat\| + `hemi` 反相；全球 850 hPa 表 / **洋面 10 m 表** 两套 | ERA5 | `Atmosphere.wind` 的 `+U_zm`、`stormGate` |
| `ZonalTables.wZm` | 纬向平均上升速度 | m/s | \|lat\|；**无季节**（W_ZM 表） | 手定（§注释） | `PrecipField.wEff` |
| `ZonalTables.SEA_ONLY_UZM` | 开关 | `bool` | 进 `configStamp` | | |

### 3.2 `Atmosphere`

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `Atmosphere.tZonalMean` | **地球**纬向年均气温 | K | \|lat\|；**参照量，不是本世界的均值** | `ZonalTables.tZmSym` | `LANDS_ANNUAL_IN_PRESSURE` 的 A/B、探针 |
| `Atmosphere.oceanBaseK` | 洋面支年均气温 | K | \|lat\|；**海平面等效** | `ZonalTables.tOceanK` | `annualSeaLevelTemp` |
| `Atmosphere.landMinusOceanSLK` | 陆地−海洋（海平面） | K | \|lat\|；**已剥海拔** | 本类 | `annualSeaLevelTemp` |
| `Atmosphere.landSeaAnnualAnomaly` | 海陆年均对比 | K | κ 连续混合；**不进 p'**（默认） | 本类 | `? 目前只被 annualSeaLevelTemp 内联` |
| `Atmosphere.annualSeaLevelTemp` | **海平面等效年均地表温度** | K | **年平**；κ 连续；SST' 从 `(1−κ)` 进 | 本类 | `surfaceTemp`、`PrecipField.mmPerDay`、`SimClimate.solveNode` |
| `Atmosphere.zonalMeanSeaLevelK` | **本世界**纬向平均（κ=⟨κ⟩） | K | 年平；\|lat\| | 本类 | `PrecipField.zonalSlTemp`、`OceanField.dTdz`、`SimClimate.airT` |
| `Atmosphere.seasonalAnomaly` | 季节温度异常 | K | **单相位 θ**；A(0)=0 硬约束；南半球相移 π | 本类 | `surfaceTemp`、`PrecipField`、`SimTerrain.warmest/coldestMonth` |
| `Atmosphere.surfaceTemp` | 地表温度 | K | **单相位 θ**；**含** `−Γ·h·κ` | 本类 | 方块层、`SimClimate.solveNode`、探针 |
| `Atmosphere.pressureAnomaly` | 地面气压异常 p' | Pa | 代数式；**海陆年均对比被解析抵消** | 本类 | `windAt`（差分）、`seaLevelPressure`、探针 |
| `Atmosphere.windAt` | 风（**速度**，含 U_zm） | m/s | **单相位 θ**；梯度步长 `gradStep` 参数化 | 本类 | `PrecipField` 的散度、`SimClimate.windAt` |
| `Atmosphere.windStress` | 风应力 τ | **Pa** | = ρ·C_D·\|U\|·U；**不是速度** | 本类 | `OceanField.curlAtmos`、`CoastalLayer` |
| `Atmosphere.kappaAt` | 大陆度 κ | \[0,1\] | 193 点环采样，**与 θ 无关** | 本类 | 全部温度/风 |
| `Atmosphere.KAPPA_MEAN` | 本世界全球平均陆地占比 | \[0,1\] | **常数**（P420: 0.3280；P491: 0.3196）⚠ P491 实测它**沿纬度不是平的**（0.26~0.41），未实现 | 手定 | `cellPressure`、`zonalMeanSeaLevelK`、`zonalSlTemp` |
| `Atmosphere.LANDS_ANNUAL_IN_PRESSURE` | 开关 | `bool` | 进 `configStamp` | | |
| `Atmosphere.SEASON_SHAPE_FROM_OBS` | 开关 | `bool` | **D79 的 A/B**：季节项形状用单一谐波（false，现状）还是观测年循环形状（true）。进 `configStamp`。⚠ 测量结果见 §225：true 分支会做出**非单调**的纬向平均温度，**不许直接翻** | | |
| `ZonalTables.SEASON_SHAPE` | 观测季节形状 | 无量纲 | 19 纬 x 12 月，零年均、单位半振幅；**只被 true 分支引用** | `gen_season_shape.py` | `Atmosphere.seasonalAnomaly` |
| `ZonalTables.SEASON_SHAPE_N` / `SEASON_SHAPE_PHI0` | 表尺寸 / 相位偏移 | 个 / 无量纲 | PHI0 = 171/365.25（θ=0 ↔ 6 月 21 日） | 同脚本 | `seasonShape` |
| `ZonalTables.seasonShape` | 观测季节形状（插值后） | 无量纲 | \|lat\| 线性插值、月**循环**线性插值；半球反相 = 0.5 月位置。⚠ 已被路线 C″ 取代，**不再被生产引用**（保留供 P493 对照） | 本类 | 无 |
| `ZonalTables.T_ZM_SL_MONTH` / `T_ZM_SL_ANN` | 观测月平均纬向剖面（海平面等效） | K | 19 纬 x 12 月 + 同一张表的 12 月平均（**同源**）；**只被 true 分支引用** | `gen_tzm_sl_month.py` | `Atmosphere.seasonalAnomaly`（true 分支） |
| `ZonalTables.tZmSlMonth` / `tZmSlAnnual` | 上表的插值访问器 | K | 同 `seasonShape` 的口径 | 本类 | `seasonalAnomaly`（true 分支） |

### 3.3 `PrecipField`

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `PrecipField.mmPerDay` | 降水 | mm/day | **单相位 θ**；生产路径由 `SimClimate` 取 4 相位平均 | 本类 | 方块层群系、`SimClimate`、`CommandTalosMap`、验收 |
| `PrecipField.zonalSlTemp` | 纬向平均海平面气温 | K | **单相位 θ**；κ = `KAPPA_MEAN`。⚠ 非 null 的 `ZONAL_PROFILE_OVERRIDE` 会**整体替换**它 | `Atmosphere.zonalMeanSeaLevelK` | `moisture`、`eddyMfc`、`eddyWEquivalent`、`eadyGrowth`、`deformRadius`、`staticN`、`columnWater` |
| `PrecipField.ZONAL_PROFILE_OVERRIDE` | **诊断钩子** | 接口/null | **D79 判读专用，生产恒为 null**（默认 null ⇒ 逐位不变，P495 A 段已证）；进 `configStamp` | 探针 | `zonalSlTemp` |
| `PrecipField.moisture` | 近地比湿 | kg/kg | 0.8·q_sat(T)·exp(−h/H_MOIST·κ) | 本类 | `mmPerDay`、`columnMoisture` |
| `PrecipField.columnWater` | 气柱水汽 | **kg/m²** | = moisture·ρ_air·H_MOIST（D48 修复，原先少乘 ρ） | 本类 | `eddyWEquivalent` |
| `PrecipField.eddyMfc` | 涡动水汽通量辐合 | kg/(m²·s) | **二阶导**：`(W(+5°)−2W(0)+W(−5°))/dy²`，dy 为**弧度** | 本类 | `mmPerDay` |
| `PrecipField.eddyWEquivalent` | 涡动的 w 当量 | m/s | MFC/ρ_w | 本类 | 探针（分解类：**必须连带自检 d²W/dφ²**，见 P493） |
| `PrecipField.stormGate` | 风暴轴西风门 | \[0,1\] | smoothstep(u_zm/U0_STORM)；**单相位 θ** | 本类 | `eddyMfc` |
| `PrecipField.EDDY_DPHI_DEG` | **求导步长** | **deg** | **5.0**；进 `curv` 的分母（用弧度） ⚠ 单位陷阱：E64 就是栽在 deg/rad | | |
| `PrecipField.UPWIND_STEP` | 上风取样距离 | m | 150,000 | | |
| `PrecipField.wEff` | 有效上升速度 | m/s | = w_zm(φ−Δ) + clamp(−H_bl·divU) | 本类 | `mmPerDay` |

---

## 4. 海洋（`sim.ocean`）

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `OceanField.anomalyAt`（`public static synchronized`，E65 提取器盲点已修） | SST 距平 | **K** | 行惰性；**唯一注入点**是 `Atmosphere.SST_PROVIDER`（D56） | `GyreRow`+`SeaSurfaceTemp` | 全部温度/降水 |
| `OceanField.H_TOTAL` | 总深度 | m | 4000 | | `GyreRow`、`CoastalLayer` |
| `OceanField.A_H` | 水平涡动粘性 | m²/s | 1.9e4 ⇒ δ=(A_H/β)^⅓≈97 km | | |
| `OceanField.ROW_H` | 行间距 | m | 5,000 | | |
| `OceanField.ROWS` | 行数 | 个 | 64（⇒ 313 km/行） | | |
| `GyreRow.solve` / `solveSpan` | Munk 解 | `Row`（ψ 单位 **m²/s**） | 4 阶 BVP；ψ_max × H_TOTAL = **Sv** | | `OceanField` |
| `GyreRow.CURL_STRIDE` | 旋度采样步长 | 格 | 10（§214 优化；进 `configStamp`） | | |
| `CoastalLayer.rossbyRadius` | 罗斯贝变形半径 | m | = √(g′·H)/f，f 下限 F_MIN | | `OceanField` |
| `CoastalLayer.hcLocal` | 沿岸层厚度 | m | 由 τ 沿岸分量 | | `jetPeak`、`transportSv` |
| `CoastalLayer.jetPeak` | 沿岸急流峰值 | m/s | | | `OceanField`（A3 判据） |
| `CoastalLayer.transportSv` | 沿岸输运 | Sv | | | 探针 |
| `SeaSurfaceTemp.anomaly` | 表层温度距平 | K | 由 ψ 松弛 | | `OceanField` |
| `SeaSurfaceTemp.ANOM_MAX` | 距平上限 | K | 8.0 | | |
| `SurfaceLayer.at` | 表层速度 | m/s | **速度**（= 正压×hTotal/H_TRANSPORT + Ekman） | | 探针/地图 |

---

## 5. 运行时（`sim.runtime`）

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `SimClimate.sample` | 气候坐标 | `Coords` | 粗格点（CELL=10 km）+ 双线性；**静态**（4 相位平均或年平） | 本类 | 群系 LUT |
| `SimClimate.kappaAt` | κ（瓦片版） | \[0,1\] | 双线性；**≠** `coords().continent`（D46） | 本类 | 雪线、方块层 |
| `SimClimate.maritimeInland` | 海洋影响权重 | \[0,1\] | 大量程距离场，MARITIME_SCALE=200 km | 本类 | 雪线（D46） |
| `SimClimate.surfaceTempK` / `windAt` | 瓦片内温度/风 | K / m/s | 与生产同源 | 本类 | 雪线/海冰 |
| `ClimateCoords.Coords.temp` | 归一化温度 | \[0.10,0.90\] | = 0.10 + (T_sfc−255.15)/55；**冷端会截断**（§217.8） | 本类 | 群系 |
| `ClimateCoords.Coords.airT` | 气团冷暖轴 | \[−1,1\] | = (tSea − tzm)/AIRT_SCALE；**§217 起有真实物理内容**（D8-b） | 本类 | `airMass` |
| `SimTerrain.coldestMonthTempK` | 最冷月温度 | K | 年平 − A；**海冰唯一判据**（D72） | 本类 | 方块层海冰 |
| `SimTerrain.warmestMonthTempK` | 最暖月温度 | K | 年平 + A；**雪线唯一判据**（D73） | 本类 | 方块层 / 群系 ALPINE |
| `SimTerrain.SEA_ICE_T` | 海水冰点 | K | 271.35（−1.8 °C，S=35） | 观测 | 海冰 |
| `SimTerrain.SNOW_T` | 雪线温度 | K | 273.15 | 观测 | 雪线 |

---

## 6. 非物理量（开关 / 计数器 / 缓存 / 调参）—— 机器核对用

| 符号 | 类别 |
|---|---|
| `Atmosphere.MEMO_MAX`,`memoHits`,`memoMisses`,`beginMemo`,`endMemo`,`memoOn`,`kappaMemo`,`suppressSst`,`sstSuppressed`,`PLATEAU_AMP`,`CELL_GAIN`,`CELL_MIGRATION`,`CELL_TROPIC_GATE_DEG`,`COAST_WIND_ON`,`COAST_WIND_V`,`COAST_WIND_W`,`SEA_ONLY_UZM` | 开关/缓存/调参 |
| `SimClimate.ENABLED`,`CELL`,`COAST_FINE`,`TILE_X`,`TILE_Z`,`GRAD_STEP`,`UPWIND_OFFSET`,`SLOPE_STEP`,`SLOPE_SCALE`,`CACHE_LIMIT`,`SEASON`,`T_LO_K`,`T_HI_K`,`TEMP_LO`,`TEMP_SLOPE`,`MOIST_A`,`P_MIN_MM_YR`,`AIRT_SCALE`,`AIRT_SEALEVEL`,`SST_PROVIDER`,`SOLVE_*`,`NODE_COUNT`,`CACHE_HIT`,`CACHE_MISS`,`SAMPLE_COUNT`,`resetStats`,`clearCache`,`configure`,`configStamp`,`coords`,`MARITIME_SCALE`,`COAST_FAR` | 配置/计数/缓存 |
| `OceanField.ENABLED`,`GRAD`,`JET_RANGE_RD`,`PH4`,`BAND_W`,`solveCount`,`WARM_SCAN`,`warmAll`,`configStamp`,`reentryBlocked`,`OceanWiring.*` | 配置/计数 |
| `BasinFinder.SAMPLE`,`MAX_R`,`earlyOutMismatch`,`WALL_TOL`,`CFL_SAFETY`,`safeDt` | 数值参数 |
| `CoastalLayer.G_PRIME`,`L_RELAX`,`F_MIN`,`UPWELL_SEASON_DAYS`,`UPWELL_WIDTH`,`COAST_TAN_*`,`SMOOTH_KM`,`smoothedWindowM`,`steady*`,`configStamp`,`eastBandContribution`,`coastTangent` | 数值参数/求解 |
| `SurfaceLayer.*`,`SeaSurfaceTemp.LAMBDA`,`SURF_FACTOR`,`configStamp`,`GyreRow.OMEGA`,`latOf`,`betaForLatitude` | 数值参数 |
| `WorldContract.OMEGA`,`DAYS_PER_YEAR`,`hemisphereSign`,`PlateField.SEA_LEVEL`,`COAST_BLEND`,`MAX_OCEAN_HALF`,`OCEAN_BREAK_H`,`OCEAN_BREAK_FRAC`,`landScore`,`elevation`,`elevationWithCellRaw`,`elevationFull`,`isLand`,`isLandFull`,`edgeDistance` | 数值参数/重载 |
| `SimTerrain.ENABLED`,`LAND_GAIN`,`OCEAN_GAIN`,`SEABED_RELIEF`,`DETAIL_AMP`,`DETAIL_W`,`BEACH_BLOCKS`,`SNOW_FROM_TEMP`,`seedOf`,`compose`,`seasonalAmpK` | 配置/重载 |
| `MapWriter.*` | 出图 |

---

## 9. 物理常数与辅助函数（`calibers_check.ps1` 第一次运行后按「未分类」清单补齐）

> 这一节是**被机器逼出来**的：§1~§7 写完之后，提取器仍然报出 217 个未分类的 `public static` 成员。
> 下面把其中**有物理量纲**的补齐；剩下的（地形/群系的调参常数）见 §10 的**棘轮**。

### 9.1 大气与通用常数

| 符号 | 单位 | 口径 / 说明 |
|---|---|---|
| `Atmosphere.GAMMA` | K/m | 标准大气递减率 6.5e-3；只作用在**陆地**（乘 κ） |
| `Atmosphere.CHI` | 无量纲 | p' 的归一系数 0.55 |
| `Atmosphere.K_P` | Pa/K | = CHI·ρ·g·H_EFF/T0（引用同名字段，防静默失配：D5） |
| `Atmosphere.H_EFF` | m | 12,000（**对流层**厚度，不是边界层） |
| `Atmosphere.RHO_AIR` | kg/m³ | 1.225 |
| `Atmosphere.GAMMA_OCN`,`GAMMA_LND` | s⁻¹ | 摩擦系数（2.5e-5 / 6.0e-5） |
| `Atmosphere.CD_OCN`,`CD_LND` | 无量纲 | 拖曳系数（1.3e-3 / 3.0e-3） |
| `Atmosphere.H_BL` | m | 边界层厚度 1000 |
| `Atmosphere.U_MAX` | m/s | 地面风速硬上限 25 |
| `Atmosphere.OBLIQUITY` | rad | 黄赤交角 23.44° |
| `Atmosphere.PSI_LAND_DAYS`,`PSI_SEA_DAYS` | day | 季节相位滞后（30 / 60） |
| `Atmosphere.PLATEAU_H_REF` | m | 高原放大参考高度 3000 |
| `Atmosphere.T0`,`T2`,`T4` | K | ⚠ **历史勒让德系数**，§217 起**不再是温度场来源**（只被 K_P 与旧探针引用） |
| `Atmosphere.tropicGate`,`cellPressure`,`SUM_SHAPE`,`coastalAlongshoreV`,`gammaOf`,`cdOf`,`subsolarLat`,`theta`,`clamp01`,`p2`,`p4`,`sstAnom` | 视返回 | 辅助函数；`sstAnom` 是 **SST' 唯一注入点**（D56） |
| `PrecipField.EPS_C` | 无量纲 | 对流效率 0.75 |
| `PrecipField.RH_SEA` | 无量纲 | 海面相对湿度 |
| `PrecipField.G_ACC` | m/s² | 重力 |
| `PrecipField.CP_AIR`,`RHO_AIR`,`RHO_WATER`,`P_SURF` | SI | 物性常数 |
| `PrecipField.H_MOIST` | m | 水汽标高 2000 |
| `PrecipField.EDDY_DEPL_H` | m | 气柱过山损耗尺度 1000 |
| `PrecipField.EDDY_MIX`,`EDDY_PHYS_GAIN`,`EDDY_TAU`,`EDDY_CLOSURE`,`EADY_COEF`,`eadyGrowth` | 混 | 涡动闭合（EDDY_TAU 秒；EADY_COEF 无量纲） |
| `PrecipField.ITCZ_MIGRATION` | rad | ITCZ 季节迁移幅度（**唯一**给中纬以外雨带季节性的量） |
| `PrecipField.U0_STORM`,`W_LOC_MAX` | m/s | 风暴门阈值 / 局地 w 上限 |
| `PrecipField.qSat`,`precip`,`wEff`,`upwindElev`,`depletion`,`depletionCol`,`moisture` | 视返回 | 辅助函数 |
| `ZonalTables.P_REF_PA`,`P_REF_BASE` | Pa | 纬向平均海平面气压表 / 基准 101300 |
| `WorldContract.OMEGA`,`DAYS_PER_YEAR`,`hemisphereSign` | SI | 地球自转与历法 |

### 9.2 海洋常数

| 符号 | 单位 | 口径 / 说明 |
|---|---|---|
| `CoastalLayer.RHO`,`SurfaceLayer.RHO` | kg/m³ | 1025（**多份独立拷贝**，改一处不会改另几处 —— 同一个物理量在海洋层有两份定义，**已记账未统一**） |
| `SurfaceLayer.A_Z` | m²/s | 垂向涡动粘性 0.01 |
| `SurfaceLayer.EKMAN_MAX` | m/s | 0.35；`EKMAN_ENABLED` 默认 **false** |
| `SurfaceLayer.H_TRANSPORT` | m | 800 |
| `CoastalLayer.G_PRIME` | m/s² | 约化重力 0.02 |
| `CoastalLayer.H_THERMOCLINE` | m | 150 |
| `CoastalLayer.L_RELAX` | m | 3e6 |
| `CoastalLayer.F_MIN` | s⁻¹ | 科氏下限（赤道保护） |
| `CoastalLayer.SMOOTH_KM` | km | 1500（沿岸窗平滑尺度） |
| `CoastalLayer.FORMULA_REV` | 版本号 | **公式版本位**（非物理量）：只用来让上层缓存失效 —— D58 的准入判据；D80 起 = 2。**改本类任何公式都要 +1** |
| `CoastalLayer.steadyPeriodic`,`steadySegmented`,`steadySmoothed`,`smoothedWindowM`,`hcLocalRaw`,`eastBandContribution` | 视返回 | 求解/辅助 |
| `OceanField.spanOf`,`bandMeansAt`,`solveNanos`,`installedSeed`,`isWarm`,`warmRows`,`warmRowsDone` | 视返回 | 诊断/接线 |

### 9.3 地形 / 群系（`space.talos.chunk`）

| 符号 | 单位 | 口径 / 说明 |
|---|---|---|
| `LandformField.MTN_RISE_SCALE`,`DETAIL_MTNCOMP_SCALE`,`axial*/amp*/aGate*` | m / 无量纲 | 山层形状参数 |
| `V2TerrainGen.BEACH_LAND_BLOCKS`,`SHELF_BLOCKS`,`GRAVEL_SEA_DEPTH`,`SAND_SEA_DEPTH`,`OCEAN_MAX_DEPTH`,`OCEAN_MIN_DEPTH`,`BASIN_EXTRA_*`,`SOFT_CAP_*`,`MTN_RISE_*` | m / 无量纲 | 地形剖面参数 |
| `V2BiomeSelect.ALPINE_TEMP_*`,`BASIN_*`,`INLAND_*`,`MOIST_*`,`TEMP_*`,`W_LAT` | 混 | 群系门限（**坐标空间**，不是物理单位 —— 见 §5 `Coords.temp`） |
| `V2BiomeField.BLUR_*`,`V2BiomeField.NX`,`V2BiomeField.NZ`,`V2BiomeField.SX`,`V2BiomeField.SZ`,`V2BiomeField.CELL`,`texPow`,`textureSeed` | 格/m | LUT 与纹理参数 |
| `LandformField.*`,`OrographyField.*`,`ClimateCoords.AIR_DT`,`AIR_DQ`,`ENABLE_*`,`SST_GAIN`,`SST_OFFSET`,`sstExpectation`,`ENABLE_AIRMASS` | 混 | ⚠ 旧栈（D68/D70）；`AIR_DT/AIR_DQ` = **D66**（气团加项消失） |
| `MapWriter.*` | — | 出图 |

---

## 10. 未分类棘轮（`UNCLASSIFIED_BASELINE`）

剩余的 `public static` 成员都没有登记在上面任何一张表里。**它们不会静默增长**：
`calibers_check.ps1` 会把当次提取的未分类数写进 `build/eoh_probe/mtn/_calibers_unclassified.txt`，
并与本行的基线比对 —— **数目变多就说明「多了一个没人登记的生产者」**，那正是 D67/D75/D78 的缺陷类。

```
UNCLASSIFIED_BASELINE=0
```

---

## 7. 验收探针清单（与 `git ls-files` 交叉核对 —— D67/D75/D78 三次同族缺陷的守卫）

```acceptance-probes
P258 P284 P296 P268 P452 P293 P292 P285 P442 P294 P295 P297 P477 P478 P479 P480 P484
```

## 8. ⚠ 已知的**未对账 / 未实现**条目（不许当成"已核对"）

| # | 条目 | 状态 |
|---|---|---|
| 1 | `KAPPA_MEAN` 沿纬度**不是平的**（P491 实测 0.26~0.41，赤道最低、±55° 最高） | **未实现**：生产仍用常数 0.328（`cellPressure` 与 `zonalMeanSeaLevelK` 共用同一个常数，改一个会造成口径分裂） |
| 2 | 地球锚点表**南北对称化**的代价：真实 Δ_SL 在 75° 是北 −6.4 / 南 −16.8 K，对称化后两边都用 −11.6 K | **记账未实现** |
| 3 | ERA5 的 `sst` 掩膜把**多年海冰**并进「陆地」⇒ `T_OCEAN` 在 \|lat\|>70 实为「冰盖海洋气温」 | **口径已记账** |
| 4 | 模型陆面平均高程 < 地球 ⇒ 陆地系统性偏暖 `Γ(z̄_earth − z̄_our)` | **记账未实现** |
| 5 | **D79**：涡动替身没有季节迁移机制（P493：峰位 46.50°/44.00°，迁移 2.5°）⇒ B2.b 失败 | **未修**，下一轮主任务 |
| 6 | 三路审计子代理（`Audit sim/ocean`、`Audit PlateField and MapWriter`、`Audit SimClimate and SimTerrain`）的线索 | 见 §195/§199 的落地情况；**未有新线索进表** |
