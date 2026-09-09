# Talos 地形层 · 排查报告 + 接入工作表（X1 阶段2）

> 记录时间：接「完成大气与洋流 + 代码清理」之后。本表是 X1 阶段2（生产接入）的活工作表，
> 完成一项勾一项；参数定稿回写 design.md（D26 状态同步更新）。

---

## 一、排查结论：现在是「双轨并行」

### 轨 A —— 生产管线（游戏中真实生成的 Talos2 世界，dim 14001）

```
ChunkProviderTalos2 (extends ChunkProviderSpaceLakes)        ← WorldProviderTalos2 挂载
 └─ TalosChunkContext.create(每 chunk 16×16 列, 只采样一次)
     ├─ land[]        ← TalosLandMask → WorldgenAPI.samplePointTiled → TectonicWorld (旧板块/超大陆)
     ├─ macroPkg[]    ← TalosMacroClimate.getMacroPackageId   (旧宏气候: 站点哈希×纬度带)
     ├─ biomes[]      ← TalosMacroClimate.getBiomeChunk        (宏包→16 个 BiomeGenTalos2*)
     ├─ baseHeight[]  ← TalosBaseTerrain → TerrainEngine → TerrainMacroPresetRegistry(按宏包)
     ├─ hydro[]       ← TalosRiverSystem (RVR2 模板河网, 骨架=超大陆)
     ├─ mountain*[]   ← TalosMountainSystem (DLA 山, 预构建消费 WorldgenAPI 海陆)
     └─ bankIntensity ← 宏包河岸强度
 └─ 高度链 TalosTerrainHeights.sampleColumn: 基础→海岸→裂谷→山脉→河岸→河谷下切
 └─ 方块铺设 / 水场授权(TalosWaterField) / 表层(TalosSurfaceRegistry) / 洞穴(CaveCarver+Decorator)
WorldChunkManagerTalos2 (游戏内群系查询) → TalosMacroClimate.getBiome（与 ctx.biomes 同源）
BiomeDecoratorTalos2 / TalosBoundedFeatures → 按群系对象查配置
```

### 轨 B —— V2 原型（本次大重构的产物，全部完成但 0 接入生产）

| 模块 | 位置 | 状态 |
|---|---|---|
| L1 新海陆 | continent_layer/NoiseContinentGrid（residual/coastDistBlocks/自适应阈值 D27） | ✅ 原型完成 |
| L1b 地形骨架 | continent_layer/OrographyField（elevation01/relief01/beltMask01/kind 双轨 D28） | ✅ 原型完成 |
| L0 耦合环流 | circulation_layer/ M1 ThermalForcing + M5 RelaxedClimate（按种子离线求解~5s/种子）+ M6 查表 | ✅ 完成 |
| 统一门面 | circulation_layer/GlobalClimate.sample → ClimateSample（含 P1b 降水算子） | ✅ 完成（D24） |
| 生产外接入点 | CommandTalosMap（/talosmap 出图）、handler/ClimatePreheat（WorldEvent.Load 预求解, dim 14001 ✓） | ✅ 已接线 |
| 文档 | design.md（D1–D29）、climate-layer-internals.md | ✅ |

### 核心差距（D26）

**V2 新场没有产生任何方块**：生产侧海陆采样仍在 WorldgenAPI/TectonicWorld，群系仍在
TalosMacroClimate，高度仍在「按宏包 preset」——它们依赖的 superId / supercontinent / macroPkg
概念在 V2 设计里不存在（新世界观 = 海陆残差场 + Orography kind/elevation + GlobalClimate 气候场）。

因此 X1 阶段2 不是「换一个采样函数」，而是**把整条生产列的数据源世界观换掉**。

### 换源爆炸半径（哪些文件在消费旧概念）

