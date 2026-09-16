# 工程调研：Minecraft 气候/洋流模组 + libnoise/FastNoise 系 + 其它引擎

> 调研目标：为"无限世界行星气候模拟器"（纯函数 f(seed,x,z,season)，禁止全局二维椭圆求解）找可借鉴的**源码级**实现。
> 所有 URL 均为实际抓取过的页面（HTTP 200）。凡未验证的，标注"未验证"；凡不存在的，标注"**未找到**"。

---

## 0. 一句话结论（先看这个）

| 问题 | 结论 |
|---|---|
| 有 MC 模组做过**风生洋流 / 上升流**吗？ | **未找到**。一个都没有。 |
| 有 MC 模组做过**沿岸风**吗？ | **未找到**（方向性沿岸风）。只有 `GenLayerCoastalClimate`，它是**四邻随机取一个**，与风向无关。 |
| 有 MC 模组做过**空间变化的风场**（含科氏符号）吗？ | **有，且只有一个**：**Aerodynamics4MC**。见 §D1.6，这是本次调研最重要的发现。 |
| libnoise 有气候模块吗？ | **没有**。29 个模块全是噪声/组合器，**明确写：只有噪声模块，没有气候模块**。见 §D2.1。 |
| libnoise 组合器能拼气候场吗？ | 能。`ScaleBias`+`Curve`+`Select`+`Blend`+`Cache` 就是"查表+噪声扰动"的完整积木。见 §D2.2。 |
| FastNoiseLite domain warp 能做水汽平流吗？ | 只能做**各向同性**位移；要沿风向做**各向异性**平流需自己包一层（§D2.4 给了配方）。 |
| Godot/Unity/Unreal 有行星气候插件吗？ | **未找到**。Gaea 也**没有 climate 节点**（只有侵蚀里的 orographic/directional precipitation 参数）。 |

---

# D1. Minecraft 模组逐个查清

## D1.1 TerraFirmaCraft 1.7.10（TFC-Classic）

1. **项目名 + 链接 + 语言 + 许可证 + 维护状态**
   - 链接：https://github.com/Deadrik/TFCraft （1.7.10 主线源码镜像，`src/Common/com/bioxx/tfc/...`）
   - 分支/服务器实现另见：https://github.com/valentaim/TFC-1.7.10 （"TFC-Classic → TerraFirmaCraft for MC 1.7.10 [Maintenance only]"）
   - TFC+（1.7.10 的社区续作）：https://github.com/TerraFirmaCraft-Reloaded-Classic/TFCR-Plus
   - 语言 Java；1.7.10 版**已停止维护**（社区 wiki 仍在线：https://1710-wiki.terrafirmacraft.com/Climate ）
   - ⚠️ 许可证：该 GitHub 镜像**未提供 LICENSE 文件**（我抓取时未见），视为"许可证未标明"，**不可直接抄代码**。

2. **算的是什么场**
   - 温度 `getTemp(world,x,z)` / 年均温 `getBioTemperature` / 高度修正温度 `getHeightAdjustedTemp`
   - 降水 `getRainfall(world,x,y,z)`
   - 蒸散 EVT、土壤 pH、排水、稳定度、树种、岩石层（后 6 个是同一套 DataLayer 机制）

3. **方程/近似（参数级）** —— 文件 `Core/TFC_Climate.java`，函数 `initCache()` 与 `getTemp0(...)`
   - **温度是纯 z（纬度）函数，零经度结构**。
   - 纬向因子：`factor = (maxZ − z)/maxZ`，**maxZ = 30000**（`getMaxZPos()`）；`angle = factor·π/2`；`latitudeFactor = cos(angle)`。
   - 12 个月的表 `MONTH_TEMP_CACHE[month][z]`，`MAXTEMP = 35F`，系数按月份查表：
     - month 10 → `35 − 13.5·latF − 55·latF`
     - month 9,11 → `35 − 12.5·latF − 53·latF`
     - month 0,8 → `35 − 10·latF − 46·latF`
     - month 1,7 → `35 − 7.5·latF − 40·latF`
     - month 2,6 → `35 − 5·latF − 33·latF`
     - month 3,5 → `35 − 2.5·latF − 27·latF`
     - month 4 → `35 − 1.5·latF − 27·latF`
   - 实时温度 `getTemp0(world, day, hour, x, z, bio)`：
     ```
     zMod = getZFactor(z)                       // = (30000-|z|)/30000, clamp
     zTemp = zMod·getMaxTemperature() − 20 + (zMod−0.5)·10      // getMaxTemperature() = 52
     rain  = getRainfall(world,x,SEALEVEL,z)
     rainMod = (1 − rain/4000)·zMod
     monthDelta = (monthTemp − lastMonthTemp)·dayOfMonth/daysInMonth
     temp = lastMonthTemp + monthDelta + dailyTemp + hourMod·(zTemp + dailyTemp)
     if (temp >= 12) temp += (8·rainMod)·zMod; else temp -= (8·rainMod)·zMod
     ```
   - 高度修正 `adjustHeightToTemp(y,temp)`，**分段**（`Y_FACTOR_CACHE[441]`）：
     `y−SEALEVEL < 110 → factor = i²/677.966`；`else → factor = 0.16225·i`（i=y−SEALEVEL）。
     注释里写明这是把 6.49 K/1000 m 的标准递减率拟合成 MC 的 110 格标高。
   - **季节靠 z 翻转半球**：`TFC_Time.getSeasonFromDayOfYear(int day, int z)` → `(day/daysInMonth + (z>0 ? 6 : 0)) % 12`。注释：*"Season is reversed in southern Hemisphere"*。
     ⚠️ 这是**整数跳变**：z 穿过 0 时季节瞬间整体移半年，不是连续过渡。
   - 降水 `GenLayerRainInit.getInts`：`out = DRY + rand.nextInt(6)`，另有 1/12 概率向 DRY/WET 外扩一格 —— **纯随机层，无纬度、无地形、无风**。

4. **是否需要全局迭代**：**否**。温度是 O(1) 查表；降水/EVT/pH/排水用 vanilla 式 `GenLayer` 分层（`WorldGen/WorldCacheManager.java`），按 16×16 区块惰性生成 + `DataCache` 缓存，不是全局求解。
   - `WorldCacheManager` 还带一个 `worldTempCache`（`LinkedHashMap<String,Float>`，key = `x+","+z+","+totalHours`），**上限 50000 条，超了删一条**（`trimTempCache()`）—— 一个很土的 LRU。

5. **时间复杂度/实测性能**：源码无注解。可推：`initCache()` 一次性 30001×12 次 cos，之后 O(1)。

6. **输出格式**：Java float 场（无文件输出）。

7. **我们能直接借鉴什么（针对沿岸风符号卡点）**
   - ✅ **能借**：月温查表 + `cos(lat)` 纬向基函数；月际线性插值 `monthDelta`；温度对降水的**符号相关**修正（`temp≥12 ? + : −`，即"湿的地方夏天更凉/冬天更暖"）；分段高度递减率。
   - ❌ **不能借**：它的半球季节翻转是整数跳变，会在 z=0 造成气候不连续（我们的 1D 积分解必须用连续 `sign(z)` 或 `tanh(z/L)`）。
   - ❌ 它的降水与风完全解耦，**对沿岸风符号问题零帮助**。

8. **关键文件路径 + 行号 + URL**
   - `src/Common/com/bioxx/tfc/Core/TFC_Climate.java` — `initCache()`（约 L22–L100）、`getTemp0()`（约 L130–L175）、`adjustHeightToTemp()`（约 L200–L215）
     https://github.com/Deadrik/TFCraft/blob/master/src/Common/com/bioxx/tfc/Core/TFC_Climate.java
   - `src/Common/com/bioxx/tfc/Core/TFC_Time.java` — `getSeasonFromDayOfYear()`（含半球翻转注释）
     https://github.com/Deadrik/TFCraft/blob/master/src/Common/com/bioxx/tfc/Core/TFC_Time.java
   - `src/Common/com/bioxx/tfc/WorldGen/WorldCacheManager.java` — `trimTempCache()` 上限 50000
     https://github.com/Deadrik/TFCraft/blob/master/src/Common/com/bioxx/tfc/WorldGen/WorldCacheManager.java
   - `src/Common/com/bioxx/tfc/WorldGen/GenLayers/DataLayers/Rain/GenLayerRainInit.java`
     https://github.com/Deadrik/TFCraft/blob/master/src/Common/com/bioxx/tfc/WorldGen/GenLayers/DataLayers/Rain/GenLayerRainInit.java

---

## D1.2 TerraFirmaCraft TNG（TFC 现代版）

1. **项目名 + 链接 + 语言 + 许可证 + 维护状态**
   - https://github.com/TerraFirmaCraft/TerraFirmaCraft （分支 `1.21.x`），Java，**EUPL-1.2**（每个文件头都有），**活跃维护**
   - 本次引用固定在 commit `5040431220a91a5d91dbad872065e2bc97e43204`

2. **算的是什么场**：温度（平均/月/日）、降水 rainfall、雾 fogginess、水下雾、雪线、冰/海冰。

