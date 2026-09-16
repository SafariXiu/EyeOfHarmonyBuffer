# FMG（Azgaar's Fantasy Map Generator）气候系统源码级调研

调研对象：`Azgaar/Fantasy-Map-Generator`，**master @ `a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94`**（提交时间 2026-09-12，`package.json` 版本 `1.152.2`）。
本文所有行号都对应上面这个 commit，链接是可点击的永久链接（blob + #L 锚点 / raw）。
方法：`git clone --filter=blob:none --depth 1` 到本地，用本地文件逐行读取 + 在本地 clone 上跨文件 grep；关键 raw permalink 已回源 HTTP 200 校验。

---

## 0. 三个直接回答（先看这个）

1. **FMG 的风 = 纯纬向分带规则表，而且是"6 个手调角度"的规则表。** 取值为 `options.map.climate.winds = [225, 45, 225, 315, 135, 315]`，按 `tier = (|lat - 89| / 30) | 0` 取第 tier 项。角度**只是纬度的函数**：任何经度、任何海陆位置，同一纬度行的风完全相同。没有东岸/西岸概念，没有沿岸风修正。
2. **风甚至不是一个向量场。** 角度只被拿去判定落在 4 个（实际是**重叠**的）象限里，决定水汽从地图哪条边进入；真正的输运永远是**轴向**的：要么整行沿 ±x，要么整列沿 ±y，永远不会斜着走一格。所以"沿岸风符号"这个自由度在 FMG 里根本不存在。
3. **结论：FMG 对"东边界沿岸风符号 39~52%"这个卡点提供 0 条符号层面的经验**——它没有解风、没有气压场、没有 Coriolis、没有 Ekman、没有洋流、没有海陆热力差。可借鉴的是**算法骨架**（一维沿线积分 + 分段衰减 + 地形损耗；纯函数、无随机性的风查询接口；tier 表的写法），而不是符号判定。这是一个不同的设计取舍，本身就是结论。

---

## 1. 项目基本信息

| 项 | 值 |
|---|---|
| 项目名 | Azgaar's Fantasy Map Generator (FMG) |
| 主链接 | https://github.com/Azgaar/Fantasy-Map-Generator/ |
| 在线版 | https://azgaar.github.io/Fantasy-Map-Generator/ |
| 语言 | TypeScript（正在从 vanilla JS 迁移，v1.149 起；气候模块已全部 TS） |
| 许可证 | **MIT**（`LICENSE` 首行 "MIT License"，Copyright 2017-2024 Max Haniyeu (Azgaar)）。注意 GitHub API 的 `spdx_id` 报 `NOASSERTION`，因为 LICENSE 文件头与标准模板不完全一致；实际是 MIT |
| 维护状态 | **活跃维护**。默认分支 `master`，最后一次提交 2026-09-12；stars 5990；未被 archive |
| 仓库规模 | `src/` 下 71 个 generator 文件，气候相关只有 `temperature-generator.ts`(60 行) + `precipitation-generator.ts`(129 行)，无独立 winds/climate/ocean 模块 |

---

## 2. 算的是什么（输出哪些场）

管线顺序（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/generation-pipeline.ts#L7-L47）：`grid → heightmap → mapSize(Coordinates) → temperatures → precipitation → regraph(Pack) → rivers → biomes → ice → goods → cultures → states → ...`

气候写出的场只有两个：

| 场 | 类型 | 单位 | 定义位置 |
|---|---|---|---|
| `grid.cells.temp` | **Int8Array**（源码 L11；注意 https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/docs/architecture/data-model.md#L88-L89 写成 Uint8Array，是**过期文档**） | 整数 °C，clamp [-128,127] | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/temperature-generator.ts#L11 |
| `grid.cells.prec` | Uint8Array，0..255 | 1 单位 = **100 mm/年**（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/utils/unitUtils.ts#L75-L77：`prec * 100 + " mm"`） | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts#L29 |

**没有**任何风速/风向/气压/海温/洋流场被保存。`cells.temp/prec` 的下游消费者（可 grep 验证）：
- `biomes-generator.ts` → `pack.cells.biome`
- `river-generator.ts:64,118,184,622` → `cells.fl`（流量）→ 河流/湖泊
- `ice-generator.ts`（冰盖）、`relief-generator.ts:29,35`（地貌图标）、`goods-generator.ts:1067-1068`（`minTemp/maxTemp` 分布规则）
- `burgs-generator.ts:226,665`（人口/选址）、`cultures-generator.ts:65-71`、`states-generator.ts`
- `routes-generator.ts:327`（`MIN_PASSABLE_SEA_TEMP`，海路可通行性）
- 渲染层：`draw-temperature.ts` / `draw-precipitation.ts`