| 旧概念 | 生产消费方（换源时需逐一对齐/禁用/改接） |
|---|---|
| TalosLandMask / WorldgenAPI（plateId/superId/landWeight/coastWeight/shelfWeight…） | TalosChunkContext、ChunkProviderTalos2、TerrainEngine、TalosBaseTerrain、TalosTerrainHeights、TalosWaterField、TalosMountainSystem(+MountainWorldState/Prebuild)、TalosCaveSystem、river_layer(CoastClipper/SupercontinentAdapter/RiverRegistry…)、climate_layer 全部、BiomeRegionLayer、~12 个 /talos* 调试命令 |
| TalosMacroClimate / MacroPackageId | WorldChunkManagerTalos2、TalosChunkContext、TerrainEngine、TalosTerrainHeights、CaveGenerator、river_layer、BiomeDecoratorTalos2、TalosBoundedFeatures、CommandTalosBiome |
| TerrainMacroPresetRegistry(按宏包取基高预设) | TerrainEngine、CaveGenerator(runtime) |
| RVR2 模板河网（超大陆骨架） | TalosRiverSystem/HydroSample、ChunkProvider 河床铺料、水场、装饰 |
| DLA 山（MountainWorldState 预构建） | TalosMountainSystem、TalosTerrainHeights 抬升链、BiomeOverrideProvider |

### 关键观察

1. 装饰/表层（BiomeDecoratorTalos2、TalosSurfaceRegistry、TalosBoundedFeatures、CaveDecorator）
   绝大多数**只按 biome 对象查配置** → 群系对象复用 16 个 BiomeGenTalos2* 即可，不需要重写装饰。
2. 洞穴（CaveGenerator）里有一处 TerrainMacroPresetRegistry.get(pkg)（约 L473）→ 换源后 pkg 无定义，
   需排查该处用途（推测为基岩/洞厅材质或高度门），待实现期核对（见 T3.2）。
3. ClimatePreheat 的维度 (14001) 与 Talos2 生产维度一致 ✓，接入后预热直接生效，无需再动。
4. 水场 TalosWaterField 已做「陆地默认无水 + 显式授权」的激进规则（D17 精神），输入仍带 coastWeight/
   hydro/macroId → 换源后只需喂新字段，规则主体可复用。
5. 高度链 TalosTerrainHeights.sampleColumn 结构（基础→海岸→山脉→河岸→河谷）本身可复用，
   但「基础高度」来源必须从「宏包 preset」改为「L1/L1b 场直接派生」，山脉抬升输入从 DLA 改为 orography。

---

## 二、接入工作表（顺序 = 建议先手顺序）

> 原则：每步都能开新档/出图对照、可回退；旧轨代码在完全退役前只加开关不分叉改。

