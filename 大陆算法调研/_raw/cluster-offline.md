# 大陆/地形生成算法调研 —— 学术·离线·仿真派（cluster-offline）

> 调研范围：**学术论文 / 离线仿真 / 可离线预计算的算法**。实时噪声类（Perlin/simplex/GPU noise）由另一 agent 覆盖，本文不重复。
> 所有 DOI 均通过 **Crossref API 实际返回** 校验（返回值中的 title/venue/year 见各条目）；仓库信息通过 **raw.githubusercontent.com 的 README/LICENSE/package.json** 与 **shields.io 徽章** 实际抓取校验。

## 链接可信度图例（严格遵守"不臆造链接"规则）

| 标记 | 含义 |
|---|---|
| ✅DOI | 由 api.crossref.org/works/<doi> 或 Crossref 检索接口实际返回该 DOI + 标题，确认已注册 |
| ✅页面 | 实际 fetch 成功（HTTP 200）并读到内容 |
| ⚠️未打开 | URL 逐字出现在实际抓取到的页面/API 元数据中，但本会话未成功打开（如 Cloudflare 拦截） |
| ❌未验证 | 未找到可信来源，**不要使用** |

**未找到 / 无法验证的条目（重要）**：
- **"Kellner, Interactive Hydraulic Erosion"**：Crossref 作者检索（query.author=Kellner + hydraulic erosion/terrain）未返回任何相关文献；网络检索也未命中。**该条目判定为"链接未验证/疑似不存在"**，请勿引用。替代方案见 §6（Mei et al. 2007、Nguyen et al. 2007、Beneš & Forßbach 2001、Schott et al. 2023 四篇均已验证）。
- **任务书第 2 项"Cordonnier et al. *Terrain Amplification using Multi-scale Erosion*, SIGGRAPH 2016"**：核查后**该题名/年份/作者组合不存在**。实际存在的是 **Schott, Galin, Guérin, Paris, Peytavie, *Terrain Amplification using Multi Scale Erosion*, ACM TOG 2024, DOI 10.1145/3658200** ✅DOI。Cordonnier 2016 年的两篇是 §1（CGF/Eurographics）与 §18。请以本文修正版为准。
- **urn.fi/URN:NBN:fi:amk-201204023993**（platec 原始学士论文）：URL 逐字来自 plate-tectonics README，但 https://urn.fi 被 Cloudflare 403 拦截 ⚠️未打开。
- **Songs of the Eons 世界生成器源码**：官方 FOSS 仓库（Calandiel/SongsOfFOSS）README 明确写 *"notably not including the original world generator"* ✅页面 —— 即**大陆生成算法无公开源码可验证**，只能引用官方 itch 页面的功能描述。

---

# 一、核心论文（任务书要求项）

## 1. Cordonnier et al. 2016 — Large Scale Terrain Generation from Tectonic Uplift and Fluvial Erosion

- **名称**：Large Scale Terrain Generation from Tectonic Uplift and Fluvial Erosion（Eurographics 2016 / Computer Graphics Forum 35(2), 165–175）
- **验证链接**：https://doi.org/10.1111/cgf.12820 ✅DOI（Crossref 返回：Cordonnier, Braun, Cani, Benes, Galin, Peytavie, Guérin；CGF 2016）。DOI 字符串同时逐字出现在 Purdue CGVLab 论文页 meta 描述中 ✅页面（https://www.cs.purdue.edu/cgvlab/www/publications/cordonnier2016large/）
- **论文 PDF（⚠️未打开，链接来自 Semantic Scholar API 元数据）**：https://hal.inria.fr/hal-01262376/file/2016_cordonnier.pdf
- **一句话原理**：在大网格上迭代求解**河道下切能力定律（stream power law）** dh/dt = U − K·A^m·S^n 的抬升–侵蚀平衡态，从而得到具有真实分水岭层级、河谷密度与山前冲积形态的大尺度地形。
- **类别**：物理仿真 / 地貌学数值解（离线预计算）
- **有限图 vs 无限过程化**：**有限网格图**（论文用大体量规则网格，示例量级 10^3–10^4 边长），非无限拼接
- **实时 O(1) vs 离线仿真**：**离线仿真**。需成百上千次时间步迭代；单点查询 O(1) 不可得（查询要重放/插值结果栅格）
- **是否双峰（大陆高原 +0.3km / 深海盆地 −4km）**：**否**。只生成陆地抬升区（山脉高原），无洋壳模型、无海平面分层
- **大陆架/陆坡/岛弧/海沟/洋中脊**：**全无**（无洋壳与俯冲概念）
- **内海与孤岛**：可自然出现**内流盆地/封闭洼地**（洼地填充后残留的封闭区域），但论文不专门处理内海；"孤岛"不作为目标，仅可能由排水异常产生
- **语言/依赖/许可/star**：论文本身无官方仓库；HAL 存有 PDF（见上）。引用时按论文引用，无代码许可问题
- **可借鉴点（具体到可编码）**：
  1. **抬升场 U 用过程化噪声构造**：fBm + ridged 分量叠加，并把"抬升速率"而非"最终高程"作为艺术家输入参数（消除手工刷高程的不可控性）。
  2. **排水面积 A 的快速估计**：单流向 D8 + 拓扑排序（按高程降序）累积面积，O(N) 一次遍历即可；大地图按 tile 分块 + halo 交换即可并行。
  3. **洼地填充（depression filling）**：用 priority-flood（最小堆，从边界向内）得到无洼地 DEM，后续流向计算才不会断链；论文用它避免"内流死点"。
  4. **显式/半隐式时间步**：用隐式格式对 K·A^m·S^n 线性化，允许大 dt，几十~几百步即可接近稳态（比显式稳定得多）。
  5. **分水岭级联**：河网天然产生 Strahler 级数与 Hack 定律（河长 ∝ A^0.6），这是"看起来像地球"的关键统计特征，可用作验收指标。
  6. **抬升/侵蚀解耦**：先用低分辨率算大尺度水系，再在高分辨率上只做细节放大（正是后续 §2、§17 的发展方向）。
  7. **参数含义明确**：m≈0.5、n≈1（河流凹度 θ=m/n≈0.45–0.5 对应真实河流纵剖面），可直接照抄做默认值。

## 2. Schott et al. 2024 — Terrain Amplification using Multi Scale Erosion（**任务书第 2 项的正确文献**）

- **名称**：Terrain Amplification using Multi Scale Erosion（ACM Transactions on Graphics, 2024）
- **验证链接**：https://doi.org/10.1145/3658200 ✅DOI（Crossref：Schott, Galin, Guérin, Paris, Peytavie；TOG 2024）
- **一句话原理**：把**低分辨率输入地形**做多尺度放大——在多个尺度上分别执行 thermal erosion、stream power erosion 与沉积的快速近似，把粗尺度的侵蚀地标（山脊线、河谷、冲积扇）作为约束传给细尺度合成，从而兼顾物理一致性与可控细节。
- **类别**：物理仿真 × 多尺度过程化（混合）
- **有限图 vs 无限过程化**：有限图幅放大（输入是有限低分图）
- **实时 vs 离线**：**近交互/离线**（论文强调快速近似，可用于编辑循环），非严格 O(1) 单点求值
- **双峰**：否（放大山地/河谷细节，不产生洋盆）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：无
- **内海与孤岛**：继承输入图的特征；不专门处理
- **语言/依赖/许可/star**：论文；HAL 开放版 https://hal.science/hal-04565030 （⚠️未打开，来自 Semantic Scholar openAccessPdf 字段）
- **可借鉴点**：
  1. **"侵蚀地标传递"思路**：粗尺度算出河流中心线/谷底高程 → 作为细尺度合成的硬约束（可理解为"侵蚀结果当作 guide map"）。
  2. **多尺度分解**：把高程拆成若干倍频带，每个带单独决定用哪类侵蚀（大尺度=流幂、中尺度=热力坡面、小尺度=噪声细节）。
  3. **热力侵蚀（thermal erosion）近似**：仅当坡度超过**休止角（talus angle）**时把多余物质分配给最低邻居——实现十几行、可分块并行，是廉价但视觉收益极大的一步。
  4. **沉积与冲积扇**：在坡度骤减处（谷口）做沉积，避免"河流凭空消失在坡上"的穿帮。
  5. **放大时保持水文一致性**：细尺度细节的振幅随"到河道距离"衰减，保证不阻塞水系。
  6. **可控参数**：把"放大强度/细节尺度/河道保持度"暴露为独立滑杆，适合工具化。

