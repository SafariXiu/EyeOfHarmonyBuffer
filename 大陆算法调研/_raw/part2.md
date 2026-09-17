## 2. 逐条详表

---

### 条目 1 · Minecraft 1.18+ 多噪声生物群系源（multi-noise biome source）+ 密度函数样条

**1. 名称 + 链接**
- 官方说明：https://minecraft.wiki/w/Density_function （HTTP 200，已抓取 raw wikitext 复核）
- 原版数据导出（可直接读样条折点）：https://github.com/misode/mcmeta （data 分支）
- 原版 offset 样条原文：https://raw.githubusercontent.com/misode/mcmeta/data/data/minecraft/worldgen/density_function/overworld/offset.json （HTTP 200）
- 第三方 C 复刻及其参数表：https://deepwiki.com/xpple/cubiomes/3.4-biome-noise-system （HTTP 200）
- 参数可视化编辑工具：https://misode.github.io/worldgen/ （HTTP 200）

**2. 一句话原理**
先用 6 张**极低频**的 Perlin 噪声图（temperature / humidity / continentalness / erosion / depth / weirdness）描述每个点的"气候与地理坐标"，再把 continentalness（大陆度）通过一条**单调三次样条**直接映射成"相对海平面的地形偏移"，样条上人为压出的两段水平段就是大陆台地与深海盆地这两个峰。

**3. 类别**
多噪声 + 样条重映射（multi-noise + spline），实时程序化。

**4. 输出范围**
无限程序化。X/Z 均无界（1.18+ 世界边界仍是 ±29,999,999，但算法本身无界）。

**5. 实时性**
运行时 O(1) 求值。每点需采样 6 张噪声图；每张图 4~9 个倍频；再走几次样条查表（折点用二分或线性扫描，折点数 < 20）。**无任何全局状态、无缓存依赖**（原版有 `minecraft:cache` 包装，但那是同一次求值内的局部缓存，不是全局地图缓存）。

**6. 是否产生地球式双峰高程分布：是，而且是本文档中唯一"写在数据结构里"的。**
从 offset.json 抄下的 continentalness 样条折点（x = continentalness 值，y = 地形偏移，单位见下）：

    位置 location     值 value
    -1.10            0.044        （最深处略微回升，模拟海沟底）
    -1.02           -0.2222       ← 深海盆地平台起点
    -0.51           -0.2222       ← 深海盆地平台终点（水平段！）
    -0.44           -0.12
    -0.18           -0.12         ← 浅海/大陆架平台（水平段！）
    -0.16           → 交给第二级样条（erosion × ridges_folded）决定

注意：最外层还有一层 lerp(blend_alpha, blend_offset, …)，那是新版为了兼容旧世界地形混合而加的包装；剥掉后主线就是 `add(-0.50375, spline(continents))`。

**这两个水平段就是双峰分布的全部秘密**：continentalness 在 [-1.02, -0.51] 区间内怎么变，地形都钉死在 -0.2222（深海盆地）；在 [-0.44, -0.18] 区间内钉死在 -0.12（大陆架）。

**7. 大陆架 / 大陆坡 / 岛弧 / 海沟 / 洋中脊**
- **大陆架**：有。-0.44 ~ -0.18 的水平段即大陆架/浅海。
- **大陆坡**：有。-0.51 → -0.44 这一段样条斜率很陡（Δ0.07 的值变化对应 0.1022 的高度差），天然就是大陆坡。
- **海沟**：勉强有。-1.10 处值回升到 0.044（比深海盆地高），形成"海沟底抬升"。这是 MC 用来避免世界底部被挖穿的技巧，不是真正的俯冲带。
- **岛弧**：没有。MC 的岛是"大陆度刚好在海岸线附近"的残留，不是弧状。
- **洋中脊**：没有。

**8. 是否处理内海与孤岛：不处理。** 这是本项目要特别注意的：MC 1.18 **明确接受**大量内陆湖和散落小岛。continentalness 是一条 1D 单调曲线，它没有任何"连通性"概念——一片内陆低洼只要噪声值掉到 -0.19 以下就会自动变成海。这正是本项目"内海数 = 0"指标与 MC 范本的分歧点。