---

## 3. 用什么方程/近似（equation-level）

### 3.1 温度：纬度三段落线性 + 高度递减（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/temperature-generator.ts#L7-L56）

默认参数（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/components/options-model.ts#L51-L55）：
```
temperature: { equator: 27, northPole: -30, southPole: -15 }   // °C
precipitation: 100                                            // 百分比
winds: [225, 45, 225, 315, 135, 315]                          // 6 个角度，30° 一档
```
常数（L18-19）：`const tropics = [16, -20]; const tropicalGradient = 0.15;`

**实际公式**（L21-36）：

```text
T_northTropic  = T_eq - 16 * 0.15                    = 27 - 2.4  = 24.6
gradN          = (T_northTropic - T_npole)/(90 - 16) = 54.6/74  = 0.7378 °C/°lat
T_southTropic  = T_eq + (-20) * 0.15                 = 27 - 3.0  = 24.0
gradS          = (T_southTropic - T_spole)/(90 - 20) = 39.0/70  = 0.5571 °C/°lat

if -20 <= lat <= 16 :  T_sea(lat) = T_eq - |lat| * 0.15
elif lat > 16       :  T_sea(lat) = 24.6 - (lat - 16) * gradN
else                :  T_sea(lat) = 24.0 + (lat + 20) * gradS
```

高度项（L38-42），注释原文 "temperature drops by 6.5°C per 1km of altitude"：
```text
tempDrop(h) = rn( ((h - 18) ** exponent) / 1000 * 6.5 )     // h >= SEA_LEVEL(=20)，否则 0
cells.temp  = minmax(T_sea(lat) - tempDrop(h), -128, 127)
```
- `exponent = options.map.units.height.exponent`，默认 **2**（options-model.ts:61），范围 1.5~2.2（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/docs/wiki/Scale-and-distance.md#L46-L50）。
- 高度数值 `(h-18)**exponent` 按**米**解释（`unitUtils.getHeight()` 在 unit 为 m 时 unitRatio=1，https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/utils/unitUtils.ts#L59-L72 是同一个表达式），所以海拔递减率名义上是 6.5 °C/km。
- 数值感受：h=60 → (42)^2/1000*6.5 = **11.5 °C**；h=80 → 25.0 °C；h=100 → **43.7 °C**。因为是平方（默认 exponent=2），高山降温极猛，这是 FMG 温度场的主要"地形"来源。

**有没有考虑海陆/洋流/季节？——全部没有。**
- 无海陆差异：L44-55 是逐行循环，行内所有 cell 共用同一个 `seaLevelTemp`，只减高度。海洋格和陆地格用同一个公式。
- 无洋流、无海温：全仓库 grep `current|gyre|thermohaline|upwelling|SST|sea surface` → **未找到**（唯一例外是 `features.ts:412` 把海湾命名成 "gulf"，与洋流无关）。
- 无季节：官方 FAQ 明确 "Temperature is annual average"（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/docs/wiki/Knowledge Base.md#L439-L441）。
- 无经度依赖：温度是 `f(lat, h)` 的纯函数。

**是否有"纬向平均 + 距平"结构？——没有。** 整个模块就是一个纬向平均剖面（3 段折线）+ 高度修正，**不存在距平项**，也没有任何二维平滑/插值。你们的"勒让德纬向平均 + 海陆季节异常"比它复杂一个量级。

### 3.2 降水：沿风向的**一维湿度积分**（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts#L1-L129）

文件第一行注释就是设计声明：
```ts
// The simplest precipitation model: winds enter the map from each side and drop humidity as they pass the cells
```

**全局缩放因子**（L31-32）：
```ts
cellsNumberModifier = (Grid.getCellsDesired() / 10000) ** 0.25      // 网格密度归一
modifier            = cellsNumberModifier * (options.map.climate.precipitation / 100)
```
默认 10000 点 + precipitation=100 → `modifier = 1`。

**初始化**（L41-53）：每条风路径独立，初始水汽
```ts
maxPrec = min(initialMaxPrec * latMod, 255)      // 纬向带 westerly/easterly 才乘 latMod
humidity = maxPrec - cells.h[first]              // 入口格越高，进来就越干
if (humidity <= 0) continue                      // 入口格高过 maxPrec ⇒ 整行跳过
```
`initialMaxPrec`（L80-81）= `120 * modifier`；侧向（northerly/southerly，L88/L96）= `(northerly/vertT) * 60 * modifier * latMod`。