3. **方程/近似（参数级）** —— `util/climate/OverworldClimateModel.java`
   - 常量（文件顶部）：
     `MINIMUM_TEMPERATURE_SCALE = −20f`，`MAXIMUM_TEMPERATURE_SCALE = 30f`
     `LATITUDE_TEMPERATURE_VARIANCE_AMPLITUDE = −3f`，`LATITUDE_TEMPERATURE_VARIANCE_MEAN = 15f`
     `REGIONAL_TEMPERATURE_SCALE = 2f`，`REGIONAL_RAINFALL_SCALE = 50f`
     `SNOW_FREEZE = −2f / SNOW_MELT = 2f`，`ICE_FREEZE = −4f / ICE_MELT = 2f`
   - 月温：`calculateMonthlyTemperature(z, monthModifier) = monthModifier · Helpers.triangle(−3f, 15f, 1/(4·scale), z)`
     → **周期性三角波**，周期 `1/(4·temperatureSettings.scale())`，均值 15 °C、振幅 −3 °C。
   - 日温：`calculateDailyTemperature(calendarTicks) = ((rand.nextFloat() − rand.nextFloat()) + 0.3·hourMod) · 3f`，范围 −3.9…3.9；`hourMod = hourOfDay/6 − 1`（12 点最热）。随机数用 `seededRandom(day, 1986239412341L)`。
   - 高度修正 `adjustTemperatureByElevation(y, avg, month, daily)`：
     - `y > SEA_LEVEL`：`elevationTemperature = clamp((y−SEA_LEVEL)·0.16225f, 0, 17.822f)` → **−1.6 °C / 10 格**（注释原文），`temp = avg + month − elev + daily`
     - `0 < y ≤ SEA_LEVEL`：月影响 `inverseLerp(y,0,SEA_LEVEL)`，日影响 `clamp(monthInfluence·3 − 2, 0, 1)`（衰减更快）
     - `y ≤ 0`：向 `LAVA_LEVEL_TEMPERATURE = 15f` 线性插值到 `DEPTH_LEVEL = −64`
   - 气候种子：`climateSeed = LinearCongruentialGenerator.next(level.getSeed(), 719283741234L)`；雪斑/冰斑噪声：`OpenSimplex2D(climateSeed + 72397489123L).octaves(2).spread(0.3f).scaled(−1,1)`、`OpenSimplex2D(climateSeed + 192639412341L).octaves(3).spread(0.6f)`。
   - 冰阈值：`threshold = icePatchNoise.noise(x·0.2f, z·0.2f) + clamp(temperature·0.1f, −0.2f, 0.2f)`

4. **是否需要全局迭代**：**否**。全部是 f(pos, 时间) 的闭式函数 + 2 个 OpenSimplex 噪声。**注意：纬向结构只依赖 pos.getZ()，经度结构来自 `REGIONAL_*` 尺度的地方性噪声 + ChunkData 里存的 `averageTemp`。**

5. **性能**：源码无 bench。噪声是 2 个 OpenSimplex2D 实例，每方块 1–3 次采样。

6. **输出格式**：无文件；`OverworldClimateModel.onSyncToClient(FriendlyByteBuf)` 只同步 `scale`、`endlessPoles`、`climateSeed` 三个量。

7. **能借鉴什么**
   - ✅ `Helpers.triangle(amplitude, mean, period, x)` 这种"三角波纬向基函数"比 `cos(lat)` 更便宜，且天然周期化（对无限 z 很合适）。
   - ✅ **分层温度语义**很好用：月尺度 + 日尺度 + 海拔 + 深度，各自独立项后求和（`temp = avg + month − elev + daily`），非常适合我们的"纯函数"约束。
   - ✅ `getFogginess()` 用 `clampedMap(rainfall, 150f, 300f, 0, 1)`——这是"降水→次级场"的干净写法。
   - ❌ 仍然**完全没有风/洋流**，对沿岸风符号问题无帮助。

8. **关键文件 + 行号 + URL**（用 commit 固定）
   - `src/main/java/net/dries007/tfc/util/climate/Climate.java`
     https://github.com/TerraFirmaCraft/TerraFirmaCraft/blob/5040431220a91a5d91dbad872065e2bc97e43204/src/main/java/net/dries007/tfc/util/climate/Climate.java
     （只做分发；`toVanillaTemperature(t) = t·0.0217f + 0.15f`，`toActualTemperature(t) = (t−0.15f)/0.0217f`）
   - `.../util/climate/OverworldClimateModel.java` — 常量块 L60–L72；`getTemperature()` L110–L123；`adjustTemperatureByElevation()` L296–L330；`calculateMonthlyTemperature()` L335；`calculateDailyTemperature()` L345
     https://github.com/TerraFirmaCraft/TerraFirmaCraft/blob/5040431220a91a5d91dbad872065e2bc97e43204/src/main/java/net/dries007/tfc/util/climate/OverworldClimateModel.java
   - `.../util/climate/BiomeBasedClimateModel.java`（纯生物群系回退，`rainfall = clamp(downfall,0,1)·MAXIMUM_RAINFALL`）
     https://github.com/TerraFirmaCraft/TerraFirmaCraft/blob/5040431220a91a5d91dbad872065e2bc97e43204/src/main/java/net/dries007/tfc/util/climate/BiomeBasedClimateModel.java
   - 同目录还有 `KoppenClimateClassification.java`（柯本气候分类！值得单独看）：
     https://github.com/TerraFirmaCraft/TerraFirmaCraft/tree/1.21.x/src/main/java/net/dries007/tfc/util/climate

---

## D1.3 Climate Control / Geographicraft（Zeno410）—— 你点名的那个

1. **项目名 + 链接 + 语言 + 许可证 + 维护状态**
   - **有公开源码**（这是关键）：https://github.com/GTNewHorizons/Climate-Control
     （GTNH 维护的 1.7.10 分支；1.12.2 分支见 https://github.com/Zeno410/Geographicraft ）
   - CurseForge：https://www.curseforge.com/minecraft/mc-mods/climate-control-geographicraft
   - README 自述：*"Licensed LGPL-3.0 according to curseforge page"* → **LGPL-3.0**
   - 维护：GTNH 组织下**活跃**（有 `.github/workflows/build-and-test.yml`、`jitpack.yml`）

2. **算的是什么**：**不计算任何物理量**。它输出的是"气候 ID"（0..4 = snowy/cool/warm/hot/ocean）以及**把原生群系重新分配到这些气候 ID 上**。见 `api/Climate.java`：只有 `SNOWY/COOL/WARM/HOT/OCEAN/DEEP_OCEAN` 六个常量，`legitimate()` 手工白名单。

3. **方程/近似** —— 全在 GenLayer 链里：
   - `customGenLayer/GenLayerBandedClimate.java`：**纬度分带**
     ```
     bandWidth = settings.bandedClimateWidth · multiplier
     offset    = settings.bandedClimateOffset · multiplier − multiplier/2
     bandClimate[6] = {2 warm, 1 hot, 2 warm, 3 cool, 4 snowy, 3 cool}
     latitude = par2 + i1 + offset          // par2 即 z
     band = (latitude>=0) ? (latitude/bandWidth) % 6
                          : ((latitude−bandWidth+1)/bandWidth) % 6 (+6 if <0)
     ```
     → **6 带循环，关于赤道对称**（0 warm…3 cool…4 snowy 在两极），这是标准的"三圈环流式"带序。
   - `customGenLayer/GenLayerCoastalClimate.java`：**沿岸处理 = 四邻随机取一个**
     ```
     int[] adjacencies = new int[4]; int numberAdjacent = 0;
     // 上下左右四个邻居，若 !isOceanic 则收进 adjacencies
     if (numberAdjacent > 0) { initChunkSeed(...); k3 = adjacencies[nextInt(numberAdjacent)]; }
     ```
     ⚠️ **它没有风向概念！** 四邻等权随机抽取。这正是我们"东边界沿岸风符号"问题的**反面教材**：随机化会把系统性符号抹平成 ~50%（和你现在的 39–52% 症状一模一样）。
   - 其它层：`GenLayerTemperClimate`、`GenLayerDefineClimate`、`GenLayerSmoothClimate`、`GenLayerTestClimateSmooth`、`GenLayerBiomeByClimate`、`GenLayerBiomeByTaggedClimate`、`GenLayerSmoothCoast`、`GenLayerPrettyShore`、`GenLayerContinentalShelf`。
   - 分配算法：`api/ClimateDistribution.incidences(BiomeSettings.Element)` —— 把某群系的出现率在若干气候之间**均分**（`remainingIncidence / (climates.size() − doneSoFar)`），是纯参数重映射。

4. **是否需要全局迭代**：**否**。vanilla `GenLayer` 分层，16×16 惰性 + `IntCache`。

5. **性能**：无 bench。GenLayer 是 MC 原生机制，成本与 vanilla 群系生成同阶。

6. **输出格式**：无。运行时改写 `GenLayer` 链；`OverworldDataStorage` / `DimensionalDataStorage` 存维度级设置。

7. **能借鉴什么**
   - ✅ `GenLayerBandedClimate` 的**6 带对称带序**可以直接抄成我们的"纬度带索引"函数（注意它的负数取模写法 `(latitude−bandWidth+1)/bandWidth`，能避免 Java 向零取整的 off-by-one）。
   - ✅ `GenLayerSmoothClimate` / `GenLayerTestClimateSmooth` 的思路：**先在整数带索引上做平滑，再映射到连续场**——但这会引入横向扩散，对我们"必须纯函数"的约束不友好。
   - ❌ **反面教材（重要）**：`GenLayerCoastalClimate` 的四邻随机 = 无方向性 = 系统性符号被抹平。**我们的沿岸风符号必须来自解析式（`sign(f)`、`∂P/∂x` 的定向差分、海岸法向），绝不能来自邻居随机。**

8. **关键文件 + URL**
   - `src/main/java/climateControl/customGenLayer/GenLayerBandedClimate.java`
     https://github.com/GTNewHorizons/Climate-Control/blob/master/src/main/java/climateControl/customGenLayer/GenLayerBandedClimate.java
   - `src/main/java/climateControl/customGenLayer/GenLayerCoastalClimate.java`
     https://github.com/GTNewHorizons/Climate-Control/blob/master/src/main/java/climateControl/customGenLayer/GenLayerCoastalClimate.java
   - `src/main/java/climateControl/api/Climate.java` / `.../api/ClimateDistribution.java`
     https://github.com/GTNewHorizons/Climate-Control/tree/master/src/main/java/climateControl/api
   - `src/main/java/climateControl/api/ClimateControlSettings.java`（`bandedClimateWidth` / `bandedClimateOffset` / `frozenIcecaps` / `separateLandmasses` 都在这）
   - 全套 175 个文件清单（含 `genLayerPack/` 里 vanilla 层的拷贝）：
     https://ungh.cc/repos/GTNewHorizons/Climate-Control/files/master