**9. 语言 / 依赖 / 许可证 / star**
- 语言：Java（原版）；cubiomes 是 C。
- 依赖：无（原版内建）。
- 许可证：原版 JSON 数据属 Mojang 资产，**不可直接抄进你的 mod**。可以抄的是**结构与数值思路**，参数需自己重标定。
- star：mcmeta 是数据镜像仓库（star 数不重要）；cubiomes 见条目 7。

**10. 对本项目的可借鉴点（逐条、可写代码）**

1. **抄结构不抄数值。** 你自己造一张 continentalness 噪声 C(x,z) ∈ [-1,1]，然后写一条样条 S(C) → "相对海平面的高度（格）"。折点用你自己标定的两段水平段：
   - C < -0.55：S = -4000 格（深海盆地，对应 -4 km）
   - -0.55 ~ -0.20：线性上升（大陆坡，约 -4000 → -200 格）
   - -0.20 ~ -0.12：S = -200 格（大陆架平台，对应 -0.2 km）
   - -0.12 ~ 0：线性上升（海岸）
   - C > 0：S = +300 格 + 山脉项（大陆台地，对应 +0.3 km）
   这样**双峰是样条保证的，不是统计出来的**。
2. **两级嵌套样条。** MC 的 offset 样条在 C = -0.16 处以"第二个坐标（erosion）"再展开一层样条。你可以照搬：第一级用大陆度定"海陆大格局"，第二级用侵蚀度/山脊度定"山脉振幅"。这让山脉只出现在大陆内部，不会长在海里。
3. **参数换算：1.0 密度单位 ≈ 128 格。** 依据：depth = gradient(y, from -64 → 1.5, to 320 → -1.5)，即 384 格对应 3.0 单位，1.0 单位 = 128 格。所以你写样条时可以直接用"格"为单位，最后除以 128。
4. **用 quarter_negative / half_negative 压扁负值。** 原版 `sloped_cheese = quarter_negative((depth + jaggedness*half_negative(jaggedNoise)) * factor) * 4 + base_3d_noise`。语义：`quarter_negative(x) = x<0 ? x/4 : x`。作用：让水下地形比水上地形平缓 4 倍——**这是让海底像海底、山地像山地的关键一步**，仅一行代码。
5. **振幅调制器（amplitude_modifiers）不要用等比衰减。** continentalness 的振幅序列是 `[1, 1, 2, 2, 2, 1, 1, 1, 1]`（base_octave -9，9 个倍频）。中频被**放大 2 倍**，最低频和高频都不强。效果是"大陆既有大尺度、又有中尺度起伏，但没有高频噪点"。对比：erosion 是 `[1, 1, 0, 1, 1]`（直接把中间一个倍频清零），ridge 是 `[1, 2, 1, 0, 0, 0]`（只保留 3 个倍频）。
6. **大陆特征尺度换算。** 原版 xz_scale = 0.25，最低倍频 -9 → 最低频波长 = 1 / (2^-9 × 0.25) = **2048 格**；最强振幅落在倍频 -7 ~ -5，即波长 **512 ~ 128 格**。你要做"少数几块互联大陆"，把最低频波长放大到 **8000 ~ 20000 格**，振幅峰值放在 2000 ~ 5000 格。
7. **DoublePerlinNoise 去轴对齐伪影。** cubiomes 复刻文档明确写到：每个气候参数用"两个频率略有差异的倍频噪声叠加平均"。这能显著削弱噪声的方形格点感。Java 里就是 `0.5*(fbm(x)+fbm(x*1.02+17.3))` 这类写法，代价是 2 倍采样。
8. **块级精度的正解：cell_height 插值。** 原版 `preliminary_surface_level` 用 `find_top_surface` 配 `cell_height = 8`：3D 密度只在 4×8×4 的格点上算，中间三线性插值。你的 16×16 块若发现最高倍频波长 < 32 格产生块内阶梯，就照这个做——**在 16×16 块的 5×5 采样网格上算，双线性插值**，成本降 10 倍。
9. **6 张图里你只需要 3 张。** temperature/humidity 是给生物群系用的，与地形无关。本项目要的是 continentalness（海陆）、erosion（起伏度）、ridges/peaks&valleys（山脉）。depth 不是独立噪声，是 `gradient(y) + offset`。
10. **反面教材也要记：** MC 的样条折点是**手工调出来的**，不是生成的。你必须准备一套"改折点 → 重算陆地占比 / 分形维数"的脚本，否则标定会被手工试错拖死。见附录 C。

---