**核心：逐格递减（这就是你们问的"走过多少格就衰减多少"）**（L34-39, L55-74）：
```ts
// L34-39 陆地格降水
normalLoss = max(humidity / (10 * modifier), 1)          // 常规递减：每格丢掉剩余水汽的 1/(10*modifier)
diff       = max(cells.h[i+n] - cells.h[i], 0)           // 迎风爬升高度差
mod        = (cells.h[i+n] / 70) ** 2                    // 注释原文："50 stands for hills, 70 for mountains"
precipitation = minmax(normalLoss + diff * mod, 1, humidity)

// L69-73 主循环里的陆地段
isPassable   = cells.h[current + next] <= MAX_PASSABLE_ELEVATION   // = 85 (L23)
precipitation = isPassable ? getPrecipitation(...) : humidity      // 完全阻挡 ⇒ 剩余水汽全部倾倒
cells.prec[current] += precipitation
evaporation  = precipitation > 1.5 ? 1 : 0                         // L72
humidity     = isPassable ? minmax(humidity - precipitation + evaporation, 0, maxPrec) : 0
```
- **衰减系数**：湿度乘性衰减，每格因子 `1 - 1/(10*modifier)`，即 `humidity *= exp(-1/(10*modifier))`，e 折长度 = `10*modifier` 格（modifier=1 时 **9.49 格**）。当 `humidity < 10*modifier` 时被 `Math.max(...,1)` 地板接管 → 尾巴变成**每格线性丢 1 单位**。
- **水汽再补给**（L58-65）：水格 `humidity = min(humidity + 5*modifier, maxPrec)`，同时 `cells.prec[current] += 5*modifier`（为了湖泊注水正确）；水格后面紧接陆地格时给"海岸降水" `max(humidity / rand(10,20), 1)` ← **这里用了种子随机数**（`rand` 走 Alea 的 seed PRNG，所以地图可复现，但算法本身含随机）。
- **永冻土**（L56）：`if (cells.temp[current] < -5) continue;` ← 该格不记录降水，而且 `continue` **连湿度更新也跳过**，水汽原样穿过。温度场在这里耦合进降水。
- **入口格漏判**（L47）：`if (!source[0]) continue; // legacy quirk: a band starting at cell 0 is skipped, fixing it changes every map` ← 第一行的带被跳过，是**已知并刻意保留**的行为。

**风场输入是什么？规则表还是解出来的？——规则表，且只用来选方向/选边。**
`getWinds()`（L105-125）：
```ts
range(0, cells.i.length, cellsX).forEach((cellId, rowId) => {
  lat    = options.map.geography.coordinates.latN - (rowId / cellsY) * coordinates.latT   // L113
  latMod = LATITUDE_MODIFIER[((Math.abs(lat) - 1) / 5) | 0]                                // L114
  tier   = (Math.abs(lat - 89) / 30) | 0;   // 30° tiers from 0 to 5, north to south      // L115
  angle  = options.map.climate.winds[tier];                                               // L116
  if (angle > 40  && angle < 140) westerly.push([cellId, latMod, tier]);         // L118 从西边缘进入，+1
  if (angle > 220 && angle < 320) easterly.push([cellId + cellsX - 1, latMod, tier]); // L119 从东边缘进入，-1
  if (angle > 100 && angle < 260) northerly++;                                   // L120 从北边缘，+cellsX
  if (angle > 280 || angle < 80)  southerly++;                                   // L121 从南边缘，-cellsX
});
```
注意这两点（很重要，容易被二手描述写错）：
- 四个判定是**独立的 if，不是互斥分支**：角度落在 100~140 会**同时**进 westerly 和 northerly；220~280 会**同时**进 easterly 和 southerly。而且它们不是 90° 一象限的干净划分。
- `tier` 用的是 `|lat - 89|`，不是 `|lat|`：tier0 = 59~89N、tier1 = 29~59N、tier2 = 1S~29N、tier3 = 31S~1S、tier4 = 61S~31S、tier5 = 91S~61S。

**实际输运循环**：`passWind(sources, initialMaxPrec, next, steps)`（L41-76）
- 西风：`passWind(westerly, 120*modifier, +1, cellsX)`（L80）——每条纬向行一个独立的一维积分，起点是行首 cell。
- 东风：`passWind(easterly, 120*modifier, -1, cellsX)`（L81）——起点是行尾 `cellId + cellsX - 1`。
- 北风：`passWind(range(0, cellsX, 1), maxPrecN, +cellsX, cellsY)`（L89）——每一**列**独立积分。
- 南风：起点是最后一行（L97）。
→ 每个格子的降水只由它**所在行/列的上风段**决定，永不斜向传播。整个降水场 = 4 组一维积分的稀疏叠加。

