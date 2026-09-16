# 离线 GCM → 查表 / 模拟器(emulator):可行性调研

**范围**:为"运行时不能跑全局 2D 求解"的 Minecraft 模组,评估"离线跑 GCM → 生成查找表/模拟器"这条路是否可行,重点是 **T21/T42 分辨率的 GCM 能否给出东边界沿岸风急流 / 沿岸上升流**。

**方法说明**:所有结论均来自本会话中真实抓取的 URL。凡抓取失败、内容为 PDF、或被 Cloudflare/CloudFront 拦截的,一律标注"未确认"或"未找到"。搜索结果的摘要片段(snippet)单独标注,不作为已读证据。

---

## 0. 一句话结论

**NO(部分场景 partial)**。T21 = 32x64 网格、赤道 625 km、5.61 度;T42 = 64x128、310 km、2.79 度(UCAR Climate Data Guide, https://climatedataguide.ucar.edu/climate-tools/common-spectral-model-grid-resolutions )。东边界上升流系统(EBUS)的沿岸风 drop-off 在 **27 km** 大气模式里都还没解析出来,在 **3-9 km** 下仍被判定为"当前建模实践无法确定"(Capet et al. 2004, https://www.yumpu.com/en/document/view/24392361/capet-et-al-2004-legos );海洋侧要 **涡分辨**,中纬第一斜压 Rossby 半径 R1 = 30-50 km,按"每半径至少 2 格点"需要 **dx <= 15-25 km**(约 0.1-0.15 度)(Nurser & Bacon 2014, Ocean Science 10, 967, https://os.copernicus.org/articles/10/967/2014/os-10-967-2014.xml )。T21/T42 比该阈值粗 **5-60 倍**。因此 **T21/T42 查找表不能提供沿岸风急流与沿岸上升流场**,必须由独立的解析/参数化沿岸模型(或高分辨率降尺度)提供。

---

## 1. Climate emulators / GCM surrogates

### 1.1 ClimateBench(Watson-Parris et al.)

**项目名 + 主链接 + 语言 + 许可证 + 维护状态**
- 仓库:https://github.com/duncanwp/ClimateBench (Python / Jupyter)
- 许可证:**MIT License**,Copyright (c) 2021 Duncan Watson-Parris — 抓取自 https://raw.githubusercontent.com/duncanwp/ClimateBench/main/LICENSE
- 论文:ClimateBench v1.0: A benchmark for data-driven climate projections,JAMES **14**(10),e2021MS002954,DOI **10.1029/2021MS002954**,出版 2022-10 — 元数据抓取自 https://ueaeprints.uea.ac.uk/id/eprint/87693/
- 数据:Zenodo DOI **10.5281/zenodo.5196512**,记录页 https://zenodo.org/records/5196512 ,版本 1.0.0(2021-08-13 首发),**CC BY 4.0**;文件 CMIP6.zip 1.5 GB、test.tar.gz 74.4 MB、train_val.tar.gz 839.1 MB
- 维护状态:仓库存在且 README 带排行榜;论文已正式发表。**最近提交时间未确认**(GitHub API 触发限流)

**算的是什么(输出场)**
- 输出 4 个年平均值场:tas(近地面气温)、diurnal_temperature_range(日较差,由 tasmax - tasmin 导出)、pr(降水)、pr90(降水 90 分位)— 见 prepare_data.py,https://raw.githubusercontent.com/duncanwp/ClimateBench/main/prepare_data.py
- 源模式:**NorESM2-LM**。论文摘要原文:"annual mean global distributions of temperature, diurnal temperature range and precipitation (including extreme precipitation)"(UEA 摘要页)
- 网格:文献片段给出 **约 1.9 度 x 2.5 度(96 x 144),32 层(顶层 3 hPa)** — 来源为 AGU 页面 snippet,https://agupubs.onlinelibrary.wiley.com/doi/10.1029/2020EA001520 ;**page-level 未逐字确认**

**方程与近似(输入变量,参数级)**
输入构造在 baseline_models/utils.py(https://raw.githubusercontent.com/duncanwp/ClimateBench/main/baseline_models/utils.py ):
- L7:max_co2 = 9500;L9-L11:normalize_co2(data) = data / 9500
- L15:max_ch4 = 0.8;L18-L20:normalize_ch4(data) = data / 0.8
- L25:create_predictor_data(data_sets, n_eofs=5) — 对 BC(黑碳) 与 SO2 各做 **5 个 EOF**(eofs.xarray.Eof,pcs(npcs=5, pcscaling=1))
- L56-L62:拼成 inputs = {CO2, CH4, BC_0..BC_4, SO2_0..SO2_4} → **共 12 个预测因子**
- 即气溶胶空间场被 **EOF 截断到 5 模** 后再进入回归器 — 这本身就是"场 → 低维查表"的一次实践
- 损失/评估:L106 get_rmse(truth, pred) = sqrt( weighted((truth-pred)^2).weighted(cos(lat)).mean(['lat','lon']) ),即 **cos(纬度) 加权 RMSE**
- 原始强迫表 inputs_NorESM2_ERF.csv 列:year, CO2, nonCO2, ANT, GHG, AER, NAT, TOT(单位 W m^-2,1850 起年序列)
- 情景集合(prepare_data.py):1pctCO2, abrupt-4xCO2, historical, piControl, hist-GHG, hist-aer, ssp126, ssp245, ssp370, ssp370-lowNTCF, ssp585;每情景 3 个成员 r1i1p1f1 / r2i1p1f1 / r3i1p1f1(ssp245-covid 用 f2)

**是否需要全局迭代**:不需要。训练是"强迫时间序列 → 年全球场"的逐样本回归;推理是**一次前向传播**,无时间积分、无空间耦合。

**时间复杂度或实测性能(精度,非速度)**
README 排行榜(NRMSE,2080-2100 vs SSP245;数值抓自 https://raw.githubusercontent.com/duncanwp/ClimateBench/main/README.md ):

| 模型 | tas Spatial | tas Global | tas Total | dtr Spatial | dtr Global | dtr Total | pr Spatial | pr Global | pr Total | pr90 Spatial | pr90 Global | pr90 Total |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Neural Network | 0.107294 | 0.0440271 | 0.327429 | 9.91735 | 1.37219 | 16.7783 | 2.1281 | 0.2093 | 3.1746 | 2.61022 | 0.345709 | 4.33876 |
| Gaussian Process | 0.109106 | 0.0738238 | 0.478225 | 9.20713 | 2.67495 | 22.5819 | 2.34092 | 0.341453 | 4.04818 | 2.5559 | 0.429154 | 4.70167 |
| Random Forest | 0.107574 | 0.0584057 | 0.399602 | 9.19503 | 2.65241 | 22.4571 | 2.52431 | 0.502126 | 5.03494 | 2.68209 | 0.543375 | 5.39896 |

- 关键读数:**温度(tas)好,降水(pr/pr90)差一个量级**(Total NRMSE 0.33 vs 3.17 vs 4.34)。这是"热力学量可模拟、水循环量不可模拟"的直接证据。
- README 只给 NRMSE,**绝对 RMSE(K、mm/day)未给出 → 未找到**
- 训练/推理耗时:README 未给 → **未确认**

**输出格式**:NetCDF(xarray),每实验/成员一个 NorESM2-LM_{experiment}_{member}.nc;年分辨率、含 member 维;分发包为 .tar.gz。

**可直接借鉴什么**
1. **气溶胶场 → 5 个 EOF → 12 维输入向量** 的做法,是我们"把 GCM 场压成可放进存档的少量全局系数"的直接模板。
2. **cos(lat) 加权 NRMSE** 作为查找表精度验收指标(避免高纬格点被面积高估),L106 一行代码。
3. **明确区分"温度类可信 / 降水类不可信"**:若模组只需要温度、风、湿度,风险远低于需要降水。
4. pattern_scaling_model.ipynb 是最简单基线 — 若线性 pattern scaling 已够用,根本不需要 NN。

**关键文件路径 + 行号 + URL**
- baseline_models/utils.py:25 create_predictor_data(data_sets, n_eofs=5) — https://raw.githubusercontent.com/duncanwp/ClimateBench/main/baseline_models/utils.py
- baseline_models/utils.py:7 max_co2 = 9500;:15 max_ch4 = 0.8
- baseline_models/utils.py:106 get_rmse(truth, pred)(cos-lat 加权)
- prepare_data.py:14-17 variables = ['tas','tasmin','tasmax','pr'];:13 experiments 列表 — https://raw.githubusercontent.com/duncanwp/ClimateBench/main/prepare_data.py
- README.md 排行榜 — https://raw.githubusercontent.com/duncanwp/ClimateBench/main/README.md
- 基线 notebook:baseline_models/simple_GP_model.ipynb、Original_RF_model.ipynb、CNN-LTSM_model.ipynb、pattern_scaling_model.ipynb、RF_model_ESEm.ipynb、ClimateBenchGP.ipynb — 文件清单抓自 https://data.jsdelivr.com/v1/packages/gh/duncanwp/ClimateBench@main

**未确认项**:论文正文超参数/训练耗时/绝对 RMSE;输出场精确网格(仅 snippet 支撑);仓库最近提交日期。

---

### 1.2 Rasp, Pritchard & Gentine 2018(深度学习替代次网格参数化)

| 项 | 内容 |
|---|---|
| 名称/链接 | Deep Learning to Represent Subgrid Processes in Climate Models,PNAS **115**: 9684-9689,DOI **10.1073/pnas.1810286115**。可读页:https://eesm.science.energy.gov/publications/deep-learning-represent-subgrid-processes-climate-models |
| 语言/许可 | 论文;代码仓未定位 → 许可**未确认** |
| 算的是什么 | **不是输出场,而是次网格过程本身**:用 DNN 表示气候模型中**全部大气次网格过程**,替换传统参数化,从云分辨(超参数化)模式学习;NN 与分辨尺度动力学与地表通量方案自由耦合,做多年积分 |
| 方程与近似 | 从 multi-scale(对流显式)模式学"次网格倾向 = f(柱状态)";摘要未给具体公式 → 未确认 |
| 是否需要全局迭代 | **需要**:NN 嵌在 GCM 内逐步积分;只有 NN 前向廉价 |
| 时间复杂度/性能 | 摘要:多年积分稳定,复现云分辨模拟的平均气候、降水极值、赤道波谱;NN **近似守恒能量**(未显式约束)。**加速比未在摘要给出 → 未找到** |
| 输出格式 | 逐柱次网格倾向场(在线耦合,非离线文件) |
| 可直接借鉴 | (1) "NN 替代参数化"而非"NN 替代整个 GCM"的思路;(2) 泛化警告原文:"struggles to cope with temperatures far outside its training manifold" — 我们的查找表同样会在训练分布外崩坏 |
| 关键 URL | https://eesm.science.energy.gov/publications/deep-learning-represent-subgrid-processes-climate-models |
| 未确认 | 加速比、网络层数、训练样本量、代码许可 |

**同一脉络(均已读到摘要级证据)**

- **Beucler et al.**, Achieving Conservation of Energy in Neural Network Emulators for Climate Modeling,arXiv:**1906.06622**(https://arxiv.org/abs/1906.06622 )。摘要:ANN 不内禀守恒能量/质量,是长期气候预测的障碍;提出**两种强制线性守恒律的方法**:(1) 约束损失函数,(2) 约束网络结构。
- **Crossouard et al.**, Contribution of physical latent knowledge to the emulation of an atmospheric physics model: a study based on the LMDZ AGCM
  - 预印本 DOI **10.5194/egusphere-2025-1418**,https://egusphere.copernicus.org/preprints/2025/egusphere-2025-1418/
  - 正式版 **Geosci. Model Dev., 19, 5907-5931 (2026)**,DOI **10.5194/gmd-19-5907-2026**
  - 摘要级内容:离线模拟 **ICOLMDZ** 物理参数化,理想 aquaplanet;**逐大气柱**复现状态变量倾向廓线;比较 **DNN vs U-Net**;U-Net 在均值与方差上更好;DNN 均值好但**变率差**,因为**湍流没被 DNN 学好**;把 phyLMDZ 湍流参数化先验作为**潜变量**加入预测因子后显著改善。
  - 借鉴:沿岸风的**变率**比均值更难;纯 MLP 会漏掉小尺度过程。
- **Kochkov et al.**, Neural General Circulation Models for Weather and Climate,arXiv:**2311.07222**(https://arxiv.org/abs/2311.07222 ),Nature (2024) DOI **10.1038/s41586-024-07744-y**。摘要:首个"可微动力学求解器 + ML 组件"的 GCM;**140 km 分辨率**;对常规 GCM 有 "orders of magnitude computational savings"。注意:**140 km 已比 T42(310 km)细 2 倍以上**,而这只够"追踪全球平均温度、热带气旋频数",仍不是 EBUS 量级。
- **TorchClim v1.0: a deep-learning plugin for climate model physics**,**GMD 17, 5459-5475 (2024)**,https://gmd.copernicus.org/articles/17/5459/2024/ — 存在性与卷期页码已确认;**摘要与参数未确认**(页面 JS 占比过高)。

---

### 1.3 把 GCM 压成低维查表:EOF / POD 降阶模型(与"查找表"最接近的一支)

#### 1.3.1 Selten 1995 — T21 正压谱模式的 EOF 降阶(231 → 20 维)

- 论文:F. M. Selten, An Efficient Description of the Dynamics of Barotropic Flow,**J. Atmos. Sci. 52, 915-936 (1995)**
- 期刊页:https://journals.ametsoc.org/view/journals/atsc/52/7/1520-0469_1995_052_0915_aedotd_2_0_co_2.xml
- **可读摘要(KNMI 机构库)**:https://www.knmi.nl/research/publications/an-efficient-description-of-the-dynamics-of-barotropic-flow

**算的是什么**:正压涡度场的**时间演化**(动力学降阶),不是气候态输出场。

**方程与近似(参数级)**
- 基础模式:**T21 谱正压模式**,围绕一个真实冬季气候态,有低频变率
- **T21 模式状态维数 = 231 个变量**;对照的 T20 版本 = **210 个变量**(KNMI 摘要原文数字)
- 降阶方法:把动力学方程**投影到主导 EOF 上**(Galerkin 投影),并讨论积分约束
- 关键结果:**只用 20 个 EOF** 的 EOF 模式,短程预报表**显著优于** T20 截断模式;被忽略的相互作用用**线性阻尼**闭合,阻尼时间尺度是**尺度选择性的(对小尺度 EOF 更强)**;加此闭合后,**T21 的气候态与变率被 20 个 EOF 的模式很好复现**

**是否需要全局迭代**:需要(EOF 系数的时间积分),但自由度从 231 降到 20。
**时间复杂度/性能**:摘要只给精度对比,**无墙钟时间数字 → 未找到**
**输出格式**:EOF 系数时间序列 + EOF 空间基函数
**可直接借鉴什么**
1. **这是"GCM → 低维表示"最硬的先例**:T21 的 231 维正压状态,20 个 EOF 就能保住气候态与变率。
2. 适用范围限制:正压涡度**只有一个 2D 场**;真正的 3D 湿大气维数高得多 — 参照 ThousandWorlds 的 **53 个场 x 32x64 网格**(见 2.1)。
3. "被截断的相互作用 = 线性阻尼"这个闭合思想,与我们在 Minecraft 里用"平滑/扩散项"补偿未解析尺度是同一类操作。
**关键文件路径 + 行号 + URL**:论文无公开代码仓 → 无文件路径。URL 同上两条。
**未确认项**:代码、EOF 数目扫描、阻尼系数数值。

#### 1.3.2 其他降阶/低阶模式(存在性确认,细节未确认)
- Achatz/Selten 一脉:Primitive-Equation-Based Low-Order Models with Seasonal Cycle. Part I: Model Construction,J. Atmos. Sci. **60**(3),https://journals.ametsoc.org/view/journals/atsc/60/3/1520-0469_2003_060_0465_peblom_2.0.co_2.xml — 存在性确认,**内容未确认**(AMS 对本会话返回 CloudFront 403)。
- Franzke, Majda, Vanden-Eijnden, Low-Order Stochastic Mode Reduction for a Prototype Atmospheric GCM,https://www.semanticscholar.org/paper/90725d869ac8f00ca2cbe4bdf59da3a12c1685e1 — **未确认**。
- A POD-Galerkin approach to the atmospheric dynamics of Mars,https://reading-clone.eprints-hosting.org/119618/ — 抓取**失败**(fetch failed),**未确认**。方向与我们最接近(行星大气 GCM 的 POD 降阶),但本次无法验证内容。

> 对 1.3 节的总体判断:EOF/POD 降阶在文献中**真实存在且有效**,并且**正是在 T21 量级上做的**(Selten 1995)。但它降阶的是**动力学自由度**,不是"把 T21 的输出场变成沿岸风场"。**没有任何已读文献表明 EOF 截断能恢复出被模式分辨率截掉的沿岸风急流** — 谱截断丢弃的正是那些高波数模态。

---

## 2. Exoplanet-specific emulators

### 2.1 ThousandWorlds — 最接近的类比物(多 GCM → ML 模拟器基准)

**项目名 + 主链接 + 语言 + 许可证 + 维护状态**
- 论文:ThousandWorlds: A benchmark for climate emulation of potentially habitable exoplanets,**arXiv:2606.18338**(v1 2026-06-16,v2 2026-09-03),**License: CC BY 4.0**
- 链接:https://arxiv.org/abs/2606.18338 ;HTML 全文 https://arxiv.org/html/2606.18338v2
- 作者:Edward T. Stevenson(Cambridge)、Mei Ting Mak(Oxford)、Eric Wolf(CU Boulder)、Denis E. Sergeev(Bristol)、Tobi Hammond(Purdue)、N. J. Mayne(Exeter)、Miles Cranmer(Cambridge)
- 数据与代码:论文中以 "this https URL" 给出(HuggingFace es833/ThousandWorlds 出现在搜索结果中,**未直接抓取确认**)
- 领域:cs.LG / astro-ph.EP / astro-ph.IM

**算的是什么(输出场)— 变量级**
- 输入:**8 个连续行星参数** — radius(半径)、surface gravity(表面重力)、rotation period(自转周期)、surface pressure(表面气压)、CO2 体积混合比、CH4 体积混合比、incident stellar flux(恒星辐照)、stellar temperature(恒星有效温度);外加一个**离散 GCM 标签 s ∈ {1,...,5}**
- 输出:平均窗口内的 **53 个场,位于 32x64 经纬网格**(= **T21** 网格)
  - 3D 变量(各 10 个气压层):温度、比湿、东西风、南北风、云量 → **5 x 10 = 50 场**
  - 2D 变量:表面温度、OLR、ASR → **3 场**
  - 合计 **53 场**
- 数据集规模:**1,689 个模拟**(评估用目标模拟 **346** 个;其余 1,343 仅训练);新跑 **424** 个定制模拟填补参数空间
- 5 个 GCM:**UM、ExoCAM、ExoPlaSim、LFRic、ExoCAM-pre-2022**;ExoCAM 与 UM 为目标(测试)模式,其余为辅助源
- 计算成本背景(原文):单次 GCM 模拟通常 **10^4 - 10^6 core-hours**
- 两种发布格式:**32x64 网格 numpy 数组**,以及 **T21 球谐变换后的谱系数**(原文:"Truncation at degree 21 discards high-frequency spatial modes and yields a more compact representation");包内提供预计算的逆 SHT 权重

**方程与近似**:纯数据驱动,无解析方程(原文用 "parameter-to-field regression" 描述该任务族)。

**是否需要全局迭代**:**不需要**。一次前向出 53 个场。

**时间复杂度或实测性能**
- 基准方法:train-mean、**kNN**、Coord-MLP、Coord-DeepONet、PCA-MLP、PCA-GBT、ConvDec、**SFNO**、PPCA-ICM、GPLFR
- 摘要原文结论:**"GP-based methods perform best, suggesting that ThousandWorlds exposes a regime where off-the-shelf deep learning does not yet succeed."**
- 评估协议:(1) 排序用;(2) 相对 **GCM 之间的分歧** 衡量
- **具体 RMSE / ACC / RAMSE 数值未获取到 → 未确认**(HTML 抓取 100k 字符截断,结果表在更后面)
- **速度数字未给出 → 未找到**

**输出格式**:numpy 网格数组 + T21 球谐系数;含预计算逆 SHT 权重,可把谱预测映回网格。

**可直接借鉴什么(价值排序)**
1. **这是"多 GCM → 一个参数化查表"的完整可行性证明**:8 个输入参数即可预测 53 个三维场,包括**风场与云**。我们的模组本质上是同一任务,只是输入换成海拔/纬度/海陆/洋流参数。
2. **32x64(T21)就是这一整支共同体默认的"够用"分辨率**。若我们的世界只需要"行星尺度气候带 + 环流",T21 输出场是行业标准做法。
3. **"相对 GCM 间分歧"作为评估基准** — 若我们的查找表与 GCM 的差异小于 GCM 之间的差异,就没有必要追求更高精度。
4. **T21 球谐系数格式**:一个场只需约 232 个系数(degree <= 21),非常适合塞进模组存档;逆变换是固定稀疏线性算子,**运行时成本极低**。
5. **警告**:GP 类方法胜出、现成深度学习失效 → 样本量小(约 1700)时不要指望 CNN/Transformer。

**关键文件路径 + 行号 + URL**
- 摘要与元数据:https://arxiv.org/abs/2606.18338 (citation_title / citation_author / citation_date 元标签已读)
- 输入 8 参数、53 场 / 32x64、T21 SHT、5 个 GCM、1689 / 346 / 1395 计数、424 定制模拟、10^4-10^6 core-hours:https://arxiv.org/html/2606.18338v2 正文第 3.1-3.4 节(本次抓取文本偏移 12800-16600 与 20622 附近)
- **无本地文件路径**(论文仓未在本会话中定位到)

**未确认项**:数据/代码的确切仓库 URL;各基线数值 RMSE 表;各 GCM 自身的大气分辨率(附录 A.2 在 100k 截断之外);训练 GPU 小时数。

---

### 2.2 ExoPlaSim — 快速 GCM 本身的参数(T21/T42 的原始出处)

| 项 | 内容 |
|---|---|
| 名称/链接 | ExoPlaSim: Extending the Planet Simulator for exoplanets,arXiv:**2107.07685**(https://arxiv.org/abs/2107.07685 ),MNRAS **511**, 3272;可读全文:https://ar5iv.labs.arxiv.org/html/2107.07685 |
| 语言/许可 | Fortran(PlaSim 系);**ExoPlaSim 代码仓与许可未确认** |
| 算的是什么 | 3D 大气环流(温度、风、湿度、云、辐射)的气候态 |
| 方程与近似(参数级) | PlaSim 谱核心;**默认 T21(32 纬 x 64 经)或 T42(64 x 128)**,**垂直 5 或 10 层**;sigma 坐标,层界面 sigma_h,n = 0.75(n/N) + 1.75(n/N)^3 - 1.5(n/N)^4(原文式 5);可选"伪线性"与"伪对数"垂直离散;物理:垂直扩散(未解析湍流)、**Kuo 型深对流**、**Tiedtke 1983 浅对流**、干对流调整;辐射:**双波段短波**(灰水汽 + 臭氧吸收、灰云散射)+ **单波段长波**(灰吸收,水汽/CO2/云);入射谱能量分配 **SW1 = 51.7%,SW2 = 48.3%** |
| 是否需要全局迭代 | **需要**(谱模式时间积分),但极快 |
| 时间复杂度/实测性能 | 原文:**"a fast GCM, able to model a year of climate in under a minute of wall-time"** → 这是"离线批量生成查找表"最关键的数字:**一年气候 < 1 分钟** |
| 输出格式 | NetCDF(PlaSim 标准输出) |
| 可直接借鉴什么 | (1) **T21/T42 + 5-10 层是"廉价可跑"的现实配置**;(2) 一年 < 1 分钟意味着**可以自己离线跑上千个参数组合建表**,不必依赖别人的数据;(3) 但辐射是灰体双波段,**降水/云可信度低** |
| 关键 URL | https://ar5iv.labs.arxiv.org/html/2107.07685 ;https://arxiv.org/abs/2107.07685 |
| 未确认项 | 代码仓 URL/许可;T21 vs T42 的墙钟时间定量对比;Gibbs 振铃滤波细节(搜索片段提到 "At T21 and T42, none of our filters are able to completely remove the Gibbs ripples",**仅 snippet,未逐字确认**) |

---

### 2.3 用 ML 加速 3D 行星大气 GCM:OASIS + 代理辐射传输

| 项 | 内容 |
|---|---|
| 名称/链接 | Enhancing 3D Planetary Atmosphere Simulations with a Surrogate Radiative Transfer Model,arXiv:**2407.08556**(https://arxiv.org/abs/2407.08556 ),MNRAS **535**, 2210 (2024),DOI **10.1093/mnras/stae2461** |
| 作者 | Tara P. A. Tahseen, Joao M. Mendonca, Kai Hou Yip, Ingo P. Waldmann |
| 语言/许可 | 论文;代码未定位 → 许可**未确认** |
| 算的是什么 | 替换 **OASIS GCM 的辐射传输模块**(在线耦合),不是替换整个 GCM |
| 方程与近似 | 用**循环神经网络(RNN)**学习"模拟输入 → 辐射传输输出"的映射;训练/测试算例为**金星大气**(非地球) |
| 是否需要全局迭代 | **需要**:代理模型嵌在 GCM 时间步内,每步调用 |
| 时间复杂度/实测性能 | 摘要原文:代理耦合后 GCM **"above 99.0% accuracy and 147 factor GPU speed-up of the entire simulation compared to using the matched original GCM under Venus-like conditions"** — **精度 > 99.0%,整场模拟加速 147x(GPU)** |
| 输出格式 | 在线张量(加热率/通量),非离线表 |
| 可直接借鉴什么 | (1) **147x 是"ML 加速 GCM 组件"的实测上界量级**;(2) 但它加速的是**辐射**(最慢模块),动力学分辨率**完全没变** — 再次说明 ML 不能凭空造出被网格截掉的沿岸风;(3) 若离线跑 ExoPlaSim 建表太慢,可考虑同样的"只代理辐射"策略 |
| 关键 URL | https://arxiv.org/abs/2407.08556 |
| 未确认项 | RNN 结构细节、训练集大小、CPU 上的加速比 |

**搜索结论(重要)**:本次**未找到任何"把 3D 系外行星 GCM(ExoPlaSim / LMDZ / MITgcm)的完整输出场用 NN 直接模拟"的论文** — 除 ThousandWorlds(2.1)外。已找到的都是**替换 GCM 内部某个模块**(辐射、物理参数化)。**ThousandWorlds 是"模拟整场 3D 系外行星 GCM 输出"的唯一实例,而且它用的网格正是 T21。**

---

### 2.4 检索与反演侧的模拟器(PICASO / petitRADTRANS / 检索加速)

| 项 | 状态 |
|---|---|
| **PICASO** | 仓库 https://github.com/natashabatalha/picaso ;README 已抓(https://raw.githubusercontent.com/natashabatalha/picaso/master/README.md )。定位:**1D** 系外行星/褐矮星光谱计算(透射、发射、反射光)、**1D 气候建模**、网格检索与反演拟合;**21 位贡献者**(all-contributors 徽章)。README 中**未提及任何神经网络模拟器** → "PICASO NN emulator" **未找到** |
| **petitRADTRANS** | 代码在 GitLab(https://gitlab.com/mauricemolli/petitRADTRANS )。本会话**未成功抓取其文档主页面以确认是否存在 NN 模拟器** → **未确认 / 未找到** |
| **检索模拟器 / retrieval emulator** | 搜索结果指向 Speeding Up the GUIBRUSH retrieval code for modelling exoplanetary atmospheres(ScienceDirect PII S2213133725001283)与 Neural Network Accelerated Retrieval of Clear Brown Dwarf Atmospheres(UCF ETD https://stars.library.ucf.edu/etd2024/517/ )。**两者均只读到检索条目,正文未抓取 → 未确认** |
| **Rooney & Batalha 等** | Semantic Scholar 记录显示存在 Spherical Harmonics for the 1D Radiative Transfer Equation. I. Reflected Light,https://www.semanticscholar.org/paper/48c11ddb0db0356a174f3a4166ef16a35aef46e3 。这是**加速 1D 辐射传输**的工作,不是 GCM 模拟器;**正文未抓取 → 未确认** |

> 本节对模组的含义:系外行星领域的"模拟器"绝大多数是**光谱/辐射传输**层面的(因为观测是光谱),**不是环流场层面的**。唯一做环流场的是 ThousandWorlds。所以先例最终仍回到 1.1 与 2.1。

---

## 3. 关键问题:T21 / T42 能解析东边界沿岸风急流 / 沿岸上升流吗?

### 3.1 先把分辨率钉死(唯一确凿无争议的数字)

来源:UCAR Climate Data Guide, Common Spectral Model Grid Resolutions,Last modified 26 Nov 2017 —
https://climatedataguide.ucar.edu/climate-tools/common-spectral-model-grid-resolutions

| Truncation | lat x lon | km at Eq | deg at Eq |
|---|---|---|---|
| **T21** | **32x64** | **625** | **5.61** |
| **T42** | **64x128** | **310** | **2.79** |
| T62 | 94x192 | 210 | 1.89 |
| T63 | 96x192 | 210 | 1.88 |
| T85 | 128x256 | 155 | 1.39 |
| T106 | 160x320 | 125 | 1.12 |
| T159 | 240x480 | 83 | 0.75 |
| T255 | 256x512 | 60 | 0.54 |
| T382 | 576x1152 | 38 | 0.34 |
| T799 | 800x1600 | 25 | 0.22 |

该页同时给出谱模式代价标度:"the Legendre transforms whose cost increases as **N^3**" — 这也是为什么大家舍不得提分辨率。

注意:**T799 也只有 25 km**。要想接近"能看见沿岸风 drop-off"的 3-9 km,需要远超 T799 的截断,离线建表代价是天文数字。

### 3.2 大气侧:多细才能把沿岸风急流放到正确位置?

**决定性文献:Small, Curchitser, Hedstrom, Kauffman & Large (2015)**
The Benguela Upwelling System: Quantifying the Sensitivity to Resolution and Coastal Wind Representation in a Global Climate Model,**J. Climate 28(23), 9409-9432**,DOI **10.1175/JCLI-D-15-0192.1**
- 摘要全文抓到(Mendeley 目录页):https://www.mendeley.com/catalogue/d7553970-486b-30a6-a296-af5aa55a7343/
- 引文元数据(UCAR Pure):https://impacts.ucar.edu/en/publications/the-benguela-upwelling-system-quantifying-the-sensitivity-to-reso/
- 模式:**CCSM4 + ROMS**

**摘要中的原文级关键结论**
1. **"When the wind stress curl is too broad (as with a 1 deg atmosphere model or coarser), a Sverdrup balance prevails at the eastern boundary, implying southward ocean transport extending as far as 30S and warm advection."**
   → **1 度(约 111 km)大气模式的沿岸风应力旋度"太宽",东边界退化为 Sverdrup 平衡,产生暖偏差。**
2. **"Higher atmosphere resolution, up to 0.5 deg, does bring the atmospheric jet closer to the coast, but there can be too strong a wind stress curl."**
   → **0.5 度(约 55 km)只做到"把急流挪得更靠近岸",而且旋度可能过强(过头)。**
3. **"The most realistic representation of the upwelling system is found by adjusting the 0.5 deg atmosphere model wind structure near the coast toward observations, while using an eddy-resolving ocean model."**
   → **最佳方案 = 0.5 度大气风场 + 沿岸观测订正 + 涡分辨海洋。**
4. **"A similar adjustment applied to a 1 deg ocean model did not show such improvement."**
   → **1 度海洋模式即使订正风场也救不回来。**

**把 T21/T42 代进去**
- T42 = **2.79 度(310 km)**,比"太宽的 1 度"还粗 **2.8 倍**
- T21 = **5.61 度(625 km)**,比 1 度粗 **5.6 倍**,比"仅勉强把急流挪近岸的 0.5 度"粗 **11 倍**
- 结论:**T21/T42 大气场落在"Sverdrup 平衡 + 暖偏差"的范畴,按 Small et al. (2015) 的定义性判据,它连"把沿岸急流放到大致正确位置"都做不到。**

**沿岸风 drop-off 的尺度:Capet, Marchesiello & McWilliams (2004)**
Upwelling response to coastal wind profiles,**GRL 31, L13311**,DOI **10.1029/2004GL020123**
- 摘要(EarthRef 参考库):https://earthref.org/ERR/82720/
- 全文(LEGOS 翻页件,已读到正文):https://www.yumpu.com/en/document/view/24392361/capet-et-al-2004-legos

正文级关键语句:
1. **"COAMPS winds off the CCC also typically exhibit a transition in the alongshore wind speed within a narrow coastal strip where the wind stress decreases to about 10-20% of its offshore value."**
   → **离岸风应力在一条狭窄近岸带内降到离岸值的 10-20%。**
2. **"COAMPS winds at resolutions of 27, 9, and 3 km ... their structures differ significantly with the resolution. Most notably, the drop-off takes place over an increasingly small region as the resolution increases and wind curl increases proportionally, indicating that the true structure is indeterminate by current modeling practices."**
   → **27 / 9 / 3 km 三档给出的 drop-off 结构显著不同;分辨率越高 drop-off 区越窄、旋度越大;真实结构"按当前建模实践无法确定"。**
3. **"the scatterometer analyses are not reliable within 50 km of the coastline"**
   → **卫星散射计在离岸 50 km 内不可靠。** 即使想用观测订正,50 km 以内本身就缺数据。
4. **"the uncertainties in the transition scale and magnitude for the drop-off strongly affect coastal currents and temperature"**
   → drop-off 的**过渡尺度**与**幅度**不确定性强烈影响沿岸流与温度。

**大气侧阈值小结**
- 沿岸风 drop-off 的特征宽度:**数十 km 量级**(一条把风应力降到 10-20% 的狭窄带,且 27 km 仍未收敛)
- **<= 0.5 度(约 55 km)** 是"急流开始靠近岸"的最低门槛(Small et al. 2015),**且需观测订正**
- **27 km 尚未收敛,3-9 km 仍不可判定**(Capet et al. 2004)

### 3.3 海洋侧:Rossby 半径尺度论证(含真实数字)

**决定性文献:Nurser & Bacon (2014)**,The Rossby radius in the Arctic Ocean,**Ocean Science 10, 967-975**,DOI **10.5194/os-10-967-2014**
https://os.copernicus.org/articles/10/967/2014/os-10-967-2014.xml

**方程(原文级)**
- 模分离:N^-2(z) d2phi/dz2 + c^-2 phi = 0,边界条件 phi = 0 at z = -H, z = 0
- **R_i = c_i / f**,其中 **f = 2 Omega sin(Theta)**
- 常 N 的简化估计:**c_i = N H / (i pi)**(原文 "assuming constant N (Gill, 1982)" 下)
- WKBJ/LG 一般形式(C98):c_i 由积分 N dz 决定

**分辨率判据(原文级,整个第 3 节的量化核心)**
1. **"A minimum of two grid points per eddy radius is necessary to resolve eddies adequately, and one grid point per radius to 'permit' them: e.g. Smith et al. (2000), Hecht and Smith (2008)."**
2. **"Hallberg (2013) suggests that eddy parameterizations may no longer be necessary once the ratio of the baroclinic deformation radius to a model's effective grid spacing is greater than a value of about 2, where 'effective spacing' means the grid-diagonal distance."**
3. **"The typical best resolution in oceanic general circulation models (OGCMs) is currently ~0.1 deg (ca. 10 km)."**
4. **"the 30-50 km characteristic of the mid-latitude oceans"** — 中纬第一斜压 Rossby 半径 R1 的典型值

**由这些数字直接算出的阈值**
- 中纬 R1 = **30-50 km**
- 判据 1(每半径 >= 2 格点):**dx <= R1/2 = 15-25 km**(约 0.14-0.23 度)
- 判据 2(R1 / 网格对角线 > 2,即 dx * sqrt(2) < R1/2):**dx <= R1/(2 sqrt(2)) 约 11-18 km**(约 0.10-0.16 度)
- 判据 3:当前最好 OGCM 约 **0.1 度(10 km)** — 刚好落在上面两条线边缘
- **即:只有约 10-25 km 的海洋网格才能"分辨"中纬涡旋与沿岸流系;10 km 以下才谈得上"涡分辨"。**

**高纬/弱层结的额外困难(同文实测数字)**
- 北极深盆 R1:Nansen Basin 约 **5 km**,中央 Canadian Basin 约 **15 km**;分区年均值:Amerasian **11.1 km**、Eurasian **7.8 km**;mode 2 约 **4.6-5.2 km**
- 陆架海与其他区域:**1-7 km**;冬季水体均一化时可 **< 1 km**
- 原文判语:**"OGCMs will therefore (typically) be eddy-permitting in the Arctic region at best. Over the broad Arctic Ocean shelf seas, the Rossby radius will be even smaller, and here OGCMs will not even be eddy-permitting."**
  → **对高纬沿岸(挪威、阿拉斯加、格陵兰、南极),10 km 网格都只是"eddy-permitting",沿岸带根本不够。**

**Rossby 半径的全球气候态参考(摘要级已读)**
- Chelton, deSzoeke, Schlax, El Naggar & Siwertz (1998), Geographical Variability of the First Baroclinic Rossby Radius of Deformation,**J. Phys. Oceanogr. 28(3), 433-460**
  - 记录页(含完整摘要):http://westernwaters.org/record/view/244192
  - 摘要要点:给出**全球 1 度 x 1 度**的第一斜压重力波相速 c1 与 Rossby 半径 lambda1 气候态;指出早期 **5 度 x 5 度** 图因计算误差系统性偏低 **5%-15%**
  - **该摘要未给出分区域 lambda1 数值表 → 具体区域数值未确认**
- 相关(摘要级已读,含尺度定义):Resolution issues in numerical models of oceanic and coastal circulation,Greenberg, Dupont, Lyard, Lynch & Werner,**Continental Shelf Research (2007), pp. 1317-1343**,DOI **10.1016/j.csr.2007.01.023**
  - 记录页:https://manuscript.isc.ac/Inventory/10/2295870.htm
  - 摘要要点:讨论尺度由 **Rossby 半径、地形、岸线、Coriolis 纬度依赖** 等决定,专门关注"准确复现物理过程所需的水平分辨率",并讨论从全球/海盆模式向下嵌套到区域/陆架尺度

### 3.4 高纬沿岸 Rossby 半径量级(用于南极/挪威海岸)

同 Nurser & Bacon (2014) 表 1(原文数字,已读):

| 区域 | Mode 1 夏季 | Mode 1 冬季 | Mode 1 年均 | Mode 2 年均 |
|---|---|---|---|---|
| Amerasian Basin(加拿大 + 马卡罗夫) | 11.2 km | 11.0 km | **11.1 km** | 5.2 km |
| Eurasian Basin(阿蒙森 + 南森) | 7.9 km | 7.7 km | **7.8 km** | 4.6 km |

→ **高纬 R1 = 5-11 km ⇒ 涡分辨需要 dx 约 2.5-5.5 km。** 比 T21(625 km)细 **100 倍以上**。

### 3.5 本节结论

> **大气侧阈值**:要"把沿岸风急流放到靠岸的正确位置",至少需要 **0.5 度(约 55 km)**,而且即使到 0.5 度仍需沿岸观测订正;**1 度(111 km)就已退化为 Sverdrup 平衡 + 暖偏差**(Small et al. 2015)。沿岸风 drop-off 本身在 **27 km 未收敛、3-9 km 不可判定**(Capet et al. 2004)。
> **海洋侧阈值**:中纬需 **dx <= 15-25 km**(涡分辨,R1 = 30-50 km,每半径 >= 2 格点);高纬需 **dx 约 2.5-5.5 km**(R1 = 5-11 km)。
> **T21(5.61 度, 625 km)与 T42(2.79 度, 310 km)**:比大气门槛粗 **5.6-11 倍**以上,比海洋门槛粗 **12-250 倍**。
> **因此:T21/T42 的 GCM 查找表无法提供沿岸风急流与沿岸上升流场。它只能提供行星/海盆尺度的背景(Sverdrup 尺度环流、大尺度风带、海盆 SST 梯度、上升流"有利风"的大尺度分布)。沿岸风急流与上升流信号必须由独立的解析/参数化沿岸模块提供,或由极高分辨率降尺度单独生成后再压成沿岸查找表。**

---

## 4. "GCM 查找表"本身,以及游戏 / 世界生成领域是否有人做过

### 4.1 字面检索 "GCM lookup table"

- 按字面检索,**未找到任何把 GCM 输出直接做成"查找表"的论文或项目**。唯一相关的命中是一篇 GMD 预印本片段提到 "GCM snapshots",即 **CLISEMv1.0**,https://gmd.copernicus.org/preprints/gmd-2021-136/gmd-2021-136-manuscript-version3.pdf — **正文未抓取(PDF 不受支持)→ 内容未确认**。
- 但**概念上等价的做法在文献里是真实的**:
  1. **ClimateBench 用 5 个 EOF 压缩气溶胶场**(1.1 节,baseline_models/utils.py:25-63) — 把"场 → 12 维向量"。
  2. **ThousandWorlds 同时发布 T21 球谐系数版**(2.1 节) — 一个 32x64 场约 232 个系数,这是最标准的"GCM → 小表"格式。
  3. **Selten 1995 把 T21 的 231 维状态压到 20 个 EOF**(1.3.1 节)。
- **结论:不存在叫"GCM 查找表"的成熟范式;但"球谐系数 / EOF 系数作为紧凑表"是这些基准默认的存储方式。**

### 4.2 游戏 / 世界生成领域

**已确认真实存在(但都不是 GCM 驱动)**

**Tellus(Minecraft 模组,Fabric/Forge/NeoForge)**
- https://github.com/Yucareux/Tellus ;README 已抓(https://raw.githubusercontent.com/Yucareux/Tellus/main/README.md )
- 做法:**从真实地理数据流式取样**,不是查 GCM 表:
  - **ESA WorldCover 2021 v200**,原生 **约 10 m** 分辨率 + 内建 **20-640 m** COG 概览层(CC BY 4.0)
  - **ETH Global Canopy Height 10 m 2020**(Lang, Schindler & Wegner 2022,DOI 10.3929/ethz-b-000609802)
  - **Overture Maps** 水体/海岸线多边形(ODbL)
  - **Koppen-Geiger 1 km 气候分类**(Beck, Zimmermann, McVicar et al. 2018, Scientific Data,DOI **10.1038/sdata.2018.214**)— 最接近"气候查找表"的东西,但它是分类栅格,不是 GCM 场
  - **Mapterhorn** 地形 DEM;**OpenWaters Seascape** 水深栅格;**Open-Meteo** 实时天气(CC BY 4.0)
- 关键工程约束(README 原文):**"Tellus requires an active internet connection and will not work offline."** → 它靠**按需下载 + 本地缓存**规避"存档塞不下全球场"的问题
- 对我们的启示:**1 km Koppen 栅格 + 10 m 土地覆盖 + DEM + 水深** 就是"全球气候相关信息"在游戏里实际可行的体量;**GCM 的 625 km 网格反而更粗**。

**TerraFirmaCraft(Minecraft 模组,游戏内气候模型)**
- Climate.java:https://github.com/TerraFirmaCraft/TerraFirmaCraft/blob/5040431220a91a5d91dbad872065e2bc97e43204/src/main/java/net/dries007/tfc/util/climate/Climate.java
- 已读源码事实:L43 public final class Climate;L59 public static ClimateModel create(ResourceLocation id)(气候模型注册表,可通过 SelectClimateModelEvent 在载入时替换);L69 public static float getTemperature(...);L94 public static float getRainfall(...);文件共 **194 行**;许可证 **EUPL v1.2**
- 性质:**运行时的解析/参数化气候模型**(温度、降雨、雾度),**与 GCM 无关,无查找表**。这是"游戏里做气候"的现实参照 — **大家选择解析式,而不是 GCM 查表**。

**其他**
- Climate Control / Geographicraft(CC/GC,Minecraft 气候控制模组):仅见中文社区页面 https://www.mcmod.cn/post/4014.html ,**未抓取验证 → 未确认**
- 对 "Minecraft climate model lookup table"、"procedural world generation climate simulation GCM" 的字面检索:**未找到任何真实项目**。

### 4.3 本节结论

> **未找到**任何把 GCM 降为查找表并用于游戏 / 程序化世界生成的项目。游戏界的实际做法是二选一:**(a) 直接查真实观测栅格(Tellus:Koppen 1 km / ESA 10 m / DEM),或 (b) 运行时跑解析/参数化气候公式(TerraFirmaCraft)**。GCM 查表在游戏领域是空白。

---

## 5. 结论:T21/T42 查表能不能给出东边界沿岸风

### 答案:**NO**(对沿岸风急流 / 沿岸上升流);**partial**(对行星 - 海盆尺度背景场)

**找到的数值分辨率阈值(三条独立证据链)**

| 目标量 | 阈值(有文献支撑的数字) | 出处 |
|---|---|---|
| 沿岸风应力旋度不再"太宽"、不退化到 Sverdrup 平衡 | 需**优于 1 度(约 111 km)**;**0.5 度(约 55 km)** 才把急流挪近岸,且需沿岸观测订正 | Small et al. 2015, J. Climate **28**, 9409-9432, DOI 10.1175/JCLI-D-15-0192.1 — https://www.mendeley.com/catalogue/d7553970-486b-30a6-a296-af5aa55a7343/ |
| 沿岸风 drop-off 结构收敛 | **27 km 未收敛;3-9 km 仍"不可判定"**;散射计离岸 **50 km** 内不可靠 | Capet et al. 2004, GRL **31**, L13311, DOI 10.1029/2004GL020123 — https://www.yumpu.com/en/document/view/24392361/capet-et-al-2004-legos |
| 中纬海洋涡旋 / 沿岸流系"分辨" | **dx <= R1/2 = 15-25 km**(R1 = 30-50 km;每半径 >= 2 格点);Hallberg 判据给出 **dx <= R1/(2 sqrt2) 约 11-18 km**;当前最好 OGCM 约 **0.1 度(10 km)** | Nurser & Bacon 2014, Ocean Science **10**, 967-975, DOI 10.5194/os-10-967-2014 — https://os.copernicus.org/articles/10/967/2014/os-10-967-2014.xml |
| 高纬(南极/挪威/阿拉斯加)沿岸 | **dx 约 2.5-5.5 km**(R1 = 5-11 km) | 同上,表 1 |

**T21 / T42 的位置**
- **T21 = 32x64, 625 km, 5.61 度**(https://climatedataguide.ucar.edu/climate-tools/common-spectral-model-grid-resolutions )
- **T42 = 64x128, 310 km, 2.79 度**(同上)
- 相对大气门槛(0.5 度):**T21 粗 11 倍,T42 粗 5.6 倍**
- 相对"太宽的 1 度":**T21 粗 5.6 倍,T42 粗 2.8 倍** — 连"太宽"那条线都没达到
- 相对海洋涡分辨门槛(10-25 km):**T21 粗 25-62 倍,T42 粗 12-31 倍**

**查找表能提供 / 不能提供的东西**

| 能提供(partial 部分) | 不能提供(NO 部分) |
|---|---|
| 行星尺度温度带、季节循环 | 沿岸风急流(离岸 10-100 km 的狭窄加速带) |
| 大尺度风带与"上升流有利风"的海盆分布 | 近岸风应力 **drop-off**(降到离岸值 10-20% 的衰减) |
| 海盆尺度 SST 梯度 | 风应力 **旋度**(沿岸上升流的第二驱动机制) |
| 海盆尺度 Sverdrup 环流 | 沿岸上升流本身(Ekman 抽吸 + 沿岸辐散) |
| 云量 / OLR / ASR 等 2D 辐射场(ThousandWorlds 已验证) | 沿岸 SST 锋、沿岸流、涡动能 |

**给模组的工程建议(基于以上证据)**
1. **不要把"沿岸风"寄托在 T21/T42 查找表上。** 应做成**独立的解析/参数化沿岸模块**:以 GCM 表给出大尺度背景风,再叠加一个**沿岸 drop-off 剖面**(沿法向的指数/线性衰减,衰减尺度取数十 km 量级 — 注意 Capet et al. 2004 明确指出该尺度本身不确定)。
2. **查找表网格若要"看得见"沿岸,需要至少 0.1-0.25 度(10-25 km)**。以地球为例 0.1 度是 3600x1800 格点/场,单场 float32 = 26 MB。**对 Minecraft 存档不现实**,除非只存沿岸带(例如离岸 200 km 以内的条带)而不是全球。
3. **存储格式优先球谐系数(T21 约 232 系数/场)或 EOF 系数(ClimateBench 用 5 个 EOF)**,而不是原始栅格。
4. **精度验收用 cos(lat) 加权 NRMSE**(ClimateBench get_rmse,L106),并**以"模式间分歧"为上限**(ThousandWorlds 评估协议)— 若我们的误差小于 GCM 之间的差异,继续提高分辨率没有意义。
5. **离线建表是可行的**:ExoPlaSim "一年气候 < 1 分钟墙钟"(T21/T42,5-10 层),意味着可跑数千个参数组合。但**跑得快不等于分辨率够**。
6. **降水/水循环不可信**:ClimateBench 上 pr/pr90 的 Total NRMSE 比 tas 大一个量级(3.17 / 4.34 vs 0.33)。若模组需要"降雨",风险显著高于"温度/风"。

---

## 6. 未找到 / 未确认

**未找到(反复检索后确实不存在或不存在的证据)**
1. **未找到**任何"把 GCM 降为查找表并用于游戏 / 程序化世界生成"的项目或论文。
2. **未找到**任何"对 3D 系外行星 GCM 的**完整输出场**做 NN 模拟"的论文 — **唯一例外是 ThousandWorlds**(arXiv:2606.18338)。其余都是替换 GCM 内部模块(辐射 / 物理参数化)。
3. **未找到**"PICASO 神经网络模拟器"(PICASO README 中无 NN 相关内容)。
4. **未找到**"GCM lookup table"作为一个成熟术语/范式的文献。
5. **未找到** Rasp et al. 2018 的具体加速比数字(摘要未给)。
6. **未找到** ClimateBench 的绝对 RMSE(K / mm per day)与训练耗时。
7. **未找到** ThousandWorlds 各基线的数值 RMSE / ACC / RAMSE 表(HTML 100k 字符截断)。
8. **未找到** Chelton et al. 1998 摘要中的分区域 Rossby 半径数值表。
9. **未找到** Capet et al. 2004 中 drop-off 过渡尺度的**具体公里数**(只读到"过渡尺度不确定性强烈影响沿岸流与温度"以及"27/9/3 km 不收敛")。
10. **未找到** Minecraft 侧 "Climate Control / Geographicraft" 的可用源码或官方文档。
11. **未找到** Canary / Humboldt 两大 EBUS 的独立分辨率阈值论文(本轮只有 Benguela 的摘要级证据 + California 的风场证据)。

**未确认(页面抓取失败 / 被拦截 / 仅 snippet)**
12. **petitRADTRANS**:GitLab 页面未抓取成功 → 是否有 NN 模拟器**未确认**。
13. **CLISEMv1.0**:仅 PDF 链接 → 内容**未确认**。
14. **TorchClim v1.0**(GMD 17, 5459-5475, 2024):卷期页码已确认,**摘要与参数未确认**。
15. **Small et al. 2023**(Impacts of Model Horizontal Resolution on Mean Sea Surface Temperature Biases in the Community Earth System Model,DOI 10.1029/2022JC019065):**摘要未抓取成功**(peeref 返回 302 无 Location)。
16. **Franzke/Majda 低阶随机模式约化**、**Selten/Achatz 原始方程低阶模式(JAS 60(3))**、**Mars 大气 POD-Galerkin(Reading 119618)**:目标页抓取失败或 CloudFront 403 → **内容未确认**。
17. **NorESM2-LM 的 1.9 度 x 2.5 度(96x144)/32 层**:仅由 AGU 页面 snippet 支撑,**正文未逐一确认**。
18. **ExoPlaSim 的代码仓 URL 与许可证**:**未确认**。
19. **ThousandWorlds 的数据与代码仓库确切 URL**:论文中以 "this https URL" 遮蔽,**未确认**。
20. **Neural Network Accelerated Retrieval of Clear Brown Dwarf Atmospheres**、**GUIBRUSH retrieval 加速**、**Rooney & Batalha 球谐辐射传输**:仅见检索条目,**正文未确认**。
21. **CU 的 coastal low-level jet 综述与具体宽度数字**:只找到 science.gov 主题页与 EGU2016 摘要条目,**正文未抓取 → 未确认**。
22. **被 Cloudflare / CloudFront 拦截、内容不可读的站点**:agupubs.onlinelibrary.wiley.com(403 "Just a moment")、journals.ametsoc.org(403 CloudFront)、sciencedirect.com(403)、scholarsarchive.oregonstate.edu(bot check)、katalog.slub-dresden.de(bot check)、semanticscholar.org/reader(需 JS)。凡依赖这些站点的结论均已降级为"未确认"。
23. **PDF 类资源一律不可抓取**(工具返回 unsupported content type "application/pdf"):ClimateBench 的 UEA AAM 全文、MOM6/Griffies JAMES 2025b、CLISEM 预印本、EGU2016 摘要集等。

---

*本报告全部事实基于本会话内实际 web_fetch / web_search 抓取的内容。凡未经抓取验证者,一律以"未确认 / 未找到"标注,未做任何推断性补全。*