### 条目 2 · Vanilla 内建域弯曲（shift_a / shift_b）与山脊折叠（ridges_folded）

**1. 名称 + 链接**
- 官方说明（逐字抄录）：https://minecraft.wiki/w/Density_function?action=raw ，其中 `shift_a` 条目原文："Samples a noise at (x/4, 0, z/4), then multiplies it by 4."；`shift_b` 条目原文："Samples a noise at (z/4, x/4, 0), then multiplies it by 4."
- 参数来源：https://deepwiki.com/xpple/cubiomes/3.4-biome-noise-system （NP_SHIFT 振幅 `[1, 1, 1, 0]`，倍频 -3 到 1）
- 折叠公式原文：https://raw.githubusercontent.com/misode/mcmeta/data/data/minecraft/worldgen/density_function/overworld/ridges_folded.json

**2. 一句话原理**
在采样噪声**之前**先把坐标本身推开一小段距离（域弯曲），"a 用 (x/4,z/4)、b 用 (z/4,x/4)"这种交叉写法保证两个轴的位移互不相同；再把山脊噪声取绝对值再折一次，把平滑噪声变成尖山脊。

**3. 类别**
域弯曲（domain warp）+ ridged 折叠，实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)。代价：每点多 2 次噪声采样（shift_a/shift_b 各一次）。

**6. 双峰高程：否。** 这是**修饰层**，只影响海岸线与山脊的"毛边"，不改变海陆大格局。这一点非常重要：**别指望用域弯曲造大陆。**

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
- 大陆架/大陆坡：不产生，只把已有的架/坡边缘揉皱，使其不像"用圆规画的"。
- 山脊：`ridges_folded = -3 * (-0.33333334 + abs(abs(ridges) - 0.6666667))`，这是标准的 ridged 变换，产生尖锐山脊线。
- 岛弧/海沟/洋中脊：无。

**8. 内海与孤岛：不处理，且会轻微加剧。** 域弯曲会把细小的陆地轮廓揉碎，可能凭空造出小岛。这是本项目要警惕的副作用。

**9. 语言 / 依赖 / 许可证 / star**
同条目 1（原版数据，Mojang 资产）。

**10. 对本项目的可借鉴点**

1. **域弯曲幅度要小，且必须低频。** 原版幅度 = 4（噪声值 ∈ 约 [-1,1]，乘 4 得 ±4 格），采样坐标先除以 4（即只用 1/4 频率）。**关键洞察：位移量的尺度必须远小于它要修饰的地形特征尺度**，否则会把大陆揉碎。你要修饰 2000 格的大陆边缘，位移量给到 **50 ~ 200 格**即可，且位移场的波长要 ≥ 1000 格。
2. **X/Z 交叉写法值得抄。** shift_a 用 (x/4, 0, z/4)，shift_b 用 (z/4, x/4, 0)。交叉（swap）保证两个轴的位移场是**统计独立**的，否则 x 和 z 会被推向相关方向，产生斜向拉丝。
3. **Larion 式各向异性域弯曲正好适配你的 Z 轴约束。** 因为 Z 是纬度、X 是经度，你**只应该形变 X**，Z 保持不动（见条目 4）。这天然避免了"纬度被扭曲"的物理违和感。
4. **域弯曲会破坏 Z 轴周期性。** 若你对 Z 做弯曲，位移场本身必须是 Z 周期 20,000,000 的，否则接缝处会断裂。**最省事的做法：只弯曲 X，Z 一点都不碰**——这是本项目的最优解，白送。
5. **ridged 折叠公式直接抄。** `folded = -3 * (abs(abs(n) - 2/3) - 1/3)`，其中 n 是噪声。这个式子比常见的 `1 - abs(n)` 更"厚"，山脊两侧有平台感，更像山。
6. **折叠后必须再乘一个"山脉掩码"。** 原版把 ridges_folded 作为第二级样条的输入，只有 continentalness 落在内陆区时才放大它。你若直接全图叠加 ridged 噪声，海里会长出山。
7. **性能账：全流程 3 次噪声 + 2 次域弯曲 = 5 次 fbm 调用**（每 fbm 5~9 倍频）。在 16×16 块、5×5 采样网格上，每块约 25 × 5 × 7 ≈ 875 次单倍频噪声求值。纯 Java 单线程约 0.1 ~ 0.3 ms/块，与"接近纯噪声"的目标一致。

---