### 3.3 风的"规则表"全貌（含半球语义）

| tier | 纬度带（默认全球图） | 默认角度 | 方向含义（角=风**吹向**的屏幕方向，0=东/+x，90=南/+y，180=西，270=北） | 地球对应 |
|---|---|---|---|---|
| 0 | 59N~89N | 225 | 吹向 SW（来自 NE） | 北极东风带 polar easterlies |
| 1 | 29N~59N | 45 | 吹向 NE（来自 SW） | 北半球西风带 westerlies |
| 2 | 1S~29N | 225 | 吹向 SW（来自 NE） | 东北信风 |
| 3 | 31S~1S | 315 | 吹向 NW（来自 SE） | 东南信风 |
| 4 | 61S~31S | 135 | 吹向 SE（来自 NW） | 南半球西风带 |
| 5 | 91S~61S | 315 | 吹向 NW | 南极东风带 |

角度语义由三处交叉验证：① `world-configurator.ts:155-165` 的箭头路径 `M210,11 v-10 l-3,3 m6,0 l-3,-3`（基准指向**上**=北）配 `rotate(225 ...)` 等默认值；② `draw-precipitation.ts:48-58` 用 `\u21C9`（⇉ 向右）画西风、`\u21C7`（⇇ 向左）画东风，箭头画在 x=20 与 width-52（即西/东边缘）；③ `draw-precipitation.ts:61-62` 北风用 `\u21CA`（⇊）画在顶部 y=42，南风用 `\u21C8`（⇈）画在底部。

**风是解出来的吗？不是。** 用户在世界配置器里点箭头改角度：`options.map.climate.winds[tier] = (winds[tier] + 45) % 360`（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/controllers/world-configurator.ts#L395-L410），schema 硬约束长度 6（`options-schema.ts:102: winds: z.array(degrees).length(6)`）。

### 3.4 纬向降水修正常数表（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts#L12-L21）

```ts
// precipitation modifier per 5° latitude band
// x4 = 0-5: wet through the year (rising zone)
// x2 = 5-20: wet summer, dry winter
// x1 = 20-30: dry all year (sinking zone)
// x2 = 30-50: wet winter, dry summer
// x3 = 50-60: wet all year (rising zone)
// x2 = 60-70: wet summer, dry winter
// x1 = 70-85: dry all year
// x0.5 = 85-90: dry all year
const LATITUDE_MODIFIER = [4, 2, 2, 2, 1, 1, 2, 2, 2, 2, 3, 3, 2, 2, 1, 1, 1, 0.5];
```
索引公式 `((|lat| - 1) / 5) | 0` 有 **1° 偏移**（band0 实际覆盖 0~6°），与注释自洽。
**对你们的卡点最关键**：副热带下沉带（21°~31°，index 4-5）拿到的只是"少下雨"（×1），**没有任何沿岸/经向修正**——东岸沙漠是靠"水源在上风几千公里外就把水汽丢光"这个**间接机制**产生的，不是靠沿岸风。

---

## 4. 是否需要全局迭代

**否。** 两个生成器都是**单遍、无迭代、无收敛判据**：
- 温度 `generate()`：一次行循环，O(N)。
- 降水 `generate()`：4 组一维扫描（westerly / easterly / northerly / southerly），总计 ≤ 4N 次格访问 + 少量 O(cellsX+cellsY) 循环，O(N)，无松弛、无时间步、无 Poisson/椭圆求解。
- 全仓库 grep：`coriolis` 0 命中、`ekman` 0、`advect|advection` 0、`monsoon` 0、`ITCZ` 0、`continentality|oceanity|maritime` 0、`pressure` 6 命中全是市场经济里的 `applyMarketPressure()`（`markets-generator.ts`）。
- 降水里唯一的随机来源是 `rand(10,20)` 的海岸项；`getWinds()` 明确被写成"Free of randomness, so the renderer can ask for them at any time"（L101-104），供渲染器复用来画箭头——这是**纯函数查询接口**的好范例。

## 5. 时间复杂度 / 实测性能