## 3. Génevaux et al. 2013 — Terrain Generation Using Procedural Models Based on Hydrology

- **名称**：Terrain Generation Using Procedural Models Based on Hydrology（ACM TOG 32(4), SIGGRAPH 2013）
- **验证链接**：https://doi.org/10.1145/2461912.2461996 ✅DOI（Crossref：Génevaux, Galin, Guérin, Peytavie, Benes）。DOI 亦逐字出现在 Purdue CGVLab 页面 meta ✅页面
- **一句话原理**：**先有河，后有山**——用户/程序给出**河流网络图（river graph）**与参数，算法由河流的**凹形纵剖面**与"到河距离"反推高程场，使得地形水文自洽（河谷一定落在河道上、分水岭自动形成）。
- **类别**：过程化建模 + 水文学规则（离线，秒级）
- **有限图 vs 无限过程化**：有限图（可平铺但需保证河网跨界连续，论文未解决无限拼接）
- **实时 vs 离线**：**离线过程化**（秒级），且高程可由参数函数求值 → 近 O(1)，但依赖全局河网图，不是纯局部函数
- **双峰**：否（生成的是陆地地形，无洋盆/深海平原）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：无（但**河口/海岸线**处理得好，可作大陆架起点）
- **内海与孤岛**：支持**闭合流域/内流盆地**（河网图可含封闭子图）；孤岛可由独立小流域产生
- **可借鉴点**：
  1. **河网图即"地图骨架"**：把大陆形状/山系走向表达为有向无环图，比在高度图上刷更可控，且天然抗"河流爬坡"穿帮。
  2. **凹形纵剖面公式**：河流高程沿程 h(s) = h_mouth + (h_source − h_mouth)·(1 − s/L)^k（k≈0.5–1），一次求值即得自然河流剖面。
  3. **"到河道距离"驱动坡面**：h = h_river + f(dist)·slope，f 单调且饱和（如 1 − exp(−d/λ)），可直接编码。
  4. **汇流点处理**：支流汇入主干时按面积加权对齐高程，避免节点处出现台阶。
  5. **河口/三角洲**：在末端做低坡度扇形展开，是连接"陆地—大陆架"最自然的接口。
  6. **参数即语义**：河道宽度、凹度、山体高度、丘陵噪声幅度都是显式参数，便于做大陆级"风格预设"。

## 4. Paris et al. 2019 — Terrain Amplification with Implicit 3D Features

- **名称**：Terrain Amplification with Implicit 3D Features（ACM TOG 38(5), SIGGRAPH Asia 2019）
- **验证链接**：https://doi.org/10.1145/3342765 ✅DOI（Crossref：Paris, Galin, Peytavie, Guérin, Gain；TOG 2019）
- **一句话原理**：用**隐式曲面 + 构造树（construction tree, CSG）**表示三维地貌基元（拱、悬崖、洞穴、倒悬），基元由 Poisson 采样布点、形状文法生成，从而**突破 heightfield 限制**并支持内存优化的按需查询。
- **类别**：过程化建模（隐式几何）/ 离线生成 + 按需求值
- **有限图 vs 无限过程化**：有限场景，但**隐式表示可局部求值**（可在任意位置查询，近似"无限"扩展）
- **实时 vs 离线**：混合——离线构建构造树；查询/多边形化阶段近似 O(1)（依赖层次包围盒加速）
- **双峰**：否
- **大陆架/陆坡/岛弧/海沟/洋中脊**：无
- **内海与孤岛**：不适用（体积地形，无海平面概念）
- **可借鉴点**：
  1. **构造树（CSG）做地形**：max/min/smooth-min 布尔组合基元，比高度图更容易表达"刀劈状悬崖"。
  2. **smooth-min 混合系数**控制基元衔接的锐利度——一个标量即可从"圆丘"连续过渡到"绝壁"。
  3. **Poisson 采样布点**：让地貌基元既不聚团也不规则空洞（比纯随机/Jitter 更均匀）。
  4. **按需局部求值**：只在需要网格化/碰撞的区块展开隐式函数，天然支持超大世界。
  5. **以低分高度图为底 + 隐式特征为饰**：可直接叠在已有大陆地形之上做"山地戏剧化"。
  6. **形状文法组织特征链**：山脊→次脊→崖壁→碎石，层级化铺设，避免手工布点。

## 5. Musgrave, Kolb, Mace 1989 — The synthesis and rendering of eroded fractal terrains

- **名称**：The synthesis and rendering of eroded fractal terrains（SIGGRAPH '89）
- **验证链接**：https://doi.org/10.1145/74333.74337 ✅DOI（Crossref：Musgrave, Kolb, Mace；SIGGRAPH proceedings 1989）。会议录版本另见 10.1145/74334.74337 ✅DOI（ACM SIGGRAPH Computer Graphics）
- **一句话原理**：用 **fBm / multifractal** 合成初始地形，再叠加**简化的侵蚀（风化/沉积）过程**修改分形地形，使山体具有真实的坡面与水系痕迹，而非纯噪声的"云状"起伏。
- **类别**：过程化噪声 + 简化侵蚀后处理
- **有限图 vs 无限过程化**：**可分块无限过程化**（噪声可由种子在任意点 O(1) 求值），但侵蚀 pass 是有限窗口的
- **实时 vs 离线**：噪声部分**O(1)**；侵蚀部分为一次性离线 pass
- **双峰**：**否**（这一点对"地球式双峰海岸线"是关键缺口：纯 fBm/ridged 天然产生单峰、围绕均值分布的直方图）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：无（但可用于给已有大陆架"加皱"）
- **内海与孤岛**：由噪声自然产生**散落湖泊与孤岛**（海平面阈值切 noise 即可），但分布不符合地球统计（太多小碎岛）
- **可借鉴点**：
  1. **ridged multifractal**：r = (1 − |noise|)^2 反复叠加、每层乘以前一层结果（multiplicative），得到尖锐山脊；这是"山脉感"的行业标准配方。
  2. **异构地形（heterogeneous terrain）**：不同区域用不同分形参数甚至不同基元，避免全球同质感。
  3. **侵蚀作为"分形后处理"**：先噪声后侵蚀，比直接调噪声更容易得到"看起来被水刻过"的形态。
  4. **海平面阈值 + 直方图统计**：作者们强调用**高程直方图/统计量**评估"是否像地球"，这一验收方法可直接搬来检验双峰性。
  5. **多尺度混合权重**：低频决定大陆/海盆轮廓，高频只加细节，可用于人为制造"双向偏移"（见可借鉴点 6）。
  6. **人为制造双峰的廉价手段**：对噪声做**双峰重映射**（如 h' = sign(h)·|h|^γ 或双 gamma 混合），把单峰直方图掰成双峰——这是本报告推荐用于"地球式海陆分布"的兜底技巧。