### 条目 3 · TerraForged

**1. 名称 + 链接**
- https://github.com/TerraForged/TerraForged （已抓取；default_branch = 0.3.x）
- 高度层级映射源码：https://raw.githubusercontent.com/TerraForged/TerraForged/0.3.x/src/main/java/com/terraforged/mod/worldgen/terrain/TerrainLevels.java （HTTP 200）
- 噪声层级映射源码：https://raw.githubusercontent.com/TerraForged/TerraForged/0.3.x/src/main/java/com/terraforged/mod/worldgen/noise/NoiseLevels.java （HTTP 200）

**2. 一句话原理**
不走"噪声值 → 高度"的一条样条，而是把世界高度切成三个**显式命名的高度层级**（sea_floor 海底 / sea_level 海平面 / base_height 基准陆高），噪声只在层内插值；大陆、海岸、侵蚀、河流各是一张独立的低频噪声图，最后按层混合。

**3. 类别**
多噪声 + 分层高度映射（layered height mapping），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)。仓库中确实存在 `TerrainCache.java` 与 `TerrainBlender.java`，说明它对**同一区块的重复查询**做了局部缓存（不是全局地图缓存），属可接受的工程优化。

**6. 双峰高程：是，而且是显式的参数化双峰。** 从 TerrainLevels.java 抄下的默认值：
- `MIN_Y = -64`，`MAX_Y = 480`
- `SEA_LEVEL = 62`
- `SEA_FLOOR = SEA_LEVEL - 40 = 22`
- `MAX_BASE_HEIGHT = 128`
再配合 NoiseLevels.java 的映射（原文抄录）：

    depthMin   = seaFloor / worldHeight
    heightMin  = seaLevel / worldHeight
    baseRange  = baseHeight / worldHeight
    heightRange= 1 - (heightMin + baseRange)
    depthRange = heightMin - depthMin

    toDepthNoise(n)          = depthMin + n * depthRange
    toHeightNoise(base, h)   = heightMin + baseRange*base + heightRange*h

**注意 baseNoise 与 heightNoise 是两个独立噪声**：前者是"基准陆高"（决定这块大陆整体多高），后者是"局部起伏"。这正是双峰的实现方式——海底用 depthRange 映射，陆地用 heightRange 映射，两段互不干扰。

**7. 大陆架 / 大陆坡 / 岛弧 / 海沟 / 洋中脊**
- 大陆架 / 大陆坡：**有**。sea_floor 与 sea_level 之间 40 格的深度带就是浅海/大陆架，其下是盆地。
- 岛弧 / 海沟 / 洋中脊：无（不是板块模型）。
- 河流：有独立模块，且会在峡谷中切割。

**8. 内海与孤岛：部分处理。** TerraForged 以"大陆细胞（continent cell）"的方式生成大陆，倾向于产生成片陆地而不是撒芝麻。但它的配置项里有"岛屿/内陆湖"相关的开关，说明是**可调**而非**保证为 0**。

**9. 语言 / 依赖 / 许可证 / star**
- 语言：Java（Minecraft 模组，Fabric/Forge）
- 许可证：**MIT**（LICENSE 原文："MIT License / Copyright (c) 2020 TerraForged"，已抓取验证）
- star：**345**（本次通过 GitHub API 读取 `stargazers_count`）
- 依赖：Minecraft、Guava、Mojang DataFixerUpper（`com.mojang.serialization`）

**10. 对本项目的可借鉴点**

1. **"显式命名的高度层级"比"一条样条"更好标定。** 你把 SEA_FLOOR / SEA_LEVEL / BASE_HEIGHT / MAX_Y 做成 4 个 config 常量，陆地占比就变成"调 BASE_HEIGHT 与海岸线阈值"这件事，不用反推样条形状。**强烈建议本项目采用这个结构。**
2. **两个独立噪声：baseNoise 与 heightNoise。** 这是本项目"大陆台地平坦 + 山脉突起"的最简实现：
   - `base = sampledBaseNoise(x,z)` → 决定这块地方是海底还是陆地台地
   - `h = sampledHeightNoise(x,z)` → 只在陆地上叠加山脉
   - `height = heightMin + baseRange*base + heightRange*h`
   比起"一个噪声 + 一条样条"，这样调参解耦度高得多。
