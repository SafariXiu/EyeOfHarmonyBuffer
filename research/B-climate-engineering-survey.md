# 程序化行星气候实现调研（B1 WorldEngine / B2 Undiscovered Worlds 系 / B3 其它开源项目）

调研方法：全部通过 raw.githubusercontent.com / ungh.cc / data.jsdelivr.com 读取**源码**，行号取自 master/main HEAD（2026-09 时点）。
注意：web_fetch 单次截断 100000 字符，超大文件（Undiscovered Worlds `globalclimate.cpp` 515KB）只能读到前约 3210 行，下文对超出部分明确标注"未取到"。

---

## B1. WorldEngine（Python + platec）

1. **项目**：[Mindwerks/worldengine](https://github.com/Mindwerks/worldengine) ；Python；MIT（LICENSE.txt）；**仍在维护**（GitHub pushed_at=2026-08-27，HEAD 提交 43e51c6137 "drop pynoise and make use of numpy"，2026-08-24）。旧仓库 [ftomassetti/worldengine](https://github.com/ftomassetti/worldengine) 同血统。
2. **算的场**：elevation / plates / ocean / sea_depth / temperature / precipitation / humidity / irrigation / permeability / watermap(river) / biome / icecap / lakemap / rivermap。**没有 wind、没有洋流、没有水汽平流。**
3. **气候步骤顺序**（`worldengine/generation.py` L235-L269）：`Temperature → Precipitation → Erosion → Watermap → Irrigation → Humidity → Permeability → Biome → Icecap`（前置 platec 板块、center_land、place_oceans_at_map_borders）。子种子由 `RandomState(seed)` 派生 100 个（L220-L233）。

### 温度：解析式，不迭代（temperature.py `_calculate()` L30-L95）
```
distance_to_sun = clip(normal(1.0, 0.12/1.177410023), 0.1, ∞)，然后 **平方**（逆平方律）  # L61-L63
axial_tilt = clip(normal(0, 0.07/1.177410023), -0.5, 0.5)                      # L65-L66
latitude_factor = interp(y/height-0.5, [tilt-0.5, tilt, tilt+0.5], [0,1,0])     # L79-L81
n = simplex(x*n_scale/(16*8), y*n_scale/(16*8), octaves=8, base=seed)           # L84, n_scale=1024/height
temp = (latitude_factor*12 + n*1) / 13.0 / distance_to_sun                      # L90
drop = (elevation - mountain_level)/30
altitude_factor = where(elev > mountain_level+29, 0.033, 1.00 - drop)           # L93-L94
T = where(elev > mountain_level, temp*altitude_factor, temp)
```
即"纬向余弦 + 单形噪声 + 高度线性递减"，纯 numpy 向量化，O(W·H) 次 8-octave simplex。mountain_level = 海拔最高 3% 分位（generation.py L113）。温度 6 个分位（world.py L87）：temps=[0.874, 0.765, 0.594, 0.439, 0.366, 0.124] → polar/alpine/boreal/cool/warm/subtropical。

### 降水/水汽：**不是**迭代式水汽平流（重点否定）
`worldengine/simulations/precipitation.py` L31-L109：
- L53：`precipitations = simplex(x*n_scale/(64*6), y*n_scale/(64*6), octaves=6, base=seed)`（**纯噪声**）；
- L57-L61：x < width/4 处与 +width 版本插值做横向接缝；
- L99：`curve = t**gamma_curve * (1-curve_offset) + curve_offset`，默认 gamma_curve=1.25、curve_offset=0.2（world.py L89-L90）；
- L107：`precipitations = ((p*curve - min)/(max-min))*2 - 1`，最终范围 **[-1, 1]**。
**没有风向、没有从海洋出发的上风扫描、没有每步衰减系数、没有山脉抬升判据。** 唯一地形效应是温度的高度递减，经 gamma 曲线间接影响降水。
`hydrology.py` L17-L80 的 `_watermap(world, 20000)` 才是"降水→河流"：随机 20000 个陆地起点递归下坡分水（`droplet()`，递归，ql>0.05 才继续），与风无关。
**文档与代码不符（可直接引用）**：README.md L17 "…erosion, **rain shadows**, Holdridge life zones…"、L214 "precipitations are calculated considering latitude and **rain shadow** effects" —— 源码中不存在 rain shadow。**不要把 WorldEngine 当 rain shadow 参考实现。**

### 湿度（humidity.py L15-L36）
```
data = (precipitation*1.0 - irrigation*3) / (1.0+3)            # L20-L23
quantiles[key] = find_threshold_f(data, humids[i], ocean)      # L29-L35，仅非海洋格
humids = [0.941, 0.778, 0.507, 0.236, 0.073, 0.014, 0.002]
```
`irrigation`（irrigation.py L13-L54）：对每个**海洋格**用 ln(sqrt(dx²+dy²)+1)+1 核在半径 radius=10 的窗口把 watermap 撒向陆地 —— 全项目最贵步骤之一（21×21 卷积）。

### 洋流
**无**。全仓库无 current/ocean current 文件或符号（simulations/ 下仅 basic、biome、erosion、humidity、hydrology、icecap、irrigation、permeability、precipitation、temperature）。erosion.py L129 注释写 "Using the wind and rainfall data"，但 L142 实际只用 `world.layers["precipitation"].data`。

### 是否全局迭代 / 复杂度 / 网格 / 耗时
- 无任何全局椭圆或收敛迭代：O(W·H) 向量化算子 + 局部窗口卷积（irrigation r=10）+ 20000 次递归逐滴（hydrology，深递归，CLI 因此有 `--recursion-limit`）。
- 默认网格：`worldengine/cli/main.py` L336/L345 `-x/--width default="512"`、`-y/--height default="512"`；L354 plates 默认 10；L362 ocean level 默认 2000。
- **实测耗时：未找到**任何公开 benchmark。源码只在降水里自带计时打印（precipitation.py L16-L28："precipitations calculated. Elapsed time … seconds"）。可确定最慢项为 hydrology 与 irrigation。

### 输出格式
Protobuf（`worldengine/World.proto`；另有 HDF5 可选）。气候字段（proto 字段号）：humidity=14（DoubleMatrixWithQuantiles + 7 quantile）、precipitationData=23（low=24、med=25）、temperatureData=26（阈值 27-32）、icecap=36、irrigation=15、permeabilityData=16。**全部 float64 矩阵**（非量化 uint8）；温度约 [0,1]，降水严格 [-1,1]，湿度为加权差再取分位；冰厚 = freeze_threshold-(t-temp_min)，freeze_threshold=(polar阈值-min)*0.60（icecap.py L36-L57）。

### 能借鉴什么（针对东岸风符号卡点）
**不能借鉴风**：WorldEngine 完全没有风场，降水是"噪声×温度"，与经度/海陆/风向无关。可借鉴：① 分位阈值→Holdridge 生物群系（biome.py L26-L106）；② 概率式冰盖生长（含邻格冻结影响 surrounding_tile_influence=0.5，icecap.py L80-L91）；③ 气候层用 float64 矩阵 + 阈值/分位随层存储的序列化设计。

---

## B2. Undiscovered Worlds（JonathanCRH）

1. **项目**：[JonathanCRH/Undiscovered_Worlds](https://github.com/JonathanCRH/Undiscovered_Worlds)（新版，globalclimate.cpp 515KB）；经典版 [Undiscovered_Worlds_Classic](https://github.com/JonathanCRH/Undiscovered_Worlds_Classic)（424KB）、fork [MightyBOBcnc/Undiscovered_Worlds](https://github.com/MightyBOBcnc/Undiscovered_Worlds)。**C++ / SFML + ImGui**；**GPL-3.0**；**仍在更新**（MSVS2022 + linux makefile）。作者博客（二手）：https://undiscoveredworlds.blogspot.com/2019/01/what-is-undiscovered-worlds.html
2. **算的场**：windmap（整数风级）、jan/jul 气温（jantemp/jultemp、mintemp/maxtemp）、jan/jul 降水、seaice、climate 分类（calculateclimate L580）、山地降水。运行在**立方球 6 面网格**（planet.hpp L756：`int itsedge; // Default is 512`）。
3. **方程/近似**
   - **风 createwindmap（globalclimate.cpp L1378-L2128）**：在展开的 2D 经纬图（wwidth=4*edge × hheight=2*edge，edge=512 → 2048×1024）上画 **10 条风带分界线**，纬度（度，`div=height/180`）L1394-L1403：borders[1..10] = 28 / 32 / 58 / 64 / 86 / 94 / 116 / 122 / 148 / 152（即极地东风下界≈62°N、西风带上界≈58°N、马纬度 32/26°N、信风下界 4°N、ITCZ、南半球镜像）。
     分界线是**沿经度的样条**：节点间距 `pets=20` px（L1409），每节点 y 偏移做随机游走（`maxoffset=25` px ≈ ±4.4° 纬度、`maxoffsetchange=random(1,3)`、附加 `maxextraoffset=5`，L1446-L1450），相邻线按 amount2=(yoffset±Δline)/2 联动，最后用 4 点样条 `curvepos(mm1..mm4,t)` 以 t 步长 0.01 画线（L1557-L1608）。**这是它"非纬向"的唯一来源：风带边界随经度蜿蜒。**
     带内风向**纯纬向、整数**：winddir[zone][0] ∈ {-1,0,+1}（L1612-L1662），rotation==1 时 westerly=+10 / easterly=-10，否则反号（L1706-L1719），写出 setwind(face,i,j,±10)；另出 outsidehorse[] 供降水用（L1879-L1881）。planet.hpp L2185：winddir 返回 easterly=-1 / westerly=1 / none=0。
   - **温度 createwindmap 之后的 createtemperaturemap（L2132-L2257）**：`tempdiff = -0.045*tilt² + 6.872*tilt - 231.7`（L2143；tilt<22.5 时再 +(22.5-tilt)*3.5）；`northpolartemp = avetemp + tempdiff*0.55`、`equatorialtemp = avetemp - tempdiff*0.36`（L2153-L2155）；`polarvariation = tilt*1.55555*1.5`（L2163-L2165）；逐格 lattemp = eqtemp ∓ |lat|*diffperlat，海洋 +2.0（L2219-L2220），加 fractal 噪声（range 8），季节项 latvariation=(|lat|*variationperlat)*0.5（L2213），北半球 jan=min / jul=max（L2245-L2254）。
   - **降水 createrainmap（L2584-L2716）** 调用顺序（L2599/L2610/L2616/L2643-L2715）：createoceanrain → createprevailinglandrain → createmonsoons → adjustseasonalrainfall → smoothrainfall×2 → caprainfall → adjusttemperatures → adjustcontinentaltemperatures → smoothtemperatures → createmountainprecipitation。
     - createoceanrain（L2720 起）：海洋基础雨 =`(min|max temp + 15)*4`，上限 maxoceanrain=1500（L2728-L2757）；再做**海洋陆地阴影**：对每海格沿上风走 wind*landmult（landmult=5）步统计陆地数 dryness，再向下风走 dryness 步每步扣 (dryness-z)*landshadowfactor（landshadowfactor=4）（L2783-L2830）。
     - createprevailinglandrain（L3127 起，仅取到 L3210）：迎风海岸格（winddir==1 且上风是海）播种，`crount = wind*seamult`（**seamult=80**，即最多 320 个上风海格）累积 waterlog，pickup 与海温相关（`fpickuprate = waterpickup*350`、tempfactor=80、mintemp=0.15）；陆上继续 landpickuprate=40、沉降 dumprate=100、坡度判据 slopemin=300 / elevationfactor=0.002 / slopefactor=160-tempdecrease（tempdecrease 默认 6.5*20=130 → 30）、swervechance=2、spreadchance=2、splashsize=1、newseedproportion=0.85、horseSeedproportion=0.95（L3136-L3164）。**createmonsoons() 实现超出 100k 截断，未取到。**
4. **是否需要全局迭代**：**否**（无收敛迭代）。风=画线+逐列填值；降水=**沿风向的一维线积分**（每条种子线 ≤ wind·80 = 320 步）+ 若干固定次数平滑。跨立方体面用预计算 dirpoint[]（fourglobepoints：n/s/e/w 邻居指针）。
5. **复杂度/性能**：源码保留计时注释（L26）`//highres_timer_t timer("Generate Global Climate"); // 9.4s => 8.2s` → **整套全球气候生成约 8.2 秒**，网格 6×512×512 = 1.57M 格（≈5.2 µs/格）。这是本次调研中唯一有明确实测数字的"廉价"全球气候。
6. **输出**：内存 per-tile 数组 + 导出 PNG（风/云/雨/湿度/温度等图层），另有区域级地图；无标准科学格式。
7. **我们能借鉴什么**
   - **风带边界随经度蜿蜒**（随机游走节点 + 样条，±25px≈±4.4°，节点间距 20px）是不做 PDE 也能让风"非纬向"的唯一廉价手段；**但蜿蜒相位是随机种子的，不保证某个海岸的符号正确 —— 只增多样性，修不了 39~52%。**
   - **一维线积分水汽常数**（seamult=80 / dumprate=100 / landpickuprate=40 / slopemin=300 / elevationfactor=0.002）与你已有的"上风地形损耗降水"同构，可直接对标调参。
   - 海洋降水被**上风陆地阴影**削减（landmult=5、landshadowfactor=4）——你可能缺的这个非纬向反馈。
   - 8.2 s / 1.57M 格可作为你的性能标尺。
8. **关键文件/行号/URL**
   - https://github.com/JonathanCRH/Undiscovered_Worlds/blob/master/globalclimate.cpp#L1378 （createwindmap，L1394-L1403 分界线、L1409 pets、L1446 maxoffset、L1612-L1662 winddir 表、L1877 setwind）
   - 同文件 #L2132 （createtemperaturemap）、#L2584 （createrainmap）、#L2720 （createoceanrain）、#L3127 （createprevailinglandrain）、#L517 （createclimatemap）、#L580 （calculateclimate）、#L26 （9.4s→8.2s 计时注释）
   - https://github.com/JonathanCRH/Undiscovered_Worlds/blob/master/planet.hpp#L756 （edge 默认 512）、#L2185 （winddir 语义）

---

## B3. 其它开源"程序化行星气候"项目（源码级）

### B3-1. Azgaar's Fantasy-Map-Generator（FMG）— JS/TS，与你现有方案最接近
1. [Azgaar/Fantasy-Map-Generator](https://github.com/Azgaar/Fantasy-Map-Generator) ；TypeScript（Voronoi 网格，默认 10000 cells）；LICENSE 为自定义（GitHub 识别 NOASSERTION，MIT 风格）；**活跃**（pushed 2026-09-11，5990 stars）。
2. 输出：cells.temp（Int8，°C）、cells.prec（Uint8）、cells.h、biome。
3. 方程：
   - 温度 `src/generators/temperature-generator.ts` L9-L56：分带线性廓线 `tropics=[16,-20]; tropicalGradient=0.15`（L18-L19）；`tempNorthTropic = T_eq - 16*0.15`；`northernGradient = (tempNorthTropic - T_northPole)/(90-16)`（L21-L25）；高度递减 `rn(((h-18)**exponent/1000)*6.5)` 即 **6.5 °C/km**（L38-L42）；`rowLatitude = latN - (y/height)*latT`（L46-L48）。**纯解析、逐格 O(1)。**
   - 降水 `src/generators/precipitation-generator.ts`：**"风从四边进入、沿途掉落湿度"的单向扫描**（L41-L98）：
```
LATITUDE_MODIFIER = [4,2,2,2,1,1,2,2,2,2,3,3,2,2,1,1,1,0.5]     // 每 5° 带，L21
MAX_PASSABLE_ELEVATION = 85                                      // L23
getPrecipitation(humidity,i,n):                                  // L34-L39
   normalLoss = max(humidity/(10*modifier), 1)
   diff = max(h[i+n]-h[i], 0);  mod = (h[i+n]/70)**2              // 抬升判据=高差，山体权重
   return clamp(normalLoss + diff*mod, 1, humidity)
passWind(...):                                                    // L41-L76
   海水格: 下一格是陆地 → prec += max(humidity/rand(10,20),1)（海岸雨）
           否则 humidity = min(humidity + 5*modifier, maxPrec)
   陆地格: h > 85 → 全部掉落；否则掉 precip，humidity -= precip - 1（蒸发 1）
   modifier = (getCellsDesired()/10000)**0.25 * (precipitation%/100)   // L31-L32
```
     风带 `getWinds()` L105-L125：`tier = (|lat-89|/30)|0`（6 个 30° 带），角度取用户可编辑的 `options.map.climate.winds[tier]`；40<angle<140→西风（从西边界进入，逐格 +1 扫描）、220<angle<320→东风（从东边界，-1），另有南/北风整列扫描。
4. **是否全局迭代**：否。每风向全格扫一遍，共 2~4 遍 → **O(N)**，无收敛判据。
5. 复杂度/性能：无公开 benchmark；成本随 cell 数近似线性（modifier 里的 0.25 次幂缩放同时改变物理量，是"分辨率相关参数"的设计坑）。
6. 输出：内存 Int8Array temp / Uint8Array prec（**量化到 1 字节**）→ biome → 渲染。
7. 借鉴点：**FMG 完全不做非纬向风** —— 风向只有 6 个纬度带常数，因此它同样无法解决"东岸一致吹向赤道"，理论上限就是东/西岸各半 —— **你 39~52% 的正确率正是这套结构的极限附近**。可借：`(h/70)**2` 抬升权重、`rand(10,20)` 海岸雨、Int8/Uint8 量化存储。
8. URL：`https://github.com/Azgaar/Fantasy-Map-Generator/blob/master/src/generators/precipitation-generator.ts#L21`（同文件 #L34-L39、#L41-L76、#L105-L125）、`.../temperature-generator.ts#L18-L19`、#L38-L42。默认 winds[6] 数值定义处**未找到**（在 UI 选项默认值中）。

### B3-2. weigert/proceduralweather（Nicholas McDonald, 2018）— C++
1. [weigert/proceduralweather](https://github.com/weigert/proceduralweather) ；C++（libnoise + SDL2）；**未找到 LICENSE 文件**；约 2018 后**不再维护**。配套文章（二手）：https://nickmcd.me/2018/07/10/procedural-weather-patterns/
2. 输出：TempMap / HumidityMap / CloudMap(bool) / RainMap(bool) / WindMap + 5 个 Avg*Map（100×100 网格，worldgen.h 的 Climate 类）。
3. 方程（worldgen.h）：
   - 风 `Climate::calcWind(day,seed,terrain)` L347-L373：**风向是全局 2 维向量**，由 1 维 Perlin 随时间演化 `WindDirection[1]=perlin.GetValue(day/365, seed, seed)`、`WindDirection[2]=perlin.GetValue(day/365, seed+day/365, seed)`（octaves=2、freq=4）；逐格 `WindMap[i][j] = 5*(1-(depth[i][j]-depth[upwind])/1000)`，上风格 = (i+10·d1, j+10·d2)。**方向不随经度变化，只有速度随地形变化。**
   - 温度 `calcTempMap` L471-L520：半拉格朗日上风取样 `TempMap[i][j]=oldTempMap[k][l]` → 4 邻域均值 → `T += 0.8*(1-T)*addSun + 0.6*T*(addRain+addCool)`，addCool=0.5*(WindMap-5)、addSun=(1-depth/2000)*0.008（无云）、addRain=-0.01（有雨且 T>0），clamp [0,1]。
   - 湿度 `calcHumidityMap` L425-L469：上风取样（步长 2*WindMap*WindDirection）+ 9 邻域均值；海面 addHumidity=0.05*TempMap、陆地 0.01；有雨 addRain=-0.8*H；`H += H*addRain + (1-H)*addHumidity`，clamp [0,1]。
   - 云/雨 `calcDownfallMap` L522-L559：平流后判据 **H ≥ 0.35+0.5*T → Rain=1；H ≥ 0.3+0.3*T → Cloud=1**（L547-L556）。
   - 世界生成 `World::generate()` L143-L156：genDepth → erode（气候侵蚀）→ climate.init → calcAverage；主循环 territory.cpp L98-L112 每天 calcWind/calcTempMap/calcHumidityMap/calcDownfallMap，SDL_Delay(100)。
   - `calcAverage()` L260-L300：跑 years*365 天（years=1）并在线平均 `Avg=(Avg*i+X)/(i+1)` —— "收敛"其实是固定时长平均，不是判据驱动。
4. 是否全局迭代：**是固定步数的时间积分**（365 步/年），每步 O(N) 局部算子，无收敛判据。
5. 复杂度/性能：100×100=10⁴ 格；主循环 4 pass/天 + SDL_Delay(100) → 单步 < 100 ms（含渲染）。侵蚀：`depth -= 5*(depth/2000)*(1-depth/2000)*(AvgRain+0.5*AvgWind)`（L242-L247）。worldDepth=4000、海平面 200。
6. 输出：屏幕图层（overlay 0-9：风/云/雨/湿度/温度及其平均，见 readme.md）。
7. 借鉴点：① 上风取样 + 邻域平滑的**半拉格朗日平流，大 Δt 不炸**，适合瓦片预算；② 温度相关降水阈值（H≥0.35+0.5T）比固定阈值更能自动生成副热带干带；③ **风向不随经度变化 → 对你的卡点无用**。

### B3-3. Flokey82/genworldvoronoi — Go（最直接的"非纬向风"参考）
1. [Flokey82/genworldvoronoi](https://github.com/Flokey82/genworldvoronoi) ；Go；LICENSE 文件存在（Apache-2.0 风格，未逐字核对）；**活跃**。是 [redblobgames/1843-planet-generation](https://github.com/redblobgames/1843-planet-generation) 的 Go 移植 + 气候扩展。
2. 输出：RegionToWindVec（全局风）、RegionToWindVecLocal（局地风）、AirTemperature/WaterTemperature、Moisture/Rainfall、Currents、Flux/Waterpool、生物群系与文明。
3. 方程：
   - **基础风 getGlobalWindVector(lat)**（geo/wind.go L13-L49）：分段但**连续旋转**的向量（非阶跃风带）：
```
0<=|lat|<=30:  degree = 180 ± 90*|lat|/30       // 赤道处正西，30° 处正南/北（Hadley）
30<|lat|<=60:  degree =  90 ∓ 90*(|lat|-30)/30   // 60° 处与纬线平行（西风带）
60<|lat|<=90:  degree = 180 ± 90*(|lat|-60)/30
return {cos(rad), sin(rad)}   // 作者自注 "buggy or at least not nice"
```
   - **局地风 localWindModeMixed**（L215-L257，作者注明 "Adapted from FreezeDriedMangos … Generate_Weather.js"）：
```
windDir   = 基础风向量
windSpeed = max(0.1, 1 - 2*Δelev*ELEVATION_CHANGE_FACTOR)          // FACTOR=1.0 (L220)
acc = Σ_{nb∈1-ring} û(r→nb) * (T(nb) - T(r))
windDir = windDir + setMagnitude(acc, TEMPERATURE_INFLUENCE_FACTOR) // =0.5 (L219, L247-L252)
RegionToWindVecLocal[r] = setMagnitude(windDir, windSpeed)
```
     另有 localWindModeAltitude（用 dot(û(r→nb), û(r→wind_r))*Δh/maxElev 让风绕山，L140-L214）与 localWindModeTemperature（作者自评 "This is garbage code :("）。**非纬向风的唯一来源：偏转完全由温度场梯度决定。** 之后 interpolateWindVecs（L277-L300）做**4 次**邻域平均（局地风 4、全局风 0）—— 固定次数松弛。
   - 温度（geo/temperature.go）：`GetMeanAnnualTemp(lat) = sin(90-|lat|)*(MaxTemp-MinTemp)+MinTemp`，MinTemp=-15 °C、MaxTemp=30 °C（Whittaker 范围，L22-L38）；高度递减用 gameconstants.EarthElevationTemperatureFalloff（注释 ≈9.8 °C/km，L11-L20）；assignRegionAirTemperature（L61-L102）做 0.75*self+0.25*邻域均值 + numSteps=5、transferIn=0.001 的平流。
   - **降水 assignRainfallBasic**（geo/rainfall.go L237 起）：按 GetWindSortOrder()（沿风向排序的格点序）单向推进，从**上风邻居**按 dot(û(r→nb), windDir)>0 加权取湿；地形雨 `heightVal = 1 - elev/maxElev; if humidity>heightVal: rain = rainShadow*(humidity-heightVal)`，rainShadow=raininess=evaporation=0.9；`stepsTransport=2, stepsInterpolation=2`（L255-L256）。旧版 assignRainfall（L29-L235）是 5 步全局松弛，作者自评 "highly bugged"。
4. 是否全局迭代：**否（固定次数松弛）**：风 4 次邻域平均、温度 5 步、湿度 2+2 步，无收敛判据，O(k·N)。
5. 复杂度/性能：geo/config.go `NewGeoConfig()` → **NumPoints=400000、NumPlates=25、Jitter=0.0**（L17-L27）；400k 区域（≈800k 三角）+ 每步 1-ring 循环。README 自述 "the major drawback right now is the time that it takes to generate a reasonably complex planet"。已用 goroutine 分块并行（various.KickOffChunkWorkers）并缓存 dot product（L322-L346）。
6. 输出：SVG / PNG / OBJ / WebP / Leaflet XYZ 瓦片 / Cesium 3D Tiles；气候场 float64 slice。
7. 借鉴点：**你卡点的直接答案之一** —— `wind = 纬向基向量 + k·Σ_nb (T_r - T_nb)·û(r→nb)`，k=0.5，是**一次 O(N) 逐格算子、纯函数、无全局求解**，能产生经向分量（东岸才可能出现"吹向赤道"分量）。前提是你的 T 场带正确的东边界冷异常。它的连续旋转基向量也可直接替换你现在的分带阶跃。

### B3-4. FreezeDriedMangos/realistic-planet-generation-and-simulation — p5.js（非纬向风的原始出处）
1. [repo](https://github.com/FreezeDriedMangos/realistic-planet-generation-and-simulation) ；JavaScript / p5.js（Voronoi 球面网格）；**未找到 LICENSE**；约 2021 后**不再维护**（代码完整、可读性最好）。
2. 输出：r_wind（含经向分量的 2D 风向量）、r_temperature、r_waterTemperature、r_humidity、r_clouds、r_currents（洋流）、河流/湖泊/地下水、r_wetness。
3. 方程（src/Generate_Weather.js）：
   - **风 assignRegionWindVectors() L344-L413（核心）**：
```
if (map.weatherStep % 20 !== 0) return            // 风每 20 个天气步才重算（L345）
const TEMPERATURE_INFLUENCE_FACTOR = 2;            // L347
// 1) 纬向基础风（连续三角函数，非阶跃）
if (0<|lat|<30)  wind_dir = [-sin(θ), -cos(θ)], θ=(π/2)*lat/30        // L374-L376
if (30<|lat|<60) wind_dir = [ cos(θ),  sin(θ)], θ=(π/2)*(lat-30)/30    // L378-L380
if (60<|lat|<90) wind_dir = [-sin(θ), -cos(θ)], θ=(π/2)*(lat-60)/30    // L382-L384
// 2) 地形加减速
elevation_change = max(elev[r],0) - max(elev[blowsPast_r],0)
wind_speed = max(0.1, 1 - 2*elevation_change)                          // L399-L401
// 3) 温度梯度偏转（isInit 时跳过）
wind_dir = addVectors(wind_dir, getNeighbors(r).reduce((acc,nr) =>
   setMagnitude(addVectors(dirFromTo(r,nr), acc),
                TEMPERATURE_INFLUENCE_FACTOR*(r_temperature[r]-r_temperature[nr])), [0,0]))  // L408
map.r_wind[r] = setMagnitude(wind_dir, wind_speed)                     // L410
```
   - **温度 assignRegionTemperature() L415-L503**：`nr = getPreviousNeighbor(r, wind)` **连取两次**（L438-L439）= 上风半拉格朗日；`elev = pow(max(0, elev>windBlockingElevation ? elev : 0), 2)` 是**山脉阻挡权重**（L435）；`newT = (1-elev)*T_upwind + elev*T_self`（L442）→ Voronoi 1-ring 扩散平均（L446-L454）→ `T += 0.8*(1-T)*addSun + 0.6*T*(addRain+addCool) + 0.1*multiplySeason*T + 0.25*addNight*T + 0.5*addOcean*T`（L490），季节项 lat_deg=90-|lat-sunLat|、multiplySeason=clamp(0.2,1,lat/90)-0.8（L477-L481）。
   - **湿度 assignRegionHumidity() L505-L594**：同款上风 2 步 + 扩散；海面 `addHumidity = 0.1*(0.02*T_air + 0.07*T_water)`（L549）、陆地 0.02*(T-0.5)；云 ≥0.75 掉 -0.8*H、0.5~0.75 掉 -0.2*H（L555-L559）；末了硬混 `H = 0.25*H_upwind + 0.75*H`（L585）。
   - **洋流 generateCurrents() L150-L272（对东边界符号最有价值）**：在 |lat|≈60 与 75 的海洋格播种（latLeeway=2，L152/L177-L178），bfsMetaVoronoi 以陆地为障碍分组并合并同带相邻组（L183-L195）；每组算 BFS 距离场 distFromEdge（L198-L221）、指向组内的 inwardDir（L226-L233）；**洋流 = inwardDir 的垂线，旋转方向按半球/带硬编码**：`clockwise = supergroup===1||===2; perpendicular = clockwise ? [-iy, ix] : [iy, -ix]`（L235-L240）→ **东边界必然赤道向、西边界必然极向，符号由构造保证。**
   - **水温 assignRegionWaterTemperature() L274-L342**：基础 1-|lat-sunLat|/90（L289）→ 0.75*self+0.25*邻域（L305）→ **沿洋流 pull/push 平流**（getPreviousNeighbor(r,currents) / getNextNeighbor，L316-L334）→ 造出冷东边界 SST 舌，再经温度梯度项改变风。
   - 主循环 advanceWeather() L85-L98：nightness → waterTemperature → windVectors → surfaceTemperature → temperature → humidity → clouds → rivers。
4. 是否全局迭代：**否**。逐格局部算子 + 固定次数邻域松弛；洋流分组用 BFS（O(N)，但合并处有 `groups.map(...)` 的 O(N²) 污点）。
5. 复杂度/性能：无 benchmark；README 明确 "Weather simulation steps are computationally expensive just by themselves"。
6. 输出：屏幕图层（温度/水温/湿度/云/洋流涡旋/气候/卫星，可切 HD "Quads"）；另有作者手写算法笔记 CurrentsGenerationAlgorithm/Current Generation Explanation.txt。
7. 借鉴点：**最该抄的一段** —— 东岸风符号不该来自风方程本身，而应来自温度场：(a) 用洋流把冷水平流到东边界（L316-L334 两行 pull/push），(b) 风由 `wind + 2*Σ(T_r-T_nb)û` 得到经向分量（L408）。风每 20 步更新一次（L345）的解耦是省预算的实用技巧。

### B3-5. ExoPlaSim（PlaSim 3D GCM 的系外行星扩展）— Fortran + Python
1. [alphaparrot/ExoPlaSim](https://github.com/alphaparrot/ExoPlaSim) ；Fortran（动力核心+物理）+ Python API；**GPL**；版本 3.4.2，README 称 "Final maintenance release for 3.x"。DOI：https://doi.org/10.5281/zenodo.2533357 ；文档：https://exoplasim.readthedocs.io/
2. 输出：完整大气/地表场（温度、风、湿度、云、降水、辐射、海冰），支持 NetCDF4 / HDF5 / 文本 + pyburn 后处理。
3. 方程/网格（源码级）：
   - exoplasim/__init__.py L256-L270：resolution ∈ T21/T42/T63/T85/T106/T127/T170；layers 默认 10（"PlaSim has been used with 5 layers in many studies"）。分辨率→Gaussian 纬数：**T21→nlats=32（32×64）、T42→64、T63→96、T85→128、T106→160、T127→192、T170→256**（L517-L540）。谱动力核心=球谐（L1231 注释 "'spectral', meaning spherical harmonics"）。
   - exoplasim/plasim/src/plasim.f90：L157-L159 打印 NTRU/NLEV/NLAT；L183 `call inigau(NLAT,...)`（Gaussian 格点）；L238-L241 ntspd（每标准日步数）、mpstep（每步分钟数）；**L586-L588 `deltsec = day_24hr/mtspd`、`delt = TWOPI/ntspd`**；L556-L569 初始小步长 deltsec=(86400/mtspd)/2**nkits；L621-L627 每步分 **adiabatic part → diabatic part**（半隐式 leapfrog 谱变换）；L649-L652 按 mod(nstep,ndiag) 诊断输出。**每步分钟数的默认值来自 namelist（L74-L93），源码未硬编码 → 未取到。**
4. 是否全局迭代：**是，真正的时间积分 GCM**（全球谱变换，每步全网格耦合），**收敛判据 = 能量平衡漂移**：`runtobalance(threshold=None, baseline=50, maxyears=300, minyears=75)`（__init__.py L676-L731）。直接违反你"不做全局二维迭代"的约束。
5. 复杂度/性能：O(nlat²·nlev)/步 × 每步 2 substep × 数十年；项目把每年耗时写入 runtimes.log 并据此动态限制运行年数（L842-L850）——量级为"数分钟~数十分钟/模拟年/CPU"（具体数字**未取到**）。
6. 输出：NetCDF4 / HDF5 / 文本 + pyburn（finalize(..., allyears=...) L1448）。
7. 借鉴点：**不要在瓦片里跑 GCM**。可**离线**用它生成"东边界风符号修正表"（按纬度×海陆配置×季节采样，统计赤道向概率→运行时查表/回归），或仅借其物理事实（副高东侧=信风=赤道向）作为你构造式风场的约束。

### B3-6. 检索到但**没有气候**的项目（诚实否定，避免踩坑）
- [mewo2/terrain](https://github.com/mewo2/terrain)（"Generating fantasy maps" 实现）：仓库只有 terrain.js（**1074 行**，master HEAD），对 wind|climate|moisture|rain|precipit|humid|temperature 的匹配数为 **0** → **完全没有气候部分**（https://raw.githubusercontent.com/mewo2/terrain/master/terrain.js）。文章 https://mewo2.com/notes/terrain/ 同理只有地形/生物群系。
- [redblobgames/1843-planet-generation](https://github.com/redblobgames/1843-planet-generation)：文件仅 planet-generation.js / sphere-mesh.js / colormap.js。planet-generation.js L643-L670 有 r_moisture/t_moisture 字段，但 **L668-L670 是占位实现**：`// TODO: assign region moisture in a better way!` + `map.r_moisture[r] = (map.r_plate[r] % 10)/10.0;`（湿度=板块编号 mod 10）；L601-L606 的 t_flow=0.5*t_moisture² 只用于侵蚀。→ **无风、无气候**。文章 https://www.redblobgames.com/x/1843-planet-generation/
- [redblobgames/mapgen4](https://github.com/redblobgames/mapgen4)：文件清单无任何 climate/wind/rain 模块（仅 map/mesh/render/painting/serialize/worker）；2025 年文章 https://www.redblobgames.com/x/2505-mapgen-cylinder/ 、https://www.redblobgames.com/blog/2025-04-22-de-optimizing-mapgen4/ 是网格与性能主题。→ **Amit Patel 的地图生成系列没有气候/风**（未找到）。
- planetsfactory（Rust crate，docs.rs 0.0.4）：分类 #orbital-mechanics #exoplanet，轨道力学/行星参数库，**无气候**。

---

## 核心问题：哪些项目真的做了**非纬向（随经度变化）的风**？怎么得到的？

| 项目 | 风是否非纬向 | 机制 | 全局迭代 | 对你的价值 |
|---|---|---|---|---|
| WorldEngine | **无风** | — | — | 无 |
| Azgaar FMG | **否（6 段阶跃）** | options.map.climate.winds[6] 按 30° 分带 | 否 | 无（这正是 39~52% 的来源） |
| weigert/proceduralweather | **否（方向全局统一）** | 1D Perlin 随时间转，只有速度随地形 | 固定 365 步/年 | 平流与阈值写法 |
| Undiscovered Worlds | **半非纬向** | 10 条风带**边界**随机游走+样条随经度蜿蜒（±25px≈±4.4°），带内风向仅 ±10（纯东西） | 否（8.2 s / 1.57M 格） | 边界蜿蜒技巧 + 一维水汽线积分常数 |
| genworldvoronoi | **是** | wind = 连续旋转纬向基向量 + 0.5·Σ_nb(T_nb−T_r)·û（geo/wind.go L215-L257），再 4 次邻域平均；另有山脉 dot(Δh) 偏转 | 否（固定次数松弛） | ★逐格算子，直接可用 |
| FreezeDriedMangos | **是** | wind = 纬向基向量 + 2·Σ_nb(T_r−T_nb)·û（L408）；T 由**上风半拉格朗日平流**；水温由**洋流平流** | 否 | ★★最完整、最可抄 |
| ExoPlaSim / PlaSim | **是（真实物理）** | 三维原始方程谱 GCM（T21=32×64×10 层），每步 adiabatic+diabatic | **是**（能量漂移判据 runtobalance） | 只能离线做真值/查表 |

**结论（对你的卡点）**
1. **没有任何轻量项目通过"风方程"本身得到正确的东边界符号**；物理正确解只有 GCM（ExoPlaSim），代价不可接受。
2. 轻量项目的共识是**让风继承温度场**：`wind_dir = 纬向基 + k·Σ_nb (T_self − T_nb)·û(self→nb)`，k∈{0.5, 2.0}。这是一次 O(N) 逐格算子（**纯函数、无全局求解，符合你的约束**）并产生经向分量。你已有的 Ekman-Rayleigh 风在做同类事；若东岸符号仍接近随机，问题几乎一定在**喂给它的温度/气压距平场本身没有东边界冷异常**。
3. **符号保证靠"构造"而非"求解"**：FreezeDriedMangos generateCurrents()（L226-L240）用 `current = perpendicular(inwardDir)` + 半球硬编码旋转方向，使**东边界赤道向、西边界极向由构造保证**。把同一构造用于副高/海洋盆地风场（用"上风 fetch 长度 + 到岸距离"的局部量替代 BFS inward 方向），可在不做全局求解的前提下让符号确定 —— 建议作为下一步实测方案。
4. **两个配套动作**：① 让冷 SST 沿东边界赤道向平流（FDM L316-L334 两行 pull/push），否则岸边温度梯度方向仍可能反向；② 把"东岸符号正确率"固化为回归指标（FDM 的 isInit 分支、GWV 的 4 次平均都说明这类场需 1~5 次松弛才稳定）。
5. **参数对照表（可直接对标你的常数）**：UW seamult=80 / dumprate=100 / landpickuprate=40 / slopemin=300 / elevationfactor=0.002 / landmult=5 / landshadowfactor=4 / maxoceanrain=1500；FMG MAX_PASSABLE_ELEVATION=85 / (h/70)² / 6.5 °C·km⁻¹ / LATITUDE_MODIFIER[18] / 1.25+0.2 gamma（WorldEngine）；GWV TEMP_INFLUENCE=0.5 / ELEVATION_CHANGE_FACTOR=1.0 / rainShadow=0.9 / 400k regions / 4 次风松弛；FDM TEMP_INFLUENCE=2.0 / windSpeed=1−2Δh（≥0.1）/ 风每 20 步 / cloud≥0.75→−0.8H / 海面加湿 0.1*(0.02T_air+0.07T_water)；weigert rain: H≥0.35+0.5T、H≥0.3+0.3T / windSpeed=5(1−Δd/1000)。

**未找到 / 无法验证（明确列出）**：WorldEngine 公开实测耗时；FMG climate.winds 默认 6 个角度值（选项默认值文件未定位）；UW createmonsoons() 实现（超出 web_fetch 100k 截断）；PlaSim 默认 mpstep（每步分钟数，在 namelist 中）；ExoPlaSim 每年实测分钟数；mewo2/terrain 与 redblobgames 系列的气候模块（**确认不存在**）。