---

## D1.4 Serene Seasons（季节温度偏移）

1. **项目名 + 链接 + 语言 + 许可证 + 维护状态**
   - https://github.com/Glitchfiend/SereneSeasons ，Java，**All Rights Reserved**（README 明确 "© 2024 Glitchfiend. All rights reserved."；1.12.2 分支头是 CC BY-NC-ND 4.0）→ **不可抄代码，只能抄思路**
   - **活跃维护**（1.20.x / 1.21.x）

2. **算的是什么**：季节状态（4 季 × 3 子季）、每个群系的**季节温度偏移**、降水相态（雨/雪）、草/叶颜色、作物生长。

3. **方程/近似（参数级）**
   - **1.12.2 分支**（`master`）`season/SeasonASMHelper.java` → `getFloatTemperature(SubSeason, Biome, BlockPos)`：
     ```java
     if (!tropicalBiome && biome.getDefaultTemperature() <= 0.8F && enablesSeasonalEffects(biome))
       switch (subSeason) {
         case LATE_SPRING: case EARLY_AUTUMN:            biomeTemp -= 0.1F; break;
         case MID_SPRING:  case MID_AUTUMN:              biomeTemp -= 0.2F; break;
         case EARLY_SPRING:case LATE_AUTUMN:             biomeTemp -= 0.4F; break;
         case EARLY_WINTER:case MID_WINTER:case LATE_WINTER: biomeTemp -= 0.8F; break;
         default: break;              // 夏季不变
       }
       clamp(biomeTemp, -0.5F, 2.0F)
     ```
     → **全部偏移都是负的**（冬天 −0.8，夏天 0），阈值 `>= 0.15F` 判是否下雨/下雪。
   - **1.20.x 分支**（`HEAD/common/`）改成了可配置：`SeasonHooks.getBiomeTemperatureInSeason(...)`：
     ```java
     if (!tropicalBiome && biome.value().getBaseTemperature() <= 0.8F && !blacklisted)
        biomeTemp = Mth.clamp(biomeTemp + ModConfig.seasons.getSeasonProperties(subSeason).biomeTempAdjustment(), -0.5F, 2.0F);
     ```
     → 偏移值从 `ModConfig.seasons.getSeasonProperties(subSeason).biomeTempAdjustment()` 读（默认值即上表的 0/−0.1/−0.2/−0.4/−0.8）。
   - **热带特殊路径**：`TropicalSeason` 六相（EARLY/MID/LATE_DRY, EARLY/MID/LATE_WET），`MID_DRY → 不下雨`，`MID_WET → 下雨`。
   - 时间：`SUB_SEASON_DURATION = 7` 天，`DAY_DURATION = 24000` tick，12 子季 → 一年 84 天（`config/SeasonsConfig.java`）。

4. **是否需要全局迭代**：**否**。纯常量偏移 + 一个 `SeasonSavedData` 计数器。

5. **性能**：Mixin 注入 `Biome.getTemperature`，每次查询一次 switch。

6. **输出格式**：无文件；`MessageSyncSeasonCycle` 每秒（20 tick）同步一次 seasonCycleTicks。

7. **能借鉴什么**
   - ✅ **"季节偏移只在基础温度 ≤ 0.8 的群系生效、且全部为负"** —— 这是个很强的设计约束：季节性只做"降温"不做"升温"，避免热带在冬天变热。我们可以照搬这个"偏移符号单向"的规则。
   - ✅ `clamp(temp, −0.5, 2.0)` 的输出归一化区间与 vanilla 对齐（0.15 = 冰点）。
   - ✅ **热带用干/湿季而非冷/暖季** —— 我们的季节场应该在 |lat| 小的地方切换季节性语义（温度年振幅小、降水年振幅大）。
   - ❌ **没有洋流，也没有风**。源码里没有任何风/流相关字段。

8. **关键文件 + URL**
   - 1.12.2：`src/main/java/sereneseasons/season/SeasonASMHelper.java` — `getFloatTemperature()`（约 L170–L205）
     https://github.com/Glitchfiend/SereneSeasons/blob/master/src/main/java/sereneseasons/season/SeasonASMHelper.java
   - 1.12.2：`src/main/java/sereneseasons/api/season/Season.java` — `SubSeason` 枚举
     https://github.com/Glitchfiend/SereneSeasons/blob/master/src/main/java/sereneseasons/api/season/Season.java
   - 1.12.2：`src/main/java/sereneseasons/config/SeasonsConfig.java`（`SUB_SEASON_DURATION = 7`）
     https://github.com/Glitchfiend/SereneSeasons/blob/master/src/main/java/sereneseasons/config/SeasonsConfig.java
   - 1.20.x：`common/src/main/java/sereneseasons/season/SeasonHooks.java` — `getBiomeTemperatureInSeason()`
     https://github.com/Glitchfiend/SereneSeasons/blob/HEAD/common/src/main/java/sereneseasons/season/SeasonHooks.java
   - 1.20.x：`common/src/main/java/sereneseasons/mixin/MixinBiome.java`（`@Inject` 到 `shouldSnow`、`@Redirect` 到 `warmEnoughToRain`）
     https://github.com/Glitchfiend/SereneSeasons/blob/HEAD/common/src/main/java/sereneseasons/mixin/MixinBiome.java

---

## D1.5 其余点名模组：有没有做"风"/"洋流"？

### (a) Weather2 / LTWeather —— **有"风"，但是全局单一标量**

- 链接：https://github.com/Corosauce/Weather2 ；LTWeather（LoveTropics 的 fork，代码更干净）：https://github.com/LoveTropics/LTWeather
- 关键文件：`src/main/java/weather2/weathersystem/wind/WindManager.java`
  https://github.com/LoveTropics/LTWeather/blob/e0a5c78275067e00151f40032d45a9cfcf575272/src/main/java/weather2/weathersystem/wind/WindManager.java
- **源码事实**：
  ```java
  public float windAngleGlobal = 0;   // 全局唯一一个角度
  public float windSpeedGlobal = 0;   // 全局唯一一个速度
  ...
  windAngleGlobal += rand.nextFloat() - rand.nextFloat();   // 每 tick 随机游走
  if (windAngleGlobal < -180) windAngleGlobal += 360;
  if (windAngleGlobal >  180) windAngleGlobal -= 360;
  ```
  阵风：`windSpeedGust = windSpeedGlobal + rand.nextFloat()*0.6F; windAngleGust = windAngleGlobal + rand.nextInt(120) - 60;`
- **结论**：风是 `(angle, speed)` **两个全局标量**，**没有纬度、没有经度、没有科氏力、没有海陆差异**。它只用于给实体/粒子施加拖曳力（`applyWindForceImpl`：`weightDiff = windWeight/objWeight`，`windX = −sin(angle)·speed`，`windZ = cos(angle)·speed`）。
- **能借鉴**：几乎没有。它证明了"MC 社区风的主流做法 = 全局随机游走"，对我们的符号问题无帮助。

### (b) Tough As Nails —— 玩家体温，不是气候场

- https://github.com/asanetargoss/ToughAsNails —— 做的是**玩家 body temperature / thirst**，季节只影响玩家。**无风、无洋流、无空间气候场**。

### (c) Dynamic Surroundings —— 视听效果

- 无气候场、无风、无洋流。**未找到**任何相关实现。

### (d) Realistic Terrain Generation (RTG) —— 纯地形，无气候

- https://github.com/Team-RTG/Realistic-Terrain-Generation （**GPL-3.0**；README：*"The mod is no longer in active development"*，1.12.x 继任者 https://github.com/Zeno410/Realistic-Terrain-Generation-Plus ）
- 源码事实：`src/main/java/rtg/world/biome/realistic/RealisticBiomeBase.java` —— 全是 `lakeInterval = 649.0f`、`lakeShoreLevel = 0.035f`、`lakeDepressionLevel = 0.15f`、`actualRiverProportion = 150f/1600f`、`riverFlatteningAddend` 这类**地形噪声**参数；噪声用 KdotJPG OpenSimplex。**没有温度/降水/风/洋流字段**。
  https://github.com/Team-RTG/Realistic-Terrain-Generation/blob/7dcf8169a37e68f66d69aa5a78770a8e7af8e3fa/src/main/java/rtg/world/biome/realistic/RealisticBiomeBase.java

### (e) Biome Bundle / OpenTerrainGenerator (OTG)

- https://github.com/BiomeBundle/OpenTerrainGenerator —— 做群系配置与地形，有群系"温度/湿度"标签用于**放置**，但**不是气候模拟**。未找到风/洋流。

### (f) Project Atmosphere: Realistic Climate & Weather

- CurseForge：https://www.curseforge.com/minecraft/mc-mods/project-atmosphere
- Modrinth：https://modrinth.com/mod/project-atmosphere
- mcmod：https://www.mcmod.cn/class/26161.html
- **许可证 ARR（All Rights Reserved）→ 源码未公开，GitHub 上未找到仓库。** 因此**无法给出源码级细节**。MC 1.20.1 / 1.21.1，Forge。**结论：无法调研，只能确认它存在。**

### (g) Boatload / Sable Waves / Tide —— 海洋相关但都不是洋流

- Boatload（船只航行优化，MIT）：https://github.com/team-abnormals/boatload
- Sable Waves（波浪物理与水渲染）：https://www.curseforge.com/minecraft/mc-mods/sablewaves
- Tide（Bukkit 潮汐插件）：https://github.com/GoldenPotato137/Tide
- **三者都不做风生环流。明确：未找到。**

### (h) ★ Aerodynamics4MC —— **唯一一个真正做了空间变化风场（含科氏符号）的 MC 模组**

见下面单独一节。

---

## D1.6 ★★★ Aerodynamics4MC（本次调研最重要的发现）