3. **海底与陆地用两套映射公式，不共用。** `toDepthNoise` 与 `toHeightNoise` 是两个函数。你照搬这个思路：水下用一条曲线（深海盆地平台 + 大陆坡），水上用另一条（台地 + 山脉）。这就是双峰最干净的写法。
4. **`auto / scale` 自动频率标定。** NoiseLevels 里有 `calcFrequency(verticalRange, auto, scale)`，auto 模式下频率 = (LEGACY_GEN_DEPTH - SEA_LEVEL) / verticalRange × scale。意义：**当你改世界高度时，水平噪声频率会自动跟着改**，保持地形"长宽比"不变。你的世界高度若可配，必须抄这个，否则世界一高地形就变面条。
5. **unit / floor / ceil 的量化技巧。** `unit = 1/worldHeight`，`floor(v) = NoiseUtil.floor(v/unit)*unit`。作用是**把连续噪声吸附到整格高度**，避免同一格被重复算出不同高度导致接缝。块级生成时这能显著减少 z-fighting 与重复计算。
6. **`getWaterLevel` 的内海处理思路。** 原文：
   
       return (terrain.isRiver() || terrain.isLake()) && river == 0f
              ? terrainData.getBaseHeight(x, z)   // 河湖：水面 = 该点地面高度
              : seaLevel;                          // 其他：水面 = 海平面
   
   即**湖与河的水面高度是"跟地走"的**，不是固定海平面。这直接消灭了"内陆出现低于海平面的死水"这类内海伪影。**本项目要达到"内海数 = 0"，这行是最实用的借鉴。**
7. **MIT 许可证。** 相比原版 Mojang 资产，TerraForged 的 MIT 意味着**代码结构可以合法参考移植到你的 1.7.10 mod**（注意 1.7.10 与 0.3.x 的 API 完全不同，你抄的是数学与结构，不是 API 调用）。
8. **反面提醒：** TerraForged 的目标是"多样而美丽"，本项目目标是"少数几块连通大陆 + 内海 0"。它的默认参数会产生比你要的更多、更碎的陆地。**抄它的结构，别抄它的默认值。**

---

### 条目 4 · Larion（只形变水平轴的域弯曲）

**1. 名称 + 链接**
- https://github.com/ViciousBadger/larion-world-generation （README 已抓取）
- 配套"单一大陆"数据包：https://modrinth.com/datapack/larion-one-continent （Larion README 中逐字列出）

**2. 一句话原理**
在 MC 密度函数框架内，用一套**特殊的 domain wrap 技术只形变水平轴**，替代原版那种"用 3D 噪声制造悬崖"的笨办法，从而在不引入 3D 噪声的前提下得到干净的垂直悬崖。

**3. 类别**
域弯曲（各向异性 / 只形变水平轴），实时程序化。

**4. 输出范围**
无限程序化。README 明确写"Oceans are actual infinite oceans with landmasses of varying size"。

**5. 实时性**
运行时 O(1)，但 README 明确警告"Larion tends to slow it down a lot due to the added complexity"，并推荐 C2ME / Noisium / Faster Random / FerriteCore 等优化模组。**结论：结构可抄，成本要有心理准备。**

**6. 双峰高程：否（继承 MC 的样条，自己不重做）。** Larion 改的是**地形形状**（悬崖、侵蚀布局），海陆大格局仍由 MC 的 continentalness 决定。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
继承 MC：大陆架有、岛弧无、海沟无、洋中脊无。它额外贡献的是**悬崖**（cliffs）与**山中的深河谷**。

**8. 内海与孤岛：README 明确承认处理得不好。** 原文："Some seeds will spawn you in water or on a tiny island in the middle of nowhere."，并建议用 World Preview 预览。**这是本项目必须避免的反面案例。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：Java（NeoForge/Fabric 模组 + 数据包），核心是 MC 的 density function JSON
- 许可证：**Apache-2.0**（README 原文："Larion is licensed under Apache 2.0, meaning you are free to modify and use the pack as you wish. You can freely use any individual parts in your own mod or datapack."）——**这是本文档中对本项目最友好的许可证之一，明确允许你抽取任意部分放进自己的 mod。**
- star：未验证（未读取）

**10. 对本项目的可借鉴点**