- **延伸（Musgrave 的 ridged multifractal 体系，链接已校验）**：*Procedural fractal terrains*, in Texturing and Modeling (2003), DOI 10.1016/B978-155860848-1/50045-0 ✅DOI

## 6. GPU 水力侵蚀（任务书第 6 项：Mei et al. 2007 + Kellner 澄清）

### 6a. Mei, Decaudin, Hu 2007 — Fast Hydraulic Erosion Simulation and Visualization on GPU
- **验证链接**：https://doi.org/10.1109/pg.2007.15 ✅DOI（Crossref：Mei, Decaudin, Hu；15th Pacific Conference on Computer Graphics and Applications, PG'07）
- **一句话原理**：用**虚拟管道（virtual pipes）/浅水近似**在 GPU 上做水力侵蚀——每格保存水量、地高、悬移质与流速，每步依次执行"水流入/流出 → 侵蚀/沉积 → 半拉格朗日对流 → 蒸发"，从而在交互速率下迭代数千步。
- **类别**：物理仿真（水力侵蚀），GPU 加速
- **有限图 vs 无限过程化**：**有限网格**（可分块但需 halo 同步，边界难做到无缝）
- **实时 vs 离线**：**近实时**（GPU 上每秒数十~数百步），但**仍是一次全局迭代过程**，不是单点 O(1) 函数
- **双峰**：否
- **大陆架/陆坡/岛弧/海沟/洋中脊**：有**冲积扇/三角洲/河漫滩**等由侵蚀–沉积自然涌现的形态；无海洋地质构造
- **内海与孤岛**：可在低洼处自然形成**湖泊与堰塞**；孤岛由侵蚀切割产生
- **可借鉴点**：
  1. **虚拟管道通量公式**：flux = Δt·A·g·Δh / l，逐边算通量后按缩放因子限制不超过可用水量（保证质量守恒、无负水深）。
  2. **四字段纹理（height/water/sediment/velocity）**：用多渲染目标一次性更新，便于移植到 compute shader 或 CPU 分块。
  3. **侵蚀/沉积判据**：C = Kc·sin(tilt)·|v| 与 Cs，比较携带量 s 与容量 C 决定侵蚀或沉积——**简单、稳定、可调**。
  4. **半拉格朗日对流**保证大时间步稳定（避免 CFL 限制）。
  5. **蒸发项**防止水永远填不满洼地，同时留下盐湖/干湖床等特征。
  6. **用于"大陆架塑形"**：把海平面固定在 0，反复侵蚀会把海岸带自动磨出**宽缓浅水台地（大陆架）+ 陡坡**的剖面——这是无需地质模型就能得到 shelf/slope 的实用路径。
  7. **参数建议**：雨量、蒸发率、携沙容量、侵蚀/沉积系数四组即可覆盖大多数风格。
- **性能提示（论文主旨）**：把该仿真当作**离线预计算**用（跑几千步后冻结高程），再在运行时对结果做插值/细节噪声，可绕开实时性能问题。

### 6b. Nguyen, Sourin, Aswani 2007 — Physically based hydraulic erosion simulation on graphics processing unit
- **验证链接**：https://doi.org/10.1145/1321261.1321308 ✅DOI（Crossref：Nguyen, Sourin, Aswani；GRAPHITE 2007）
- **一句话原理**：同期的 GPU 物理水力侵蚀实现，重点在**把侵蚀仿真放进 GPU 着色器管线**以加速离线地形生成。
- **类别**：物理仿真 / GPU 加速
- **其余字段**：与 6a 基本一致（有限网格、离线/近实时、无双峰、无海洋构造）
- **可借鉴点**：1) 与 6a 交叉验证的管道模型实现细节；2) 更强调"侵蚀结果直接喂给渲染管线"的工程路径；3) 参数扫描（对同一初始地形跑多种参数）产出地形变体库。

### 6c. Beneš & Forßbach 2001 — Layered data representation for visual simulation of terrain erosion
- **验证链接**：https://doi.org/10.1109/sccg.2001.945341 ✅DOI（Crossref：Benes, Forsbach；Spring Conference on Computer Graphics, 2001）
- **一句话原理**：用**分层体数据（layered data）**表示地形，把侵蚀仿真产生的**沉积层序**直接存成地层，使侵蚀不改变拓扑、且可回溯/可渲染地层剖面。
- **类别**：体表示 + 侵蚀仿真
- **可借鉴点**：1) 用"层"而非"高度场"存地形，天然支持悬崖/洞穴且侵蚀时只需改层厚；2) 层序即地质历史，能做"切面/岩层"可视化；3) 侵蚀只需处理物质搬运，不用重建网格。

### 6d. （澄清）Kellner — Interactive Hydraulic Erosion
- **验证链接**：**❌未验证（未找到）**。Crossref 作者检索与网络检索均未命中该文献。
- **替代**：若需要"交互式水力侵蚀"，最接近且已校验的是 §20（Schott et al. 2023, 10.1145/3592787）。

## 7. WorldEngine（Mindwerks）— 板块 + 侵蚀 · Python

- **名称**：WorldEngine — a world generator
- **验证链接**：https://github.com/Mindwerks/worldengine ✅页面（HTTP 200，meta description 逐字："World generator using simulation of plates, rain shadow, erosion, etc."）
- **README ✅页面**：https://raw.githubusercontent.com/Mindwerks/worldengine/master/README.md（逐字："Worlds are generated using plate simulations, erosion, rain shadows, Holdridge life zones model and plenty of other phenomenons."；"The current stable version is 0.20.0"；"Python 3.9+"）
- **LICENSE ✅页面**：https://raw.githubusercontent.com/Mindwerks/worldengine/master/LICENSE.txt（**MIT License**，Copyright (c) 2013-2014 Federico Tomassetti and Bret Curtis；README 亦称 "available under the MIT License"）
- **文档 ✅页面**：https://worldengine.readthedocs.io/en/latest/
- **一句话原理**：**多物理量流水线式离线生成**——先用板块构造仿真（底层调用 plate-tectonics，§8）得到基础高度图，再依次跑侵蚀、雨影降水、Holdridge 生命带等阶段，最终输出高度/生物群系/降水等多张图。
- **类别**：模块化离线仿真管线（world generator）
- **有限图 vs 无限过程化**：**有限图幅**（-s/-n 指定尺寸与种子），非无限
- **实时 vs 离线**：**离线**（CLI 生成，秒~分钟级），非 O(1)
- **双峰（大陆高原/深海盆地）**：**部分具备**——由板块仿真产出海陆二分与海平面切分，但**不保证**地球式双峰直方图（⚠️此判定基于 README/接口描述与同类算法先验，本会话**未实际运行**验证）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：板块仿真会产出**被动/活动边缘、洋中脊型隆起**等大尺度特征（取决于底层库，⚠️未运行验证）；**大陆架**主要由侵蚀阶段的海岸带磨蚀间接形成
- **内海与孤岛**：有**降水/水文阶段**，支持内流区与湖泊；孤岛由板块切分自然产生
- **语言/依赖/许可/star**：Python（≥3.9）；依赖 plate-tectonics（PyPlatec）、numpy 等；**MIT**；★ **约 1.1k**（shields.io 徽章 https://img.shields.io/github/stars/Mindwerks/worldengine ✅页面）
- **可借鉴点**：
  1. **阶段化管线**：plate → erosion → precipitation(雨影) → biome，各阶段可单独替换/复跑，非常适合作为自研生成器的骨架。
  2. **雨影降水**：以风向对高度场做一次扫掠，迎风坡降水多、背风坡少 → 直接决定沙漠/雨林分布，成本极低。
  3. **Holdridge 生命带**：用（生物温度、降水、蒸散比）三元组查表得生物群系，比手写规则更自洽，可直接抄表。
  4. **CLI + 文件化世界（.world）**：生成结果序列化后可反复二次加工（如 ancient_map），工程上很值得学。
  5. **可复现种子**：-s 1 -n seed1 的模式表明"种子即世界 ID"，便于回归测试。
  6. **可拼装**：README 明确把它定位成"工具链中的一环"（你给草稿 → 它做仿真 → 再喂给别的工具），与"离线预计算 + 运行时查表"的架构完全一致。