- **源码里没有气候步骤的耗时注释或 benchmark**。有统一的计时开关：`pipeline.ts:18-33` 对每个 step id 和整个 pipeline 做 `TIME && console.time(step.id)`，所以气候两步在浏览器控制台里对应 id 就叫 `"temperatures"` / `"precipitation"`（`generation-pipeline.ts:14-15`）。**未找到**任何公开实测数字。
- 网格规模（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/data/graph-density.ts#L3-L25）：点/格数 **1000 → 100000**，13 档，默认 `DEFAULT_DENSITY = 4 = 10000`。
- 默认图（`options-model.ts:44`）：1280×800 px、points=10000。间距 = `sqrt(W*H/points)`（`grid-generator.ts:103-105`）= **10.12 px**，`cellsX = 126`、`cellsY = 79`（`grid-generator.ts:108-110`），实际格数 ≈ 9954。
- 物理尺度：默认 `units.distance.scale = 3`（`options-model.ts:59`，1 px = 3 km）→ **1 格 ≈ 30.4 km**；默认整图覆盖 ≈ 3840 × 2400 km。默认气候总运算量量级 = 温度 1N + 降水 4N ≈ 5×10^4 次格操作（**这是从源码推的算术，不是实测**）。
- 与你的预算对比（有用的标定）：FMG 用 ~10^4 个格算完**整个世界**；你 100 km 瓦片若用 1 km 网格就是 10^4 格/瓦片，预算是 **20 s**。也就是说你的每格预算比 FMG 的浏览器内预算高 3~4 个数量级——**你不必为性能牺牲物理**，可以把沿岸风规则做成显式判定而不是近似。
- 官方性能建议只提到降点数与缩小窗口：https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/docs/wiki/Q&A.md#L9-L16（"When generating maps, set Points number to 10K. Points (cells) number highly affects performance."）。

## 6. 输出什么格式

| 输出 | 内容 | 位置 |
|---|---|---|
| `.map`（可再载入） | 分节数组，**第 8 节 = `grid.cells.prec`，第 11 节 = `grid.cells.temp`**，逗号分隔整数 | 写：https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/services/io/save.ts#L138-L151；读：https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/services/io/load.ts#L299-L302 |
| JSON 导出（full/minimal/grid cells） | 每格对象含 `temp` 与 `prec`（`Array.from(...)`） | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/services/io/export-json.ts#L201-L224 |
| GeoJSON（cells/routes/rivers/markers/zones） | **不含 temp/prec**：cells 的 properties 只有 `id, height, biome, type, population, state, province, culture, religion, neighbors` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/services/io/export.ts#L703-L710；文档 https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/docs/wiki/GIS-data-export.md#L30-L35 |
| SVG 图层 | 温度等值面填充 `draw-temperature.ts`；降水格画圆（半径 `sqrt(prec/4)/cellsNumberModifier`，只画 h>=20 的陆格） | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/renderers/draw-precipitation.ts#L15-L28 |

## 7. 我们能直接借鉴什么（针对"东边界沿岸风符号"卡点）

**能借的：**
1. **"风向表 + 纯函数查询"的分层写法**：`getWinds()` 被刻意写成无随机纯函数（`precipitation-generator.ts:101-104`），生成器和渲染器共用同一份风。你的沿岸风符号判定也应该抽成一个**纯函数** `getCoastalWindSign(seed, lat, hemisphere, coastalNormal, band)`，渲染/调试图层和物理用同一个函数——这样"39~52% 正确"这种指标才可定位、可回归测试。
2. **1-D 沿线积分就够**：FMG 用一个"每格衰减 + 地形项"的一维扫描得到整个降水场，完全不迭代。你已经有一维 Munk 环流，完全可以把水汽输运做成**沿风轴的 1-D 积分 + 瓦片边界通量**，天然满足"x 无限 ⇒ 纯函数/一维积分"的约束（每行独立，行间不耦合，跨瓦片只需缓存上风段的状态）。
3. **tier 表的写法可以直接抄**：`tier = (|lat-89|/30)|0` + 6 项地球默认值 `[225,45,225,315,135,315]`，展开成 30° 一档的确定性函数，无限远 x 方向无接缝（因为完全不含经度）。
4. **迎风地形损耗的现成形式**：`max(humidity/(10*modifier),1) + Δh*(h_next/70)^2` 与"越过 h>85 全倒空"的硬雨影（L23, L69-73）。其中 `(h/70)^2` 这种"海拔门限平方"很便宜，适合做成你降水模块里的解析项。
5. **量级标定**：见 §5——你的每格预算比它宽 3~4 个数量级，所以**不要**照抄它的低分辨率妥协（Uint8 量化、100 mm 一格、每格乘性衰减）。