1. **"只形变水平轴"是本项目 Z 轴周期约束的正解。** Larion 的做法证明：不做 3D 域弯曲，只在 (x,z) 平面内弯曲，就能得到好看的悬崖。你的 Z 是纬度——**只弯曲 X，Z 完全不动**，这样 Z 的周期性自动保持，一行额外代码都不用加。
2. **用域弯曲替代 3D 噪声做悬崖。** 原版用 3D 噪声（`base_3d_noise` / `jagged`）造悬崖，代价高且有"奶酪洞"感。你可以：先算 2D 高度场 h(x,z)，再用 h 本身或一张低频噪声去弯曲采样坐标 → 得到垂直崖壁。**成本从 3D 噪声降到 2D。**
3. **Apache-2.0 允许直接抄实现。** 建议直接读它的 density_function JSON（数据包形式，纯 JSON 可读），把形变公式提取出来。这是本文档里**唯一一个许可证明确允许商用+改造+抽取**的 MC 生态实现。
4. **垂直分层 + 水平形变 = 便宜的"地质感"。** README 提到 Larion 里"Steep hills will have exposed stone surfaces… Somewhat steep hills get a nice gradient of stone and normal surface"。做法：用**高度场的梯度**（而不是额外的 3D 噪声）决定表层石/土。你的块级生成里 h(x,z) 的有限差分梯度几乎是免费的。
5. **README 的兼容性警告是一条硬信息：** "if mods or datapacks modify Minecraft's density functions… there will be compatibility issues"。说明整个 MC 生态的地形都是**同一组密度函数**在打架。你自建独立噪声管线（不挂在 MC 的 density function 上）反而更干净——本项目是 1.7.10 独立 mod，本来就不受这个限制。
6. **反面教训（内海与孤岛）：** Larion 用一个连续的 continentalness 决定海陆，就必然出现"出生点在海里/孤岛"。**你要做到内海 = 0，必须在 continentalness 之外再加一层"连通性约束"**（见附录 A）。

---

### 条目 5 · Eldor（alkexr 数据包）——早期密度函数样条大陆

**1. 名称 + 链接**
- https://www.planetminecraft.com/data-pack/eldor/
  （**注意**：该链接由 Larion README 逐字提供，但本次直接抓取返回 **HTTP 403**，是 PlanetMinecraft 的反爬拦截，**不代表链接失效**。）

**2. 一句话原理**
在 MC 1.18 密度函数体系里，用大量手工编写的样条与噪声组合实现"史诗地理"，是第一批证明"数据包可以完全重写地形形状"的作品。

**3. 类别**
多噪声 + 样条（MC density function 数据包），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)（纯密度函数，无预计算）。

**6. 双峰高程：是**（继承并扩展 MC 的 continentalness 样条思路）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
未验证到细节文本。据 Larion README 的自述，Eldor 的"clever density functions"是 Larion 的重要参考，Larion 具备深河谷与悬崖，故 Eldor 至少具备大陆架与山地。

**8. 内海与孤岛：未验证。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：MC 数据包（JSON）
- 许可证：**未验证**（PlanetMinecraft 页面 403）
- star：不适用

**10. 对本项目的可借鉴点**

1. **它证明了"不用写 Java 就能重写地形"**——但本项目是 1.7.10，没有密度函数系统，所以**不能直接搬**。可借鉴度评为"中"。
2. **可借鉴的是"样条折点可以手写得很夸张"这一工程事实。** Eldor/Larion 这类作品的样条折点都是人肉调出来的，说明这条路可行，但也说明**你必须有自动化标定工具**（附录 C），否则会被手工试错拖死。
3. **若你能找到它的 JSON（PlanetMinecraft 下载包内），里面的折点数值可作参考**，但注意许可证未知，**不要直接复制数值**。
4. 其余字段因页面被拦截而未能验证，**标注为不确定**，不进一步引申。

---

### 条目 6 · Tectonic（Apollo 数据包）

**1. 名称 + 链接**
- https://modrinth.com/datapack/tectonic （HTTP 200，已抓取）
- 同作者相关作品（Larion README 列出）：https://modrinth.com/datapack/deeper-oceans （HTTP 200）

**2. 一句话原理**
用 MC 的密度函数系统大幅拉高山脉、加深海洋，把原版偏"温和"的地形改成"巨大山脉 + 深洋盆"的高对比度地形。

**3. 类别**
多噪声 + 样条（MC density function 数据包），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)。