## 8. Mindwerks/plate-tectonics（platec 分支）— C++/Python 板块仿真库

- **名称**：plate-tectonics（A fork of platec）
- **验证链接**：https://github.com/Mindwerks/plate-tectonics ✅页面（meta description 逐字："A fork of platec http://sourceforge.net/projects/platec/"）
- **README ✅页面**：https://raw.githubusercontent.com/Mindwerks/plate-tectonics/master/README.md
- **LICENSE ✅页面**：https://raw.githubusercontent.com/Mindwerks/plate-tectonics/master/LICENSE（**GNU LGPL v3**）
- **一句话原理**：在二维网格上放置若干板块并令其随时间移动/碰撞/俯冲，逐周期对重叠区域做**褶皱（folding）与侵蚀（erosion_period）**处理，最终导出高度图——即"板块运动 → 造山/俯冲 → 高度场"的最小可运行模型。
- **类别**：构造地质仿真（离线，迭代式）
- **有限图 vs 无限过程化**：**有限网格图**（width × height，示例 512×512 / 1000×800）
- **实时 vs 离线**：**离线迭代**（is_finished() 循环 step()，受 cycle_count 控制），非 O(1)
- **双峰**：**部分/未验证**——提供 sea_level 参数切分海陆，但库本身是"高度图 + 海平面"，**不显式区分大陆地壳与洋壳**，因此双峰性取决于参数与后处理（⚠️未运行验证）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：碰撞/俯冲会形成山系与边缘隆起（README 示例图 https://raw.githubusercontent.com/Mindwerks/plate-tectonics/master/screenshots/map_grayscale.png ），但**没有显式的洋中脊/海沟几何模型**
- **内海与孤岛**：板块切分天然产生**内海与孤岛**（随机板块布局的直接结果）
- **语言/依赖/许可/star**：**C++**（CMake 构建，libPlateTectonics.a）+ **Python 绑定（pip install PyPlatec）** + Haskell 绑定（hplatec）；示例需 libpng；**LGPL-3.0**；★ **约 95**（https://img.shields.io/github/stars/Mindwerks/plate-tectonics ✅页面）
- **原始出处**：platec，Metropolia 应用科技大学学士论文（作者 Lauri Viitanen）；论文 URL 逐字来自 README：http://urn.fi/URN:NBN:fi:amk-201204023993 ⚠️未打开（Cloudflare 403）
- **可借鉴点**：
  1. **10 个显式参数即完整调参面**：seed, width, height, sea_level, erosion_period, folding_ratio, aggr_overlap_abs, aggr_overlap_rel, cycle_count, num_plates（README 逐字给出）——直接可用作自研实现的参数表。
  2. **aggr_overlap_abs/rel 双阈值**：用绝对+相对双阈值控制板块"聚合/重叠"判定，避免小板块被吞并或大地图数值溢出，是很实用的工程技巧。
  3. **folding_ratio 控制造山强度**：碰撞带的褶皱比例，是最直接的"山脉高度"旋钮。
  4. **erosion_period 内嵌侵蚀**：仿真循环里直接每隔 N 周期做一次侵蚀，避免高度无限增长（相当于内置平滑/守恒项）。
  5. **sea_level 与仿真解耦**：先出高度图再切海平面，方便后期做"海平面上升/下降"的世界变体。
  6. **PyPlatec 极简 API**：create(...) → while not is_finished: step() → get_heightmap()，非常适合嵌进离线批处理生成几千个大陆做统计/训练集。
  7. **LGPL 注意**：动态链接可闭源，静态链接有传染性——若项目要闭源，建议仅参考算法重写而非直接链接。

## 9. Azgaar's Fantasy Map Generator

- **名称**：Azgaar's Fantasy Map Generator (FMG)
- **验证链接**：https://github.com/Azgaar/Fantasy-Map-Generator ✅页面（meta description 逐字："Web application generating interactive and highly customizable maps"）
- **LICENSE ✅页面**：https://raw.githubusercontent.com/Azgaar/Fantasy-Map-Generator/master/LICENSE（**MIT License**，Copyright 2017-2024 Max Haniyeu (Azgaar)；明确允许商业使用与衍生作品，包括生成的地图/截图/视频）
- **package.json ✅页面**：https://raw.githubusercontent.com/Azgaar/Fantasy-Map-Generator/master/package.json（"license": "MIT"，version 1.153.1，engines node>=24；**dependencies 含 d3 ^7.9.0 与 delaunator ^5.0.1** → 证实其**Voronoi/Delaunay 单元**核心）
- **文档 ✅页面**：https://raw.githubusercontent.com/wiki/Azgaar/Fantasy-Map-Generator/Home.md 、https://raw.githubusercontent.com/wiki/Azgaar/Fantasy-Map-Generator/Heightmap-customization.md
- **一句话原理**：把地图离散为 **Voronoi 单元（delaunator）**，高度来自**高度图模板（noise/blob 组合）+ 手工笔刷编辑**，随后在单元图上做河流下切、降水/风、生物群系、文化/国家等派生模拟。
- **类别**：过程化 + 交互编辑（web 工具），**不是**物理板块仿真器
- **有限图 vs 无限过程化**：**有限图幅**（可 wrap 成球面/环面，见 wiki 的 Wrap Tool）
- **实时 vs 离线**：**交互式**（浏览器内秒级重算），但仍是全局重算而非单点 O(1)
- **双峰**：**否**——高度由模板/笔刷决定，无海陆双峰约束（海平面只是一条切分线）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：**基本没有**；但**海岸线/大陆架可视化**（Ocean height + contours/hachures）与**洋流/深海渲染**做得不错
- **内海与孤岛**：**很好**——有湖泊、内海、群岛的自然表现，且有 Coastline Editor / River Editor 手工微调
- **语言/依赖/许可/star**：JavaScript/TypeScript（Vite + Electron 打包）；d3、delaunator、three、alea（可复现随机）；**MIT**；★ **约 6k**（https://img.shields.io/github/stars/Azgaar/Fantasy-Map-Generator ✅页面）
- **重要诚实说明**：本会话在 README/LICENSE/package.json/wiki Home 与 Changelog 中**未发现任何 tectonic/plate tectonics 生成模块**（Changelog 关键词检索只命中 heightmap template 相关行）→ **FMG 不是板块构造生成器**，不要把它当作 §1/§8/§11 的同类实现。
- **可借鉴点**：
  1. **Voronoi/Delaunay 单元图**：把连续场转成不规则单元图后，河流/降水/国家等图算法都能在单元图上跑，天然支持"任意分辨率"与不规则边界。
  2. **高度图模板系统**：用一组可组合的"步骤"（Hill / Pit / Range / Trough / Strait / Smooth / Mask / Invert）在模板编辑器中拼出大陆形状——这是"可复现 + 可分享"的大陆预设方案，非常适合作为用户层 API。
  3. **alea 可复现随机**：把 PRNG 显式化并随地图保存，实现"同种子同世界"。
  4. **Wrap Tool（球面/环面拼接）**：解决有限图"东西边界断裂"的经典工程解。
  5. **河流编辑器 + 海岸线编辑器**：生成后允许手工接管关键水系/海岸——对最终成品质量提升极大。
  6. **降水/风场在单元图上计算**：一次扫描即可得到雨影与湿度分布。
  7. **纯前端零后端**：整套生成可跑在浏览器 → 说明算法复杂度可控，可作为"轻量方案"对照。