**明确不能借的（会踩坑）：**
6. **`prec` 的量化与单位**：Uint8Array（0..255）× 100 mm → 降水分辨率只有 100 mm/年，且上限 25500 mm；你已经有 Bolton 比湿，别退回整数场。
7. **衰减系数是"每格"不是"每公里"**：`1/(10*modifier)`，`modifier=(N/10000)^0.25`。这让 e 折长度**依赖网格分辨率**：10000 点（10.12 px/格）→ 9.49 格 ≈ 96 px；100000 点（3.2 px/格）→ 16.8 格 ≈ 54 px。换算成物理长度随密度变化约 1.8 倍，**不是尺度不变的**。你的世界是无限瓦片，必须写成 `humidity *= exp(-ds / L_moist)`（`L_moist` 为公里量级的物理长度）。要复现它的做法，指数应为 +0.5 而不是 0.25。
8. **轴向输运的近似**：`passWind` 只能 ±x 或 ±y，斜向风场在它这里等于被拆成"整行西风 + 整列北风"两套互不相干的积分（FMG 的 45° 默认值实际就同时触发了这两套）。你的模型若需要斜向沿岸流/水汽通道，这条必须废弃。
9. **`temp < -5 ⇒ continue`（L56）**：降水被清零但湿度**原样穿透**，是一个语义 bug 级行为，别抄。

## 8. 关键文件路径 + 行号 + URL

| 内容 | 文件:行 | 链接 |
|---|---|---|
| 温度公式（纬向三段落 + 高度递减） | `src/generators/temperature-generator.ts:7-56` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/temperature-generator.ts#L7-L56 |
| 温度参数默认值 | `src/components/options-model.ts:44-63` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/components/options-model.ts#L44-L63 |
| **风角度表默认值** | `src/components/options-model.ts:54` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/components/options-model.ts#L54 |
| 降水主算法 / 衰减 / 地形项 | `src/generators/precipitation-generator.ts:34-98` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts#L34-L98 |
| **getWinds() 分带规则 + 4 个 if** | `src/generators/precipitation-generator.ts:105-125` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts#L105-L125 |
| LATITUDE_MODIFIER 表 | `src/generators/precipitation-generator.ts:12-21` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts#L12-L21 |
| MAX_PASSABLE_ELEVATION=85 / 入口格漏判 | `src/generators/precipitation-generator.ts:23,47` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts#L23 |
| 风箭头渲染（⇉/⇇/⇊/⇈ + 进入边） | `src/renderers/draw-precipitation.ts:37-63` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/renderers/draw-precipitation.ts#L37-L63 |
| 世界配置器 6 个可点箭头（默认旋转角） | `src/controllers/world-configurator.ts:153-166, 385-424` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/controllers/world-configurator.ts#L153-L166 |
| winds schema（长度必须 6） | `src/components/options-schema.ts:99-102` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/components/options-schema.ts#L99-L102 |
| 经纬框计算（plate carrée） | `src/generators/coordinates.ts:70-84` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/coordinates.ts#L70-L84 |
| 网格尺寸 / 间距 / cellsX | `src/generators/grid-generator.ts:61-64, 102-110` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/grid-generator.ts#L102-L110 |
| 点数档位 1k..100k，默认 4=10k | `src/data/graph-density.ts:3-25` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/data/graph-density.ts#L3-L25 |
| 管线顺序（temp→prec→rivers→biomes） | `src/generators/generation-pipeline.ts:7-47` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/generation-pipeline.ts#L7-L47 |
| 每步计时开关 | `src/generators/pipeline.ts:16-36` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/pipeline.ts#L16-L36 |
| 生物群系：湿度/温度→biome | `src/generators/biomes-generator.ts:81-88, 113-148` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/biomes-generator.ts#L113-L148 |
| 文化：温度差罚函数 / 极端气候告警 | `src/generators/cultures-generator.ts:65-71, 1041-1084` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/cultures-generator.ts#L65-L71 |
| 湿度→mm、高度→m 换算 | `src/utils/unitUtils.ts:59-77` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/utils/unitUtils.ts#L59-L77 |
| .map 分节（8=prec, 11=temp） | `src/services/io/save.ts:138-151` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/services/io/save.ts#L138-L151 |
| JSON 导出含 temp/prec | `src/services/io/export-json.ts:201-224` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/services/io/export-json.ts#L201-L224 |
| GeoJSON cells 属性（**无 temp/prec**） | `src/services/io/export.ts:703-710` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/services/io/export.ts#L703-L710 |
| 侵蚀是坡度驱动、**与风无关** | `src/renderers/erosion-bake.ts:553-563, 628-633` | https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/renderers/erosion-bake.ts#L553-L563 |

---

## 9. 逐条回答你点名的 6 个问题