**6. 双峰高程：是。** 名称即"构造"，其设计目标正是拉开陆地与海底的高差，强化双峰。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
- 大陆架 / 大陆坡：有（配合 Deeper Oceans）。
- **海沟 / 洋中脊：名称暗示但没有验证到明确声明**，标注为不确定。

**8. 内海与孤岛：未验证。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：MC 数据包（JSON）
- 许可证：**未验证**（Modrinth 页面未抓取到许可证段落）
- star / 下载量：未验证

**10. 对本项目的可借鉴点**

1. **"加深海洋"是达到双峰分布最直接的一招。** 原版 MC 深海只有约 30 格，远达不到 -4 km 的盆地感。你的项目若把海平面设 Y=64，深海盆地应下探到 Y = 64 - 4000/…（按 1.7.10 的 256 高度限制，实际给 -0.15 ~ -0.25 的世界高度比例）。**注意 1.7.10 的世界高度上限是 256，这是硬约束**——你无法真的做 -4 km 到 +0.3 km 的 4.3 km 高差，必须做**垂向压缩**（例如 1:20），并把"大陆架/大陆坡/深海盆地"的**比例关系**保留而不是绝对高度。
2. **许可证未验证 → 只做思路参考，不要复制 JSON。**
3. **它的存在说明"数据包能做的事"有边界：** 深洋盆可以做，但**岛弧与海沟需要板块边界信息**，纯噪声样条做不到。这正是本项目要引入条目 29（Cellular 板块化）的理由。
4. 其余字段未验证，不引申。

---

### 条目 7 · cubiomes —— MC 噪声的 C 语言权威复刻

**1. 名称 + 链接**
- https://github.com/Cubitect/cubiomes （README 已抓取，HTTP 200）
- 噪声系统逐行解析：https://deepwiki.com/xpple/cubiomes/3.4-biome-noise-system （HTTP 200）
- 图形前端：https://github.com/Cubitect/cubiomes-viewer （README 中逐字列出）

**2. 一句话原理**
用 C 从零复刻 Minecraft Java 版的生物群系与地物生成，把六个气候参数的全部噪声配置（base_octave、octave_count、amplitude_modifiers）硬编码为表，做到与官方逐位一致。

**3. 类别**
多噪声 + 样条（工业级参考实现），实时程序化。

**4. 输出范围**
无限程序化（可查询任意坐标，包括超出世界边界）。

**5. 实时性**
运行时 O(1)，且以"最小内存占用"为设计目标（README 原文："intended as a powerful tool to devise very fast, custom seed-finding applications and large-scale map viewers with minimal memory usage"）。**这是本文档中与本项目工程目标最一致的实现。**

**6. 双峰高程：是**（完整复刻 MC 的 spline 深度计算）。cubiomes 文档原文："The depth parameter (NP_DEPTH) is not directly sampled from noise but calculated using a complex spline system… The spline system uses recursive evaluation where each spline point can reference other splines, creating a hierarchical calculation."

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
同 MC：大陆架有，其余无。

**8. 内海与孤岛：不处理**（同 MC）。

**9. 语言 / 依赖 / 许可证 / star**
- 语言：C（无外部依赖，README 给出 `gcc find_biome_at.c libcubiomes.a -fwrapv -lm`）
- 许可证：**MIT**（LICENSE 原文："MIT License / Copyright (c) 2020 Cubitect"，已抓取验证）
- star：未验证（未读取）
- **注意 `-fwrapv` 编译选项**：说明其哈希/整数运算依赖二进制补码回绕语义。**Java 的 int 溢出正好是回绕语义**，所以移植到 Java 天然对齐——这是一个隐藏的加分项。

**10. 对本项目的可借鉴点**

1. **这是你验证"Java 噪声实现是否与参考一致"的黄金标准。** 你写完自己的 Perlin/Octave 实现后，可以用 cubiomes 生成一组参考值做逐点比对——**cubiomes 是 MIT，可以合法地作为测试基准**。
2. **抄它的参数表结构。** 文档给出的六参数配置（已抓取原文）：
   
       NP_TEMPERATURE      [1.5, 0, 1, 0, 0, 0]       倍频 -10..-4
       NP_HUMIDITY         [1, 1, 0, 0, 0, 0]          倍频  -8..-2
       NP_CONTINENTALNESS  [1, 1, 2, 2, 2, 1, 1, 1, 1]  倍频  -9..0
       NP_EROSION          [1, 1, 0, 1, 1]              倍频  -9..-4
       NP_WEIRDNESS        [1, 2, 1, 0, 0, 0]           倍频  -7..-1
       NP_SHIFT            [1, 1, 1, 0]                 倍频  -3..1
   
   **NP_SHIFT 就是条目 2 的域弯曲位移场**，只有 4 个倍频、且第 4 个振幅为 0（等于只有 3 个有效倍频）。这告诉你：**位移场要非常低频、非常平滑**。