## 10. Songs of the Eons（SotE）— 大陆用什么算法？

- **名称**：Songs of the Eons（SotE）— 沙盒式幻想世界模拟器
- **验证链接（官方 itch 页，含算法功能描述）**：https://demiansky.itch.io/songs-of-the-eons ✅页面
- **逐字摘录（官方页面）**：
  - *"A complex plate tectonic system which generates geologic features along plate boundaries similar to what one encounters in the real world, generates hotspots, and includes many other geologic features."*
  - *"A climate system that considers the topography of the world and influences weather patterns. Rainfall, surface water movement, temperature distributions, and erosion which is influenced by the topography of the world and climate."*
  - *"The ability to alter dozens of variables related to world generation including the speed and frequency of tectonic plates, volcanic activity, the frequency of historic orogeny, and more."*
  - *"Complex water body system with interconnected rivers, lakes, and seas."*
- **FOSS 释放（⚠️关键限制）**：https://github.com/Calandiel/SongsOfFOSS ✅页面（meta description 逐字："A FOSS release of source code of the unreleased version 0.3 of Songs of the Eons"）；其 README ✅页面（https://raw.githubusercontent.com/Calandiel/SongsOfFOSS/master/README.md ）逐字写明：*"It's written in Lua and includes the game part of the project, notably **not including the original world generator**."*
  - **结论：SotE 的大陆生成算法没有公开源码可验证。** 任何"它用了 XX 算法"的具体断言都属推测。
- **许可 ✅页面**：https://raw.githubusercontent.com/Calandiel/SongsOfFOSS/master/LICENSE （除 /sote/emblems|engine|icons|music|data 等目录外的文件为 **MIT**；上述目录见 licensed_libraries_and_assets.txt）；★ 约 90（https://img.shields.io/github/stars/Calandiel/SongsOfFOSS ✅页面）
- **一句话原理（可验证的部分）**：以**板块构造仿真**（板块边界成山、热点火山）+ **气候/降水/侵蚀耦合** + **连通水体系统**为骨架的世界模拟器；算法细节未公开。
- **类别**：离线世界仿真（游戏世界生成）
- **有限图 vs 无限过程化**：有限世界（一整个星球/地图），非无限
- **实时 vs 离线**：**离线**（世界生成阶段），运行时是模拟推进
- **双峰**：**很可能具备**（板块构造 + 海平面 → 大陆与洋盆二分），但**无源码可验证** → 标注为"推测，未验证"
- **大陆架/陆坡/岛弧/海沟/洋中脊**：官方描述提到**板块边界地质特征**与**热点**（可对应岛弧/火山链）；海沟/洋中脊的具体实现未公开（推测具备）
- **内海与孤岛**：官方明确有 *"interconnected rivers, lakes, and seas"* → 内海/湖泊体系是有意设计的
- **可借鉴点（从其功能列表反推的工程要求）**：
  1. **"板块速度/频率、火山活动、历史造山频次"作为世界生成参数** —— 把地质"过程速率"而非"结果高度"作为输入，与你项目中"参数化大陆"的需求高度一致。
  2. **构造 → 气候 → 侵蚀 三级耦合**：侵蚀强度取决于气候，气候取决于地形 → 至少要把"降水场"接进侵蚀系数。
  3. **热点（hotspot）链**：独立于板块边界的火山链，是制造**岛弧/群岛**的廉价机制（固定热点 + 移动板块 = 一串岛）。
  4. **连通水体系统**：把河流/湖泊/海建成图结构并跟踪"水体身份"，比孤立的水位场更有表现力（可做内海盐度、流域归属）。
  5. **土壤/基岩分层**：官方描述土壤由冰川/河流/风/风化产生 → 与 §6c 的"分层表示"思路一致。
  6. **反面教训**：世界生成器是其核心资产且**未开源** → 若你的项目需要可验证/可复现的大陆算法，应基于本报告中的公开论文（§1–§4、§11）自建，而非依赖 SotE。

## 11. Procedural Tectonic Planets（球面板块构造）

- **名称**：Procedural Tectonic Planets（Computer Graphics Forum, 2019）
- **验证链接**：https://doi.org/10.1111/cgf.13614 ✅DOI（Crossref：Cortial, Peytavie, Galin, Guérin；CGF 2019）
- **摘要（Semantic Scholar API 返回，逐字要点）**：*"we capture the fundamental phenomena into a procedural method that faithfully reproduces large-scale planetary features generated by the movement and collision of the tectonic plates. We approximate complex phenomena such as plate subduction or collisions to deform the lithosphere, **including the continental and oceanic crusts**. The user can control the movement of the plates, which dynamically evolve and generate a variety of landforms such as **continents, oceanic ridges, large scale mountain ranges or island arcs**."*
- **PDF（⚠️未打开，来自 Semantic Scholar openAccessPdf）**：https://hal.science/hal-02136820
- **一句话原理**：**不做重物理仿真，而是把板块俯冲/碰撞的几何效果过程化参数化**——在球面上用可控的板块运动驱动"陆壳/洋壳变形"，直接产出大陆、洋中脊、山系与岛弧，并用真实或过程化高程数据放大细节。
- **类别**：过程化构造地质（球面），离线/可交互生成
- **有限图 vs 无限过程化**：**球面（闭合有限域）**，非平面无限
- **实时 vs 离线**：作者刻意"避免计算昂贵的物理仿真" → **可交互**，但仍是全局过程（非纯 O(1) 噪声）
- **双峰**：**最接近双峰的一篇 CG 论文**——摘要明确区分 **continental crust 与 oceanic crust**，即地形分布天然是"陆壳高 / 洋壳低"的双峰结构（⚠️具体直方图形状未逐图核对）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：**岛弧 ✔、洋中脊 ✔、大型山系 ✔、俯冲（海沟的成因）✔**；摘要未提"大陆架"，需自行在陆壳边缘补 shelf 剖面
- **内海与孤岛**：板块布局可产生内海与孤岛（球面板块的自然结果）
- **可借鉴点（本报告最推荐用于"地球式大陆"的论文）**：
  1. **陆壳/洋壳二分**：给每个板块打上 "continental / oceanic" 标签并让两者具有不同的基准高程与密度行为——这是得到**双峰高程直方图**最直接的结构性手段（比任何后处理重映射都更"有理由"）。
  2. **板块运动即输入**：用户控制的是速度/方向/旋转，而非高度；世界随时间演化 → 天然支持"同一世界的历史快照"。
  3. **俯冲近似**：两板块汇聚时，洋壳侧下沉形成海沟、陆壳侧增厚形成山系/岛弧——用**规则**而非求解器实现，成本低。
  4. **球面上的板块**：用球面 Voronoi/测地网格划分板块，避免平面地图的极区畸变与东西接缝。
  5. **放大阶段解耦**：大尺度由构造决定，细节由过程化噪声或**真实地球高程数据**放大 → 可复用现成 DEM。
  6. **可交互时间轴**：允许拖时间轴看大陆漂移，是极佳的用户体验与调试工具。
  7. **洋中脊**：在离散板块的边界线上加"隆起剖面"（沿边界的高斯脊）即可得到 mid-ocean ridge，实现成本极低。