**Q1 温度怎么算 / 有没有海陆洋流 / 有没有"纬向平均+距平"**
→ §3.1。`f(lat, h)` 纯函数；纬向三段落线性（0.15 / 0.7378 / 0.5571 °C/°lat）+ 海拔 `((h-18)^2/1000)*6.5` °C；**没有**海陆差异、**没有**洋流/海温、**没有**季节（官方 FAQ：年是年均值）、**没有**距平结构。输出 Int8Array 整数 °C。

**Q2 降水是否做上风递减 / 衰减系数 / 风输入是什么**
→ §3.2。**是**，而且是全文件的核心。衰减 = 每格 `max(humidity/(10*modifier), 1)`，即乘性 `e^{-1/(10*modifier)}`（e 折 9.49 格 @默认）。另有迎风爬坡项 `Δh·(h_next/70)^2`、水格 `+5*modifier` 再补给、海岸 `humidity/rand(10,20)`、`h>85` 硬雨影、`temp<-5` 跳过。风输入 = **用户手调的 6 项角度规则表**（不是解出来的），且只用来选 ±x/±y 方向与"哪条边进入"。

**Q3 风怎么生成 / 是不是纬向分带表 / 有没有让东岸一致向赤道**
→ §3.3。**是纯纬向分带**：`tier=(|lat-89|/30)|0` 取 `options.map.climate.winds[tier]`，默认 `[225,45,225,315,135,315]`（北极东风/北半球西风/东北信风/东南信风/南半球西风/南极东风）。**完全没有**东/西岸区分；同一纬度上所有经度的风完全相同，所以"东边界沿岸风"这个量在 FMG 里不存在，也就不可能"39~52% 正确"——它是另一个设计取舍。另外它连向量场都不是：角度只进 4 个**重叠**范围（100~140 同时算 westerly+northerly，220~280 同时算 easterly+southerly），输运永远轴向。

**Q4 洋流 / current / ocean / gulf stream**
→ **未找到**。grep `current|gyre|thermohaline|upwelling|Ekman|SST|sea surface|gulf stream`：0 个气候/海洋学命中。`ocean-generator.ts` 只是**画海岸线外圈等深环**（把 `cells.t` 为 -1..-9 的水域描成闭合多边形给渲染器用，https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/ocean-generator.ts#L1-L12），不含任何流场。所以：**FMG 没有洋流，也没有海温**——你已有的"Munk 风生环流"在 FMG 里没有任何对应物可对照。

**Q5 biomes / cultures 里对气候的用法**
→ 生物群系（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/biomes-generator.ts#L106-L148）：`moisture = rn(4 + mean(prec[自己] + mean(prec[陆地邻居])))`，有河再加 `max(flux/10, 2)`；然后 `getId()` 的规则序：`h<20→0(marine)`；`T<-5→11(permafrost)`；`T>=25 && !river && moisture<8→1(热沙漠)`；湿地判据（`moisture>40 && h<25` 或 `moisture>24 && 24<h<60`，且 `T>-2`）→12；否则查 `biomesMatrix[moistureBand][temperatureBand]`，其中 `moistureBand=min((moisture/5)|0, 4)`、`temperatureBand=min(max(20-T,0),25)`（5×26 矩阵，https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/biomes-generator.ts#L81-L88）。
文化（https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/cultures-generator.ts#L58-L71）：把温度当"气候相似度罚项" `td(cell, goal) = |temp - goal| + 1`，每个文化有一个 goal 温度（示例：10/12/15/16/5/6/18/17/11/14/13/19/26/22/24/25 °C，见 L81-275 各行 `td(i, X)`），与生物群系罚 `bd()`、海岸罚 `sf()` 一起做选址排序；人口太少时弹 "The climate is harsh and people cannot live in this world" 告警（L1041-1084）。

**Q6 是否浏览器内一次性算完 / 网格规模**
→ **是**，主线程一次性跑完整条 `GenerationPipeline`（无 Web Worker：全仓库只有 Service Worker 缓存，`production-generator.ts:835` 的 "worker" 是经济人口概念）。点数 1000~100000（默认 10000），布局是"抖动方格点 + Voronoi"，`cellsX/cellsY` 由 `spacing=sqrt(W*H/points)` 决定；默认 1280×800 → spacing 10.12 px → 126×79。投影是 plate carrée（x/y 每像素度数相同，`lonT = min((W/H)*latT, 360)`，https://github.com/Azgaar/Fantasy-Map-Generator/blob/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/coordinates.ts#L70-L84），**没有 cos(lat) 修正**——如果你要按公里做瓦片，这一点要自己处理。