3. **"深度不是独立噪声"这一条直接抄。** NP_DEPTH 是由 continentalness/erosion/ridges/weirdness 四个参数递归样条算出来的。你的实现里**不要**为高度单独采样一张噪声——那会破坏双峰。
4. **DoublePerlinNoise 是内建的抗伪影手段。** 文档原文："DoublePerlinNoise… samples two octave noise instances with slightly different frequencies to create more complex patterns."
5. **气候参数用"硬编码 MD5 哈希"做确定性分离**（文档原文："hardcoded MD5 hashes for deterministic parameter separation"）。你不用 MD5，但**"每个参数用不同的种子派生方式，而不是同一个种子 + 偏移"** 这个原则要抄——否则参数之间会有残余相关性。
6. **`getBiomeAt(&g, scale, x, y, z)` 支持 scale=4 的"生物群系坐标"**。本项目是块级 16×16，等价于 scale=16。**建议照它的做法把"采样坐标"与"块坐标"显式分离**，避免频率单位混乱。
7. **它明确区分 `MC_1_18` 等多个版本常量**（`setupGenerator(&g, MC_1_18, 0)`）。你做地形版本演进时也该这样：**把参数集命名成版本常量**，而不是散落在代码里。

---

### 条目 8 · misode/mcmeta —— 原版 worldgen 数据的机器可读镜像

**1. 名称 + 链接**
- https://github.com/misode/mcmeta （data 分支；本次通过 GitHub API 验证了分支与目录结构）
- 例：https://raw.githubusercontent.com/misode/mcmeta/data/data/minecraft/worldgen/density_function/overworld/offset.json （HTTP 200）
- 例：https://raw.githubusercontent.com/misode/mcmeta/data/data/minecraft/worldgen/noise/continentalness.json （HTTP 200）
- 可视化编辑器：https://misode.github.io/worldgen/ （HTTP 200）

**2. 一句话原理**
把原版每一个版本的全部 worldgen JSON（噪声配置、密度函数、生物群系参数）从 JAR 里导出成带版本分支的 Git 仓库，使样条折点这样的"黑盒数值"变成可 diff、可 grep 的文本。

**3. 类别**
工程基础设施（不是算法）。

**4. 输出范围**
不适用（数据镜像）。

**5. 实时性**
不适用。它的价值在于**离线阅读**。

**6. 双峰高程**
不适用；但它**是双峰样条数值的唯一权威来源**（本文档条目 1 的全部折点均抄自此处）。

**7. / 8.**
不适用。

**9. 语言 / 依赖 / 许可证 / star**
- 内容为 Mojang 资产（数据），仓库本身是导出脚本。**不可把你抄到的数值直接发布。**
- star：未验证

**10. 对本项目的可借鉴点**

1. **把它当"参考手册"而不是"代码源"。** 具体查法（已验证可用）：
   - 大陆度样条：`.../density_function/overworld/offset.json`
   - 侵蚀度因子：`.../density_function/overworld/factor.json`
   - 锯齿度：`.../density_function/overworld/jaggedness.json`
   - 折叠山脊：`.../density_function/overworld/ridges_folded.json`
   - 最终密度合成：`.../density_function/overworld/sloped_cheese.json`
   - 噪声参数：`.../worldgen/noise/continentalness.json`、`erosion.json`、`ridge.json`
2. **"把参数外置成 JSON/配置"这个工程决策要抄。** 你的样条折点、倍频振幅表都应放在资源文件里，而不是硬编码——否则调参时每小时都要重编译一次客户端。
3. **用 `misode.github.io/worldgen` 可视化你的样条。** 在把折点写进 Java 之前，先在图形界面里看曲线形状：**你要的两段水平段在图上应该一眼可见**，如果看不到，双峰就不成立。
4. **版本 diff 的价值：** mcmeta 有 `diff` 分支。当你调参调到"哪里变了"说不清时，diff 是好工具。本项目自建的参数文件也应该进 git 并保持单一职责。

---