## 12. 大陆架 / 洋盆 / 洋中脊 的"真实参照"（任务书第 12 项）

> 说明：**没有找到**一篇专门讲"过程化生成大陆架/洋盆/洋中脊"的 CG 论文（检索未命中，标为 ❌未验证）。CG 侧最接近的是 §11（Procedural Tectonic Planets，含洋中脊/岛弧/俯冲）。下面是三篇**已验证的地球物理/海洋地质文献**，它们给出可编码的定量模型与形态分类，是自研"大陆架–陆坡–洋盆–洋中脊"的正确参照。

### 12a. Parsons & Sclater 1977 — An analysis of the variation of ocean floor bathymetry and heat flow with age
- **验证链接**：https://doi.org/10.1029/JB082i005p00803 ✅DOI（Crossref：Parsons, Sclater；Journal of Geophysical Research, 1977）
- **一句话原理**：洋底深度随年龄（离洋中脊距离）按**半空间冷却/板块模型**下沉：depth(t) ≈ 2500 m + 350·√t（t 为百万年），到约 70 Ma 后趋于平坦（约 −5~−6 km）——即**洋中脊高、深海平原低**的定量规律。
- **类别**：地球物理定量模型（可直接作为程序化洋盆的函数）
- **可借鉴点**：
  1. **直接可编码的公式**：d(age) = 2500 + 350*sqrt(age)（米/百万年），脊轴 ~2.5 km、老洋壳 ~5.5–6 km → **天然形成"洋中脊 → 深海平原"的缓变剖面**。
  2. **√t 饱和**：用 min(age, 70) 或指数饱和项模拟"板块模型"的平坦化，避免无限加深。
  3. **与板块模型联动**：把 §8/§11 的板块边界当洋中脊，边界距离当年龄 → 立刻得到真实的洋盆深度场。
  4. **热流/深度同源**：同一条公式还能给出热流，可驱动"洋中脊热液/火山"等视觉元素。
  5. **双峰性来源**：洋壳深度（−2.5 ~ −6 km）与陆壳高程（+0 ~ +1 km）叠加海平面后，正是地球双峰直方图的物理成因。

### 12b. Smith & Sandwell 1997 — Global Sea Floor Topography from Satellite Altimetry and Ship Depth Soundings
- **验证链接**：https://doi.org/10.1126/science.277.5334.1956 ✅DOI（Crossref：Smith, Sandwell；Science, 1997）
- **一句话原理**：用卫星测高重力反演 + 船测深度融合，给出**全球统一的海底地形网格**——这是"真实海底长什么样"的权威数据源。
- **类别**：数据集/反演方法（非生成算法）
- **可借鉴点**：1) 作为**统计目标**：从该数据统计洋盆深度分布、脊–盆剖面、粗糙度谱，用来验收自研生成器；2) 用其**频谱（power spectrum）**校准程序化海底噪声的振幅–频率关系；3) 作为 §11 放大阶段可用的真实高程数据源。

### 12c. Harris, Macmillan-Lawler, Rupp & Baker 2014 — Geomorphology of the oceans
- **验证链接**：https://doi.org/10.1016/j.margeo.2014.01.011 ✅DOI（Crossref：Harris, Macmillan-Lawler, Rupp, Baker；Marine Geology, 2014）
- **一句话原理**：把全球海底划分为**大陆架（shelf）、陆坡（slope）、陆隆（rise）、深海平原（abyssal plain）、海沟（trench）、洋中脊（mid-ocean ridge）**等**地貌省（geomorphic provinces）**，并给出各自的深度/坡度范围。
- **类别**：地貌分类学（生成器的"目标形态字典"）
- **可借鉴点**：
  1. **直接拿来做"地貌省"枚举**：shelf / slope / rise / abyssal plain / trench / ridge / seamount / canyon——每一类对应一个可参数化的剖面生成器。
  2. **大陆架典型形态**：坡度极缓（~0.1°）、宽度数十~数百 km、外缘水深约 −130 m 的**陆架边缘（shelf break）** → 一个 smoothstep + 轻微噪声就能实现。
  3. **陆坡**：坡度陡（数度），是 shelf 到 rise 的过渡带 → 用 tanh/erf 做 S 型过渡最自然。
  4. **海沟**：窄而深的线状负地形（沿俯冲带）→ 在板块边界线上减一个高斯槽即可。
  5. **洋中脊**：宽缓隆起 + 中央裂谷 → "宽高斯脊 − 窄高斯槽"的叠加。
  6. **分类驱动渲染/生态**：地貌省标签可直接驱动材质、生物群系与资源分布，是"语义化地形"的良好实践。

## 13. Fournier, Fussell & Carpenter 1982 — Computer rendering of stochastic models（Diamond-Square / 中点位移）

- **名称**：Computer rendering of stochastic models（Communications of the ACM, 1982）
- **验证链接**：https://doi.org/10.1145/358523.358553 ✅DOI（Crossref：Fournier, Fussell, Carpenter；CACM 1982）。重印版（Seminal Graphics, 1998）10.1145/280811.280993 ✅DOI
- **一句话原理**：用**递归中点位移（midpoint displacement，俗称 diamond-square）**把随机扰动逐级细分到网格中点上，以极低成本得到自相似的分数维地形（布朗曲面近似）。
- **类别**：分形过程化（非物理）
- **有限图 vs 无限过程化**：**有限图幅**（2^n+1 网格，需递归细分），**不能**像 Perlin 那样任意点 O(1) 求值（新版有可并行/可分块变体但边界需匹配）
- **实时 vs 离线**：**一次性生成 O(N)**，可视为近实时；查询 O(1)（查已生成的栅格）
- **双峰**：**否**（近似高斯分布，单峰）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：无
- **内海与孤岛**：由阈值切分可产生；**孤岛偏多且不自然**是分形地形的经典缺陷
- **可借鉴点**：
  1. **H 参数（Hurst 指数）**控制粗糙度：位移幅度按 2^(-H) 或 r^(H) 衰减，H≈0.7–0.9 像山地，H≈0.5 像云。
  2. **可为不同区域设置不同 H / 初始幅度** → 得到"平滑平原 + 粗糙山地"的异构地形。
  3. **二值分割/多尺度金字塔**：diamond-square 与"四叉树/小波金字塔"同源，可直接与 §14 的小波表示互换。
  4. **缺点必须知道**：轴向网格伪影（明显的方块/十字纹）与"不够像被侵蚀过的地形"——这是后世转向 §1/§3/§6 这类侵蚀仿真的直接原因。
  5. **作为"基底"仍有用**：先生成 diamond-square 基底，再跑一次 §6 水力侵蚀，性价比很高。
  6. **不能做无限世界**：若项目需要无限大陆，diamond-square 需要改成分块 + 边界层同步（或直接换 Perlin/小波，见 §14）。

## 14. Cook & DeRose 2005 — Wavelet Noise