**附加问：GIS/heightmap 流程里有没有"诊断风"成分？**
→ **没有**。`heightmap-generator.ts` 只有 blob/hill/pit/strait/smooth/mask/invert/图像导入这些工具（`type Tool = "Hill"|"Pit"|"Range"|"Trough"|"Strait"|"Mask"|"Invert"|"Add"|"Multiply"|"Smooth"`，L12），全文件 grep `wind` 只命中 `window.ERROR` 之类无关词。3D 侵蚀（`erosion-bake.ts`）是**坡度/梯度驱动**的（`erosionKernel(vec2 p, vec2 dir)`，L563-580；flow direction 由 `baseGradient` 给，另加 FBM 扰动防平行沟，L628-633），**没有风蚀、没有沙丘、没有 fetch**。整个代码库里唯一的"风可视化"就是 `draw-precipitation.ts` 的 4 个进入箭头 + 世界配置器地球仪上的 6 个可旋转箭头，两者都是**规则表的显示**，不是诊断量。

---

## 10. 结论：3~8 条对你卡点直接有用 / 明确无用

1. **【明确无用｜最重要】FMG 对"东边界沿岸风符号"零参考价值。** 它的风是 `tier=(|lat-89|/30)|0` 索引的 6 项手调角度表，同纬度所有经度完全相同，既没有东西岸概念，也没有气压/Coriolis/Ekman/海温/海陆热力差。你的 39~52% 只能靠**你自己的判据**修（例如把沿岸切向分量显式约束为"副热带东岸 ⇒ 向赤道"的确定性符号函数），FMG 帮不上。
2. **【有用】风向只做"选边/选轴"，不做场——这是满足"x 无限"约束的可行骨架。** `getWinds()` 是 `(lat, cellsX)` → 4 组带 + 无随机的**纯函数**，同一份被生成器和渲染器共用。建议你把沿岸风符号也做成同形态纯函数，而不是从二维场里读符号（后者就是你 39~52% 的来源：符号由噪声/插值决定）。
3. **【有用】水汽输运做成"每格乘性衰减 + 地形项"的一维扫描即可**：`h -= max(h/(10m),1)`、`+Δh·(h_next/70)²`、水格 `+5m`、`h_next>85` 全倒空。这套常数可以直接当你的 baseline 对照，且完全无迭代、无收敛判据。
4. **【有用，且是必须改的地方】它的衰减是"每格"而非"每公里"，因此不尺度不变。** `modifier=(N/10000)^0.25` 只补偿了一半：10000 点 e 折 ≈ 96 px，100000 点 ≈ 54 px。你的无限瓦片世界必须写成 `humidity *= exp(-ds/L_moist)`（`L_moist` 取公里量级），否则换分辨率/换瓦片尺寸结果就变。
5. **【有用】性能标定：你比它宽 3~4 个数量级。** FMG 用 ~10⁴ 格算完整世界（默认 1 格 ≈ 30.4 km，整图 ≈ 3840×2400 km），气候部分是 1N + 4N 的 O(N) 扫描；你 100 km 瓦片 + 20 s 的预算不需要任何低精度妥协（别学它 Uint8 量化、100 mm 一格、整数 °C）。
6. **【有用】风角度表可直接抄成你的初始参数表**：30° 一档、`[225,45,225,315,135,315]`（tier0..5 = 59~89N / 29~59N / 1S~29N / 31S~1S / 61S~31S / 91S~61S），角 = 风吹向，0=E、90=S、180=W、270=N；注意它的 4 个象限判定是**重叠**的（100~140 同时 westerly+northerly），要抄就改成互斥区间。
7. **【明确无用/别抄】** ① 轴向输运（只能 ±x/±y，斜向风被拆成两套独立行/列积分）——你若要斜向沿岸水汽通道必须废弃；② `temp<-5 ⇒ continue` 让水汽原样穿透（L56），语义是 bug 级的；③ `if (!source[0]) continue`（L47）"第 0 行整带跳过"的 legacy 行为；④ 温度与降水的耦合只有这一个 `temp<-5` 门限，没有任何 SST/海冰反馈。
8. **【验证方式】** 所有结论都能在这个 SHA 上复现：`git clone --filter=blob:none --depth 1`，或直接读 https://raw.githubusercontent.com/Azgaar/Fantasy-Map-Generator/a7289d3e21bcd0ab3dc73d87b29c9ad8c32a2f94/src/generators/precipitation-generator.ts（129 行，全部逻辑在 `generate()` 与 `getWinds()` 两个方法里）。