| # | 任务 | 关键动作 | 产出/验证 | 依赖 | 状态 |
|---|---|---|---|---|---|
| T0.1 | 基线参数核对 | ✅ 生产 seaLevel=64 / worldHeight=256；探针数据（4 种子分布）已入 D30 | 参数表在 design.md D30 | — | ✅ |
| T0.2 | V2 生产开关 | ✅ V2TerrainConfigSection.terrainV2Enabled（默认关，cfg 分类 talos.v2）；Provider/WCM/装饰器顶部分轨 | gradle 编译绿；旧轨未触碰 | — | ✅ |
| T1.1 | 列采样 | ✅ 每列一次 OrographyField.sample（已含 coastDist，OroSample 增字段）≈210ns/点 → 256 列 ≈ 60µs/chunk；ClimateSample 留给 T2 群系再取 | 成本量级 OK | T0.2 | ✅ |
| T1.2 | 高度映射定稿 | ✅ V2TerrainGen（D30）：陆高/海深/沙滩带/雪线；medNoise 2→4 级+二次扭曲去闭合环 | 全图+放大渲染验证（run/talos_maps/v2_terrain_*） | T1.1 | ✅ |
| T1.3 | 方块铺设分支 | ✅ ChunkProviderTalos2.generateTerrainV2：陆地 profile/沙滩/雪表层 + 岩性变体；海床浅沙→砂砾→岩石 + 水面=64 | gradle 编译绿；等游戏内开档目检 | T1.2 | ✅ |
| T1.4 | 群系占位 | ✅ V2BiomePicker：海→SHELF/OCEAN（coastDist<2400），陆→kind→MOUNTAINS/PLATEAU/PLAINS；WCM 与装饰器同源切换 | 装饰随地形自洽（树只长在陆生群系） | T1.3 | ✅ |
| T2.1 | 群系 v1 选择器 | V2BiomeSelect: ClimateSample(温度/干湿/海陆)×kind×coastDist → 映射到现有 16 群系对象（海洋/陆架按水深细分） | 群系随气候场分布（雨林=湿热带、沙漠=副高带…） | T1.x | ⬜ |
| T2.2 | 群系双端同源 | WorldChunkManagerTalos2 与 ctx.biomes 用同一函数（防装饰错位） | 装饰/表层自动跟随（无需改装饰代码） | T2.1 | ⬜ |
| T2.3 | 游戏内验证 | 新档飞行抽查：树/草/雪线/沙丘与气候场一致 | 截图+记录偏差 | T2.2 | ⬜ |
| T3.1 | 水场 v1 | 海洋恒 seaLevel；陆地默认无水；近海浅水带按 coastDist；湖/河暂缺（无 hydro 数据源） | 海岸观感无伪影 | T1.x | ⬜ |
| T3.2 | 洞穴门控核对 | CaveGenerator 中 macroPkg 用途排查；换源 isLand/kind；CaveWorldState 与预构建钩子改喂新场 | 洞穴不崩、密度合理 | T1.1 | ⬜ |
| T3.3 | DLA 山去留决策 | 拍板：新轨山 = Orography 直接抬升（DLA 仅旧轨） vs 移植 DLA 挂新场。**大决策，需用户定** | 决策记录 D31 | T1.x | ⬜ |
| T3.4 | 河网占位 | 新轨 hydro=无（RVR2 模板以超大陆为骨架，新大陆无定义）；河床/湖塑形暂缺 | 无河但有明确 TODO（阶段4 WatershedBuilder） | T1.x | ⬜ |
| T4.1 | 首区块性能 | 预热已配；核对首次 chunk 触发 per-seed 标定成本（≈5000 点噪声，预计可接受） | 新档无卡顿 | T1.x | ⬜ |
| T4.2 | 对照与收口 | /talosmap 增 v2 新层(height/kind/biome)；旧轨对照图(landlegacy 先例)；design.md D26 标记阶段2 完成 | 对照图归档 | T2.x | ⬜ |
| T4.3 | 旧轨退役 | TectonicWorld/macroClimate 保留为对照；确认稳定后讨论归档（沿 climate-v2-legacy 先例） | 无死代码遗留 | T4.2 | ⬜ |

---

## 三、后续大块（X1 阶段3–4 / S7，不在本表范围）

| 块 | 内容 | 前置 |
|---|---|---|
| L5 全面重做 | 海洋群系（暖/寒洋流）、按气候向量选型、站点骨架复用 | T2.x 群系 v1 稳定 |
| 山脉细节 | 山带组织/雪线/山体材质 | T3.3 决策 |
| WatershedBuilder | 新大陆上的水系（X1 阶段4）：流域并查集→主干逆流→支流→宽渐变→湖泊 | T1 高度场稳定 + 阶段4 排期 |
| L4 地貌 | 盆地/盐沼/峡谷（basinMask01 落地） | 水系后 |
| 海洋装饰 | 海床地形/珊瑚/浮冰群系 | L5 |

## 四、待用户拍板的决策点

- **入口选择**：按表 T0.2→T1→T2 顺序（推荐，先出「能走的新海陆」）还是其它切法？
- **T0.2 开关形态**：Config 布尔分轨（推荐，可对照回退）还是直接改轨（git 回退兜底）？
- **T3.3 DLA 山**：新轨由 Orography 直接出山形（DLA 退役）还是移植保留？
---

## 追加轮（2026-09 · 用户拍板：类型/高度分家 + 噪声复用 + DLA 挂 V2）