1. **项目名 + 主链接 + 语言 + 许可证 + 维护状态**
   - 主仓库：https://github.com/MozillaFiredoge/Aerodynamics4MC-Core
     （描述：*"A Fabric mod that adds realistic intime aerodynamics to Minecraft"*）
   - 风系统设计文档（**强烈建议全文读**）：https://github.com/MozillaFiredoge/Aerodynamics4MC-Core/blob/main/docs/wind-system-overview.md
   - 语言：Java（Fabric）+ C++（原生 LBM 求解器，JNI）；**许可证 MIT**（README badge: "License: MIT"）
   - 分发：https://modrinth.com/mod/Aerodynamics4MC
   - 维护：**活跃**（文档标注 "written 2026-05-03 against the code on main"）

2. **算的是什么场**（4 个协同组件，**三层嵌套网格 + 1 个驱动器**）
   | 组件 | 类 | 分辨率 | 说明 |
   |---|---|---|---|
   | WorldScaleDriver | `runtime.WorldScaleDriver` | 384×384 cells，256 格/cell | 行星尺度：气旋、对流团、龙卷、**行星波** |
   | L0 BackgroundMetGrid | `runtime.BackgroundMetGrid` | **41×41** cells，256 格/cell，1 层 | 天气尺度：半拉格朗日平流-扩散-地转调整-地形阻力 |
   | L1 MesoscaleGrid | `runtime.MesoscaleGrid` | **33×33×8**，64×64×40 格 | ABL 风切变、**Ekman 偏转**、地形反弹 |
   | L2 Native LBM | `runtime.NativeSimulationBridge` / `client.ClientL2Solver` | 服务端 64³，1 格/voxel | D3Q27 cumulant LBM + SGS + Boussinesq |
   - 输出：`windX/windZ`、`pressureAnomalyPa`、`ambientAirTemperatureKelvin`、`surfaceTemperatureKelvin`、`deepGroundTemperatureKelvin`、`humidity`、`stormActivity`、对流/龙卷强迫场。

3. **方程/近似（参数级）**
   - **行星波**（`WorldScaleDriver.sample()`）：
     ```
     u_target = baseFlow.x + waveScale·(0.90·sin(φ) + 0.35·sin(ψ))
              + Σ(cyclone swirl/radial) + Σ(convective inflow) + Σ(tornado)
     clamp 到 ±MAX_DRIVER_WIND_MPS = ±12 m/s
     DRIVER_SPATIAL_SCALE_X = 0.11, DRIVER_SPATIAL_SCALE_Z = 0.09
     BASE_FLOW_RELAX_PER_SECOND = 1/900   // 引导气流 ~15 min 时间常数
     ```
   - **气旋**：`swirl_outer = 10 m/s·intensity·0.55·Gaussian(outerNorm)·CoriolisSign`；`swirl_core = 10·intensity·1.40·Gaussian(coreNorm)·CoriolisSign`；`radial_outer = 3·0.70·Gaussian`；`radial_core = 3·1.20·Gaussian`；`ΔP = ±1350 Pa·intensity·envelope`。最多 `DEFAULT_CYCLONE_CELL_COUNT = 6` 个。
   - **L0 地转调整**（`BackgroundMetGrid` 约 L814–L841，我实测源码 L821 起）：
     ```
     ∂P/∂x ≈ (P_east  − P_west ) / (2·cellSizeBlocks)
     ∂P/∂z ≈ (P_south − P_north) / (2·cellSizeBlocks)
     coriolis         = pseudoCoriolisFactor(cellZ)
     coriolisSign     = coriolis >= 0 ? +1 : −1
     coriolisStrength = max(MIN_CORIOLIS_FACTOR = 0.55, |coriolis|)
     windX = −gradientZ · GEOSTROPHIC_WIND_SCALE_M2_PER_PA_S · coriolisSign / coriolisStrength
     windZ = +gradientX · GEOSTROPHIC_WIND_SCALE_M2_PER_PA_S · coriolisSign / coriolisStrength
     GEOSTROPHIC_WIND_SCALE_M2_PER_PA_S = 12.0f
     ```
   - **★★ 伪科氏因子（这就是你卡点的答案）** —— `BackgroundMetGrid.pseudoCoriolisFactor(int cellZ)`（约 L858）：
     ```java
     float periodCells = 384.0f;
     float wrapped = cellZ % periodCells;
     if (wrapped < 0.0f) wrapped += periodCells;
     return Mth.clamp((wrapped - periodCells * 0.5f) / (periodCells * 0.5f), -1.0f, 1.0f);
     ```
     → `f_sim(z) = clamp( (z mod 384 − 192) / 192, −1, +1 )`，**纯 z 的线性斜坡 + 周期 384 cell = 98304 格 ≈ 98.3 km 的"半球"**。
     **这是纯函数、无全局求解、天然支持无限 x** —— 与你的约束完全一致。
     ⚠️ 一个坑：`coriolisSign / coriolisStrength` 里 `strength = max(0.55, |f|)`，所以在 f≈0 的"赤道"上风不是趋零而是被**放大**（除以 0.55）。如果你照抄，赤道附近会出现风速虚高。
   - **L0 更新步**（`advanceDynamicField()` 约 L474–L579）：
     1) 半拉格朗日回溯平流 + 双线性插值；2) 扩散 `FLOW_DIFFUSION_BLEND = 0.16f`、`PRESSURE_DIFFUSION_BLEND = 0.10f`、`THERMAL_DIFFUSION_BLEND = 0.10f`、`HUMIDITY_DIFFUSION_BLEND = 0.08f`；3) 地转调整；4) 地形形式阻力；5) 松弛到目标；6) 粗糙度拖曳；7) 温度/湿度松弛。
     全部常量（源码 L14–L45，**已逐行核实**）：
     ```
     BASE_AIR_TEMPERATURE_K = 288.15f          BIOME_TEMPERATURE_SCALE_K = 12.0f
     ALTITUDE_LAPSE_RATE_K_PER_BLOCK = 0.0065f DEEP_GROUND_OFFSET_K = 1.5f
     FLOW_RELAXATION_PER_SECOND = 1/90         PRESSURE_RELAXATION_PER_SECOND = 1/180
     AIR_TEMPERATURE_RELAXATION_PER_SECOND = 1/1200   HUMIDITY_RELAXATION_PER_SECOND = 1/900
     DEEP_GROUND_RELAXATION_PER_SECOND = 1/3600       SURFACE_RELAXATION_PER_SECOND = 1/1800
     FLOW_DIFFUSION_BLEND = 0.16f              PRESSURE_DIFFUSION_BLEND = 0.10f
     THERMAL_DIFFUSION_BLEND = 0.10f           HUMIDITY_DIFFUSION_BLEND = 0.08f
     SOLAR_SURFACE_HEATING_K = 18.0f           CLEAR_SKY_COOLING_K = 8.0f
     MAX_DRIVER_WIND_MPS = 14.0f               MAX_DYNAMIC_WIND_MPS = 18.0f
     MAX_PRESSURE_ANOMALY_PA = 2400.0f
     GEOSTROPHIC_WIND_SCALE_M2_PER_PA_S = 12.0f
     GEOSTROPHIC_DIRECT_WIND_BLEND = 0.18f     GEOSTROPHIC_FALLBACK_WIND_BLEND = 0.32f
     GEOSTROPHIC_FALLBACK_SPEED_MPS = 0.35f    MIN_CORIOLIS_FACTOR = 0.55f
     MAX_HUMIDITY_RESPONSE = 0.20f             EVAPORATION_SURFACE_DELTA_SCALE = 0.01f
     BASE_ROUGHNESS_DRAG_PER_SECOND = 0.0025f  ROUGHNESS_DRAG_SCALE_PER_SECOND = 0.010f
     MAX_ROUGHNESS_DRAG = 0.22f
     TERRAIN_FORM_DRAG_SCALE = 0.65f           TERRAIN_FLOW_DEFLECTION_SCALE = 0.45f
     MAX_TERRAIN_WIND_ADJUSTMENT = 0.55f
     ```
   - **地形形式阻力**（`applyTerrainFormDrag()` 约 L521 调用处，函数约 L743–L797）：上坡阻力 `clamp(u·∇h·0.65, 0, 0.55)`；等高线偏转 `clamp(|∇h|·0.45, 0, 0.55)`。
   - **湿度演化**：`H_new = relax(H_advected, H_target, dt, 1/900) + evap_boost·(T_surf − T_air)·0.01 + rain·0.05`
   - **L1 Ekman**：ABL 廓线（`ABL_NEUTRAL_HEIGHT_BLOCKS` / `ABL_STABLE_HEIGHT_BLOCKS` / `ABL_UNSTABLE_HEIGHT_BLOCKS`），Ekman 转角上限 `ABL_EKMAN_MAX_TURN_RADIANS`。

4. **是否需要全局迭代：部分是，但都在"玩家锚定的有限窗口"里**
   - L0：41×41 网格，**每 256 tick（≈12.8 s）** 刷新一次（`BACKGROUND_MET_REFRESH_TICKS = MESOSCALE_REFRESH_TICKS × 4`）
   - L1：33×33×8，**每 64 tick（≈3.2 s）** 刷新（`MESOSCALE_REFRESH_TICKS = 64`）
   - L2：D3Q27 cumulant LBM，**每 tick 步进**（`SOLVER_STEP_SECONDS = 0.05`），服务端 64³ 窗口
   - L2→L1 反馈：每 `L2_TO_L1_FEEDBACK_STEPS = 64` tick
   - **关键工程手法：网格锚定玩家焦点（"Anchored to player focus; cells re-seed when the focus moves"），只维护 ~10.5 km × 10.5 km 的活动足迹。** 这是"无限世界 + 局部求解"的可行范式。
   - 收敛判据：**没有显式收敛判据**，用的是固定时间步 + 松弛（relaxation）而非迭代求解。