- **名称**：Wavelet Noise（ACM SIGGRAPH 2005 Papers / ACM TOG 24(3)）
- **验证链接**：https://doi.org/10.1145/1073204.1073264 ✅DOI（Crossref：Cook, DeRose；ACM Transactions on Graphics, 2005）。会议录版本 10.1145/1186822.1073264 ✅DOI（Crossref：ACM SIGGRAPH 2005 Papers）
- **一句话原理**：用**小波基（紧支撑的 Daubechies 型滤波器组）**做带限噪声合成，替代 Perlin 噪声——逐倍频带用无混叠的下/上采样与系数扰动得到各向同性、无网格伪影的噪声。
- **类别**：过程化噪声（可离线预计算为小波金字塔，也可 O(1) 求值）
- **有限图 vs 无限过程化**：**两种都行**——既可在点处按需求和（近似 O(1)，与频带数成正比），也可离线烘成小波金字塔/多分辨率纹理
- **实时 vs 离线**：**实时可用**（论文目标之一就是在渲染中替代 Perlin）；同时天然适合**离线金字塔**存储
- **双峰**：否（自身是细节噪声；但小波金字塔是**多尺度编辑与双峰重映射的理想载体**）
- **大陆架/陆坡/岛弧/海沟/洋中脊**：无
- **内海与孤岛**：取决于上层阈值/重映射
- **可借鉴点**：
  1. **带限（band-limited）**：每个倍频带独立生成并做正确的下/上采样，消除 Perlin 的高频混叠与方向性伪影。
  2. **小波金字塔 == 天然 LOD**：低频带决定大陆轮廓，高频带只加细节 → 与"离线算大陆、运行时加细节"的架构完美契合。
  3. **任意分辨率一致**：同一金字塔可在任何放大倍数下重建，不会像 Perlin 那样在放大后暴露网格结构。
  4. **可编辑性**：只修改某几个频带的系数即可"整体抬高/降低大陆"或"加密海岸线破碎度"——是**直接实现双峰重映射**的最佳位置（低频系数做双峰化）。
  5. **存储换速度**：离线烘成多级纹理后，运行时只是一次三线性插值（真 O(1)）。
  6. **可做"无缝球面"**：小波/球面谐波可在球面上构造，规避平面地图接缝。

---

# 二、额外发现的相关论文（均已校验 DOI）

## 15. Kelley, Malin & Nielson 1988 — Terrain simulation using a model of stream erosion
- **验证链接**：https://doi.org/10.1145/378456.378519 ✅DOI（Crossref：Kelley, Malin, Nielson；SIGGRAPH Computer Graphics 1988）。会议录版 10.1145/54852.378519 ✅DOI
- **一句话原理**：**最早把"河流侵蚀"引入计算机地形生成**的工作之一——用降雨→汇流→下切/搬运的过程修改初始地形，产生树枝状水系。
- **类别**：物理仿真（水系侵蚀），CG 史源头
- **可借鉴点**：1) 流水累积（flow accumulation）在地形生成中的奠基性用法；2) "侵蚀算子 + 沉积算子"分离的框架；3) 说明为什么纯分形不够（对比 §5/§13）。
- **其余字段**：有限网格；离线；否（双峰）；无海洋构造。

## 16. Galin, Guérin, Peytavie, Cordonnier, Cani, Benes, Gain 2019 — A Review of Digital Terrain Modeling
- **验证链接**：https://doi.org/10.1111/cgf.13657 ✅DOI（Crossref：Galin 等；CGF 2019）
- **一句话原理**：数字地形建模的**权威综述**，系统梳理"过程化 / 仿真 / 草图交互 / 放大 / 体表示"五大流派及其取舍。
- **类别**：综述（**强烈建议作为报告的引用骨架**）
- **可借鉴点**：1) 提供完整的分类学，可直接用于本报告的章节划分；2) 汇总各方法的计算成本与可控性对比；3) 指出"大尺度地形"研究的开放问题（正对应你的大陆需求）。

## 17. Tzathas, Gailleton, Steer & Cordonnier 2024 — Physically-based analytical erosion for fast terrain generation
- **验证链接**：https://doi.org/10.1111/cgf.15033 ✅DOI（Crossref；CGF 2024）
- **摘要要点（Semantic Scholar，逐字）**：*"we explore the analytical solutions of the stream power law and propose a method that is both physically-based and procedural, allowing fast and consistent large-scale terrain generation. In our approach, time is no longer the …"*
- **一句话原理**：解**流幂定律的解析解**，把原本需要上千次迭代的侵蚀仿真变成**可直接求值的解析表达式**——同时具备物理一致性与过程化的速度。
- **类别**：物理基础 + 解析（**最契合"离线/可预计算"需求的一篇**）
- **可借鉴点**：1) **免迭代**：不需要时间步进就能得到稳态地形，天然适合离线烘焙成高程图；2) 与 §1 同一物理定律，可互为验证；3) 参数（U/K/m/n）直接映射到"山有多高、河有多密"。
- **其余字段**：有限图；离线/快速；否（双峰）；无海洋构造。

## 18. Cordonnier, Galin, Gain, Benes, Guérin, Peytavie 2017 — Authoring landscapes by combining ecosystem and terrain erosion simulation
- **验证链接**：https://doi.org/10.1145/3072959.3073667 ✅DOI（Crossref；TOG 2017）
- **一句话原理**：把**生态系统（植被/土壤）仿真与地形侵蚀仿真耦合**，植被改变侵蚀速率、侵蚀改变地形与水分 → 更真实的景观演化与"作者可控"的中间尺度特征。
- **类别**：耦合仿真（地貌 + 生态），离线
- **可借鉴点**：1) 侵蚀系数随植被覆盖变化（坡面保护）；2) 生态与地形互为反馈的迭代框架；3) "画家控制"如何嵌入物理仿真循环。

## 19. Cordonnier, Cani, Benes, Braun 2018 — Sculpting Mountains: Interactive Terrain Modeling Based on Subsurface Geology
- **验证链接**：https://doi.org/10.1109/TVCG.2017.2689022 ✅DOI（Crossref：Cordonnier, Cani, Benes, Braun；IEEE TVCG 2018）
- **一句话原理**：用户编辑**地下地质构造（地层、褶皱、断层）**，系统按地质规则在表面产生地形——把"造山"的因果链还给用户。
- **类别**：交互建模 + 地质规则
- **可借鉴点**：1) **地下分层（subsurface layers）**表示，与 §6c 互补；2) 褶皱/断层可用简单的坐标变换（warp）实现，成本极低；3) 把"可见地形"作为"地下结构的解算结果"，是极佳的作者工具范式。

## 20. Schott, Paris, Fournier, Guérin, Galin 2023 — Large-scale Terrain Authoring through Interactive Erosion Simulation
- **验证链接**：https://doi.org/10.1145/3592787 ✅DOI（Crossref；TOG 2023）
- **摘要要点（Semantic Scholar，逐字）**：*"We set aside modeling in the elevation domain in favour of the uplift domain and compute emerging reliefs by simulating the stream power erosion… relies on a fast yet accurate approximation of drainage area and flow routing to compute the erosion interactively…"*
- **一句话原理**：**在"抬升域"而非"高程域"建模**——用户编辑抬升速率场，地形由快速流幂侵蚀解算"浮现"出来；配套快速排水面积/流向近似以支持交互。
- **类别**：交互式仿真（本报告认为最适合"可控大陆生成"的交互范式）
- **可借鉴点**：
  1. **抬升域编辑**：把用户的笔刷解释为抬升速率 U 而非高程 h → 自动得到合理的水系与山形（避免"刷出来的山没有河"）。
  2. **快速排水面积近似**：不需要精确 D8 全图拓扑，用局部/多尺度近似即可，实现交互速率。
  3. **增量式仿真**：只重算被编辑区域影响的范围，支持局部分辨率提升。
  4. **warp 模拟褶皱/断层**、**copy-paste** 复用山系 —— 工程上非常实用的"拟态"操作。
  5. **点/曲线高程约束**：用户给定若干控制点，仿真结果被拉向这些约束而不破坏水系。