> 游戏内实测反馈：V2 地形过平（λ80k 慢场直接当高度 → 几百~几千格才 1 格台阶）、DLA 山脉完全缺失。
> 新架构：L1/L1b 只出类型；块高 = 类型档案 × 三层噪声（复用 TerrainBaseHeight/TerrainNoise）；山 = DLA 重挂 V2 tier 场。
> 已拍板：elevation01 保留（类型判据+气候慢场）；噪声机械直接复用；DLA 挂 V2 取代 D31 原案。

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| T1.2r | 高度职责拆分 | ✅ | V2TerrainGen 重写：elevation01 退出块级定高；五档档案权重混叠 + 单次三层分解（design.md D32） |
| T1.5 | 走廊高海拔门 | ✅ | 走廊(relief/belt)覆盖陆地 5~6 成，无门则低走廊也成大雪山；m=smoothstep(0.22,0.60,elev01) 门槛后低走廊并入丘陵档 |
| T3.3 | DLA 挂 V2 | ✅（待游戏目检） | 唯一缝 = TalosTectonicStyles.tierAt V2 分支（包络 env≥0.40 ∩ kind/belt/elev 分层，D31v2）；洪水/DLA 生长/查询机械零改动；生命周期对任意 Talos2 世界触发 |
| T1.2v | 出图验证 | ✅ | run/talos_maps/v2_terrain_noise_full|zoom_s12345.png：中纬 6000 格窗起伏可见、无伪影；全图雪区 10-15% |
| T2.x | 群系 v1 气候映射 | ⬜ | 下一轮：climate × kind → 16 群系；群系档案高度带并入本高度链 |
| T3.2 | 洞穴/装饰接缝 | ⬜ | CaveGenerator 中 macroPkg 用途核对；DLA 山带群系覆盖(ALPINE)可选接入 |
| T4.x | 游戏目检与调参 | ⬜ | 调参旋钮：档案带限/三层幅度、雪线 172→116、包络阈值 0.40、tier 门限、DLA debugSummary |

- **T3.3 决策已更新**：DLA 挂 V2（上表），原 Orography 直接出山作废。
## 追加轮 2（2026-09 · L1c 峰核场 SummitField，S1~S4 完成）

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| S1 | 峰核场（512 格网格 + 峰势局部极大 + CHM 缓存） | ✅ | `chunk/world/SummitField.java`；开关 talos.v2.summitEnabled |
| S2 | 高度链接入（绝对目标收敛 + DLA 双重封顶） | ✅ | `h = min(h,T) + f(T−min(h,T))`；DLA peak ≤ min(peakForKind, base+55, T) |
| S3 | 群系挂接 + 指令展示 | ✅ | Alpine = 峰核影响≥0.55 且 T≥雪线；/talos_here 显示峰核、/talosmap terrain 同口径 |
| S4 | 验收 | ✅ | 438 峰核 violations=0（中心恒为 ±512 邻域最高点）；出图 run/talos_maps/v2_summit_* |
| — | 待游戏内目检 | ⬜ | 重点看：峰形坡度、雪线边界、DLA 山脊与峰核的衔接；调参旋钮见 summit-field-design.md §12 |
## 追加轮 3（2026-09 · V2 山层接入，替换 DLA）

| # | 任务 | 状态 | 备注 |
|---|---|---|---|
| 1 | 山层算法选型 | ✅ | 噪声/骨架类均否决；改用过程驱动（抬升场 + 迭代侵蚀 + 权威权重），参考 INRIA 解析侵蚀线 |
| 2 | 探针迭代（A/B/C → E1/E2 → mtn7/8/9/10/11） | ✅ | run/talos_maps/mtn*.png；最终形态：单条长山带（164k）超出窗口、主脊突出、高度适中 |
| 3 | 主工程落库 `MountainLayerV2` | ✅ | 400k×200k 环面、400m 网格、5 条山带、150 轮侵蚀、按种子缓存 3.1s |
| 4 | 权威仲裁合成 | ✅ | h = plain + (1−w)·mtnComp + w·uplift + 细节 + 软封顶；剖面验证无台阶 |
| 5 | 群系接线 | ✅ | Alpine = 合成高度 ≥ 雪线 且 mountainish；Mountains 同判据；SummitField 删除 |
| 6 | 指令/预热 | ✅ | /talos_here 显示 plain/base/mtnComp/w/uplift/合成高度；/talosmap terrain 同口径；ClimatePreheat 预求解 |
| 7 | 游戏内验收 | ⬜ | 重点看：山带分布/主脊形态、过渡带（9k）、块级细节是否毛糙（可能要加输出平滑）、雪线/Alpine |