5. **时间复杂度 / 实测性能**
   - 文档只给常量不给 bench。可推：L0 每 12.8 s 一次 1681 cell 的 advect+diffuse+geostrophic；L1 每 3.2 s 一次 33×33×8 = 8712 cell；L2 每 tick 64³ 的 LBM（原生 SIMD，**服务端权威 L2 默认关闭** `SERVER_AUTHORITATIVE_L2_ENABLED = false`，默认客户端本地跑 32³）。
   - 规模量级：活动网格 ≈ 10.5 km 见方；行星尺度驱动网格 384 cell × 256 格 = **98.3 km 一个"半球"**。

6. **输出格式**
   - 无文件输出。对外 API：`aerodynamics4mc-api`（**不含 Minecraft 类**的稳定 API），`AeroWindApi` / `GameplayWindSample` / `SamplePolicy`；粗网格风广播包 `AeroCoarseWindPacket`（32 格一个采样点，`COARSE_WIND_SYNC_CELL_SIZE_BLOCKS = 32`）。
   - 调试：`/aero status`、`/aero dumpdata`；Python 快照工具 `eval_background_snapshot.py`、`eval_mesoscale_snapshot.py`、`eval_l2_capture.py`。

7. **★★ 我们能直接借鉴什么（直接针对"东边界沿岸风符号 39~52%"卡点）**
   1. **照抄 `pseudoCoriolisFactor` 的"伪科氏"设计**：把科氏参数写成**纯 z 的周期线性斜坡** `f(z) = clamp((z mod P − P/2)/(P/2), −1, +1)`，P 取 384 cell。它给出**确定的半球符号**，且完全不依赖全局求解。→ 你的"副热带东岸一致吹向赤道"的符号，应该由 `sign(f)·(海陆气压梯度方向)` 决定，而不是靠风向的随机/对称化处理。
   2. **地转关系代替"拟合风向"**：`u_g = −∂P/∂z·K·sign(f)/max(ε,|f|)`，`v_g = +∂P/∂x·K·sign(f)/max(ε,|f|)`。你已经有"热力/副高气压距平"场 → **直接对它做中心差分求地转风**，符号自动正确。这是把 39~52% 提升到接近 100% 的最短路径：**不要再从温度场"猜"风向，而是从气压距平的梯度 + sign(f) 算出风向。**
   3. **避开它的坑**：`sign(f)/max(0.55,|f|)` 会在赤道放大风。你应该用 `u_g = −K·sign(f)·∂P/∂z / sqrt(f² + ε²)` 或者在 `|f| < f_min` 时平滑切到非地转（气压梯度力 + 摩擦平衡），保证赤道附近风速有限且符号连续。
   4. **地形形式阻力两件套**：`clamp(u·∇h·0.65, 0, 0.55)`（迎风坡减速）+ `clamp(|∇h|·0.45, 0, 0.55)`（沿等高线偏转）。沿岸风在做"东边界"判定时，`∇h` 可以用**海岸线法向**替代，得到"沿岸分量 vs 离岸分量"的干净分解。
   5. **局部锚定网格** 是符合你"无限世界"约束的架构：**不要全局格，锚定到查询点/玩家，只解一个有限窗口**。你的 20 s / 100 km 预算下，L0 那种 41×41 的窗口是最现实的形态。
   6. **松弛而非迭代**：全部用 `relax(curr, target, dt, rate)`，没有 Newton/椭圆求解。你的 1D Munk 积分 + 松弛风场可以并存。

8. **关键文件 + 行号 + URL**
   - `docs/wind-system-overview.md`（35 KB，中文+英文双语，含全部常量表与 file:line 引用）
     https://github.com/MozillaFiredoge/Aerodynamics4MC-Core/blob/main/docs/wind-system-overview.md
   - `src/main/java/com/aerodynamics4mc/runtime/BackgroundMetGrid.java`
     - 常量块 **L14–L45**（已逐行核实）
     - `advanceDynamicField()` ≈ **L474–L579**
     - 地形阻力 `applyTerrainFormDrag()` ≈ **L743–L797**
     - 地转风 `computeGeostrophicWind()` ≈ **L814–L841**（我实测梯度计算在 **L821**，`coriolisSign` 在 **L823**）
     - `pseudoCoriolisFactor(int cellZ)` ≈ **L858**
     - 常量：`GEOSTROPHIC_WIND_SCALE_M2_PER_PA_S = 12.0f`（L33）、`MIN_CORIOLIS_FACTOR = 0.55f`（L37）
     https://github.com/MozillaFiredoge/Aerodynamics4MC-Core/blob/main/src/main/java/com/aerodynamics4mc/runtime/BackgroundMetGrid.java
   - `src/main/java/com/aerodynamics4mc/runtime/WorldScaleDriver.java`（~1817 行；`sample()` / `CycloneCell` / `ConvectiveCluster` / `TornadoVortex`）
     https://github.com/MozillaFiredoge/Aerodynamics4MC-Core/blob/main/src/main/java/com/aerodynamics4mc/runtime/WorldScaleDriver.java
   - `src/main/java/com/aerodynamics4mc/runtime/MesoscaleGrid.java`（~2372 行；Ekman/ABL/地形反弹）
     https://github.com/MozillaFiredoge/Aerodynamics4MC-Core/blob/main/src/main/java/com/aerodynamics4mc/runtime/MesoscaleGrid.java
   - `docs/world-scale-weather-design.md`、`docs/wind-sampling-api.md`、`docs/native-jni-interface-reference.md` 等 17 篇文档（清单见 wind-system-overview.md §9）

---

## D1.7 GeoCraft 天圆地方 —— 另一个做了"逐区块大气"的 MC 模组

1. **项目名 + 链接 + 语言 + 许可证 + 维护状态**
   - https://github.com/QGMoe/GeoCraft ；CurseForge https://www.curseforge.com/minecraft/mc-mods/qg-geocraft ；Modrinth https://modrinth.com/project/3CKJAWbv ；MC百科 https://www.mcmod.cn/class/22470.html
   - Java（Minecraft 1.12.2，Forge + Cleanroom，需 MixinBooter 前置）；`mod_version = 0.2.7`，`root_package = top.qiguaiaaaa.geocraft`（来源 `gradle.properties`）
   - 维护：**活跃**（早期开发，"不保证稳定"，README 自述）
   - ⚠️ 许可证：README 未声明；我**未找到 LICENSE 文件**（未验证）

2. **算的是什么**：流体物理（有限体积/压强系统）+ **逐区块大气系统**（温度、水汽、相态、地面能量收支）+ 土壤湿度。

3. **方程/近似**
   - 官方资料页明确：**"该大气系统的本质是一个极度简化的气候模型"**；*"水汽会在大气间输送，只有有充足水汽时才会下雨"*；*"水的相态变化会影响地面、大气的能量交换（覆雪地面短波反射率升高，雪融化吸热减缓升温）"*；*"地面热容和反射率会影响地面和大气的温度变化，进而产生局地热力环流"*。
   - ⚠️ **具体系数的源码级细节我未能取得**：仓库文件清单超过抓取上限（>100 KB），且 GitHub API 在本会话被速率限制（unauthenticated 60/h 已耗尽），Software Heritage 尚未收录该仓库。**因此我只给结构级结论，不给方程级结论——这是本次调研的已知缺口。**

4. **是否需要全局迭代：否，但它是"逐区块 cells + 邻域交换"**
   - **网格：1 个大气实例 = 1 个区块（16×16 格）**；垂直方向是"层级链"（下垫面层级 + 大气层级，可多层循环）。
   - **时间步：大气刻 = 60 游戏刻**；*"每游戏刻理想情况下只会更新六十分之一的大气"* → **把 60 个区块的更新摊到 60 tick 上，摊销成本**。这是很值得我们抄的一个性能技巧。
   - 邻域：`获取相邻大气数据` → 从最上层级往下更新。
   - 存储：大气区域 = **128×128 区块 = 16384 个大气实例**；区域坐标 = 区块坐标 >> 7；区域内相对坐标 = 区块坐标 & 0x7F。文件 `./DIM<i>/atmosphere/r.X.Z.atmdat`，格式仿 Anvil（128 KiB 头 + zlib 数据）。
   - 卸载：每 **8000 tick（400 s）** 用曼哈顿距离算最近玩家，超过 `atmosphere.maxLoadDistance`（**默认 100 区块**）就标记卸载。

5. **性能**：官方未给数字；只给了摊销策略（1/60 per tick）与"未加载区块的大气仍可能被加载"的独立性。

6. **输出格式**：`r.X.Z.atmdat` 二进制（Anvil-like + zlib），NBT 结构 `{posX, posZ, Level}`。官方附了两个 **由 Claude AI 编写的 Python 读取器**（`AtmosphereDataFileReader` / `...GUI`），自述"可靠性不一定保证"。

7. **能借鉴什么**
   - ✅ **"1 区块 = 1 个气候 cell + 60 tick 摊销"**：这与我们"20 s / 100 km 瓦片"的预算方向一致，且天然支持无限世界（没有全局格，只有按需加载的 cell）。
   - ✅ **垂直分层链**（下垫面层 / 大气层，可重复循环）——比我们的"纬向平均 + 异常"更有结构，但成本也高。
   - ✅ **加载范围与区块解耦**（大气独立加载/卸载）——如果我们要做沿岸风，海岸线的"上风/下风"查询不应该触发区块加载，应像 Aerodynamics4MC 那样用 `SeedTerrainProvider` 直接按 seed 采样高度。
   - ❌ 它的"局地热力环流"**只是计划/待验证**（README 原文："进而产生局地热力环流（需要实验验证）"）——**不能作为沿岸风符号的可靠参考**。

8. **关键文件 / 文档 + URL**（源码路径未能取得，给资料页）
   - 大气实例（术语+生命周期+60 tick 规则+加载范围）：https://www.mcmod.cn/item/894817.html
   - 大气区域文件（128×128 区块 / `.atmdat` / 位移与掩码）：https://www.mcmod.cn/item/905005.html
   - 仓库：https://github.com/QGMoe/GeoCraft ；Wiki：https://github.com/QGMoe/GeoCraft/wiki
   - `gradle.properties`（`root_package = top.qiguaiaaaa.geocraft`）：https://github.com/QGMoe/GeoCraft/blob/master/gradle.properties