## 21. Yang, Cordonnier, Cani, Perrenoud 2024 — Unerosion: Simulating Terrain Evolution Back in Time
- **验证链接**：https://doi.org/10.1111/cgf.15182 ✅DOI（Crossref；CGF 2024）
- **一句话原理**：**反向跑侵蚀**——从现今地形出发，反推出（抬升 + 侵蚀）历史，从而恢复过去的地形与侵蚀量。
- **类别**：逆问题 / 历史重建
- **可借鉴点**：1) 若你要从真实 DEM（如地球大陆）反推"抬升场"，这是对口方法；2) "侵蚀可逆"为地形版本控制/历史快照提供理论基础。

---

# 三、横向对比表（关键：双峰性 / 海洋形态 / 计算范式）

| # | 算法 | 双峰海陆高程 | 大陆架/陆坡 | 岛弧/海沟 | 洋中脊 | 内海/孤岛 | 计算范式 | 有限图/无限 |
|---|---|---|---|---|---|---|---|---|
| 1 | Cordonnier 2016（抬升+河流侵蚀） | ✘ | ✘ | ✘ | ✘ | 部分（内流盆地） | 离线迭代 | 有限图 |
| 2 | Schott 2024（多尺度侵蚀放大） | ✘ | ✘ | ✘ | ✘ | 继承输入 | 近交互/离线 | 有限图 |
| 3 | Génevaux 2013（水文过程化） | ✘ | ✘（但有河口） | ✘ | ✘ | **✔（闭合流域）** | 离线过程化 | 有限图 |
| 4 | Paris 2019（隐式 3D 特征） | ✘ | ✘ | ✘ | ✘ | 不适用 | 离线+按需求值 | 有限/可局部 |
| 5 | Musgrave 1989（分形+侵蚀） | ✘ | ✘ | ✘ | ✘ | 部分（碎岛偏多） | O(1) 噪声+后处理 | **可分块无限** |
| 6 | Mei 2007（GPU 水力侵蚀） | ✘ | **✔（磨蚀出 shelf/slope）** | ✘ | ✘ | **✔（湖泊/堰塞）** | GPU 离线迭代 | 有限图 |
| 7 | WorldEngine | 部分 | 部分（侵蚀间接） | 部分 | 部分 | ✔ | 离线管线 | 有限图 |
| 8 | plate-tectonics | 部分（需后处理） | ✘ | 部分 | ✘ | **✔** | 离线迭代 | 有限图 |
| 9 | Azgaar FMG | ✘ | ✘ | ✘ | ✘ | **✔** | 交互式 | 有限图（可 wrap） |
| 10 | Songs of the Eons | 推测 ✔（未验证） | ? | ✔（热点/边界） | ? | **✔** | 离线仿真 | 有限星球 |
| 11 | **Procedural Tectonic Planets** | **✔（陆壳/洋壳二分）** | 需自补 | **✔** | **✔** | ✔ | 过程化构造 | **球面** |
| 12 | Parsons&Sclater / Smith&Sandwell / Harris | 提供定量依据 | **✔（分类+剖面）** | **✔（海沟）** | **✔（√t 公式）** | — | 解析/数据 | — |
| 13 | Diamond-Square (1982) | ✘ | ✘ | ✘ | ✘ | 部分（碎岛多） | O(N) 一次性 | 有限图 |
| 14 | Wavelet Noise (2005) | ✘（但载体） | ✘ | ✘ | ✘ | 取决于上层 | O(1) / 金字塔 | **两种皆可** |
| 15 | Kelley 1988 | ✘ | ✘ | ✘ | ✘ | 部分 | 离线迭代 | 有限图 |
| 16 | Review 2019 | — | — | — | — | — | 综述 | — |
| 17 | Tzathas 2024（解析侵蚀） | ✘ | ✘ | ✘ | ✘ | 部分 | **解析 O(1) 式** | 有限图 |
| 18 | Cordonnier 2017（生态+侵蚀） | ✘ | ✘ | ✘ | ✘ | 部分 | 离线耦合 | 有限图 |
| 19 | Sculpting Mountains 2018 | ✘ | ✘ | ✘ | ✘ | 部分 | 交互 | 有限图 |
| 20 | Schott 2023（交互侵蚀） | ✘ | ✘ | ✘ | ✘ | 部分 | 交互仿真 | 有限图 |
| 21 | Unerosion 2024 | ✘ | ✘ | ✘ | ✘ | — | 离线逆解 | 有限图 |

## 给"地球式双峰大陆"项目的三点硬结论

1. **没有任何一篇 CG 论文直接产出地球式双峰直方图**。CG 界的目标是"好看的陆地地形"，不是"地球海陆分布"。
2. **唯一结构性正确的路线是"陆壳/洋壳二分"**（§11 Procedural Tectonic Planets 的思路）：给板块打地壳类型标签 → 陆壳基准 +0~+1 km、洋壳基准 −2.5~−6 km → 海平面切分。**双峰是构造的结果，不是噪声的结果**。
3. **若只需视觉上的双峰**，最省事的组合是：§14 小波金字塔的低频系数做**双峰重映射**（把单峰掰成两峰）+ §12 的解析公式（shelf/slope/ridge/trench 剖面）+ §6 的水力侵蚀磨出海岸带过渡 + §3/§20 的水文一致性保证河流不穿帮。

---

# 四、本报告未能验证的链接清单（汇总）

| 项 | 链接 | 状态 |
|---|---|---|
| Kellner "Interactive Hydraulic Erosion" | 无 | ❌**未找到该文献**（Crossref 作者检索 + 网络检索均无命中） |
| Cordonnier "Terrain Amplification using Multi-scale Erosion" (SIGGRAPH 2016) | 无 | ❌**该文献不存在**；正确文献为 Schott et al. 2024, 10.1145/3658200 ✅ |
| Cordonnier 2016 论文 PDF | https://hal.inria.fr/hal-01262376/file/2016_cordonnier.pdf | ⚠️未打开（URL 逐字来自 Semantic Scholar openAccessPdf 字段） |
| Schott 2024 开放版 | https://hal.science/hal-04565030 | ⚠️未打开（同上） |
| Procedural Tectonic Planets 开放版 | https://hal.science/hal-02136820 | ⚠️未打开（同上） |
| Schott 2023 开放版 | https://hal.science/hal-04049125 | ⚠️未打开（同上） |
| Review 2019 PDF | https://hal.archives-ouvertes.fr/hal-02097510/file/A%20Review%20of%20Digital%20Terrain%20Modeling.pdf | ⚠️未打开（同上） |
| Tzathas 2024 PDF | https://hal.science/hal-04525371v1/document | ⚠️未打开（同上） |
| Mei 2007 开放版 | https://inria.hal.science/inria-00402079 | ⚠️未打开（同上） |
| Génevaux 2013 PDF | https://hal.archives-ouvertes.fr/hal-01339224/file/siggraph2013.pdf | ⚠️未打开（同上） |
| platec 原始论文 | http://urn.fi/URN:NBN:fi:amk-201204023993 | ⚠️未打开（Cloudflare 403；URL 逐字来自 plate-tectonics README） |
| platec 原始项目 | http://sourceforge.net/projects/platec/ | ⚠️未打开（URL 逐字来自 README/meta description） |
| Songs of the Eons 世界生成器源码 | 无 | ❌**不存在公开源码**（SongsOfFOSS README 明确排除） |

**其余所有 DOI 均通过 Crossref API 实际返回校验 ✅DOI；所有 GitHub 链接均已实际 fetch ✅页面。**