---

## D1.8 D1-5 搜索结果汇总（"minecraft mod ocean current / realistic climate wind / planetary climate"）

| 查询 | 结果 |
|---|---|
| `minecraft mod ocean current simulation` | 只搜到波浪渲染/航行类：Sablewaves、Wavey Capes、Boatload、Tide。**没有风生环流。** |
| `minecraft mod realistic climate wind` | **Aerodynamics4MC**（§D1.6）、Weather2/LTWeather（§D1.5a）、Project Atmosphere（ARR 无源码） |
| `minecraft planetary climate mod` | **未找到**任何真正做行星气候的 MC 模组。最近的是 Aerodynamics4MC 的 `pseudoCoriolisFactor` + GeoCraft 的逐区块大气。 |
| `minecraft biome climate simulation github` | Climate Control/Geographicraft（§D1.3）、TFC（§D1.1/§D1.2） |

### ★ D1 最重要的结论（针对你的卡点）

> **没有任何 Minecraft 模组做过风生洋流、上升流或方向性沿岸风。**
> 唯一具备"空间变化风场 + 科氏符号"的实现是 **Aerodynamics4MC**，且它的做法是 **纯 z 的伪科氏斜坡 `clamp((z mod 384 − 192)/192, −1, 1)` + 气压梯度的地转关系**——正好是纯函数、无全局求解、支持无限 x。
> **你的"39~52% 正确率"最可能的原因，是沿岸风向没有从"气压梯度 × sign(f)"解析地导出，而是走了某种对称/随机/局部平均的路径**（Climate Control 的 `GenLayerCoastalClimate` 就是四邻随机 → 系统性符号被抹平到 50% 的教科书案例）。

---

# D2. libnoise / FastNoise 系

## D2.1 libnoise —— **只有噪声模块，没有气候模块**

1. **项目**：http://libnoise.sourceforge.net/ （C++，**LGPL**（噪声库）/ GPL（示例），**已停止维护**，最后更新 2003–2005，作者 Jason Bevins）
2. **算的是什么**：只生成**相干噪声值**。官方原文：*"libnoise is not a renderer; it simply generates the terrain elevations using coherent noise."*
3. **方程/近似**：Perlin（improved gradient）、Simplex、Value、Worley/Cellular、Ridged-multifractal。
4. **是否需要全局迭代**：否。全部是逐点求值。
5. **性能**：官方行星示例给了一个实测：**AMD Athlon 2000+ XP / Windows 2000 上生成 2048×2048 图约 25 分钟**（http://libnoise.sourceforge.net/examples/complexplanet/index.html ）。该示例用了 **100+ 个模块：23 generator + 52 modifier + 18 combiner + 9 selector + 26 cache**，最大分辨率 7.5 m。
6. **输出格式**：库本身输出 float；示例输出 PGM/图片。
7. **能借鉴什么**：见 §D2.2。**气候模块：明确未找到。**
8. **关键 URL（均已验证 HTTP 200）**
   - **完整模块目录（源码文件名列表，29 个 .cpp）**：https://libnoise.sourceforge.net/docs/dir_000001.html
     实测包含：`abs, add, billow, blend, cache, checkerboard, clamp, const, curve, cylinders, displace, exponent, invert, max, min, modulebase, multiply, perlin, power, ridgedmulti, rotatepoint, scalebias, scalepoint, select, spheres, terrace, translatepoint, turbulence, voronoi`
     → **29 个，全部是噪声生成器 / 数学组合器 / 坐标变换器。0 个气候模块。**
   - 模块分组（Models / Noise / Modifier / Combiner / Generator / Selector / Miscellaneous / Transformer）：https://libnoise.sourceforge.net/docs/modules.html
   - 官方行星示例（含性能数字）：http://libnoise.sourceforge.net/examples/complexplanet/index.html
   - 教程 1–8：https://libnoise.sourceforge.net/tutorials/tutorial1.html …`tutorial8.html`
   - ⚠️ 我**没有逐个验证**每个类的 doxygen 页 URL（形如 `docs/classnoise_1_1module_1_1<Name>.html`），因此**不在此处列出未验证的类页链接**。请从上面的 `dir_000001.html` 或 `modules.html` 点进去。

## D2.2 libnoise 的组合器能不能拼"查表 + 噪声扰动"的气候场？—— **能，而且是完整积木**

| 需要的功能 | libnoise 模块 | 行为 |
|---|---|---|
| 纬向查表基（lat → 基准温度） | `Curve` (curve.cpp/h) | 把输入值映射到**任意控制点定义的曲线**上 → 相当于 1D LUT |
| 幅值/偏移标定 | `ScaleBias` (scalebias.cpp/h) | `out = in·scale + bias` |
| 噪声扰动叠加 | `Add` (add.cpp/h)、`Turbulence` (turbulence.cpp/h) | `Turbulence` 就是 **domain warp**：用噪声随机位移输入坐标 |
| 海陆/地形驱动切换 | `Select` (select.cpp/h) | 用 control 模块的输出在两个源之间**平滑选择**（不是硬 if） |
| 区域混合（陆性 vs 海洋性） | `Blend` (blend.cpp/h) | 按 control 值加权混合两个源 |
| 高程衰减 | `Exponent`、`Power`、`Clamp` | 幂/指数重映射 |
| 分层叠加 | `Max`、`Min`、`Multiply`、`Invert`、`Abs` | 基本运算 |
| 坐标变换（把 z 变成纬度、把 x 变成离岸距离） | `TranslatePoint`、`ScalePoint`、`RotatePoint` | 输入坐标仿射变换 |
| 性能 | `Cache` (cache.cpp/h) | 缓存上一次输出 → 对"同一坐标被多次查询"很有效 |

**能拼出的气候场配方（基于以上模块，标注为我的构造）**：
```
lat      = ScalePoint(z, 1/30000)                     // 归一化纬度
baseT    = Curve(lat)                                  // 1D LUT：纬度→基准温度
cont     = Curve(continentality)                       // 1D LUT：海陆度→季节振幅
season   = Multiply(Curve(monthIndex), cont)           // 季节项
noiseT   = ScaleBias(Perlin(seed), 3.0, 0)             // ±3 K 区域扰动
temp     = Add(Add(baseT, season), noiseT)
```
再对 `temp` 做 `Select`/`Blend` 与海洋路径混合 → 得到"查表 + 噪声扰动"的气候场。
**唯一放不进去的东西**：任何需要全局约束的量（质量守恒的水汽、连续性方程、椭圆型压力求解）。libnoise 图是**纯 feed-forward DAG**，没有反馈边也没有迭代。

## D2.3 FastNoiseLite —— domain warp 参数级细节

1. **项目**：https://github.com/Auburn/FastNoiseLite （C/C++/C#/Rust/Java/JS…，**MIT**，作者 Jordan Peck / Auburn）
   - 头文件版本：**VERSION 1.1.1**（`Cpp/FastNoiseLite.h`）
2. **算什么**：Perlin / Value / ValueCubic / OpenSimplex2 / OpenSimplex2S / Cellular（6 种噪声）+ 5 种 fractal + **domain warp**。
3. **domain warp 的实际公式**（`Cpp/FastNoiseLite.h`，函数 `DoSingleDomainWarp` / `DomainWarpSingle`）：
   ```cpp
   amp  = mDomainWarpAmp * mFractalBounding;   // mDomainWarpAmp 默认 1.0f
   freq = mFrequency;                          // 默认 0.01f
   switch (mDomainWarpType) {
     case OpenSimplex2:        amp *= 38.283687591552734375f;  // 2D
     case OpenSimplex2Reduced: amp *= 16.0f;                   // 2D
     case BasicGrid:           /* amp 不缩放 */                 // 2D
   }
   // 3D: OpenSimplex2 → amp *= 32.69428253173828125f; Reduced → amp *= 7.71604938271605f
   ```
   分形（`DomainWarpFractalProgressive` / `...Independent`）：
   ```cpp
   for (int i = 0; i < mOctaves; i++) {
       xs = x; ys = y; TransformDomainWarpCoordinate(xs, ys);
       DoSingleDomainWarp(seed, amp, freq, xs, ys, x, y);
       seed++;  amp *= mGain;  freq *= mLacunarity;   // 默认 gain 0.5, lacunarity 2.0
   }
   ```
   − **Progressive**：下一层的采样点用**上一层已经偏移过的** `(x,y)`；**Independent**：每层都用同一个原始 `(xs,ys)`。
4. **是否需要全局迭代**：否，逐点。
5. **性能**：C++ 单头文件，SIMD 版本见 FastNoise2。
6. **能借鉴什么 —— 能不能做"沿风向的水汽平流"？**
   - **直答：FastNoiseLite 的 domain warp 只能做各向同性位移，本身不能表达"沿风向"。** 它的位移向量来自内部梯度噪声，**没有 API 让你指定位移方向**（`SetDomainWarpAmp` 是标量，`mFrequency` 也是标量，不能分轴设频率）。
   - **可行配方（我的构造，非文档）**：把"沿风向的滞后"做成一次**显式坐标偏移**，用噪声只要一个标量滞后场：
     ```
     lag   = 0.5 + 0.5 * fnl.GetNoise(x*0.002, z*0.002)   // [0,1] 范围的无量纲滞后
     tau   = lag * TAU_MAX                                 // TAU_MAX ≈ 3000 格 / 风速
     xs    = x - u(x,z) * tau                              // u,v = 你的风场
     zs    = z - v(x,z) * tau
     q     = saturatingHumidity(xs, zs) * exp(-tau * k)    // 顺风衰减
     ```
     即：**不是用 domain warp 去平流，而是用 domain warp 去扰动"平流时间"，真正的平流由你显式做坐标回溯（semi-Lagrangian 单步回溯）**。这跟 Aerodynamics4MC L0 的 `半拉格朗日回溯 + 双线性插值` 是同一招（见 §D1.6 第 4 点第 1 步）。
   - ✅ 可以真正借的：`FractalType_DomainWarpProgressive` 的 `amp *= gain; freq *= lacunarity` 多尺度叠加，用来给"海岸线曲折度"或"水汽通道弯曲"做多尺度扰动。

7. **关键文件 + URL**
   - https://github.com/Auburn/FastNoiseLite
   - `Cpp/FastNoiseLite.h`（VERSION 1.1.1；`DoSingleDomainWarp` / `DomainWarpSingle` / `DomainWarpFractalProgressive` / `DomainWarpFractalIndependent`；`mDomainWarpAmp` 默认 1.0）
     https://github.com/Auburn/FastNoiseLite/blob/master/Cpp/FastNoiseLite.h

## D2.4 FastNoise2

1. **项目**：https://github.com/Auburn/FastNoise2 （C++17，**MIT**，**活跃维护**，SIMD 节点图）
2. **算什么**：节点图（node graph）。官方 README 列出的能力：
   - Coherent Noise: Perlin, Simplex, SuperSimplex (OpenSimplex2S), Value；Cellular Value / Distance / Lookup
   - Fractals: FBm, Ridged
   - Blends & Operators: Add, Subtract, Multiply, Divide, (Smooth)Min, (Smooth)Max, Fade …
   - Modifiers: Remap, Terrace, Domain Scale/Offset/Rotate …
   - **Domain Warping: Gradient, Simplex, SuperSimplex；Fractal Progressive, Fractal Independent**
   - 维度：2D/3D/4D + **2D tiling**
   - **线程安全**（同一节点树可多线程并行生成）、节点树可**序列化成字符串**
3. **是否需要全局迭代**：否。
4. **性能**：README 明确设计目标是 SIMD 融合——*"the entire computation is fused and executed in SIMD, maximizing throughput and minimizing both memory allocation and bandwidth"*；节点间中间值不落内存。**无具体 bench 数字。**
5. **能借鉴什么**：
   - ✅ **"节点图 = 融合执行 + 无中间内存"** 这个性能论点，正是你"20 s / 100 km"预算下应该采用的写法：把整个气候链编译成一次遍历，别为每个场分配一个 2D 数组。
   - ✅ **Domain Warp 节点类型清单**（Gradient / Simplex / SuperSimplex）给了你选择：**Gradient warp 是最便宜的各向异性友好的那种**（位移来自单一梯度场，你可以用两个独立 seed 的 Gradient 节点分别当 u、v 位移分量 → 就得到了"有方向的位移场"）。
   - ✅ 2D tiling 支持 → 对"x 方向无限、z 方向有限"的场，可以直接用。
6. **关键文件 + URL**
   - https://github.com/Auburn/FastNoise2
   - `include/FastNoise/FastNoise.h`（主入口，`FastNoise::New<T>()`、`NewFromEncodedNodeTree()`；包含清单里明确有 `Generators/DomainWarp.h`、`Generators/DomainWarpSimplex.h`、`Generators/DomainWarpFractal.h`、`Generators/Modifiers.h`、`Generators/Blends.h`）
     https://github.com/Auburn/FastNoise2/blob/master/include/FastNoise/FastNoise.h
   - 在线节点编辑器（WASM）：https://auburn.github.io/FastNoise2/
   - README（功能清单）：https://github.com/Auburn/FastNoise2/blob/master/README.md

## D2.5 noise-rs

1. **项目**：https://github.com/Razaekel/noise-rs （Rust，**MIT / Apache-2.0 双许可**，crate `noise = "0.9"`，**活跃**，docs.rs 显示 0.9.0）
2. **算什么**：见下（**全是噪声，无气候**）
3. **完整模块清单（来自 docs.rs，已核实）**：
   `Abs, Add, BasicMulti, Billow, Blend, Cache, Checkerboard, Clamp, Constant, Curve, Cylinders, Displace, Exponent, Fbm, HybridMulti, Max, Min, Multiply, Negate, OpenSimplex, Perlin, PerlinSurflet, Power, RidgedMulti, RotatePoint, ScaleBias, ScalePoint, Select, Simplex, SuperSimplex, Terrace, TranslatePoint, Turbulence, Value, Worley`
   Traits：`MultiFractal`、`NoiseFn`、`Seedable`
   → **libnoise 的 1:1 Rust 移植 + 扩展。同样 0 个气候模块。**
4. **domain warp 等价物**：`Displace`（"uses multiple source functions to displace each coordinate of the input value"）与 `Turbulence`（"randomly displaces the input value before returning the output"）—— **`Displace` 比 libnoise 的 `Turbulence` 更灵活：它接受多个源函数，可以给 x/z 分别用不同的位移函数 → 可实现"沿风向的位移"。**
5. **能借鉴什么**：**`Displace` 是本次调研中最适合做"有方向的位移场"的现成 API**：`Displace::new(source, x_displace_fn, y_displace_fn, z_displace_fn, ...)` 这种形态允许 `Δx = τ·u(x,z)`、`Δz = τ·v(x,z)`。若用 Rust 写工具链，这是首选。
6. **URL**
   - 仓库：https://github.com/Razaekel/noise-rs
   - 模块/结构体全清单（docs.rs 0.9.0）：https://docs.rs/noise/latest/noise/
   - README（Planetary Surface 示例、PlaneMapBuilder）：https://github.com/Razaekel/noise-rs/blob/master/README.md

## D2.6 Accidental Noise Library (ANL)

1. **项目**：https://github.com/JTippetts/accidental-noise-library （C++11，**header-only**，`#include <anl.h>`；需在恰好一个 TU 里 `#define ANL_IMPLEMENTATION`）
2. **算什么**：噪声函数**表达式图**。核心两类：`anl::CKernel`（一个复合噪声函数，存成 `std::vector<CInstruction>` 扁平数组）与 `anl::CNoiseExecutor`（求值器）。
   - 生成器：Perlin improved gradient、simplex 变体、value、Worley cellular
   - 插值：None / Linear `A + t(B−A)` / Cubic `t = t²(3−2t)` / Quintic `t = t³(t(6t−15)+10)`
   - **支持 2/3/4/6 维求值**（`CCoordinate` 封装任意维坐标）
   - 表达式解析器 `CExpressionBuilder`：`"clamp(scaleY(scaleX(gradientBasis(3,rand),3),3)*0.5+0.5,0,1)"`
   - 图像映射：`map2D / map2DNoZ / mapRGBA2D / map3D / mapRGBA3D` + `CArray2Dd / CArray2Drgba / CArray3Dd / CArray3Drgba`
   - ⚠️ **周期警告（对"无限世界"很关键）**：默认哈希是 512 项查找表 + 坐标 `& 0xff` → **噪声周期 = 256**！要更长周期必须 `#define ANL_LONG_PERIOD_HASHING`（换成基于 KU Leuven 长周期哈希的算法，性能略降）。
3. **是否需要全局迭代**：否。
4. **性能**：README 无数字；只提到长周期哈希"slight decrease in performance"。
5. **能借鉴什么**
   - ✅ **`CExpressionBuilder` 的表达式字符串**非常适合我们：把气候链写成一条可热重载的表达式（`"clamp(curve_lat(z)*seasonAmp + perlin(seed)*3, ...)"`），便于调参。
   - ✅ **6 维求值**：把 `(x, z, season, continentalness, ...)` 塞进坐标，噪声核自动处理——如果我们想把季节当作一个连续维度做插值，这是最省事的做法。
   - ⚠️ **256 周期必须避开**：我们的 z 到 30000 会重复 117 次。必须开 `ANL_LONG_PERIOD_HASHING`。
6. **URL**
   - https://github.com/JTippetts/accidental-noise-library
   - README（含周期警告与全部 API 说明）：https://github.com/JTippetts/accidental-noise-library/blob/master/README.md
   - `VM/kernel.h`：https://github.com/JTippetts/accidental-noise-library/blob/master/VM/kernel.h
   - `VM/vm.h`（`CNoiseExecutor` 在 L99）：https://github.com/JTippetts/accidental-noise-library/blob/master/VM/vm.h
   - `Expression/expressionbuilder.h`：https://github.com/JTippetts/accidental-noise-library/blob/master/Expression/expressionbuilder.h

## D2.7 D2 结论

| 库 | 有气候模块吗 | 有 domain warp 吗 | 能否拼"查表+噪声扰动"气候场 | 能否做"沿风向平流" |
|---|---|---|---|---|
| libnoise | **没有** | `Turbulence`（各向同性） | 能（`Curve`+`ScaleBias`+`Select`+`Blend`+`Cache`） | 不能（无方向参数） |
| noise-rs | **没有** | **`Displace`（可分工轴！）** | 能 | **最接近能**（`Displace` 各轴独立位移函数） |
| FastNoiseLite | **没有** | `DomainWarp`（各向同性，amp 标量） | 能 | 不能（需自己回溯坐标） |
| FastNoise2 | **没有** | Domain Warp 节点（Gradient/Simplex/SuperSimplex） | 能（节点图） | 部分（Gradient warp 可用双 seed 当 u/v） |
| ANL | **没有** | `displace` 类算子 | 能（`CExpressionBuilder`） | 部分 |

---

# D3. 其它引擎 / 库

## D3.1 Godot / Unity / Unreal —— 行星气候插件

- **结论：未找到**任何"行星气候模拟"插件。
- 找到的都是**渲染/大气散射**类，不含气候场：
  - Godot：`fbcosentino/godot-extremely-fast-atmosphere` —— *"A (spherical) planet atmosphere system for Godot which does not use any form of ray marching"* → **纯着色器大气散射，无风无洋流**
    https://github.com/fbcosentino/godot-extremely-fast-atmosphere
  - Unreal：论坛帖 *"Create realistic planets"*、*"Procedural Planet – Voxel Worlds, Atmosphere & Player System (URP)"* → 均为**地形/大气渲染**，非气候
    https://forums.unrealengine.com/t/plugin-create-realistic-planets/2743419
    https://forums.unrealengine.com/t/kevrpss-store-procedural-planet-voxel-worlds-atmosphere-player-system-urp/2726925
- Unity Asset Store 的 "planet climate" 类资源：**未找到**可引用的官方文档页。

## D3.2 Gaea —— **没有 climate 节点**

1. **项目**：https://quadspinner.com/gaea （商业，闭源）；文档 https://docs.gaea.app/
2. **节点分类（已核实，共 9 类）**：`terrain / primitive / simulate / surface / modify / derive / colorize / utility / output`
   → **没有 "climate" 分类，也没有名为 Climate 的节点。**（node index: https://docs.gaea.app/reference/nodes/index.html ）
3. **最接近气候的两个节点**（可借鉴的**参数语义**，不是方程）：
   - **Erosion2**（`Simulate › Erosion`，shortcode `e2`）：有 **Orographic Influence** 开关、**Directional Precipitation**（"Simulates rain falling more intensely from one direction to create rain shadows"）+ **Direction**（"The direction from which rainfall comes most strongly"）+ **Rain Shadow**（"Controls how strongly terrain blocks rainfall from certain directions"）+ **Slope / Altitude / Reverse** 掩码。
     → **这就是"上风地形损耗降水"的工业级参数化**，且**明确带有风向**！文档：https://docs.gaea.app/reference/nodes/simulate/erosion2
   - **Snowfall / Snowfield / Dusting / Glacier**（Simulate 类）：雪花粒子物理（clumping / adhesion / melt / thaw / settling），Gaea 用"**多轮降雪 + 融雪交替**"而不是单纯降低降雪量来产生可信雪盖。
     文档：https://github.com/QuadSpinner/gaea2docs-guide/blob/main/using-gaea/simulations/snowfall.md
4. **性能**：Erosion2 自述 "up to 10x faster performance, even on the CPU"（相对旧 Erosion）。
5. **能借鉴什么**
   - ✅ **"Directional Precipitation + Rain Shadow + Direction"** 的参数模型可以直接映射到你的"上风地形损耗"：把 `direction` 设成你的沿岸风方向，`rain shadow` 强度设成损耗系数。**这是本次调研里唯一明确把"风向"作为降水参数暴露出来的工业工具。**
   - ⚠️ 但 Gaea 是**离线烘焙工具**（全局迭代、非纯函数），不能直接搬算法，只能搬参数语义。

## D3.3 GPlates / pyGPlates → 气候链路

1. **GPlates**：https://www.gplates.org/ （开源，GPL；板块重建，**本身不做气候**）
   - pyGPlates 文档：http://www.gplates.org/docs/pygplates/index.html
2. **"板块 → 气候"的实际链路（有真实开源项目把两者接起来）**：
   - **EarthByte / paleoclimate-reconstruction**（GPL-3.0 + CC BY-NC-ND 4.0）：
     https://github.com/EarthByte/paleoclimate-reconstruction
     链路（README 明确写）：
     ```
     [GPlates 板块重建 + query_paleogeography]  →  古地理坐标 (PaleoXY)
     + 岩性数据库 (LithData_PaleoXY_Matthews2016.csv, LithologyCodes.csv)
     + 中新世 2.5° 全球降水场 (PRECT_Average_annum.grd, Herold+ 2012)
     → data_preprocess_miocene.py  (约 4 分钟)
     → 贝叶斯高斯过程 + Gibbs 采样 MCMC (Matlab, 改编自 GPplus)
     → reconstruction_prediction/model/results_all.csv  →  降水重建图
     ```
     论文：R. Chandra, D. Muller, N. Butterworth, S. Cripps, *"Precipitation reconstruction from climate-sensitive lithologies using Bayesian machine learning"*, Environmental Modelling & Software, 2021
   - **R-gplates**（已并入 GPlates 家族）：https://www.earthbyte.org/rgplates-now-part-of-gplates-software-family/
3. **是否需要全局迭代**：**是，而且是重度迭代**——高斯过程 + Gibbs 采样 MCMC，在 2.5° 全球网格上跑。**与你的约束不兼容。**
4. **能借鉴什么**
   - ✅ **"岩性/沉积指示物 → 古降水"** 这个思路可以反向用：**用我们已有的风场/上升流场去约束"哪里该是蒸发岩（干旱）/煤（湿润）"**，作为一个 sanity check。但它是离线贝叶斯，不是实时场。
   - ❌ **pyGPlates 本身不含任何气候模块**。GPlates 只输出板块运动/古地理坐标。
   - **结论：GPlates 侧"板块→气候"没有可直接搬的实时算法；唯一开源链路是离线的贝叶斯降水重建。**

## D3.4 ExoPlaSim 的 Minecraft 用法

1. **ExoPlaSim**：https://github.com/alphaparrot/ExoPlaSim （Python 封装 + Fortran 谱动力核心，**GPL**，活跃）
   - 文档：https://github.com/alphaparrot/ExoPlaSim/blob/master/docs/index.rst
   - 它是一个**真正的三维谱大气环流模式（GCM）**：全球谱展开、需要全局求解 → **与你的"纯函数、无全局二维椭圆求解"约束根本冲突**，只能当"离线真值生成器"用。
2. **"ExoPlaSim + Minecraft"：未找到。**
   - 搜索 `ExoPlaSim minecraft` 只返回 **ExoPlaSim-InCon**：https://github.com/OstimeusAlex/ExoPlaSim-InCon
     README 自述：*"A user-friendly interface for configuring an ExoPlaSim .py file"*（tkinter GUI，只在 Linux 上跑 ExoPlaSim）。**它与 Minecraft 无关**，只是把 ExoPlaSim 的配置项做成 GUI，外加一个 Image→SRA 转换器。
   - **结论：没有任何人把 ExoPlaSim 的输出接进 Minecraft。**
3. **能借鉴什么**：**用 ExoPlaSim 离线生成"真值场"，然后拟合/校验你的 1D 解析解**。例如：让它给出纬向风 `u(φ)` 与 `∂P/∂x` 的典型量级，用来标定你的伪科氏斜坡的周期与幅值。这是唯一合理的用法。

---

# 附录 A：给卡点的最短行动清单

针对"**东边界沿岸风的符号（副热带东岸应当一致吹向赤道）只有 39~52% 正确**"：

1. **不要再从"沿岸"几何或邻域平均推风向**。Climate Control 的 `GenLayerCoastalClimate` 证明这条路会把系统性符号抹平到 ~50%（四邻随机）。
2. **改成"气压距平梯度 + 符号化的科氏参数"解析导出**（Aerodynamics4MC `BackgroundMetGrid.computeGeostrophicWind`，源码 ~L814–L841）：
   ```
   u_g = −(∂P/∂z)·K·sign(f(z)) / sqrt(f(z)² + ε²)
   v_g = +(∂P/∂x)·K·sign(f(z)) / sqrt(f(z)² + ε²)
   f(z) = clamp( (z mod P − P/2) / (P/2), −1, +1 ),  P = 384 cell ≈ 98.3 km（或按你的纬度尺度重标定）
   K = 12.0（对应 12 m²/(Pa·s)，可按你的气压距平量级缩放）
   ```
   副热带东岸：副高 `∂P/∂x` 的符号 + `sign(f)` 自动给出向赤道的分量，**正确率应接近 100%，因为符号是解析的**。
3. **避坑**：Aerodynamics4MC 用 `max(0.55, |f|)` 做分母 → 赤道附近风速被放大。改用 `sqrt(f² + ε²)` 或 `|f| < f_min` 时切到"气压梯度力 + 摩擦"平衡。
4. **沿岸分解**：用海岸线法向 `n̂`，把风分解成 `u·n̂`（离岸/向岸）与 `u·t̂`（沿岸）。沿岸风的符号判据就变成 `sign(u·t̂)`。
5. **上风地形损耗**：抄 Gaea Erosion2 的 `Directional Precipitation + Direction + Rain Shadow` 参数语义（https://docs.gaea.app/reference/nodes/simulate/erosion2 ）。
6. **季节**：Serene Seasons 的"偏移只做负向 + 只在基础温度 ≤ 0.8 时生效 + clamp[−0.5, 2.0]"（`SeasonASMHelper.getFloatTemperature`）；热带切换成干/湿季。
7. **半球季节翻转**：**不要**用 TFC 1.7.10 的整数跳变 `(day/30 + (z>0?6:0)) % 12`，用连续函数。

---

# 附录 B：本次调研的方法与已知缺口

**方法**：全部结论来自 `web_fetch` 直接抓取源码/文档原文（GitHub raw、docs.rs、sourceforge doxygen、官方文档站），未使用二手博客描述。GitHub 文件清单通过 `ungh.cc` 代理获取（GitHub REST API 在本会话触发未认证速率限制）。

**已知缺口（明确说明，不编造）**：
1. **GeoCraft 的源码级方程未取得**：仓库文件清单 > 100 KB 抓取上限，GitHub API 速率限制，Software Heritage 未收录。只得到结构级结论（1 区块 = 1 大气实例、60 tick 摊销、128×128 区块区域文件、默认加载距离 100 区块）。
2. **Project Atmosphere 无源码**（ARR），无法调研。
3. **libnoise 各模块的 doxygen 类页 URL 未逐个验证**，只给出已验证的目录页 `docs/dir_000001.html` 与 `docs/modules.html`。
4. **Deadrik/TFCraft 未找到 LICENSE 文件**，许可证状态不明。
5. **GeoCraft 未找到 LICENSE 文件**。
6. **Aerodynamics4MC 的 Java 源码路径**：设计文档写的是 `fabric-mod/src/main/java/...`，但实际仓库路径是 `src/main/java/com/aerodynamics4mc/runtime/...`（我实测确认 `src/main/java/...` 返回 200）。行号我按**实际仓库路径的文件**核实（`BackgroundMetGrid.java` 常量 L14–L45、梯度计算 L821、`pseudoCoriolisFactor` ≈L858）。
