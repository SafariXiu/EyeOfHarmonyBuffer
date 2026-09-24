# 物理量口径登记表（CALIBERS）
| 171 | **★ §7147 新增开关 `PrecipField.SHALLOW_FLOOR_ZI_LIMIT`（默认 false）**：给浅对流地板加「下沉封顶」`zCt := min(Z_CT_COND, z_i)`，`z_i = w_*·cbrt(A·Θ0/(g·D·Γ_eff))`。出处：Eq.131/136（**Lilly 1968, QJRMS 94, 292**），经 CSU PBL 讲义 p.137-140 转述。**状态：探针已否证**（§7149 P1 否证：撒哈拉 `divU<0` ⇒ 永不激活；§7150 Q1：只在海洋过冲到 0.80×）⇒ **保留为对照臂，不进生产**。中性验收：指纹变、**40 个 GATE token 全不变**。 |
| 170 | **★ §7147 新增常数 `PrecipField.ENTRAIN_A = 0.2`**：夹卷常数 `A`（`w_e/w_* = A·Ri_*^(-1)`），出处 **Lilly (1968), QJRMS 94, 292-309**（`Ri_*` 定义见 Deardorff 1980, BLM 18, 495-527）。⚠ **它是新引入的自由参数，自设禁令：不得靠调它转绿**；若必须改 `A` 才能过门，视为失败（与 §686 判死的「1000 m 单点巧合」同类）。只在 `SHALLOW_FLOOR_ZI_LIMIT=true` 时被读。 |
| 139 | **★★★★★★ 第 (3) 项残差的根因：`curv(W)` 是【相消主导】的二阶差分，符号不是物理的（§485，P691）**。先确认生产路径 = **`modelShapeVarFull`**（`EDDY_GATE_MODE=2, VAR=0, GRAD=0, PLACEMENT_FROM_OBS=false`），**不是**「直接读观测表」那条（否则 §484 会是同义反复）。分解（`eddyMfc` 符号 = `curv(W)` 符号，因 `K`、`gate` ≥ 0）：**反号 JJA 7/13、DJF 1/13**。★ 根因：`curv = Δ²W/dy²`，`dy² = 3.087e11 m²` ⇒ **`Δ²W/W ~ 0.01%~1%`** ⇒ 符号由两个 ~20~31 的数的千分之一到百分之一的差决定 ⇒ **相邻纬度反复翻转（JJA: +,−,−,−,+,+,+,+,+,+,+,−,−）正是相消主导的签名** | **结论性**。这是 §426「观测是单极型、模型是二阶导多极型」的**定量化**。**⇒ 调 `stormGate`/`K` 不可能修好（它们都 ≥ 0，翻不了号）；要修只能换闭合形式（二阶导 → 单极/一阶），那是一次闭合重做** |
| 140 | **★★★★ 目标第 (3) 项判定（§485 五）**：**量级已达标**（§484 DJF 30~50N = 1.08）**+ 形状不可修**（除非换闭合形式，§485）。⇒ 建议登记为「量级达标；形状问题需要闭合重做」，**与那个大分叉一起裁决** | **待裁决** |
| 141 | **★★★★★★ 目标第 (4) 项后半完成：P600 指名站点【海岸门】接入验收套件，首次读数 FAIL（§486，P692）**。8 个指名站点（4 海岸沙漠 vs 4 同纬度暖流区）；物理要求 **沙漠组 P < 暖流组 P**。实测：**沙漠组 1.596 / 暖流组 0.587 ⇒ GATE_COASTAL_RATIO = 2.719（应为 <1）、VERDICT = FAIL**；`LANDFRAC = 6/8`（2 站在本世界是海）。★ 失败形状：**两组都大面积是 0，唯一的「湿站」落在沙漠组**（西撒哈拉单站 **6.386**，另三站 0.000）⇒ **极端湿点诊断性地出现在海岸沙漠站上**，正是 §399/§400「近岸 κ 低 ⇒ 类海洋 ⇒ 必然湿」的现场 | **已接入**（`rerun_acceptance.ps1` 门数 **18 → 19**）。与 §485 是**两个独立残差**：§485 是涡动闭合相消（中纬），本门是**近岸 κ**（低纬海岸） |
| 142 | **★★★★★★ 用户裁决「按最物理最正确」⇒ 缺陷不再留默认 OFF（§487）**：`Atmosphere.PZREF_VZ_MODE` **0 → 1**（修赤道 divU 大 **14 倍**、25~30N 符号翻，§422/§451；**活的**，`wind()` 被一切消费）；`StationaryWave.QRAD_ASR_MINUS_OLR` **false → true**（修 `absSolar` 372~404 W/m² 漏项 + `qRad` 不是 OLR，§454/§455；防御性）。**`P293 +9.6531e-07` 仍绿 ✓**（两者都不碰 `seasonalAnomaly`） | **已落地**。⚠ **默认已变 ⇒ 全部验收基线需要重捕**。★ **两道门的量级都朝正确方向动了**：相位门亚洲 2.972→**3.508**（obs 7.667）、澳洲 3.754→**4.302**（obs 6.371）、非洲南部 3.560→**4.293**（obs 6.609），`HOLDOUT` 保持 2/2；海岸门 **2.719 → 2.002**（暖流组 0.587→**0.802**，东澳 0.000→**0.832**）。两道仍 FAIL，但**方向全部正确** |
| 143 | **★★★★★ 「缺陷修复 ≠ 新机制」（§487 四）**：本步与 §468/§469 那条「每个**新机制**都中性或负」**不矛盾** —— 前者把错的变对，后者在错的基态上加东西 | **结论性**：后续应按此区分两类工作；「缺什么做什么」优先做**缺陷与缺失的物理**，而不是再加机制 |
| 144 | **★★★★★★ 海岸门有【一半是我的仪器缺陷】：混合了两个半球的季节（§488，P692 修正 + P693）**。P692 v1（承 P600）用**单一 JJA** 评全部 8 站 ⇒ 南半球四站被拿**冬季**当夏季比。修法：每站用**它自己的夏季**（NH: JJA；SH: DJF）。实测：沙漠组 1.605→**3.591**、暖流组 0.802→**2.331**、**`GATE_COASTAL_RATIO` 2.719 → 2.002 → 1.541**，仍 FAIL | **已修**。⚠ 判据已变 ⇒ P692 基线要按新判据重捕 |
| 145 | **★★★★★ 顺带否证「近岸 κ ⇒ 类海洋 ⇒ 必然湿」（§399/§400）（§488 三）**：同一季节（JJA）三个近岸站的 **κ 都在 0.48~0.60**，而 **P = 5.780 / 0.000 / 0.000**；真正的区别在 **`tSl`**（302.11 / 294.72 / 291.79 K）⇒ `q ∝ qSat(tSl)` 只差 **1.88 倍**，P 却差无穷 ⇒ **剩下的差别全在 `wEff`（季节相位）** | **已否证**（作为这里的操作机制） |
| 146 | **★★★★★ 修好季节后真正的缺陷更尖锐（§488 四）**：**纳米布（23S/15E）7.370 vs 阿塔卡马（23S/70W）0.575** —— **同纬度、同季节、同为东岸上升流**，模型给它们差 **13 倍**；沙漠组还是**双峰**的（6.42/7.37 vs 0.575/0.000） | **未查**：差异来自**该世界自己的陆海几何**（15E vs 70W 在模型世界里布局不同，§476 纪律），不是气候机制 ⇒ **下一步** |
| 147 | **★★★★★★ 两个同纬度同季节的东岸上升流沙漠差 15.5 倍，主因是 `divU` 符号相反（§489，P694）**。纳米布 23S/15E **P=6.482** vs 阿塔卡马 23S/70W **P=0.417**（SH 夏 DJF）。**仪器自检通过**：`wBase` 两站**逐位相同**（+2.3000e−03，同纬度）。**主因**：`wLocal` **+1.9911e−03 vs −2.0000e−03**，即 `divU` **−6.1019e−06（辐合）vs +1.7420e−05（辐散）** ⇒ **两个物理同类的站在模型里散度符号相反**，而 `divU` 来自 `cellPressure`（§479 定性的「陆海开关」）⇒ **§479 第一次在同纬度同季节的对照上被验证**。**次因**：`sstAnom` **+0.000 vs −6.490 K**，经 `(1−κ)`（κ≈0.54 ⇒ 权重 0.46）把阿塔卡马 `tSl` 拉低 3.57 K ⇒ q 只差 1.09× | **已定位**。⚠ 反方向小项：`depl` 0.8792 vs 1.0000（有损耗的那站反而更湿，已记账）。**⇒ 与 §473/§487 合流：唯一方向被证实的是【输送】（+63%），而 `divU` 缺的是【经度方向的信息】；乙₁ 同时补这两者** |
| 148 | **★★★★★★ 平流采样密度【完全饱和】⇒ §473「1.91 是下界」被否证（§490，P695）**。`ADVB_MAX_EVAL` 扫描：**2/4/8/16/32 点全部给 JJA 比 = 1.80**（亚洲 3.013、撒哈拉 1.678），DJF 比 0.41 也不变；代价只涨 13%（1.06→1.20 ms/点）。**⇒ 采样密度不是限制因素** | **已否证**。★ **证明了的**：要提高对比只能改变【路径】或【场结构】—— 逐点法每条流线**独立、无横向混合**，而 2-D 的 `div(qV) = E − P` 允许**相邻流线互相补给**（辐合聚集水汽）。**⇒ 「为什么需要 2-D」从猜测变成证据**。⚠ 记账：修正默认后无拟合最好成绩是 **1.80**（§473 的 1.91 是旧默认）|
| 149 | **★★★★★★ 自我抓到单位错误：§473 的「平流积分」退化成【局地平衡】（§491）**。`moistureAdvected` 里 `a = lam*ds/sp` 应当是**无量纲**的，但 `lam` 的第一项 `86400*chv ≈ 3266`（量纲不是 1/s），`ds/sp ≈ 1.5e4 s` ⇒ **`a ≈ 4.9e7`，每一步都被 `a>40` 的截断吃掉** ⇒ `exp(-40) ≈ 4e-18` ⇒ **每步直接跳到局地平衡，海边界 `q_sea` 完全不起作用** | **已记账**。⚠ **撤销**§473 的「输送被证实 +63%」；§473 的数值（1.91/1.80）保留为**局地平衡的另一种实现**，**不作为输送证据**。★ §490 的结论**更强了**：既然积分本身就是局地平衡，它对分辨率**必然**不敏感 ⇒ 饱和不是「信息榨干」 |
| 150 | **★★★ 乙₁ 的依据反而更干净（§491 三）**：真正的 `div(qV)` 求解**从未被尝试过**（现实现的输送项因单位错误而失效）。仍然成立的依据：① §470/§472 局地闭合给不出对比且 `M` 符号由自由参数决定；② §490 真正带输送的积分**必须对分辨率敏感**；③ §489 `divU` 在两个同类站符号相反 ⇒ 散度场需要经度信息 | **待做**：**先写「只做量纲与量级自检」的探针**（不接生产），在同一具体点上算 `E`、`P`、`W·divV` 三项并互相核对（`P` 必须复现模型 3 mm/day 量级），**量级矛盾解决后才写 2-D 求解器** |
| 151 | **★★★★★★ 单位修好，判据通过：平流积分现在【依赖分辨率】了（§492）**。修法：`lam`/`E0` 必须整体除以 `rho_a*H_MOIST (=2450)`（flux 形式→1/day），`a` 再除以 86400。量级自检：`86400*chv/(rho_a*H)=1.33 /day`（**18 h** ✓ 物理）、`a = 1.33×1.5e5/(10×86400) = 0.23`（修前是 `4.9e7`）。实测**分辨率敏感**：2/4/8/16/32 点 ⇒ 比 **1.73 / 1.63 / 1.53 / 1.51 / 1.51**（修前全部 1.80 不敏感） | **已修**。★ §490 的判据「真带输送的积分必须对分辨率敏感」**通过** ⇒ 这第一次是真的平流积分 |
| 152 | **★★★★★ 真平流的数值是【负面】的：1.17 → 1.51（+29%，不是 63%）（§492 三）**：局地平衡 §472 = **1.17**；真平流 §492 = **1.51**；退化的「平流」§473/§490 = 1.91/1.80（**不能作基线**）。方向：亚洲更干 3.013→2.744、撒哈拉更湿 1.678→1.814 ⇒ **平流把对比拉低了** | **已记账**：撤销 §473 的「+63%」 |
| 153 | **★★★★★★ 乙₁ 的核心仍未做，而且现在只剩一行（§492 四）**：即使单位修好，用的仍是 `Math.abs(-H_BL*divU)`（**对称通风**），不是**【带符号】的 `q·div V`**。物理上辐合应让 `q` **增长**（横向补给），辐散应让它衰减 —— 这正是 §490 证明「逐点法结构上缺失」的那一项。**修法**：去掉 `Math.abs` 用带符号 `divV` + 加 `q ≤ qSat(Ts)·β` 饱和上限（推导得出） | **下一步**（一行改动 + 饱和上限） |
| 154 | **★★★★★★★ 乙₁ 的核心项实施完成，结果是【更差】：1.51 → 1.27（§493，P695）**。实现：散度项改**带符号**（`lam = lamFlux + 86400*divU`，辐合 ⇒ `lam` 变小 ⇒ `q*` 变大）+ 饱和上限 `qCap = qSat(tS)*beta` + `advCapped` 诊断。实测 JJA 比：2 点 **1.47** → 4 点 1.36 → 8 点 1.28 → 16/32 点 **1.27**（仍对分辨率敏感 ⇒ 实现是真的）。**撒哈拉 1.814 → 2.384（+31%）、亚洲 2.744 → 3.017（+10%）** | **已实现**。★ **为什么更差**：横向补给的物理**是对的**（辐合聚集水汽），**但模型的 `divU` 在沙漠上是辐合**（§478 撒哈拉 −2.50e−06、§489 纳米布 −6.10e−06、§451、§437「陆海开关」）⇒ **正确地实现一个机制、作用在反号的场上，必然放大错的方向** |
| 155 | **★★★★★★ 六条路的最终总表（每条都有实测）（§493 四）**：① S2 定常波 **0.233**（§449）；② 植被 `D_B` **0.40**（§462）；③ 植被 `RS_BARE` **唯一不动点**（§467）；④ 局地收支 **1.17**（§472）；⑤ 真平流·对称通风 **1.51**（§492）；⑥ **真平流·带符号散度 1.27（更差）**（§493）；观测 **72.76** | **失败理由唯一且可证明**：不是缺公式（§479）、不是缺输送（§493）、不是缺记忆（§459/§467/§462），**是 `divU` 这个场本身反了符号**（沙漠辐合、海洋辐散），而所有能区分沙漠与季风区的候选量（高程/β/温度）符号都反或用不了（§438/§458/§481） |
| 156 | **★★★★★★ 请裁决（§493 五）**：**丙**（承认 72.76 在现架构不可达，目标改分层判据 + 落地已有成果）；**乙₂**（给散度场引入新的区域信息 —— 真架构加法，但需 `F_net` 可靠 + 真垂直剖面，而 §482 已证两层口径下 `M` 不可用）；**丁**（把已修好的缺陷逐项落地） | **建议：丙 + 丁**。理由：乙₂ 需要两个前置，而 §482 已证其一不可用；在拿到垂直维度前再试散度侧是**第 7 次同型否证** |
| 157 | **★★★★★★ 涡动符号不稳是【求导模板】造成的，而那个步长一直是 `final`（§494，P697）**。工艺缺陷：`EDDY_DPHI_DEG` 是 `static final` ⇒ **从未被 A/B 过**，而它决定 `curv(W)`（相消主导，§485）的符号。新增 `EDDY_DPHI_DEG_V`/`dphiDeg()` 后扫描（25~65N，17 点，与观测符号一致率）：**2.5° 26/34、5°（现状）25/34、7.5° 26/34、10° 29/34、15° 30/34**。JJA：9/17 → **15/17** | **已实现**（默认 -1 ⇒ 逐位不变）。★ **证实了源码第 1343 行 P632 的注释**：「`EDDY_DPHI_DEG = 5` 恰好落在节点上 ⇒ `curv(W)` 拾取的全是折角伪影」—— 那句话一直只是注释，因为步长是 `final`。⚠ 不是完整解：DJF 在 15° 反而 16/17→15/17；相消本质不消失，长期解仍是 §426 的换形式 |
| 158 | **★★★★★★★ 查文献找到「缺什么」：模型没有【对流闭合】（§495）**。Raymond《Convection and the Environment》Ch.6 逐字：`M_u = gamma*(s_bl - s*_th)`（6.9）—— **上升质量通量正比于【边界层湿熵】减【throttling 层的饱和湿熵】**，带**硬零阈值**（`s_bl ≤ s*_th ⇒ M_u = 0`）。模型现在 `P = EPS_C·ρ·q·wEff/ρ_w` 是**大尺度凝结式、无任何对流触发** ⇒ 只要 `wEff>0` 就下雨 ⇒ **这就是 §493「正确地实现辐合却把沙漠弄得更湿」的根因**。可算量：`h_bl = cp·T_bl + L·q_bl`、`h*_th = cp·T_th + g·z_th + L·qSat(T_th)`、`T_th = T_bl − GAMMA·z_th` ⇒ `P ∝ max(0, h_bl − h*_th)` | **★ 这是六条路之后第一个【不依赖 divU 符号】、且有文献逐字依据的机制。** 试算（z_th=1000 m）：基线亚洲 **+5,928**、撒哈拉 **+9,368**（**撒哈拉更大 ⇒ 复现「沙漠更湿」的错误排序 ✓ 判据有判别力**）；§448 水汽源下两盒都变负（−15,926 / −20,850）⇒ **水汽源在 BLQ 口径下是过度去湿** |
| 159 | **★★★★ 工艺缺陷：`EDDY_DPHI_DEG` 是 `final` ⇒ 求导步长从未被 A/B 过（§494）**。新增 `EDDY_DPHI_DEG_V`/`dphiDeg()` 后可扫：符号一致率 **2.5° 26/34、5° 25/34、7.5° 26/34、10° 29/34、15° 30/34**（25~65N，JJA+DJF） | **已修**（默认 -1 ⇒ 逐位不变，P293 复验绿） |
| 160 | **★★★★★★ BLQ 判据实施并实测（§496，P698）**：新增 `PrecipField.BLQ_GATE`（默认 false ⇒ 逐位不变，`P293 +9.6531e-07` 绿）。实测：**判据是活的**（BLQ 开 ⇒ 拦掉 **39.9%** 的点）；**方向对**（基线 dh 亚洲 +4,141 / **撒哈拉 +2,030，撒哈拉更小**）；**但不够翻相位**（撒哈拉 3.300 → 2.178，仍 > DJF 0.990 ⇒ `GATE_ALL` 仍 **3/4**）；**且与水汽源严重冲突**（加水汽源后 dh 两盒都负 −18,294 / −28,126 ⇒ `blqBlocked` 跳到 **81.3%**，亚洲 JJA 塌到 0.347 ⇒ 相位反相） | **已实现**。★ §495 的试算预测被实测复现 ✓。**82% 的点被判「不该对流」是太多了** |
| 161 | **★★★★★★ 七条路的统一诊断：模型的【热带状态量本身信号太弱】（§496 三/四）**：`dh = cp·ΔT + g·z_th − L·(q*_th − q_bl)` 在热带把三项抵消到 **dh/h ~ 0.6%~1.2%**；而 §485 的 `curv(W)` 是 **Δ²W/W ~ 0.01%~1%**。**两者都是「大数相减得小数」⇒ 符号由 1% 的余量决定 ⇒ 任何判据都不稳** | **结论性**：七条路都试过（S2 0.233 / D_B 0.40 / RS_BARE 唯一不动点 / 局地 1.17 / 平流对称 1.51 / 平流带符号 1.27 / BLQ 拦 39.9%），都有文献或推导依据，**不是缺机制，是状态量信号太弱** |
| 162 | **★★★★★★★ 全状态量判别力总表：模型里只有一个 O(1) 判别量，而它已被用掉（§497，P699）**。度量 `contrast=(亚−撒)/(abs(亚)+abs(撒))`，JJA：kappa **0.022**、tSl **0.0016**、tSfc −0.0085、beta 0、q −0.037、depl −0.069、divU **0.197**、wEff **0.206**、wBase 0.498、elev **0.848**、P **0.031**、dh 0.342、eddyMfc 0.143、**u **1.000**（亚洲 +4.586 西风 / 撒哈拉 −1.938 东风，符号相反）**。⚠ `sstAnom` 的 1.0000 是**分母幻觉**（两边都近 0，真实幅度仅 0.19 K） | **结论性**：**唯一「强」且方向正确的经向判别量是【风向 `u`】—— 而 §444 的 `upwindSea` 用的正是它** ⇒ 解释了为什么只有它有效（3.54）。**其它六条路的量 contrast 都在 0.02~0.34 ⇒ 结构上给不出 20 倍对比** |
| 163 | **★★★★★★ 剩下的两个 O(1) 候选，都前置到同一个东西（§497 四/五）**：① **`F_net`**（§456 实测符号翻转 +7.20/−18.01 W/m² ⇒ O(1) ✓）但需要 `M`，而 §482 证两层口径下 `M` 符号由 `zFT` 旋钮控制、66% 点 `M<0`；② **`div(qV)` 的符号**，但模型的 `divU` 同号（§489），符号由 `cellPressure` 的静态 `(KAPPA_MEAN−κ)` 决定（§479）。**两者都前置到「良定义的 `M` / 区域散度场」⇒ 需要真垂直剖面** | **可证明封闭**：在拿到垂直维度之前，剩下的路是封闭的 |
| 164 | **★★★★★★★ §498 真抬升算法（`ParcelLift.java`，零自由参数）**。把 `M` 里硬编码的 `zFT = M_FT_FRAC*H_EFF = 6000 m` 换成【气块抬升算出的中性浮力层 z_LNB】。**纯静态、不接线、无开关 ⇒ 对 P293 结构上为零**。自检（对文献，与模型无关）：`Γ_d = 9.767928e-3` ✓ | 多元大气指数 **5.2561** vs 标准大气 **5.2559** ✓ | `Γ_s(300K,1bar) = 3.7113e-3`、`Γ_s(273.15K,1bar) = 6.5560e-3`、`Γ_s(210K,200hPa)/Γ_d = 0.990` ✓ | **Bolton1980 vs Davies-Jones1983 两条独立 LCL 公式在 0.0003~0.033 K 内吻合**（5 个情形） |
| 165 | **★★★★★★ §498 旧 M 反号 → 新 M 正号（实测的缺陷修复）**。旧 `M ∝ dh(zFT=6000 硬编码)`：亚 `+2.070008e+04` / 撒 `+2.382371e+04`，**contrast = −0.0702（方向反）**；新 `M = h_BL − h_env(z_LNB 算出)`：亚 `+6.788737e+03` / 撒 `+4.125385e+03`，**contrast = +0.2440（方向对）**（n=63/36）；编码B（无对流记 M=0）`+0.1289` | 命名情形 4/5 正中：热带陆地 305K/16g/kg → CAPE **1129** J/kg、LNB **11 100** m、CIN **−109.9**；**副热带沙漠 310K/5g/kg → 无 LFC/LNB/CAPE** ✓；极地无对流 ✓；中纬 288K/9g/kg → CAPE 210.6 ✓ | **限制已记账**：判别力只到「中」，且「有没有对流」的方向仍反（亚洲 21.3% 无对流 vs 撒哈拉 0.0%，由错误的 q 主导） |
| 166 | **★★★★★★★ §498 根因定量：模型【没有对流层顶】**。`GAMMA = 6.5e-3` 是【中纬标准大气 ISA（288.15 K 定义）】的递减率，套到 300~310 K 热带地面 ⇒ T(12 km) = **217 / 222 / 227 K**，对观测热带 **200 K** 偏暖 **+17 / +22 / +27 K**；模型 300 K 地面的「顶」= `tS/GAMMA` = **46 154 m**（观测对流层顶 16 000~17 000 m） | **这解释了七条路在同一个地方失败**：所有需要【上层温度】的机制（`M`、`F_net/M`、`eddyMfc`、`upwindSea` 廓线）都在跟同一个 17~27 K 偏置作战 ⇒ 这就是 §497「只有一个 O(1) 判别量」的力学来源 | **是「甲（真垂直维度）」的实测依据** |
| 167 | **§498 仪器错误（第 2 次被交叉校验救回）：Bolton(1980) Eq.(15) 的常数 56 在【开尔文】不在摄氏度**。摄氏度版：300K/20g/kg → 298.344（DJ 298.036，差 0.31 K）、310K/5g/kg → 268.318（DJ 270.480，差 2.16 K）、**295K/2g/kg → NaN**；开尔文版三者差值降到 **0.008 / 0.0003 / 0.006 K** | **纪律**：凡能用两条独立公式算同一个量的地方，两条都要算 |
| 168 | **§498 文档缺陷（本轮发现并修复 2 处）**。① 文件末尾有一个孤立且栅栏畸形的碎片（3 条栅栏），其正文「⚠ §470 三 的算术已改正…」在 §470 里**从未出现**（全局唯一匹配就在孤儿块里）；但 §470 自己的算术（33812-33815 行）**本来就是对的** ⇒ 判定为已废弃的重复告示，已删除并归档于 §498 八 | ② 删掉那 3 条后全文栅栏数 1044(偶) → 1057(**奇**)，**暴露出 §471「五 纪律」的代码块从未闭合**：它开着，紧接着就是 `## §472` 标题 ⇒ §472 的标题与 §一/§二 起头被吞进代码块，而 §472 里的 ` ```java ` 反而变成了【闭合栅栏】⇒ 那段 Java 被当正文渲染。已在 33929 前补上缺失的闭合栅栏 | **复核：`fences=1058 balanced=True eofState=0 headings=204 violations=0`** |
| 169 | **§498 探针缺陷（我自己记账）**：P700 首版 C 段对 `r.cape` / `r.mParcel` **没做 NaN 保护**，一个无对流的亚洲点就把整列盒平均污染成 NaN（`CAPE亚 = NaN`）。已修；**该 NaN 是探针 bug，不是模型结果** | 同时记账：C 段把「无对流」编码为 0，而 C2 排除 NaN 点 ⇒ 同一判别力有 `+0.1289` 与 `+0.2440` 两个读数，**两个都已记录**，物理上「无对流」不等于「M=0」 |

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
| `ZonalTables.pRef` / `carrier` | 纬向平均海平面气压 | Pa | \|lat\|；10° 分段线性 | 观测 | `cellPressure`（★ `seaLevelPressure` 已于 §7176 删除：零调用者） |
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
| `Atmosphere.seasonalAnomaly` | 季节温度异常（**逐点口径**） | K | **单相位 θ**；A(0)=0 硬约束；南半球相移 π；振幅 = aSea/aLand 按**局部 κ** 混合 | 本类 | `surfaceTemp`、`SimTerrain.warmest/coldestMonth`、`SimClimate.solveNode` 的 `tSl` |
| `Atmosphere.seasonalAnomalyZonal` | 季节温度异常（**纬向平均口径**） | K | 同相位约定，但振幅读 **`ZonalTables.aZonalMean`**（表 `A_ZM_K`）—— 与逐点**分表**（§231.4 步骤 0，因为两者口径不同） | 本类 | `PrecipField.zonalSlTemp`（涡动链） |
| `ZonalTables.A_ZM_K` / `aZonalMean` | 纬向平均季节振幅 | K | 10 度表、\|lat\| 对称；播种值 = 旧口径在 κ=⟨κ⟩ 处的混合值（纯拆分）；**决定 2 会把它换成推导机制** | `gen_a_zm.py` | `Atmosphere.seasonalAnomalyZonal` |
| `Atmosphere.surfaceTemp` | 地表温度 | K | **单相位 θ**；**含** `−Γ·h·κ` | 本类 | 方块层、`SimClimate.solveNode`、探针 |
| `Atmosphere.pressureAnomaly` | 地面气压异常 p' | Pa | 代数式；**海陆年均对比被解析抵消** | 本类 | `windAt`（差分）、探针 |
| `Atmosphere.sstAnom` / `sstAnom` | 注入后的 SST 距平 参数为 (x,z,theta) 参数为 (x,z,seed,theta)| **K** | 无提供者 ⇒ **恒 0**（⚠ §420：所有地球掩膜探针都必须先 `OceanField.install`，否则读到的是 0 而不是缺测） | `SstProvider` | `surfaceTemp`、`annualSeaLevelTemp` 的调用方、`PrecipField`、`StationaryWave` |
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
| `ZonalTables.EDDY_MFC_OBS_MONTH` / `eddyMfcObsMonth` | 观测涡动 MFC 的**逐月**纬度剖面 | 无量纲 | 19 纬 x 12 月，**只取北半球**（逐月对称化会把两半球相反季节平均掉），45~60 度年均值为 1，保留符号；南半球由消费者做半周期相位平移。⚠ 目前**尚未被生产引用** | `gen_eddy_obs_month.py` | 无（待接） |
| `PrecipField.EDDY_PLACEMENT_FROM_OBS` | 开关 | `bool` | **A′**：涡动项的纬度放置/符号/季节迁移用**逐月**观测剖面（true）还是模型 `curv(W)`（false）。⚠ 当前 **true 已接线**（B2.a/B2.b 达标，§239），但量级标定未过关；进 `configStamp` | | |
| `PrecipField.EDDY_MFC_REF` | A-ii 的基准幅值 | kg/(m²·s) | 标定值 **3.40e-5**（命中 45~55 夏 = 2.565）；开关 false 时无作用；进 `configStamp` | 标定 | `eddyMfc` |
| `PrecipField.ZONAL_PROFILE_OVERRIDE` | **诊断钩子** | 接口/null | **D79 判读专用，生产恒为 null**（默认 null ⇒ 逐位不变，P495 A 段已证）；进 `configStamp` | 探针 | `zonalSlTemp` |
| `PrecipField.SPLIT_ASCENT` | 开关 | `bool` | **候选 A（§244.5）**：`wEff` 的两项分别取正再相加（true）还是先加后取正（false）。**实测否决**（§245.5：判据①②③全挂）⇒ **默认 false**；进 `configStamp` | | `wEff` |
| `PrecipField.Q_AT_SURFACE_TEMP` | 开关 | `bool` | **候选 R-2（§249）**：算 q 前先减 `GAMMA*max(0,elev)*kappa`（局地真实地表温度）还是用海平面等效温度（false）。**对海洋逐位中性**（P501 自检[4] 残差 0.00e+00）；单独打开会让陆地降水更干 ⇒ **默认 false**，须与供水项一起评估；进 `configStamp` | | `mmPerDay` |
| `PrecipField.SHALLOW_FLOOR` | 开关 | `bool` | **候选 S-1（§251/§252）**：`P = max(P_原有, ALPHA_SH*E_sh/rho_w)`。**默认 true（已落地）**：判据① 0.0%→97.9%、⑤ 0.00→1.12、GPCP 赤道冬 −42.4%→−11.3%；进 `configStamp` | | `mmPerDay` |
| `PrecipField.ALPHA_SH` | 浅对流效率 | 无量纲 | `0.40`（判据①仍通过 97.9% 的**最小值**，对热带冬季过冲最小）；仅 `SHALLOW_FLOOR=true` 时有作用；进 `configStamp` | 标定（判据①观测锚） | `mmPerDay` |
| `PrecipField.V_GUST` | 地表通量风速下限 | m/s | `4.0`（L-3：QTCM `VVsmin`）。没有下限，季风反转点通量归零；仅浅对流地板用；进 `configStamp` | L-3 | `mmPerDay` |
| `PrecipField.moisture` | 近地比湿 | kg/kg | 0.8·q_sat(T)·exp(−h/H_MOIST·κ) | 本类 | `mmPerDay`、`columnMoisture` |
| `PrecipField.columnWater` | 气柱水汽 | **kg/m²** | = moisture·ρ_air·H_MOIST（D48 修复，原先少乘 ρ） | 本类 | `eddyWEquivalent` |
| `PrecipField.eddyMfc` | 涡动水汽通量辐合 | kg/(m²·s) | **二阶导**：`(W(+5°)−2W(0)+W(−5°))/dy²`，dy 为**弧度** | 本类 | `mmPerDay` |
| `PrecipField.eddyWEquivalent` | 涡动的 w 当量 | m/s | MFC/ρ_w | 本类 | 探针（分解类：**必须连带自检 d²W/dφ²**，见 P493） |
| `PrecipField.stormGate` | 风暴轴西风门 | \[0,1\] | smoothstep(u_zm/U0_STORM)；**单相位 θ** | 本类 | `eddyMfc` |
| `ZonalTables.EDDY_MFC_OBS` / `eddyMfcObs` | 观测的涡动 MFC 纬度剖面 | 无量纲 | 19 纬 5 度表，45~60 度均值为 1，**保留符号**（负 = 辐散）；NCEP 日资料 vwnd+shum 850/500 hPa、1460 天。⚠ 目前**尚未被生产引用**（A-ii 尚未接线） | `gen_eddy_obs.py` | 无（待接） |
| `PrecipField.EDDY_DPHI_DEG` | **求导步长** | **deg** | **5.0**；进 `curv` 的分母（用弧度） ⚠ 单位陷阱：E64 就是栽在 deg/rad | | |
| `PrecipField.UPWIND_STEP` | 上风取样距离 | m | 150,000 | | |
| `PrecipField.wEff` | 有效上升速度 | m/s | = w_zm(φ−Δ) + clamp(−H_bl·divU) | 本类 | `mmPerDay` |
| `PrecipField.Q_FROM_SOURCE` | 开关 | `bool` | **§444**：算 q 前先沿风逆推到海面、用**上游海温**（true）还是用**本地温度**（false，逐位不变）。默认 false；进 `configStamp`（在 `Q_FROM_SOURCE` 块内） | | `mmPerDay`、`StationaryWave.netColumnHeating` |
| `PrecipField.upwindSea` | 上游海面 | `{fetch(m), SST(K)}` | **§444**：沿风**多步**逆推（`UPWIND_STEP`）到第一个海点；`SOURCE_FETCH_MAX` 内找不到海返回 `{-1, NaN}`。⚠ 与 `upwindElev`（只走一步）不是同一个仪器 | 本类 | `moistureFromSource`、探针 |
| `PrecipField.SOURCE_FETCH_L` | 水汽 e 折输送尺度 | m | **1.5e6**（§444 P656 扫过 1e6/2e6/3e6）；进 `configStamp` | 标定 | `moistureFromSource` |
| `PrecipField.SOURCE_SEASONAL_T` | 开关 | `bool` | **§448**：`upwindSea` 返回的海温要不要 `+ seasonalAnomaly(lat,0,theta)`。默认 **false**（不执行加法）；只作用于 `Q_FROM_SOURCE=true` 的支路；进 `configStamp`（在 `Q_FROM_SOURCE` 块内） | | `upwindSea` |
| `PrecipField.moistureFromSource` | 近地比湿（内陆口径） | kg/kg | **§444/§448**：`rhEff(β)·q_sat(T_上游海面)·exp(−fetch/L)·depletion(elev,κ)`；`κ ≤ 0.5`（海洋）**不走这一支**；逆推不到海时按 `SOURCE_FETCH_MAX` 处理（**不许回落旧口径**，§444 第 40 条） | 本类 | `mmPerDay` |

---

## 4. 海洋（`sim.ocean`）

| 符号 | 物理量 | 单位 | 口径 | 生产者 | 消费者 |
|---|---|---|---|---|---|
| `OceanField.anomalyAt`（`public static synchronized`，E65 提取器盲点已修） | SST 距平 | **K** | 行惰性；**唯一注入点**是 `Atmosphere.SST_PROVIDER`（D56） | `GyreRow`+`SeaSurfaceTemp` | 全部温度/降水 |
| `OceanField.anomalyAt` | SST 距平（**带相位**） 参数为 (x,z) 参数为 (x,z,seed,theta)| **K** | **§447**：`PHASE_SEASONAL=false` ⇒ **直接回落**到 2 参年平版本（逐位不变）；true ⇒ 4 相位求解 + theta 上**环形线性插值**（4 点采样的 Nyquist = 二次谐波，**只保留年 + 半年谐波**，已记账） | 同上 | `Atmosphere.sstAnom(x,z,theta)` |
| `OceanField.PHASE_SEASONAL` | 开关 | `bool` | **§447**：SST 异常逐相位求解。默认 **false**；代价 = 解行数 ×5；进 `OceanField.configStamp()` **与** `SimClimate.configStamp()`（`0x7A11D`，D58 那一位） | | `curlAtmos`/`solveRow`/`anomalyAt` |
| `OceanField.zkey(zIdx,phase)` | 缓存下标 | 整数 | **§447**：`phase<0 ? zIdx : ROWS*(phase+1)+zIdx`（年平 ∈[0,64)、相位 ∈[64,320)，值域不相交）。⚠ `SPAN` 里存的第 0 位**永远是真实行号 zIdx**，不是 zk | | `hit`/`findSpan`/`ANOM`/`BAND`/`SPAN_BY_ROW` |
| `OceanField.PH4` | 4 个采样相位 | rad | `{0, π/2, π, 3π/2}`；θ=0 = NH 夏至（`Atmosphere.theta(day)`，day=0 = JJA） | | `curlAtmos`/`solveRow`/`warmAll` |
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
| `SimClimate.CELL`,`COAST_FINE`,`TILE_X`,`TILE_Z`,`GRAD_STEP`,`UPWIND_OFFSET`,`SLOPE_STEP`,`SLOPE_SCALE`,`CACHE_LIMIT`,`SEASON`,`T_LO_K`,`T_HI_K`,`TEMP_LO`,`TEMP_SLOPE`,`MOIST_A`,`P_MIN_MM_YR`,`AIRT_SCALE`,`AIRT_SEALEVEL`,`SST_PROVIDER`,`SOLVE_*`,`NODE_COUNT`,`CACHE_HIT`,`CACHE_MISS`,`SAMPLE_COUNT`,`resetStats`,`clearCache`,`configure`,`configStamp`,`coords`,`MARITIME_SCALE`,`COAST_FAR` | 配置/计数/缓存 |
| `OceanField.ENABLED`,`GRAD`,`JET_RANGE_RD`,`PH4`,`BAND_W`,`solveCount`,`WARM_SCAN`,`warmAll`,`configStamp`,`reentryBlocked`,`OceanWiring.*` | 配置/计数 |
| `BasinFinder.SAMPLE`,`MAX_R`,`earlyOutMismatch`,`WALL_TOL`,`CFL_SAFETY`,`safeDt` | 数值参数 |
| `CoastalLayer.G_PRIME`,`L_RELAX`,`F_MIN`,`UPWELL_SEASON_DAYS`,`UPWELL_WIDTH`,`COAST_TAN_*`,`SMOOTH_KM`,`smoothedWindowM`,`steady*`,`configStamp`,`eastBandContribution`,`coastTangent` | 数值参数/求解 |
| `SurfaceLayer.*`,`SeaSurfaceTemp.LAMBDA`,`SURF_FACTOR`,`configStamp`,`GyreRow.OMEGA`,`latOf`,`betaForLatitude` | 数值参数 |
| `WorldContract.OMEGA`,`DAYS_PER_YEAR`,`hemisphereSign`,`PlateField.SEA_LEVEL`,`COAST_BLEND`,`landScore`,`elevation`,`isLand` | 数值参数/重载 |
| `SimTerrain.ENABLED`,`LAND_GAIN`,`OCEAN_GAIN`,`SEABED_RELIEF`,`DETAIL_AMP`,`DETAIL_W`,`BEACH_BLOCKS`,`SNOW_FROM_TEMP`,`seedOf`,`compose`,`seasonalAmpK` | 配置/重载 |
| `MapWriter.*` | 出图 |

---


### 6.1 P2-15 补登记（219 项生产者的登记表 —— 消灭 D67/D75/D78 类缺陷）

本表由 calibers_check.ps1 的 UNREG 清单机械生成（219 项 / 14 类），**只做登记**：
目的是让「出现了一个不在任何登记表里的 public static 生产者」这一缺陷类不再能静默发生。
类别列按**机械规则**填写（未逐项判读，不许当成已核对）：
全大写名 → 常数/开关；含小写字母名 → 派生/运行时。逐项物理判读留待后续。

| 符号 | 类别 |
|---|---|
| `Atmosphere.BETA_LAND_REF`,`C_LAND`,`C_SEA`,`CELL_MIG_REF_LAT_DEG`,`CELL_MIG_SENS`,`CELL_PHASE_FROM_TEMP`,`CHV_REF`,`PA_DRY_WARMTH`,`PA_FIXED_KAPPA`,`PA_NO_CELL`,`PA_NO_THERMAL`,`SEASON_FROM_HEAT_CAPACITY`,`SOIL_GRAD_STEP` | 常数/开关 |
| `Atmosphere.cellMigrationDegHardcoded`,`divVzmAt`,`landSeaTempContrast`,`seasonalSolarAmp`,`slabAmpK`,`slabLambda`,`slabPhaseRad`,`slabTau`,`vZmAt`,`vzWbarC` | 派生/运行时 |
| `HadleyCell.H_TROP`,`KEEP_EDDY_OUTSIDE`,`OM` | 常数/开关 |
| `HadleyCell.cellEdgeLatDeg`,`cellEdgeResidual`,`deltaTModel`,`innerEdgeLatDeg`,`lastBisectIters`,`phiHDeg`,`psiShape`,`resetCache`,`rFromDeltaT`,`selfCheck`,`smallAngleSinPhiH`,`solveSinPhiH`,`tablePeak`,`wShape`,`wZmSolved` | 派生/运行时 |
| `MountainLayerV2.SOLVE_NANOS` | 常数/开关 |
| `ParcelLift.CP_D`,`GAMMA_D`,`P0`,`R_D`,`R_V`,`T_TRIPLE` | 常数/开关 |
| `ParcelLift.dewpoint`,`dTdlnp`,`es`,`gammaMoist`,`lclTempBolton`,`lclTempDaviesJones`,`lift`,`moistAdiabatT`,`pOfZ`,`qs`,`rk4T`,`rs`,`vaporPressure`,`zOfMoistAdiabatT`,`zOfP` | 派生/运行时 |
| `PlateField.MASK`,`PlateField.WORLD_IS_TALOS` | 常数/开关 |
| `PrecipField.BLQ_SMOOTH_K`,`BLQ_THETA_E` | 常数/开关 |
| `PrecipField.advCalls`,`advEvalPoints`,`advWalkSteps`,`blqCalls`,`blqLastDh`,`qnetLastFnet`,`qnetLastM`,`qnetNegMBandLand`,`qnetNegMBandSea`,`smoothstep01b`,`thetaE`,`thetaEConv` | 派生/运行时 |
| `Radiation.ALB_SEA`,`ALB_SNOW`,`BUCKET_BETA`,`SKIN_TEMP_FROM_ENERGY_BALANCE`,`T_SNOW_K`,`WK_OVER_WFC` | 常数/开关 |
| `Radiation.bucketBeta`,`insolation`,`potentialEvapMmDay`,`residual` | 派生/运行时 |
| `SoilMoisture.MAX_YEARS`,`NTHETA`,`RH_DRY`,`SPIN_TOL`,`SPIN_TOL_REL`,`SPINUP_NANOS`,`V_HIST`,`W_FC`,`W_INIT_FRAC` | 常数/开关 |
| `SoilMoisture.betaOfFrac`,`blend`,`clearMemo`,`isSpinningUp`,`lastSpinResid`,`lastSpinRelResid`,`lastSpinYears`,`lastV`,`lastVResid`,`memoSize`,`vHistN` | 派生/运行时 |
| `StationaryWave.ABS_ATM_FRAC`,`C_GRAV`,`DAMPING`,`LOOP_MAX_ITER`,`LOOP_RELAX`,`LOOP_TOL`,`LV_W`,`NPHI`,`Q_OVERRIDE`,`Q_PER_MMDAY`,`Q_SCALE`,`ZERO_F` | 常数/开关 |
| `StationaryWave.divAt`,`divGridCopy`,`ensureSolved`,`fAtRow`,`gridRows`,`lastForcing`,`lastLoopChange`,`lastLoopGain`,`nodeCount`,`pGridCopy`,`qGridCopy`,`selfCheckMapping`,`signSelfCheck` | 派生/运行时 |
| `TalosField.DCELL`,`HH`,`RW`,`SWS` | 常数/开关 |
| `TalosField.age`,`bfield`,`fieldValue`,`hf`,`level`,`setHurst`,`USx` | 派生/运行时 |
| `Vegetation.VEG_TOL` | 常数/开关 |
| `Vegetation.gain`,`isSolving`,`iterTotal`,`lastGain`,`lastIterations`,`lastResidual`,`rsOf`,`veq`,`vStarClosed` | 派生/运行时 |
| `VerticalColumn.F_WELLMIXED`,`FIXED_TS`,`K_M`,`K_W`,`LW_SFC_EMIS`,`MASS`,`P_EDG`,`P_TOP`,`PC`,`RH_B`,`RH_M`,`RH_T`,`S0`,`TAU_ADJ`,`TAU0_LW`,`TAU0_SW`,`THETA_SB`,`W_REF`,`W_STAB` | 常数/开关 |
| `VerticalColumn.calibrateTauLW`,`setupOptics` | 派生/运行时 |
| `WorldContract.bandDD1`,`betaForLatitudeD1`,`coriolisD1`,`d1SelfCheck`,`latOfD1`,`wrapZ`,`zOfLat` | 派生/运行时 |
| `ZonalTables.W_COL_ANN`,`W_COL_MONTH` | 常数/开关 |
| `ZonalTables.wColAnnual`,`wColMonth` | 派生/运行时 |

> **§7199 记账（用户裁决 D-5）**：`WorldContract.Z_CYCLE_D1`（`static final int = Z_CYCLE` 的纯别名）**已删** ——全仓零外部读者（唯一 3 处都在 `WorldContract.d1SelfCheck()` 自己的自显示里，已改用正名 `Z_CYCLE`）。
> 中性证明（三层）：① `javap -p -c -constants` 全量反汇编 diff **只有被删字段那一行**；② 实跑 `d1SelfCheck()` 输出 **1110 B 逐字节相同**（SHA `F1E7DDCD…`）；③ 它自检判据 `PASS`。
> `.class` 3807 -> 3778 B（字段被删，本应不同）⇒ **纪律④的「字节码逐位相同」不适用于「删除字段」，已改用 javap + 实跑双层判据。**
> **§567 记账（2026-09-19）**：旧地形（PlateField 自带的 Voronoi 海陆实现）与它的 37 个旋钮（A1~A13 / ROUGH_* / WARP_* / COAST_* / COLLIDE_H / ARC_H / TRENCH_D / RIDGE_H / MAX_OCEAN_HALF / OCEAN_BREAK_* / TALOS_TERRAIN …）**整支删除**。
> 因此上表里 `PlateField.*` 只剩 `MASK`，并新增世界身份位 `WORLD_IS_TALOS`（**不是开关**，见该字段 javadoc 与设计冻结 §567）。
> 施工图：`build/eoh_probe/mtn/delete_legacy_landsea_plan.md`。

### 6.2 实验编号 / 残留开关索引

| 符号 | 形态 |
|---|---|
| `Atmosphere.PA_DRY_WARMTH`,`Atmosphere.PA_FIXED_KAPPA`,`Atmosphere.PA_NO_CELL`,`Atmosphere.PA_NO_THERMAL` | 实验编号（A/PA 前缀） |

### 6.3 S620–S623 补登记（P943/P944/P946 与 §620–§623 新增的公开生产者）

本表由 `calibers_check.ps1` 的 UNREG 清单逼出（2026-09-22 报 **11 项**，其中 5 项是 S615/S619 留下的）。
**只做登记**；逐项的物理判读与实测依据见设计冻结 §620–§623。

| 符号 | 类别 | 依据 |
|---|---|---|
| `Atmosphere.phiZonalMean` | 派生（物理） | `T_ZM_SL_MONTH` 首谐波相位的纬向插值（S615，设计冻结 §615） |
| `Atmosphere.phiZmNodes` | 派生（缓存） | 上一项的 19 节点惰性缓存 |
| `Atmosphere.zmslkNew` | 派生（物理） | §620 修法本体：`tZmSlAnnual + (κ − zfEarth)·Δ_SL` |
| `Atmosphere.zmslkLegacy` | 派生（物理） | §620 的旧式（重构式）；**供 §621/§622 归因**，非生产路径 |
| `Atmosphere.PHASE_FROM_OBS` | 开关（默认 true） | 季节项相位取观测首谐波 / 旧常数（S615） |
| `Atmosphere.ZMSLK_LEGACY` | 开关（默认 false） | §620 归因开关；折入指纹 `0x7A134L` |
| `OceanField.DTDZ_FORCE` | 开关（默认 0） | §621 归因开关（分离直接/间接通路）；指纹 `0x7A135L` |
| `OceanField.DTDZ_MODE` | 开关（默认 3） | §621 物理修正（位 0 极向符号 / 位 1 5 度尺度）；指纹 `0x7A136L` |
| `OceanField.vAt` | 派生（物理） | §622 新增：表层经向速度（m/s，正 = 向极），口径与 `anomalyAt` 平行 |
| `PrecipField.T850_NN` | 常数（= 37） | §432 的 PCHIP 节点数（S615） |
| `PrecipField.t850Slope` | 派生（物理） | 当前生效温度源的 850 hPa 纬向斜率（S615） |

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
| `PrecipField.EDDY_NN` | 计数 | 涡动链的 5 度节点数 = 19（0..90 度）。生产者/消费者都在 `eddyNodeSlopes` |
| `PrecipField.CW_SMOOTH` | 开关 | ⚠ **死开关（§427.1）**：`dWdy` 用的是 `columnWater` 而不是 `wOf` ⇒ 它只能改 `fluxAt` 里的守卫，进不了生产路径。默认 false |
| `PrecipField.COL_WATER_FROM_TABLE` | 开关 | 柱水汽取 ERA5 月表（true，生产）还是 `rho*H_MOIST*moisture(...)`（false）。默认 true |
| `PrecipField.ZONAL_SL_FROM_TABLE` | 开关 | 纬向平均海平面温度取观测月表（true，生产）/ 解析式。默认 true |
| `PrecipField.EDDY_T_SMOOTH` | 开关 | **§432**：纬向平均温度的经向梯度走 PCHIP 解析导数（true）还是节点 ±5° 中心差分（false，生产）。`tZmSlMonth` 是 **5° 节点线性插值** ⇒ 一阶导在节点上跳变；σ 由它求导、MFC 再求一次 ⇒ 对分段线性表求了两次导。默认 false |
| `PrecipField.EDDY_SIGMA_T850` | 开关 | **§433（生产默认 true）**：Eady 增长率的经向温度梯度改用 850 hPa（`ZonalTables.t850Month`）而非海平面还原地面温度；热成风式里的 `1/T` 同层 |
| `ZonalTables.T850_MONTH`,`T850_ANN`,`t850Month` | K | **§433**：NCEP/NCAR R1 月平均 850 hPa 温度表（19 纬 × 12 月）与访问器；口径与 `tZmSlMonth` 同（|lat| 对称 + 月循环线性 + 5° 节点） |
| `PrecipField.zonalSlTempSlope`,`T_NN` | K/m / K/m / 37 | **§432** 上述光滑重建的实现（-90..+90 每 5° 共 37 节点，Fritsch-Carlson 保形） |
| `PrecipField.WZM_ITCZ_SHIFT` | 开关 | `w_zm` 的升降边界是否随 ITCZ 平移（§406）。默认 true |
| `PrecipField.WZM_FROM_TABLE` | 开关 | **§436（生产默认 true）**：纬向平均上升支改用观测月表（`ZonalTables.wZmMonth`，NCEP ω500 月平均）；true 时**不再叠加** `WZM_ITCZ_SHIFT` 的人工平移（表自带迁移） |
| `ZonalTables.W_ZM_MONTH`,`W_ZM_ANN`,`wZmMonth` | m/s | **§436**：观测月平均纬向上升速度表（19 纬 × 12 月）与访问器；口径同 `tZmSlMonth`。生成器 `refs/gen_wzm_month.py`（**幂等**；此前那个 §230.4 设计已作废，理由写在脚本头） |
| `PrecipField.EDDY_FULL_DIVERGENCE` | 开关 | 用 `d(gate*K*W')/dy`（true，生产）还是只看曲率项（false）。默认 true |
| `PrecipField.EDDY_MASK_OUTSIDE` | 开关 | 门放在散度外面（true，不守恒）/ 里面（false，生产，守恒但带 dgate/dy 伪项，见 §427.6）。默认 false |
| `PrecipField.DIAG` | ThreadLocal<double[]> | 逐点诊断槽（15 个：divU/wEff/q/precip/beta/...）。只读诊断，不进降水 |
| `PrecipField.EDDY_MIX`,`EDDY_PHYS_GAIN`,`EDDY_TAU`,`EDDY_CLOSURE`,`EADY_COEF`,`eadyGrowth` | 混 | 涡动闭合（EDDY_TAU 秒；EADY_COEF 无量纲） |
| `PrecipField.EDDY_VAR` | 枚举 0/1/2 | **§427**：被涡动扩散的标量 —— 0 柱水汽 W（kg/m²，ERA5 月表）/ 1 纬向平均近地面比湿 q（ρ·H_MOIST·q）/ 2 湿静能 h（ρ·H_MOIST·(cp·T/L_v + q)）。后两者都换算成 kg/m² 与 W 同量纲。生产者：本类 `eddyScalar`；消费者：`modelShapeVarFull` → `eddyMfc`。默认 0 |
| `PrecipField.EDDY_GRAD` | 枚举 0/1 | **§427**：X 的经向梯度构造 —— 0 节点中心差分（±EDDY_DPHI_DEG）/ 1 PCHIP 保形三次样条的解析导数。生产者：本类 `eddyDXdy`；消费者：`fluxAtVar`。默认 0 |
| `PrecipField.rhinesWidthRad`,`criticalLatRad`,`polarEdgeLatRad`,`stormGateCrit` | rad / rad / \[0,1\] | **§428**：L_R = sqrt(2v*/β)（Rhines 混合长，零新常数）；临界纬度（u_zm 由东转西的第一个交点）；`gate = S((\|φ\|−φ_c)/W)` |
| `PrecipField.EDDY_GATE_MODE` | 枚举 0/1/2 | **§427.3**：涡动门的过渡尺度 —— 0 阈值 smoothstep（现状）/ 1 混合长宽度 tanh（0.5 交点不变，u_w = \|du_zm/dy\|·ℓ_L）。生产者：本类 `gateOf`；消费者：`fluxAtVar`。默认 0 |
| `PrecipField.mixingLength`,`uShearAbs`,`stormGateWide`,`gateOf` | m / s⁻¹ / \[0,1\] | **§427.3** 涡动门链：ℓ_L = v*·τ_L（v* = σ_Eady·L_d）；u_w = \|du_zm/dy\|·ℓ_L。零新常数（用已有的 EDDY_TAU） |
| `PrecipField.zonalQ`,`eddyScalar`,`eddyDXdy` | kg/kg 或 kg/m² 或 m⁻¹·(kg/m²) | **§427** 涡动链的纬向平均标量与梯度；`zonalQ` 显式用 RH_SEA，**不读** `BETA_OVERRIDE`（纬向平均量不该随本地土壤湿度变） |
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
| `V2BiomeSelect.ALPINE_TEMP_*`,`BASIN_*`,`MOIST_*`,`TEMP_*`,`W_LAT` | 混 | 群系门限（**坐标空间**，不是物理单位 —— 见 §5 `Coords.temp`） |
| `V2BiomeField.BLUR_*`,`V2BiomeField.NX`,`V2BiomeField.NZ`,`V2BiomeField.SX`,`V2BiomeField.SZ`,`V2BiomeField.CELL`,`texPow`,`textureSeed` | 格/m | LUT 与纹理参数 |
| `LandformField.*`,`OrographyField.*`,`ClimateCoords.AIR_DT`,`AIR_DQ` | 混 | ⚠ 旧栈（D68/D70）；`AIR_DT/AIR_DQ` = **D66**（气团加项消失） |
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

<!-- §534 修正（P1-7）：本块由 17 项补到 20 项，补上 P692 P683 P712 三个门。
     ⚠ 说明只能写在栅栏【外】：calibers_check.ps1:74 把块内每个空白分隔 token 当成探针名，
       写在块内会被当成 16 个不存在的探针（实测 acceptance-probes 由 20 虚报为 36、untracked 由 3 虚报为 19）。
     §743（2026-09-23）：21 -> 22，补上 P499（§244.4 副热带海洋夏季硬零门，三条判据：
       GATE_SUBTROP_ZERO / GATE_MONSOON_IDX / GATE_DJF_3040）。 -->
```acceptance-probes
P258 P284 P296 P268 P452 P293 P292 P285 P442 P294 P295 P297 P477 P478 P479 P480 P484 P692 P683 P712 P991 P499
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
| 7 | **涡动扩散的变量口径**：本模型扩散**实测柱水汽 W 的水平经向梯度**；文献（Caballero & Hanley 2012 式 12~19）扩散 **750~1000 hPa 层平均比湿**、梯度**沿倾斜轨迹**（含 ∂/∂p）。§427.3 的 A/B 只否证了「定 RH=0.80 的表层 q」，**不是**文献构型 | **未对账**：理由（实测柱水汽含真实 RH 结构）成立，但两者从未在同一口径下比过（§429 第四节） |
| 8 | **涡动扩散率的量级**：模型 `K(35N, JJA) = 1.80e7 m²/s`；文献反演值 1.85e6（Lu et al. 2022 cosφK 峰 @35N）~6.3e6（Caballero & Hanley `v*ℓ`）⇒ 偏大 2~9 倍。**但公式本身与 Green/Stone 的 `D = 3.23σL_d²` 只差 7%，且 GPCP 判据已通过（0.88~1.09）** | **未对账**：疑为「参数化系数」与「反演量」的定义差（垂直加权 + 轨迹倾斜），但未验证（§429 第四节） |
| 9 | **涡动项与平均项是否互相抵消** | **已闭环（§431，P640 逐项分解）：没有相消。** 41.3N DJF：主项 **+1.131**、涡动 **+4.705**、合计 **5.836**（涡动是主项 4.2 倍，**主项为正**）。<br>⚠ 本条原先写的「总降水 3.42 而涡动 3.87」是**口径错误**：3.42 是拿 P639 的逐带比值 × 观测反推的（±0.8° 三点平均），与单纬度直接读数不能相减。 |
| 10 | **50~62N 涡动供雨不足**（§431 新暴露）：旧配置在该带的涡动降水**本来就是门梯度伪项**，修掉后归零；主项只有 0.20~0.92 mm/day，而 GPCP 锚 2.4，且 NCEP 观测表在该带为**正**（DJF 55N +1.12、60N +0.96） | **未修**：新的、定义清楚的工作项（`stormGate`/`K` 在 50~62N 的涡动辐合不足甚至反号） |<br>**§432 追查结果**：洞由 `K'X'` 驱动（K 在 55N 局部极小、60N 局部极大）；K 的鼓包来自 σ 在 60N 的峰（11.17e-6 vs 邻点 8.9e-6）；而 **PCHIP 光滑化否证**（σ 峰仍在 10.54e-6）⇒ **鼓包在 ERA5 温度剖面本身，不在求导**。两条新线索：① σ 用的是**海平面等效**温度（65N 有最低点后向极回升，疑为该变换的产物）；② 求导模板宽度 ±5° 未做 A/B。
| 11 | **JJA 中纬偏干的位置**（§431 定位）：33.8~46.3N JJA 的主项只有 **0.013~0.076 mm/day**，合计 0.569~0.590 几乎**全部来自 `SHALLOW_FLOOR`**；锚是 2.4~2.9 | **未修**；⚠ **§740 标 stale**：本条读数「几乎全部来自 `SHALLOW_FLOOR`」所依靠的那个地板，已被本弧**两处改动直接改写**：§687（凝结形式地板，有出处三件套）与 **§720（地板也受 BLQ θ_e 门控约束）**。⇒ **`0.569~0.590` 与 `0.013~0.076` 两个数不再代表当前代码**；需按当前代码重取。可对照的当前读数：`P499 GATE_SUBTROP_ZERO` = **14.2%**（25~40N 夏海洋 P>=0.3 比例；§244.4 当年记为 **0%**）。 |
| 12 | **`P296` 的「EDDY_MIX 标定检查」已失效**：该检查假设 `EDDY_MIX` 是标定乘子，但 §429 之后它是推导值 `1/EADY_COEF` | **§740 复核：仍未改** —— 本条仍成立，处置仍为「改写成推导值检查，或删除」。**本弧未动 `P296`**（本目标只做工具层与记账，不改其判据）。⇒ 保留为待办，不得当成「已核对」。 |
| 13 | **`T850_MONTH` 的口径与来源**：NCEP/NCAR R1 月平均 LTM 1991-2020 850 hPa 温度；`air` 的**原生单位是摄氏度**（已换算成 K 再入表）；lat 为**降序**（生成时已排序）。生成器 `refs/gen_t850_month.py`，**幂等可重跑** | **已记账**（两个单位/顺序坑都实际踩过） |
| 14 | **JJA 56~59N 因 §433 变干**（海 JJA 25~60N 平均 0.88 → 0.74；56.3N 1.22 → 0.59、58.8N 0.79 → 0.34），而 43.8~46.3N 变好（0.45 → 0.86） | **新暴露，未修**：风暴轴**出口**的 JJA 涡动辐合不足 |
| 15 | **主判据「亚洲/撒哈拉 >= 10」在现架构下不可达**（§434）：7 个开关组合实测最好 = 年 0.99 / JJA 1.39；撒哈拉绝对值恒在 0.98~2.04（观测 0.105） | **未修**：缺的是**水平水汽输送**——`moisture()` 完全是局地的（`RH*q_sat(T_local)`），季风区的水汽本应主要来自海洋平流 |
| 16 | **S3 的 `beta` 无空间对比**（§434，P647 实测）：JJA beta_亚洲 0.2524 / beta_撒哈拉 0.2870（比 0.879，**符号反**）；物理需要约 20:1 | **未修**：桶的水源是模型自己的 P，而模型两地 P 几乎相同（2.972 / 3.054）⇒ **正是目标原文点名的「循环」** |
| 17 | **去掉一个补偿性错误之前，必须先实现它所补偿的东西**（§436 的教训）：`W_ZM` 手定表 + ITCZ 平移，实际是在替**尚未实现的区域季风**顶班。换成物理上更正确的**观测纬向平均** omega（`W_ZM_MONTH`）后，亚洲 JJA 从 2.972 掉到 **0.222**、年比 0.81 → **0.38** | **未修**：缺的是**区域（2-D）上升** —— 纬向平均表（手定或观测）都不是季风的正确仪器。开关 `WZM_FROM_TABLE` 默认已撤回 false |
| 18 | **「写了但没接」清单**（§436 新增两例）：① `refs/omega500_month.txt` —— 观测月表数据早就在 refs，从未接进 src；② `refs/gen_wzm_month.py` —— §230.4 的生成器写好了但**从没跑过**（`ZonalTables` 里既无表也无标记），且其设计会原样保留 §435 的三个缺陷 | **已清理**（生成器已按 A′ 重写，作废理由写进脚本头）。**这是「写了但没接」的第 2 例，值得警惕为缺陷类** |
| 19 | **S2 定常波无法修沙漠/季风对比**（§437，P650 实测）：两种强迫下 S2 给出的 div(V) 对比都**反号**（`Q∝P`：两地都辐合、撒哈拉更强；`Q=净加热`：亚洲辐散/撒哈拉辐合）；量级只占总 divU 的 **8.8~11.5%**；净柱加热经向对比 **−13.49 W/m²**（亚洲更冷，需要约 +100） | **未修**。原因：强迫来自模型自己倒置的 P，或来自缺少季风潜热加热的净加热 |
| 20 | **★ 缺陷类：「湿沙漠」是一个不动点**（§437 归纳）：模型的 P 在沙漠与季风之间几乎相同（3.054 vs 2.972），而 **A（柱水汽收支）/ fetch / A′（观测纬向平均 ω）/ A″（S2）四个机制全部由它喂 ⇒ 四个全部失败** | **结论性**：在模型自己的状态里再找机制是徒劳的；必须引入一个**不依赖 P** 的约束（径流/入渗依赖地形，或 BL 夹卷依赖 T 与 BL 深度） |
| 21 | **D（地形坡度径流）否证**（§438，P651）：季风区比撒哈拉**陡 2.1 倍、高 12 倍**（亚洲 1151 m/0.00726 vs 撒哈拉 95 m/0.00338；坡度>2% 占 8.8% vs 0%）⇒「陡坡径流大 ⇒ 更干」符号相反 | **已否证** |
| 22 | **`Q_AT_SURFACE_TEMP` 无对比增益**（§438，P646 第 10~12 格）：JJA 比 0.97 → 0.98（两个盒子各降约 17%）；全栈 1.39 → 1.42 | **已否证**（作为对比机制） |
| 23 | **★ 定论：缺的不是公式，是【经度方向的信息】**（§438）：撒哈拉（0~30E）与亚洲季风（70~120E）**在同一纬度带** ⇒ **任何纬向平均的量在原理上都无法区分它们**（A′ 失败的根本原因）；而模型自己的经度方向信息里，唯一能区分的两项（高程、过山损耗）**方向都是反的**，其余（κ/tSl/q/divU）全在 5~10% 以内 | **结论性**：需要一个新的**状态量**（植被/土壤双稳态），或一个**区域边界条件**。**把任何新公式套在这些场上都会得到同一个答案** |
| 24 | **★ 桶是【单稳】的（解析 + 实测，§439）**：稳态方程 `R(beta) = A*rh(beta) - beta*(e1 - e2*rh(beta)) = 0`，`rh = 0.20+0.60*beta` 是**线性**的 ⇒ 扫 beta∈[0,1] 数根，**根个数 ≤ 1**（亚洲 JJA 恰好 1，其余 <1）⇒ **没有沙漠分支** | **已判**。湿支自持的判据是 **`E_p(1) < P(1)`**；实测亚洲 `E_p(1)/P(1) = 4.05`、撒哈拉 3.15 ⇒ **差 3~4 倍** |
| 25 | **模型里沙漠湿的机制**（§439）：`chv = bulkCoeff(kappa,|V|)`，撒哈拉风速 5.83 m/s vs 亚洲 8.38 ⇒ `E_p` 低 30% ⇒ 同样降水下 beta 更高。**模型的沙漠湿，是因为它「风平浪静」**（真实撒哈拉是强风 + 巨大 E_p） | **已定位** |
| 26 | **V 的正反馈必须落在【本地水量】侧**（§439）：反照率反馈是**负**的（稳定）；Charney 型环流正反馈走 S2，而 §437 已否证。本地水量侧现在缺的、且标准的，是**表面阻力（canopy/surface resistance）** —— 现在 `E_p` 只有空气动力学阻力 | **未实现**：Monteith 型表面阻力会把植被表面的 E_p 压低 2~5 倍，正好是所需量级 |
| 27 | **★ 真缺陷：`SoilMoisture` 的 beta 记忆没有失效入口**（§440）：`MEMO` 只按 `(x, z)` 作键，**没有任何 `invalidate()`**。我把 `RS_SURF` 接进 `spinup` 后 A/B 在 r_s = 0/50/100/200/400 上给出**完全相同**的 beta ⇒ **新常量等于没接**。**D58 那条教训的又一次重演，而且是我自己犯的** | **已修**：新增 `SoilMoisture.invalidate()`，并把 `RS_SURF` 折进键（默认 0 时键逐位不变） |
| 28 | ⚠ **`SoilMoisture.MEMO` 的键只含 `(x, z)`**，不含 `seed` / `cell` / 任何开关。单次运行里 seed 与 cell 是常量所以未暴露，**但与第 27 条/D58 是同一缺陷类** | **已记账，未修**（需要把 seed/cell 折进键，或明确禁止运行期更换这两者） |
| 29 | **`RS_SURF` 表面阻力否证**（§440，P653）：两条都如 §439 预期地变湿（亚洲 beta 0.25 → 0.54），**但亚洲/撒哈拉的比从 1.13 掉到 0.87**（撒哈拉 beta 涨得更快）⇒ 有效的【桶旋钮】，不是【判别器】 | **已否证**（作为判别器） |
| 30 | **★ 七次否证的归纳（§439/§440）**：第 1~6 次说明**模型自己的场里没有能经向区分沙漠与季风的量**（§438）；第 7 次说明**任何【全局均匀】的干预都不会翻转排序，只会放大它**（撒哈拉起点就更湿）。⇒ **能翻转它的机制必须【在沙漠上比在季风区强】，且依赖一个模型现在没有的状态量或区域输入** | **结论性** |
| 31 | **Charney 反照率环路否证**（§441，P654）：`ALB_LAND_ADD` 0→+0.20 时环路**符号是对的**（反照率↑ ⇒ `qSens`↓ ⇒ `Qnet`↓ ⇒ S2 更多辐散 ⇒ P 略降），**但增益只有 4×10⁻⁴**（`dP/d(dALB)=0.07 mm/day`，要把 P 动 2 mm/day 需 `dALB≈29`）。另：`Atmosphere.surfaceTemp` **不含辐射** ⇒ 反照率只能经 `qSens` 一项影响 `Qnet` | **已否证**（作为双稳环路） |
| 32 | **★★ 定量的不敏感性（§441，八次否证的归纳）**：陆地降水 `P = A·rh`，`A ∝ qSat(tSfcSl)·wEff`，而热带这两项都光滑近乎均匀 ⇒ **P 实际上只是 `tSfcSl` 的函数**；对任何【由模型自己状态构造】的扰动，响应都在 **1% 量级或更小** | **架构性，不是参数性**：`P` 是瞬时局地状态的诊断函数 —— **没有记忆、没有输送、没有反馈**。真实沙漠靠【输送】（水汽平流）与【历史】（双稳 + 初值/轨道强迫），模型两个都没有 |
| 33 | **★ 观测锚从来没算全**（§442）：§404 只给了撒哈拉 0.105 mm/day，**亚洲盒子的观测值一直隐含**，我还在 §438 据此断言「亚洲没问题」。从 `refs/gpcp_ltm.nc` 直接算出：亚洲 JJA **7.667**、年 3.560；撒哈拉 JJA 0.105、年 0.236 ⇒ **观测 亚洲/撒哈拉 = JJA 72.76、年 15.06**。模型 3.020/3.078（比 0.98）⇒ **两个都错：亚洲少 2.5 倍、撒哈拉多 29 倍** | **已纠正**：模型的陆地降水**几乎没有空间动态范围**（真实跨度 73 倍） |
| 34 | **★★★ 缺的那一项（文献定位，§442）**：Jalihal & Mikolajewicz 2025（`refs/monsoon_desert_energetics_2025.pdf`）逐字：季风-沙漠的 TOA 辐射差**主要由水汽的长波吸收驱动，地表反照率只是次要**；**水汽/云-OLR-环流反馈占降水变化的约 65%，植被-反照率反馈占 35%**；**沙漠化主要由动力学驱动**；季风建立后其非绝热加热在**西侧**造成下沉（Rodwell-Hoskins） | **我们的 `netColumnHeating` 辐射项 `sigma*Ts^4*(1-2*EPS)` 只依赖地表温度、完全没有水汽**，且归因相反（更热的撒哈拉辐射掉更多）。**这一项就是缺的东西** |
| 35 | **撒哈拉是印度季风的【西侧下游】**（§442，Rodwell-Hoskins）：季风非绝热加热激发的定常 Rossby 波在其西侧造成绝热下沉。**模型恰好有这条路**（S2，§396 已证「Q 取正确强迫时给出的正是亚洲辐合/撒哈拉辐散」），缺的是强迫里的水汽长波项 | **已定位**，下一步 |
| 36 | **水汽长波项实现并实测（§443，P655）**：`StationaryWave.WVLW_K`（默认 0，逐位不变），`absFrac = 1-exp(-k*CWV)`。基线归因是错的（`qRad` 让更热的撒哈拉辐射掉更多）；加进水汽后 `Qnet` 对比 **−14.95 → −27.90 → −46.82**（k=0.02/0.05）⇒ **放大错误 1.9~3.1 倍**。原因：**撒哈拉 CWV 47.89 > 亚洲 41.43** | **已否证（单独用）**：缺的不是辐射项，是它的输入 |
| 37 | **★★ 水汽源 go/no-go 通过（§443，P656）**：从盒子点沿风逆推到海、读上游海温。JJA：亚洲 fetch 975 km / SST 297.48 K / q源 0.015165；**撒哈拉 fetch 1906 km / SST 293.86 K / q源 0.012297** ⇒ `q源*exp(-fetch/L)` 之比 **L=1000km 3.1×、2000km 1.96×、3000km 1.68×**，**方向对**（十次尝试里第一个） | **可实装**。⚠ DJF 反号（亚洲 fetch 3208 km，冬季风把来向推回内陆），已记账 |
| 38 | **★ 配对假设（§443）**：水汽源（初始对比，JJA 3.1×）+ 水汽长波吸收（放大器，文献占 65%）**互补**——单独任何一项都不行（辐射项单独放大会实测已证；水汽源单独只有 1.7~3.1 倍，要 29 倍） | **待实测**：步骤如下一节 |
| 39 | **★ 水汽源实现（§444）**：`PrecipField.Q_FROM_SOURCE`（默认 false）/`SOURCE_FETCH_L`（默认 1.5e6 m）/`SOURCE_FETCH_MAX`（4e6）/`upwindSea`（多步逆推到海）/`moistureFromSource`。接进 `mmPerDay` 与 `netColumnHeating` **两条路** | **已实现**，默认关 ⇒ 逐位不变 |
| 40 | **★★ 一个「找不到就退回旧值」的回落分支会反转结果**（§444）：第一版 `moistureFromSource` 在逆推不到海时**回落到原始（湿）q**，而撒哈拉盒子里 **36%** 的点落在这个分支 ⇒ **把 3.5 倍的改善反转成 0.73（更差）**。修法：按 `SOURCE_FETCH_MAX` 处理 | **已修**。教训与 D58 同族：**回落分支必须走同一个物理，不能偷偷退回旧口径** |
| 41 | **★★★ 配对成功（§444，P657）**：水汽源 + 水汽长波（`k=0.02`）后，JJA 的 **CWV 排序翻正**（撒哈拉 47.89→15.47，比亚洲干 1.48 倍）、**`Qnet` 经向对比从 −14.95 翻到 +69.51 W/m²**（`k=0.03` 时 +81.38，**正是文献量级 ±80 且符号为亚洲正**）、**主判据 JJA 比从 0.96 走到 3.56**（观测 72.76） | **方向与符号结构已对**；绝对量还差（亚洲 1.189 vs 观测 7.667、撒哈拉 0.334 vs 0.105） |
| 42 | **§443 的 P656 go/no-go 有 Jensen 不等式缺陷**（§444 记账）：拿「盒均 fetch」算 `exp(-fetch/L)`，而 `exp` 凸、短 fetch 主导 ⇒ 那个 3.1 倍虚高 | **已记账**（实际生效的是逐点 exp 再平均） |
| 43 | **DJF 仍反号**（§444）：源开 k=0.02 的 DJF 是亚洲 0.511 / 撒哈拉 0.952（比 0.54），观测是 1.95。与「冬季风把亚洲来向推回内陆 3208 km」一致 | **未修** |
| 44 | **闭环次临界（§445，P658）**：`CLOSED_LOOP=true` 时 6 次迭代收敛（change 0.0004 < TOL 1e-3），**环路增益 = 0.233** ⇒ 放大只有 `1/(1-0.233)=1.30` 倍，实测相对开环只变 **0.4%**。§443 那个「O(1~10)」的估算**偏乐观约 10 倍** | **已记账** |
| 45 | **★★ 配对净效果（§445）**：撒哈拉 JJA 3.068 → **0.334**（观测 0.105，即 **29 倍 → 3.2 倍**）；亚洲 JJA 2.934 → **1.184**（观测 7.667，即 2.6 倍偏干 → **6.5 倍偏干**）；比 0.96 → **3.55** | **瓶颈从撒哈拉转到亚洲** |
| 46 | **★★★ 季风源区海温偏低 4.5~5.5 K（§445，P659）**：沿风逆推读出源点位置 —— 亚洲 JJA 的源是**孟加拉湾 19.3N/88.5E，SST 297.48 K**，**恰好等于 `tOceanK(20N)` 的年均值本身** ⇒ **`sstAnom` 在季风源区 ≈ 0**，而真实孟加拉湾/阿拉伯海 JJA 比年均暖 **4~5 K**。源区冷 4.5 K ⇒ `qSat` 少 27% | **未修**：下一步主任务 |
| 47 | **⚠ 第 46 条的「JJA 比年均暖 4~5 K」是【没有出处的估值】，§448 已用实测改正。** COBE-SST2 月气候 1991-2020（`refs/sst.mon.ltm.1991-2020.nc`，生成器 `refs/gen_sst_box_cycle.py`）：孟加拉湾盒 JJA−年 = **+1.13 K**、南海 **+1.30**、阿拉伯海 **+0.04**、索马里外海 **−1.01**。4~5 K 是**季风爆发前（5 月）**的峰 | **已改正**（§448 一）。教训：**没出处的数字不许进设计冻结**，它已经在 §445 里当了「下一步主任务」的依据 |
| 48 | **★★★ §448 的真缺陷：水汽源温度漏了季节项（口径不一致）**。`mmPerDay` 的 `tSl` 带 `+ seasonalAnomaly`（§216.7 注释原文要求「年均 + 季节项 + SST距平」），而 `upwindSea` 返回的海面温度**只取前半截** ⇒ **海洋点**（走 `moisture(tQ)`）有季节项、**内陆点**（走 `moistureFromSource`）没有 | **已修**：`PrecipField.SOURCE_SEASONAL_T`（默认 false）；实测孟加拉湾源温度 JJA **+0.941 K**（297.729→298.670） |
| 49 | **★ 模型自己的纬向季节循环其实是【对的】**：19.3N 处 `seasonalAnomaly(lat,0,JJA)` = **+1.0234 K**，而观测纬向带 17.5~21.5N 的 JJA−年 = **+1.01 K**，**差 0.01 K**。⇒ `aSea` 表正确，缺陷只在接线 | **已确认**（§448 三） |
| 50 | **★★ §448 对主判据只值 +1.1%**：JJA 亚洲/撒哈拉 3.50 → **3.54**（亚洲 1.292→1.352、撒哈拉 0.369→0.382） | **已实测**：方向对，但不是瓶颈。**§445「瓶颈已转到亚洲」这个判读偏乐观，记账** |
| 51 | **★★ §447 逐相位海洋 SST 实施（`OceanField.PHASE_SEASONAL`，默认 false）**：`curlAtmos` 逐相位、`solveRow(…,phase,…)`、`zkey` 缓存下标、`anomalyAt(x,z,seed,theta)` 环形线性插值、provider 3 参重载、`warmAll` 逐相位预热、`SimClimate` 折入 `0x7A11D` | **已实现**；A 段（OFF）全部 4 相位与年平**逐位相同**（max 0.000e+00）⇒ A/B 干净；代价 5 倍解行数 |
| 52 | **★★ §447 实测：方向对、量级差 1~2 个数量级**（P661/P663）。JJA−DJF：孟加拉湾 OFF **+0.0000** → ON **+0.1020**（观测 caliber 2 **+1.38**，比 0.074）；南海 +0.0133（观测 +1.64，比 0.008）；西撒外海 −0.2714（观测 −1.79，比 0.152）；阿拉伯海 +0.0422（观测 **−0.38**，**符号反**）；索马里 +0.0165（观测 −0.99，**符号反**） | **已实测**：OFF 时全部 6 个盒子 JJA−DJF 恒为 **+0.0000** ⇒ 干净地证明原模型在区域 SST 上**没有任何季节信号** |
| 53 | **★★★ §447 的结构定位（为什么只有 0.1 K）**：`SeaSurfaceTemp.anomaly` 的闭合是 `T' = ANOM_MAX·tanh(−(vSurf/λ)·dT̄/dy / ANOM_MAX)`，即**「水平流速 × 纬向平均经向温度梯度」= 副热带环流闭合**。P663 段 1：本世界自己的 `dT/dz` 在 17.5N = **−1.99e−06 K/m**（5 cm/s 就给 **+1.79 K**）⇒ **梯度不是瓶颈**；由 P663 段 2 的 +0.0604 K 反解出热带海盆实际表层流速只有 **~0.008 m/s** ⇒ **流速才是瓶颈** | **结构性**：观测的热带区域异常（孟加拉湾 +1.73 K、索马里 −0.74 K、西撒外海 −2.42 K）是**温跃层/热含量（暖池）与沿岸上升流**造的，这套闭合原理上造不出。**缺的不是系数，是适用物理** |
| 54 | **★ 观测口径的两次分离（§447/§448 立的规矩）**：① **caliber 1** = 盒均 − 盒**年均** ⇒ 判「源温度缺不缺季节项」；② **caliber 2** = 盒均 − **同纬度同月纬向平均** ⇒ 判「风应力旋度能造出什么」。**混用会得出错误结论**（纬向平均的季节循环不可能由经向结构制造） | **已建立**，`refs/gen_sst_box_cycle.py` 同时输出两个口径 |
| 55 | **⚠ `WVLW_K` 在【降水链】上目前完全没有出口**（§448 五 判读二）：配置 3 与配置 5（WVLW 关）**逐位相同** ⇒ 它唯一的出口 `StationaryWave.netColumnHeating` 只在 `StationaryWave.ENABLED=true` 时被用，而它默认 false。§444 那个「Qnet 从 −14.95 翻到 +69.51」是 **`DIAG_HEAT` 诊断量**的读数，**不是**降水链上的读数 | **已记账**：报「配对成功」时必须写清是哪个链上的成功 |
| 56 | **⚠ `SoilMoisture.MEMO` 的键仍不含任何本轮新开关**（承第 27/28 条）：`RS_SURF` 已折入，但 `Q_FROM_SOURCE`/`SOURCE_SEASONAL_T`/`PHASE_SEASONAL` 都会改 `P` ⇒ 运行期翻开关会读到旧 beta。本轮探针靠显式 `SoilMoisture.invalidate()` 规避 | **未修**（D58 缺陷类仍在） |
| 57 | **★ 第 46 条的落地结论**：§445 指引的方向**做完了**（§447 + §448），实测对主判据合计只值 **+1.1%** ⇒ 瓶颈仍是 §438 的「**缺经度方向的信息**」，不是源区海温 | **结论性**：撒哈拉 JJA 0.382（观测 0.105，**3.6 倍太湿**）、亚洲 1.352（观测 7.667，**5.7 倍太干**），比 3.54 vs **72.76** |
| 58 | **⚠ 我自己的仪器缺陷：`P658.box()` 的 `CWV` 列写错，而且我在解释时把两列的对错判反了**（§449 二，已改正）。逐字查 `PrecipField` DIAG 写入点：`DIAG[1] = dWave`（**S2 叠加进来的 div(V)**，s⁻¹）⇒ **P658 印成 `div(V)` 是对的**；`DIAG[5] = q`（kg/kg）⇒ 印成 `CWV` 才是错的。**而初版 §449 把两列都判成错的，并据错判把 DIAG[1] 写成「wLoc(m/s)」** | **已改正**（§449 二/三）。这是本轮第 3 次同类错误，**且这一次是我在【解释】时犯的** —— 同族于 E65/D58 |
| 59 | **★★★ `Qnet` 的对比早就翻正了 —— 不需要水汽长波项**（§449 三，P658 重跑）：只开 `Q_FROM_SOURCE`（`WVLW_K=0`）时 Qnet 经向对比 = **+17.59 W/m²**（亚洲为正）；`k=0.02` → **+69.38**；`k=0.03` → **+81.25**。而 JJA 降水比 **3.51 → 3.55 → 3.57（+1.7%）** | **结论性**：`Qnet` 不是瓶颈，**`Qnet → divU_S2 → wLoc → P` 这条通路才是** |
| 60 | **★★★ S2 环流反馈头【第二次否证】（§449，第 11 次）**：强迫翻正后，`DIAG[1]`（**S2 注入的散度**）在**两个盒子都是正的（辐散）**，且撒哈拉从 +3.415e−07 涨到 **+5.570e−07**（亚洲 +5.070e−07 → +5.652e−07）⇒ 两盒之差从 **1.655e−07 塌到 0.82e−08**。参照 §422 的 20N `DIAG[0]` = +2.74e−06 ⇒ S2 只占 ~18% 且同号叠加 | **已否证**（排除了「强迫反号」这个解释）。⚠ 初版把这一列写成 `wLoc`，已改正 |
| 65 | **⚠⚠ `pzRef` 进 `v` 是【已确证但未修】的缺陷（§422，选项 D 未落地）**：`wind()` 第 137/141 行，`v = (f·px − gam·(pz + pzRef))/den`。`f→0` 时 `den → rho·gam²` ⇒ `v = −pzRef/(rho·gam)` ≈ **−12 m/s**（`gam≈2.5e-5`、`pzRef≈3.6e-4 Pa/m`）。§422 实测赤道纬向平均 `divU` 因此**大 14 倍**，且**25~30N 符号翻**。物理：赤道经向气压梯度受**质量连续性**约束，不是纯摩擦平衡。**§422 的修法（选项 D）从未落地，开关 `PZREF_IN_V` 默认仍为 `true`（缺陷是活的）** | **未修**：这是 B（柱水汽收支）与一切【散度驱动】机制的前置缺陷 —— §435 的 `−div(qV)` 否证、§449 的 S2 否证都可能被它污染过 |
| 66 | **§422 选项 D 已落地并实测（§451，`Atmosphere.PZREF_VZ_MODE` 默认 0）**：`v_zm` 由 `w_zm` 经球面质量连续性导出，`wbar_c = +1.014e−03 m/s` 是**推导出的流函数闭合项**（保证极点 `V=0`）。实测 `v_zm` 峰值 **−0.379 m/s @15N**（对比现状赤道 `v ≈ −12 m/s`）；纬向平均 `divU` 赤道 **−1.418e−05 → +3.759e−07**、30N `+5.586e−07 → −1.321e−06` | **已实现**（默认 OFF）。§422 的 `−1.4178e−05` **逐位复现** ✓。⚠ 表源硬绑 `ZonalTables.wZm`（生产口径），若 `WZM_FROM_TABLE` 改成 true 必须同步 |
| 67 | **★★★ 但缺陷修好后对比仍然不存在（§451 四）**：模式 1 的盒子读数 JJA 亚洲 **3.508** / 撒哈拉 **3.300**（比 **1.06**，现状 0.97）。诊断：**撒哈拉仍比亚洲更辐合**（−4.364e−06 vs −2.931e−06） | **结论性**：**§435 对 `−div(qV)` 的否证、§438「缺经度方向的信息」都被免除「标度 bug 污染」的嫌疑**。比值 1.06 仍离 72.76 有 69 倍 ⇒ **散度不是对比的制造者** |
| 68 | **★★★ 观测的柱水汽对比只有 1.41 倍（§452）**：NCEP/NCAR R1 月平均 LTM 1991-2020 垂直积分 `TCWV = (1/g)∫q dp`（`refs/gen_tcwv_box.py`）。亚洲 JJA **23.48** / 年 27.62 / DJF 31.94；撒哈拉 JJA **16.68** / 年 23.86 / DJF 31.56 ⇒ **比 1.41 / 1.16 / 1.01**。⚠ `shum` 原生单位是 **grams/kg**（第一版漏换算，报出 1000 倍） | **已建立**，这是 JJA 对比的第一个柱水汽锚 |
| 69 | **★★★★★ B（柱水汽收支）被自己的前提检验否证（§452，P665）**：模型配对的 W = 亚洲 **19.49** / 撒哈拉 **14.60**（**比 1.33**）vs 观测 23.48/16.68（**比 1.41**）⇒ **W 基本是对的（差 6%）**。而 `wEff` 在三个配置里**逐位相同**（`Q_FROM_SOURCE` 只改 q）。分解 `P比 = W比 × wEff比`：观测 72.76 = 1.41 × **51.6**；模型 3.54 ≈ 1.33 × **1.50** | **结论性**：**§450 对 B 的推荐已撤回**。缺的全部在 `wEff`（需 ~52 倍，实际 1.50 倍）——这解释了为什么所有【改 q】的尝试都只值 O(1%) |
| 70 | **★ 缺的那条路：局地潜热 → 上升（从未实现）**（§452 五）：现状 `wEff = w_zm(φ) + clamp(−H_bl·divU)`，而 `divU` 来自气压场、**气压场完全不依赖 `Q_lat`** ⇒ 局地正反馈 `wEff → P → Q_lat → wEff` **根本不存在**。已有的只有 S2 定常波那条（增益 0.233、两盒同号，两次否证） | **未实现**。⚠ 系数必须**推导**（`w = Q/(ρ·c_p·Δθ)`，`Δθ` 取自本世界剖面），**不许**再引入 `Q_WM2_TO_SW` 式的标定常数（§429 纪律） |
| 71 | **★★★ 公式必须换成柱 MSE 收支（§453，文献核实）**：Neelin（`refs/neelin_moist_dynamics.pdf`）逐字 `ω = Ω₁(p)·∇·v₁`；`(∂_t+D)(T̂+q̂) + M·∇·v₁ = F_net`，**`M = ⟨Ω ∂_p h⟩` 是毛湿稳定度（gross moist stability）**，`h` 是**湿静力能 MSE**。⇒ 我提的 `w = Q/(ρ·c_p·Δθ)` **是错的**（`Δθ` 要换成 MSE 差 `c_pΔT + gΔz + LΔq`）；回路方向对、且要用**固定形函数**。Neelin 1997; Yu et al 1998; Neelin & Zeng 2000 | **推导已修正**。⚠ 文献**未**回答「撒哈拉热干 BL 的 `h_BL` 是否更低」⇒ `M` 能不能给出沙漠下沉**必须实测**，不许预设符号 |
| 72 | **★★★★ `WVLW_K` 是一个【把基态挪走的偏置】，不是物理扰动（§453 二，自查缺陷）**：`qRad = σTs⁴(absFrac − 2·EPS)`，`2·EPS = 1.2202`。`absFrac=1` ⇒ `−0.22σTs⁴ ≈ −101 W/m²` ✓（真实量级 −100~−150）；`absFrac=0.33`（`WVLW_K=0.02, cwv≈20`）⇒ `−0.89σTs⁴ ≈ −409 W/m²` ✗。P658 同数据两 k 对照：Qnet **−52.81/−37.86（k=0）→ −506.01/−575.39（k=0.02）**，整体挪了 **~−450 W/m²** | **未修**。水汽长波吸收的真实效应是 **O(10~50 W/m²)**。这比 §449 的「S2 映射不分辨」更靠上游：`∇·v₁ = F_net/M` 要的是 `F_net` 的**符号**，而基态被挪走 450 ⇒ 两盒都成强下沉 |
| 73 | **★ 下一步顺序（§453 三）**：① 先修 `qRad` 基态（`WVLW_K` 做成**相对 `absFrac=1` 参照态的扰动**），判据「打开后 Qnet 整体仍在 −50~−150」；② 用模型自己的 T、q 两层剖面**算出 `M` 的两侧**；③ 实现 2 层 MSE 口径的 `∇·v₁ = F_net/M`（默认 OFF），测环路增益是否 > 1（S2 路只有 0.233） | **排队中** |
| 74 | **★★★★★ 缺陷：`absSolar` 算出来了却从未接入加热（§454，P666）**。`netColumnHeating` 第 190 行算出净短波，**只喂给 `skinTempLand`，从未进入 `qRad` 或返回值** ⇒ 净柱辐射**完全没有短波**。实测 `ASR` = **+371.96（亚洲）/ +403.51（撒哈拉）**，而 `qRad` = **−95.79 / −102.34** | **未修**。「写了但没接」缺陷类（第 18 条）的又一例，量级 **372~404 W/m²** |
| 75 | **★★★★ `qRad` 不是 OLR ⇒ 不能直接加 ASR（§454 三）**：`qRad = σTs⁴(1−2·EPS) = −0.2202σTs⁴`（`ts=295.35` ⇒ `−94.9` ✓ 实测 −95.79），是一个**纯长波项**。天真加 ASR 得 `Qnet` = **+320.18 / +365.22**（量级过大且对比符号反）。Neelin 口径应为 **`F_net = ASR − OLR + LH + SH`**，`ASR/LH/SH` 三项模型都有，**缺的是 `OLR`** —— 模型没有这个量 | **未修** |
| 76 | **★ 缺的 `OLR` 可以从我们自己的观测锚补上（§454 四）**：§443 注释已有观测标定「CWV 10 → 50 kg/m² 时晴空 OLR 340 → 265 W/m²」，两点定两常数的对数式 **`OLR = 447.3 − 46.6·ln(CWV)`**（核对 CWV=10 ⇒ 340.0 ✓、CWV=50 ⇒ 265.0 ✓）。代入实测场：亚洲 `F_net = +142`（文献 +100~+150 ✓）；**撒哈拉 +199 ✗ —— 因为模型沙漠反照率只有 0.172（真实 ~0.35）**；用 0.35 重算落到 **+112** | **待实现**。另暴露：沙漠反照率偏低是独立缺陷（§441 的 `ALB_LAND_ADD` 是**全局**偏置，这里需要**状态依赖**） |
| 77 | **★ 步 a 已落地（§455，`StationaryWave.QRAD_ASR_MINUS_OLR` 默认 false）**：`qRad → absSolar − olrClear(ts,cwv)`，`olrClear` 是**推导的单层灰体**（干极限 → σTs⁴，湿极限 → σT_ft⁴，`T_ft = ts − GAMMA·H_EFF/2` 用本世界递减率），唯一常数 `OLR_K = 0.0667` 由观测锚定。自检 **CWV=10 → 339.0**（锚 340 ✓）、**CWV=50 → 250.2**（锚 265，差 5.6%，记账）。实测 `qRad` **−90.01 → +97.00**，`F_net` 对比 **+24.24 → +31.23**（**符号转正** ✓）。`SimClimate` 折入 `0x7A120` | **已实现**（默认 OFF）；`P293 +9.6531e-07` ✓ |
| 78 | **★★★★★ 新缺陷：`qSens = −180 W/m²`，陆地皮温比空气【低 4.7 K】（§455 三，P667）**。`qSens = chv·cp·(ts−ta)`；由 `qSens = −179.98`、`chv·cp ≈ 37.97` 反解得 `ts − ta = −4.74 K`（`ts = 290.98`、`ta = 295.72`）。**在 372 W/m² 净短波之下这是反的** —— 物理上 `ts − ta` 应为 **+9.8 K**（若全走感热），至少为正。后果：两盒 `F_net` 被一起拽到 **−44.65 / −75.87**，而文献要求 **+100~+150 / 近 0** ⇒ **与 §453 完全同型的失败模式**（`qSens` 两盒几乎相同 ⇒ 不破坏对比，只破坏绝对量级） | **未修**：步 a′。查 `Radiation.skinTempLand` 的能量平衡与 `ta` 的口径 |
| 79 | **★★★★★★ `qSens = −180` 的根因不是 `Radiation`，是 `beta = 1` 的回落（§456，P668）**：`netColumnHeating` 的 `beta = SoilMoisture.ENABLED ? betaAt : 1.0`，默认 S3 关 ⇒ **处处饱和面** ⇒ `le = beta·chv·LV·(qSat(ts)−qa)` 过大 ⇒ 皮温被压到空气之下。实测（`Q源`+`源季节项` 开，JJA）：**S3 关** β=1.000 ⇒ `ts−ta = −5.43 / −8.69 K`、`qSens = −179.98 / −174.80`、`F_net = −44.65 / −75.87`；**S3 开** β=0.252/0.287 ⇒ `ts−ta = −0.74 / −0.61 K`、`qSens = −40.46 / −31.21`、**`F_net = +62.43 / +35.30`（双双翻正）**，对比 **+27.14** | **已定位**。文献判据（+100~+150 / 近 0）现在**符号对、量级同阶**；沙漠偏高是步 b 的事。`qSens` 仍轻微负值与 P570「`H` 在 β≈0.3 翻正」**定量一致**（此处 β=0.25~0.29）⇒ 不是缺陷 |
| 80 | **★ 配对要求（§456 四）：`Q_NET_HEATING ⇒ SoilMoisture.ENABLED = true`**。`beta = 1` 的回落**在海洋上是对的、在陆地上等于「处处饱和面」** ⇒ 潜热冷却过量 ⇒ 皮温与感热全错。与 §444 的「水汽源必须与水汽长波配对」**同型**：**一个开关的物理前提由另一个开关提供** | **已写成代码守卫（§457，步 a″）**：`StationaryWave.betaFallbackLandCalls` 只在**陆地**（`k > 0.5`）计数、首次打印一次 stderr 警告、`betaGuardTripped()`/`resetBetaGuard()`。P669 自检（576 点）：错配 **220**、正配 **0**，`GUARD_SELFTEST_FAILURES=0` ✓。javadoc 已补配对要求 |
| 81 | **★★★★ 步 b（状态依赖的沙漠反照率）在模型现有场里【做不到】（§458）**。读代码：`Radiation.albedo(isLand, tSurfK)` 只有海/陆/雪三个分支，**唯一状态是温度**，而温度给撒哈拉的是**更暗**的陆地反照率（方向反）。逐条查候选判别量：高程（亚洲 1151 m vs 撒哈拉 95 m）✗反、`beta`（0.2524 vs 0.2870）✗反、`tSurfK`（295.7 vs 300.9）✗反、`kappa` 无区分、`E_p(1)`（27.4 vs 20.7）✓但**是驱动量不是状态**、上风 fetch（971 vs 1879 km）✓但取了会**自反馈**、`P` ✓但**循环** | **结论性**：真实的沙漠反照率来自**沙/裸岩 vs 植被**，而植被由降水决定 ⇒ **沙漠反照率是 A（植被/干旱度）机制的【输出】，不是独立输入**。步 b 必须并入 A。这是 §438 第 23 条在反照率上的重演 |
| 82 | **★★ §450 的窗口现在【存在】了（§458 五）**：窗口判据 `P_亚洲/P_撒哈拉 > 1.252`。基线 0.97（**无窗口**，§450 当时）→ §448 配对 **3.54**（**窗口存在 `f ∈ (2.28, 34.5)`**）→ 观测 73.0（`f ∈ (1.57, 91.6)`） | **结论性**：现在做 A **不再是「在没有对比的世界里放大排序」** —— 对比已经在了且方向正确 |
| 83 | **★ 待裁决：A 需要【有记忆的状态量】`V(x,z)`（§458 六）**：`dV/dt = 生长(P,V) − 衰亡(E_p,V)`；`albedo = ALB_LAND + (ALB_DESERT−ALB_LAND)(1−V)`（满足步 b 判据）；`r_s = RS_BARE(1−V)`（§439 的 E_p 通道）。⚠ **这会改变 `SimClimate` 的契约**（现在一切都是 `(x,z,seed,cell,theta)` 的纯函数）；带记忆就必须有自旋、收敛判据、与 `configStamp` 的失效关系（§439 的桶已处理过同一件事）。**A-预报 vs A-诊断**两条路，推荐 A-预报（§439/§441 两条独立证据说明无记忆诊断式给不出沙漠） | **已裁决 A-预报**（用户 2026-09-16），但要求**控制性能** ⇒ 见第 84~86 条 |
| 84 | **★★★★★ A-预报已落地（§459，`sim/atmos/Vegetation.java`，`ENABLED` 默认 false ⇒ 逐位不变）**：`dV/dt = K·V(1−V)(P−P_C)/P_C`（平衡只有 `V∈{0,1}` ⇒ **阈值判定，不解方程**）；`albedo += (ALB_DESERT−ALB_LAND)(1−V)`；`r_s = RS_BARE(1−V)`（默认 0）。**关键实现选择**：`V` 的输入是 `netColumnHeating` **已收到的 `pMmDay`**（`PrecipFn` 回调）⇒ **不重算降水**、不递归。五条性能设计：惰性记忆 / 有界迭代 / 防重入 / 代价可测 / **键含全部配置**（D58 & §440 第 27 条的教训）。`SimClimate` 折入 `0x7A121` | **已实现**。⚠ **新 src 文件必须同时加进 `runprobe4.bat` 的 copy 列表**，否则探针构建报「找不到符号」（本轮实际踩到，precheck 有双向检查但只警告） |
| 85 | **★★★★ 代价实测（§459 三，P670）—— 用户硬约束**：V 关 solveCount 0；**V 开 101 个陆地点 / 326 次迭代 / 平均 3.23 / 累计 0.4 ms / 重入拦截 0**；该段墙钟 **479.2 ms vs 520.4 ms（噪声内）**。解点数 101 < 采样 ~162 正是设计意图（只在 `k > 0.5` 调用） | **已满足**：代价可忽略且**可测** |
| 86 | **★★★ 行为读数 + 两个新问题（§459 四/五/六）**：V 开使 `Qnet` 亚洲 **+62.43 → +17.34**、撒哈拉 **+35.30 → −5.50（翻负）** ✓ 方向对。但①**`P_C` 握住全部答案**：盒心 `P` = 0.370/0.529 **都 > `P_C`=0.30** ⇒ 盒心 `V=1`，而盒均 `Qnet` 变 45 W/m² ⇒ 盒内有相当部分落到 `P<0.30` 而变裸 ⇒ **`P_C` 必须文献锚定**（草地 100~200 mm/yr ⇒ 0.27~0.55 mm/day）；②★ **回路是断的**：`Atmosphere.surfaceTemp` 与 `PrecipField` 的 `tSl` **都不含辐射**（§441 第 31 条早记过）⇒ 反照率**不改 `P`**，只能经 S2 环流那条路（增益 0.233）⇒ **`V` 现在实质仍是 `P` 的诊断函数，「记忆」没被激活**；要让 A 真成为预报，**必须先修 `surfaceTemp` 的辐射项** | **未修**：下一步；`P_C` 文献核实与 `surfaceTemp` 辐射项是两件并列的前置 |
| 87 | **⚠ 我自己的探针缺陷（§459 五 2）**：P670 的 `ASR` 列硬编码 `Vegetation.albedoAdd(1.0)` ⇒ 在 `V<1` 的点上**不是真实 ASR**，两行显示相同是假象（`Qnet`/`qRad` 列来自 `DIAG_HEAT`，是真的） | **已记账**：本轮第 **4** 次仪器列名/列值错误 |
| 88 | **★★★★★★ 文献核实 `P_C`：我们的形式错了，且文献的回路不经过地表温度（§460）**。Groner, Claussen & Reick (2015) 复述 **Claussen et al. (2013)**（源自 Brovkin et al. 1998 / Liu et al. 2006b）逐字：`dV_i/dt = (V^E_i(P) − V_i)/τ`，**`τ = 5 years`**；`V^E_i` 是 **`[P_C1^i, P_C2^i]` 之间的线性斜坡**（不是阶跃）；**`P^E = P_d + D_B·V_S`，`D_B = 140 mm/yr`** | **三条纠正**：① 单一阈值阶跃 → 必须改**斜坡**；② 缺时间尺度 `τ = 5 yr`（我们隐含 `τ→0`）；③ **回路路线选错** —— 文献把反照率+蒸发+粗糙度**聚合成 `D_B·V` 一项直接加在降水上**，而我们去补 `surfaceTemp` 的辐射项 |
| 89 | **★★★ 为什么不是补辐射项（§460 二）**：文献原文「Rachmayani et al. (2015) recently showed a positive effect of vegetation on precipitation caused by **evapotranspiration effects rather than albedo effects**」 | **结论性**：§441 的单反照率通道增益只有 4e−4，正是因为**反照率本来就不是主通道**。`D_B` 是文献系数，**不是拟合** |
| 90 | **★ 代价影响：性能设计不用推翻（§460 三）**：`D_B·V ≤ 0.3836 mm/day` 而盒均 `P ≈ 0.7~0.95` ⇒ 修正 ≤ 55%；`τ = 5 yr` ⇒ `V` 在世界内**准静态** ⇒ §459 的「逐点一次记忆化」仍然成立 | **已评估**；`Vegetation` 需要新增 `P_C1/P_C2/D_B/TAU` 四个文献常数与「降水加项」通道 |
| 91 | **⚠ 还差一个数：`P_C1`/`P_C2` 的阈值表（§460 四）**：文献里它们是逐植物类型的值，在 Claussen et al. (2013) 与 Groner et al. (2015) 的表里，**本轮只取到形式、`τ`、`D_B`** | **已取到（§461）** |
| 92 | **★★★★★★ 阈值表已取到（§461）：植物类型降水包络（逐字）** —— Saharan **< 150 mm/yr**、**Sahelian（草地/疏林草地）150~500 mm/yr**、Sudanian 500~1500、Guineo–Congolian > 1500。⇒ 荒漠 vs 季风草地那一档 = **`P_C1 = 150`、`P_C2 = 500 mm/yr`（= 0.411 / 1.370 mm/day）**，是**基于观测物种分布的数据包络**、不是拟合。另：`D_B = 140 mm/yr`（Liu et al. 2006a; Claussen et al. 2013，全类型相同）；反照率方向「tropical leaves are **darker** than steppe grasses」(White 1983) ✓；★ **主通道是蒸发不是反照率**（Hély et al. 2009：「leaf area ... up to three times higher ... strong evapotranspiration differences」；Rachmayani et al. 2015）⇒ `RS_BARE` 才是第一通道 | **已取到**。§440 否证的是 `RS_BARE` 作为**全局均匀旋钮**，**状态依赖形式从未被否证** |
| 93 | **★★★★★★ 用文献常数算出：植被回路增益 = 0.40，次临界（§461 三）**：`g = D_B · dV/dP = 140 / (500−150) = **0.40** < 1`。与 §449 的 S2 路 **0.233** 并列。文献自己也说多样性会**降低**增益（「high plant diversity could stabilize an ecosystem」，因 `V_S = mean_i V_i` 抹平多斜坡） | **结论性**：**植被（A）是放大器，不是双稳的来源**；与 §442 文献权重（水汽/环流 65%、植被/反照率 35%）一致。**超临界回路必须是水汽-环流那条 ⇒ 步 d 是主回路**；A 的作用是把 `F_net` 对比放大 `1/(1−0.40) = 1.67` 倍并**锁住**状态。两个次临界回路若**不独立**，合起来可能越过 1 —— **这是必须实测的** |
| 94 | **★★★★★★ 文献口径的 `Vegetation` 已落地并实测（§462，P671）**：常数逐字进码 —— `P_C1=0.4110`、`P_C2=1.3699`、`D_B=0.3836 mm/day`、`TAU=5.0 yr`、**`g=0.4000`（与 §461 手算 140/350 逐位一致）**。★ **性能关键**：`D_B` 通道的回路不动点有**闭式解**（`V* = (P_d−P_C1)/((P_C2−P_C1)(1−g))`），`g<1` ⇒ 唯一稳定根 ⇒ **闭式即精确**；`g≥1` 时分母 `≤0` ⇒ **闭式失效本身就是双稳信号**（记 `bistableDetected`）。外层只迭代次级通道，`VEG_ITER=3` | **已实现**（默认 OFF）。实测：**116 解点 / 193 迭代 / 平均 1.66 次 / 累计 0.4 ms / 墙钟 469.2 vs 503.2 ms / 重入 0 / 双稳信号 0** ⇒ 代价与 §459 同量级 |
| 95 | **★★★★★ `V` 实测（§462 三，JJA，全套开关开）**：V 关 P 0.949/0.697、Qnet **+62.43/+35.30**（与 §456 **逐位复现**）；**V 开** P 1.138/0.812、**V = 0.4923 / 0.3016（比 1.63，方向正确 ✓）**、Qnet **+7.20 / −18.01** | **★ 撒哈拉 `F_net` 翻负** —— 正是要的方向（沙漠 = 下沉）。两盒都在向「有对比」移动 |
| 96 | **⚠ 次级通道还没进回路（§462 四）**：`netColumnHeating` 给 `vegAt` 的回调**返回常数 `P_d`** ⇒ **只闭合了 `D_B` 通道**（文献主导通道，闭式、零额外 `mmPerDay`）；`RS_BARE`（**第一物理通道**）与 `ALB_DESERT` 目前只作用于反照率→`Qnet`，**没有反馈回 `P_d`** | **未接**：要迭代它们必须先让 `mmPerDay` 认 `V`（`V_OVERRIDE` 已在 `PrecipField` 接好）。**下一步** |
| 97 | **★★★★ 次级通道接线【主动中止】+ 三条约束（§463）**：我按最直接方式改了 `SoilMoisture.chvE`（叠加 `Vegetation.RS_BARE*(1−V)`），**然后自己回退**。约束：① `V` 的来源必须与当前迭代一致、非求解期走记忆化 `vegAt` —— 回落成常数 ⇒ **通道静默失效**（§444 第 40 条 / §440 第 27 条）；② 回调要算降水 ⇒ 造出 `PrecipField → SoilMoisture → Vegetation → PrecipField` 新环 ⇒ **E30 同型的指数级重入**；③ **每次迭代 = 一次完整桶自旋** ⇒ 代价从 0.4 ms 变成 O(3×桶自旋×陆地点数)，**直接违反用户「必须控制性能」**。★ 约束 3 是关键：`D_B` 通道只值 0.4 ms 正因为它有**闭式解**，而 `RS_BARE → chvE → E_p → beta → q → P` **没有闭式解**（`beta` 本身是桶的不动点） | **已回退**，`P293 +9.6531e-07` ✓。下一轮方案：**按通道代价分层**，层 3 的正确形态是**准静态一次前向代入**（用 `V` 的上一轮值算一次 `chvE`，不是不动点迭代）⇒ 代价 = 1 次桶自旋且仍保留状态依赖。**接入前必须先查 `SoilMoisture.spinup` 的实测代价（§405/P604 有记账）** |
| 98 | **⚠ 自我回退的纪律记账**：我在没满足上述三条约束时就把代码写进去了 —— 与 §456 a″「先把配对写成守卫再谈功能」的纪律相反 | **记账** |
| 99 | **★★★★★ 层 3 的预算判据实测（§464，P672）**：40 个陆地点 —— `betaAt` 冷启动 **40 次自旋 / 175.1 ms ⇒ 单次自旋 4.378 ms/点**；`mmPerDay` 冷启动 40 点 **118.6 ms ⇒ 2.965 ms/点**。⚠ **A 先跑 B 后跑，JIT 预热污染对比**（B 单点里本应含一次自旋却更小）⇒ **两数不可相减**，可用的是量级（同为毫秒级）。判据：迭代 `VEG_ITER=3` = **+222%**（否证）；准静态一次前向 = **+74%**（仍太贵） | **★★★ 更强的结论**：**`V` 必须在 `SoilMoisture.spinup` 之前解析完** ⇒ 桶那一次自旋**顺带吸收 `V`** ⇒ **边际代价 = 0**。形态定案：单遍流水线（估 V0 → rsTot → 唯一一次自旋 → P_d → 闭式更新 V → 未收敛只记录不重跑），**严格零额外自旋**，代价是 `V` 与 `P_d` 之间**一拍滞后**（准静态近似，须记账） |
| 100 | **★★★★★ 零额外代价的插入点已定位（§465）：`SoilMoisture.spinup` 的年循环第 205 行 `double pMm = A[kk]*rh;` —— `V` 的输入 `P` 已经在那里。** ⇒ 在年循环里加 `V` 的**闭式**更新 + 每年更新 `rsTot`（影响 `chvE → A/e1/e2`），代价 = 每（采样点×年）**一次乘法 + 一次闭式解**，**桶自旋次数不变**。`V ↔ β` 的耦合与 `w` 的自旋**共用同一个年循环**同步收敛，**但 `V` 的残差必须计入 `resid`**（否则重演 §213 的「只看 `w[0]` 的假收敛」） | **已定位、未接线**。这一条同时解掉 §463 的三条约束：① `V` 就在自旋里（无需 `V_OVERRIDE` 回落）；② **不造 `PrecipField → SoilMoisture → Vegetation → PrecipField` 新环**；③ **零额外自旋** |
| 101 | **★★★★★★ 层 3 接线完成，三项验收全过（§466，P673）**：`SoilMoisture.spinup` 内加 **pass 循环**（不是迭代；`Vegetation.RS_PASS` 默认 1）。40 个陆地点实测：**`spinupCount` 40 = 40（严格相等 ⇒ 零额外自旋 ✓）**；`evalCount` 960 → 1920（**恰好多一个 pass 的块求值**，48.0/点）；墙钟 **4.310 → 6.386 ms/点（+48.3%）**；`V̄` 1.0000 → **0.4549**；`refreshUsed` 0 → **40**。`P293 +9.6531e-07` ✓ | **已实现**。★ **`V` 残差（两 pass 之差）= 0.2386，而 `V̄` 只有 0.4549（52%）⇒ 准静态一拍滞后远未收敛**，须记账 |
| 102 | **★★★ `V` 残差 0.2386 的两种读法（§466 三）**：① **坏的**：`RS_BARE` 增益很大 ⇒ 真解更远，现在的 `V̄` 不是解；② **好的（更可能）**：**这正是强反馈/双稳的信号** —— `D_B` 通道增益只有 0.40（已被闭式精确处理），而 `RS_BARE`（**文献指的第一物理通道**）**一次代入就动 52%** ⇒ 两者同量级或更大 ⇒ **「0.233+0.40 能否越过 1」第一次有了可能为正的答案** | **判据**：把 `RS_PASS` 从 1 提到 2~3，看残差是否收敛（显著下降 ⇒ 唯一不动点；仍振荡 ⇒ **双稳**）。代价：每多一 pass = **+24 次块求值/点 ≈ +2.08 ms/点**；pass=3 ≈ **10.5 ms/点**（基线 2.4 倍）—— **有界可测，但需用户点头** |
| 103 | **★★★★★★ RS_PASS 扫描定案：V̄ 序列收敛到唯一不动点，不是双稳（§467，P674）**：24 个陆地点、`RS_BARE=150` —— V̄ 序列 **0.2321（1 pass）→ 0.6373（2）→ 0.6373（3）→ 0.6373（4）**，单调收敛、第 3 遍起逐位不变 ⇒ **唯一不动点**。代价 ms/点：**5.000 / 6.579 / 9.272 / 11.980**（RS_PASS=0/1/2/3）。**⇒ 正确取值 = 2**（1 不够、3 浪费），代价 = 基线的 **1.85 倍** | **★ §466 的「好的读法」被否证** —— 残差 0.2386 只是**遍数不够**，不是双稳信号。**「残差大」首先应读成「没算完」**，除非用序列本身证明 |
| 104 | **★★★★ 三条回路的增益总表（全为实测/文献值，§467 四）**：S2 定常波 **0.233**（§449）；植被 `D_B` **0.40**（§462 = 文献 140/350）；植被 `RS_BARE` **强**（V̄ 0.2321 → 0.6373，**2.75 倍**）**但收敛到唯一不动点**（§467） | **结论性**：**三条回路没有一条是双稳的**。与 §461 文献常数结论、§442 文献权重（水汽/环流 65%、植被/反照率 35%）一致 ⇒ **超临界回路只能是水汽-环流那条**（`∇·v₁ = F_net/M`，步 d） |
| 105 | **★★★★★★ ★ 补上缺失的数字：全套开关 = 2.08，比 §448 单独的 3.54 更差（§468，P675）**。消融：V **+31%**（1.59→2.08）、PZREF **+9%**（1.91→2.08）、**ASR−OLR 中性略负（−0.02）**、**S3+Qnet+闭环 −62%**（3.54→1.34）。**最佳组合仍是只有两个开关的 §448 = 3.54**（§469，P676：A 3.54 / B +S3 1.36 / C +V 1.93 / D +PZREF 2.14 / E −S3 2.69）。观测 **72.76** ⇒ **差 20.6 倍** | **★★★ 本阶段在目标数字上没有推进，方向是反的**。机制：主判据由**分母**支配，而本阶段那些「物理上更正确」的机制**都在给沙漠水汽** —— 因为**模型的沙漠本来就不干**（P 0.7~3.0，观测 0.105）⇒ 把正确物理施加在错的基态上只会放大错的。这正是 §437「湿沙漠是不动点」与 §441 第 32 条在主判据层面的确认 |
| 106 | **★ 必须诚实说清：修好缺陷 ≠ 改善主判据（§468 四）**：`absSolar` 漏项 372~404 W/m²、`qRad` 不是 OLR、`beta=1` 回落、`pzRef` 重复计数 —— **都是真缺陷、都该修**；但它们改善的是**中间量**（`F_net` 符号结构、`divU` 量级），而那些中间量在现架构下**传不到主判据**（§449 增益 0.233、§467 唯一不动点，两条独立证据） | **结论性**：后续任何「再加一个正确机制」的路线，都应当先回答「它怎么把中间量的改善传到主判据」 |
| 107 | **★★★★★★ 选项甲（把外生 `w_zm` 换成 `F_net/M`）的 go/no-go：【病态，不可用】（§470，P677，第 13 次否证）**。两层口径全用模型自己的量（`T_FT = T_BL − GAMMA·H_EFF/2`；`q_FT = q_BL·exp(−(H_EFF/2)/H_MOIST)`；`M = ρ·H_EFF·Δh`）。实测 `Δh`/`M`：基线 **+20489 / +23880**（M +3.0e8 / +3.5e8）；**§448 最佳 −298.5 / −4962**（M **−4.4e6 / −7.3e7**）；§448+S3+V −3848 / −9360（M −5.7e7 / −1.4e8）。`w_甲` 跨 **+2.0e−2 … −2.2e−3（4 个数量级且跨 0）** | **★ 根因**：`Δh = −19704 + L·Δq`（§471 八 已改正符号）⇒ **`M` 的符号只由 `q_BL` 是否越过 ≈0.00846 kg/kg 决定**，而 `q_BL` 是**降水方案的诊断输出**（§441 第 32 条）⇒ 换个水汽方案就换号。**这是条件数否证，不是增益否证** |
| 108 | **★★★★★ 三条路的失败理由是同一条（§470 四）**：S2（增益 0.233，§449）、植被 `D_B`/`RS_BARE`（0.40 / 唯一不动点，§462/§467）、`F_net/M`（`M` 符号由 `q_BL` 决定，§470）⇒ **全部卡在「模型里没有独立于降水方案的边界层水汽状态」**。真实 `q_BL` 由**海温 + 表面能量/水量平衡**约束；模型的由 `RH_SEA·qSat(T_local)·depl` **直接给出** ⇒ **自由参数** | **结论性**：需要让 `q_BL` 成为**被约束的状态**（柱水汽/边界层 q 的预报或稳态解） |
| 109 | **★★★★★★ 选项乙 的 go/no-go【通过】（§471，P678）**：稳态边界层水汽收支 `E = P + V`，`E = 86400·chv·(qSat(Ts)·β − q)`、`P = k_P·q`、`V = 86400·ρ·abs(w_BL)·(q − q_FT)`、`q_FT = q·exp(−(H_EFF/2)/H_MOIST)` ⇒ **闭式解、无新常数、零迭代**。判据①（验证）：现状下收支 q 与模型现在 q 同量级（**0.01138 vs 0.01367 = 0.83**；**0.00918 vs 0.01300 = 0.71**）⇒ 是现有 `RH_SEA·qSat` 口径的合理替代。**判据②（关键）：M_收支 两盒都正且远离 0（亚洲 +9.994e7、撒哈拉 +2.452e7）**，而 M_现 在 §448 下是 **−5.656e7 / −1.376e8（都负）** ⇒ **把 §470 的病态修好了** | **已通过**。★ 另一读数：q_收支 在开/关 `Q_FROM_SOURCE` 之间**几乎不变**（0.01138→0.01131），而 q_现 从 0.01367 掉到 0.00681 ⇒ **水汽源把模型 q 压得远低于收支平衡值**（蒸发在补充它）—— 正是 §470 要的「被约束的状态」 |
| 110 | **★★★★★★ 乙 让【甲】重新可用，且甲给出正确的定性结构（§471 三）**：取 §456 的 `F_net`（亚洲 **+7.20**、撒哈拉 **−18.01** W/m²）与 M_收支 ⇒ `w = +H_BL·F_net/M` 得 **亚洲 +7.2e−5（上升）／撒哈拉 −7.3e−4（下沉）** ⇒ **两盒异号**（最强对比形式）；而模型现在两盒都是正 wEff（1.97e−3 / 1.31e−3） | **待接线**。⚠ **符号约定**：净加热⇒上升⇒**低层辐合**⇒`∇·v₁<0`⇒`w = +H_bl·F_net/M`。P678 第一版写成 `-H_BL*(qn/M)`，**那一列作废** |
| 111 | **⚠ §470 三 的算术改正**：`Δh = cp·(T_BL−T_FT) + g·(0−z_FT) + L·Δq = +39156 − 58860 + L·Δq = −19704 + L·Δq`（初版把两个符号写反成 +19704）。**阈值不变**：`M>0 ⟺ Δq > 0.008043 ⟺ q_BL > 0.00846 kg/kg` | **已改正** |
| 112 | **★★★★★★ 收支口径接线并实测：它把 §448 的 3.54 打成 1.11（§472，P679，第 14 次否证）**。`PrecipField.Q_FROM_BLBUDGET`（默认 false ⇒ 逐位不变，`P293 +9.6531e-07` ✓）在 `mmPerDay` 内以闭式实现收支 `q`。实测 JJA 比：**A 基线 0.97 / B §448 最佳 3.54 / C 仅收支 1.17 / D 收支+S3+V+PZREF 1.11 / E B+收支+S3+V+PZREF 1.11**。★ **D 与 E 逐位相同** ⇒ 开收支后 `Q_FROM_SOURCE` **完全失效**（收支块直接覆盖 q） | **结构性否证**：收支口径是**局地平衡**（`E = P + V`，**无水平输送项**），而 §444 那 3.54 倍的唯一来源正是**上游** ⇒ 换局地约束 = 删掉「空气从哪来」。**第 3 次确认 §438/§441-32（缺经度方向的信息），且是负向确认**。⚠ 探针侧：`DIAG[5]` 在收支块**之前**写入 ⇒ P679 的 q 列作废（本轮第 5 次同类仪器错误） |
| 113 | **★★★★★ 需求现在可以写精确了（§472 四）**：`q_BL` 必须**同时**① **局地被约束**（否则 `M` 符号由自由参数决定，§470）——由收支口径提供（已实现）；② **被输送、有记忆**（否则经向对比消失，§472/§438/§441-32）——**只有 §444 的 `upwindSea` 近似提供**。⇒ 正确形态是**稳态平流收支**：`dq/ds = (E − P − V_vent)/abs(V_h)`，**沿流线积分、海洋作边界条件** | **待实现**。它就是 §444 fetch 方案的**严格版**（把拟合的 `exp(−fetch/L)` 换成沿真实轨迹的积分），而**本节实现的收支块正是它的源汇项** |
| 114 | **★★★★★★ 平流水汽收支接线并实测：局地 1.17 → 平流 1.91（输送被证实），但仍低于 §448 的 3.54（§473，P680）**。`PrecipField.Q_ADVECT_BUDGET`（默认 false ⇒ 逐位不变，`P293 +9.6531e-07` ✓）：`dq/ds = (E0 − λq)/abs(V)`，`λ = 86400·chv + k_P + C(1−qftF)`，**精确递推** `q ← q·exp(−a) + (E0/λ)(1−exp(−a))`，`a = λ·ds/abs(V)`；`L_eff = abs(V)/λ` **是导出的**，替换 §444 拟合的 `SOURCE_FETCH_L`。实测 JJA 比：**A 基线 0.97 / B §448 3.54 / C 仅平流 1.91 / D 平流+S3+V+PZREF 1.69 / E B+平流+S3+V+PZREF 1.69** | **已实现**。★「加上输送就能恢复对比」被证实（局地 1.17 → 平流 1.91，**+63%**） |
| 115 | **★★★★★ §448 的 3.54 里有一半来自【拟合常数】（§473 三 判读二）**：把拟合的 `SOURCE_FETCH_L = 1.5e6 m` 换成导出的 `L_eff = abs(V)/λ`，对比从 **3.54 掉到 1.91（−46%）** ⇒ **那个自由参数贡献了约 1.85 倍的对比**。⇒「§448 最佳 = 3.54」必须加注脚 | **结论性**。也解释了 §443 为什么要把 `SOURCE_FETCH_L` 扫 1e6/2e6/3e6 —— 那是在调自由参数 |
| 116 | **⚠⚠ 平流口径的代价 = 20~21 ms/点（基线的 50 倍）（§473 四，用户硬约束）**：平流路径上 ~2.8 个源汇求值点，**每点都调 `SoilMoisture.betaAt` ⇒ 每点触发一次完整桶自旋**（§464 实测 4.378 ms/点/次）。**这是 §464/§466 的教训原样重演**：在自旋之外再调 `betaAt` 会再造自旋。对比：A 基线 **0.41**、B §448 **0.60**、C 仅平流 **1.01**、D/E **20.15 / 21.00** ms/点 | **未修**。修法：平流路径上**不要独立求 β**（用调用点已有的 `betaUsed`，或路径上取 β=1）。判据：D/E 回落到 **2 ms 量级** |
| 117 | **★★★★★ 平流口径的代价修好了：50× → 10×（§474，P680 复测）**。修法：`moistureAdvected` 的路径点上**绝不独立求 `beta`**，改为读 `mmPerDay` 调用前设好的 `BETA_OVERRIDE`（准静态近似，换来零额外自旋）。实测 **D 20.15 → 4.03**、**E 21.00 → 4.29 ms/点**；而 JJA 比 **1.69 → 1.68（≤1%）** ⇒ 该近似在结果上无关紧要。★ **残余代价归属**：A 基线 0.40、C 仅平流 1.02 ⇒ **平流自身边际代价 0.62 ms**；D 的 4.03 里剩下 **3.0 ms 是 S3 桶自旋本身**（§405），**不在本轮新增机制头上** | **已修，满足硬约束** |
| 118 | **★★★★★★ 平流之上的完整消融：无拟合前提下的最好成绩 = 1.91（平流 only）；每个追加都更差（§475，P681）**。JJA 比：**0 §448 拟合参照 3.54 / 1 平流 only 1.91 / 2 +V 1.88 / 3 +PZREF 1.73 / 4 +S3 1.68 / 5 +S2 1.62 / 6 平流+S3+S2 1.76** | **结论性**。四条口径总表：基线 0.97（0 自由度）< 局地收支 1.17（0）< **平流 1.91（0）** < fetch **3.54（1 个拟合 `SOURCE_FETCH_L`）** << 观测 **72.76** |
| 119 | **★★★★★★ 需要裁决的设计分叉（§475 三）**：**乙₁** 把平流升级为 2-D 稳态求解 `div(qV) = E − P`（逐点法只取**一条**流线、只采样 2.8 点 ⇒ 1.91 很可能是**下界**）；**丙** 承认 72.76 在现架构下不可达，把目标改为「可测中间量上的物理正确」并落地已有成果；**丁** 先把已修好的 4 个真缺陷（`absSolar` 漏项、`qRad` 不是 OLR、`beta=1` 回落、`pzRef` 重复计数）落地为生产默认 | **待用户裁决**。建议 **丁 → 乙₁**，并把**丙**作为明确备选（15 次否证 + 完整消融全负 = 「现架构不可达」的强证据） |
| 120 | **★★★★★★ 目标第 (5) 项建成：多元锚 + 留出集，判据用【季节相位】（§476）**。GPCP v2.3 LTM 锚：亚洲季风 JJA/DJF **10.05**、撒哈拉 **0.27**（in-sample）；**留出集**：北美季风 **3.98**、南美季风 **0.07**、澳洲季风 **0.02**、非洲南部 **0.01**。相位由太阳直射纬度与海陆热力差决定，**量级调参伪造不出来** —— 正好治 §448 那个拟合长度 | **已建成**（`refs/gen_holdout_anchors.py`）。⚠ GPCP `lon` 是 0~360，负经度必须 +360 |
| 121 | **★★★★★ 相位门已经在区分两种口径，并修正了 §473 的叙事（§476 二）**：**A §448 拟合参照 5/6**（撒哈拉 JJA/DJF = 0.41，观测 0.27 同相 ✓）；**B 平流 only 4/6**（撒哈拉 **1.37**，**反相 ✗**）。⇒ **§448 的优势不只是一个拟合长度的量级放大 —— 它在沙漠季节相位上也是对的**，而无拟合的平流口径把沙漠相位搞反了 | **结论性**：「§448 = 3.54 且 1.85 倍来自拟合」必须补上：**那个方案在相位这个不可伪造的判据上更强** |
| 122 | **⚠ 仪器限制：地球经纬度盒子有两个在模型世界里是【海洋】（§476 三）**：北美季风（112–105W/25–33N）与南美季风（65–50W/20–10S）都返回 `NaN`（`n==0`）。原因：**盒子坐标取自地球，模型的世界是自己的** ⇒ **有效留出集实际只有 2 个**（澳洲、非洲南部）。**本条对前面所有盒子型探针同样成立** | **已记账**：所有盒子判据都必须先声明该盒子在模型世界里是不是陆地 |
| 123 | **★★★★★★ 目标第 (4) 项完成：常驻季节循环相位门 `P683` 接入验收套件（§477）**。`rerun_acceptance.ps1` 门数 **17 → 18**；输出 `GATE_PHASE_ALL` / `GATE_PHASE_HOLDOUT` / `GATE_VERDICT` 三行机器可 diff 的判据。首次读数：**亚洲 OK、撒哈拉【反相 ✗】、澳洲（留出）OK、非洲南部（留出）OK** ⇒ `GATE_PHASE_ALL=3/4`、`GATE_PHASE_HOLDOUT=2/2`、**`GATE_VERDICT=FAIL`** | **已接入**。★ **留出集 2/2 全过且从未调过** ⇒ **季节机制在南半球是对的**；唯一失败的是撒哈拉，**正是 §403 那个「恒湿」缺陷**，现在被常驻门稳定抓住 |
| 124 | **★★ 相位门把待裁决分叉变成【可判定】问题（§477 三）**：判据三行 —— ① `GATE_PHASE_HOLDOUT` 必须保持 **2/2**（不许靠牺牲南半球换北半球）；② `GATE_PHASE_ALL` 要从 **3/4 → 4/4**（只有撒哈拉那一格要翻）；③ 撒哈拉**幅度**要从 **2.028 → 观测的 0.287 量级** | **已建立**：乙₁/丙/丁 三个选项都用同一组判据评判 |
| 125 | **★★★★★★ 相位门失败项的逐项分解：失败由撒哈拉 JJA（29 倍）支配，不是 DJF；而 JJA 的涡动贡献是 0（§478，P684）**。撒哈拉 **JJA**：主项 **3.054** = 终值（**涡动增量 0.000**）、`wEff = +1.31e−03`（**上升**）、`divU = −2.50e−06`（**辐合**）；撒哈拉 **DJF**：主项 0.334 → 终值 1.025（**涡动增量 +0.691，占 67%**）。亚洲 DJF 涡动 +0.084；南半球两盒 `abs(mfc)=0` ⇒ 增量 0 | **假设被推翻**：拿掉 DJF 涡动增量后撒哈拉是 3.054/0.334，观测 0.105/0.392 ⇒ **仍反相**。**⇒ 相位失败 100% 由撒哈拉 JJA 的 29 倍太湿支配**（= 目标原文那句），而它来自 `wEff>0` ⇐ `divU<0` ⇐ **cellPressure 的「陆海开关」散度场** |
| 126 | **★ 目标第 (3) 项的结论（§478 四）**：`eddyMfc` 的修正**不能**修好相位门（JJA 涡动贡献为 0）；它能修的是**撒哈拉 DJF 的量级**（拿掉涡动后 0.334 vs 观测 0.392，**很接近**）与 §441 记过的 **DJF 30~50N 偏大 3~5 倍** | **第 (3) 项仍值得做**（有独立物理判据），但**不是相位门的解** |
| 127 | **★★★★★★ 目标第 (2) 项的代码级体检：按现在的形式【不可能】给出 6~9 倍（§479）**。`Atmosphere.cellPressure` = `CELL_GAIN·carrier(φ − migDeg)·tropicGate(φ)·(KAPPA_MEAN − κ)`。**`(KAPPA_MEAN − κ)` 是静态的**（海正陆负）⇒ **符号全年由「海还是陆」决定，季节只把 `carrier` 剖面沿纬度推 8 度** ⇒ **撒哈拉两季都拿到负 `p'`（热低压）⇒ 都辐合 ⇒ 永远上升**，与 §478 实测（JJA divU = −2.50e−06、wEff = +1.31e−03）一致 | **结论性**：第 (2) 项换的是**位相**，而病灶在**符号结构**。要翻号，驱动量必须①能随季节翻号②能区分沙漠与季风区③除以 `M`。**唯一同时满足的是 `F_net/M`** |
| 128 | **★★★★★ 第 (2) 项的正确形态 = `WZM_FROM_QNET`，且必须与 `Q_FROM_BLBUDGET`【配对】（§479 四）**：`w = +H_bl·F_net/M`。两个前提都已实现并实测：`Q_FROM_BLBUDGET` ⇒ `M` 两盒都正（**+9.994e7 / +2.452e7**，§471）；§456 已翻正的 `F_net` ⇒ **亚洲 +7.2e−5（上升）/ 撒哈拉 −7.3e−4（下沉）**。判据：`GATE_PHASE_ALL` 从 **3/4 → 4/4** 且 `GATE_PHASE_HOLDOUT` 保持 **2/2** | **待实现**。与 §444/§456 同型：**一个开关的物理前提由另一个开关提供**。⚠ `F_net` 含 `qLat = LV·P` ⇒ 成环 ⇒ **只做一次前向代入，不迭代**（§464/§466 纪律） |
| 129 | **★★★★★★★ `WZM_FROM_QNET` 落地：相位门 3/4 → 4/4，撒哈拉 29 倍 → 6.1 倍（§480，P685）**。`wEff` 的纬向平均项换成 `+H_bl·F_net/M`（`F_net = qRad+qSens+qLat`，`qLat` 用旧闭合估的 P ⇒ **一次前向代入**；`M = ρ·H_EFF·Δh`）。实测相位门：A 基线 **3/4**（撒哈拉 3.054/1.025 **反相**）→ **C 仅 `WZM_FROM_QNET` 4/4**（撒哈拉 **0.637/0.911 同相 ✓**）、**D 配对 4/4**、**E D+V+PZREF 4/4**；`HOLDOUT` 全程 **2/2** | **已实现**（默认 OFF，`P293 +9.6531e-07` ✓）。★ **撒哈拉 JJA 3.054 → 0.637（观测 0.105）⇒ 29.1 倍 → 6.1 倍**，是本段第一次让目标数字往正确方向动，**且不是靠拟合常数**。⚠ 但亚洲 JJA 从 2.972 掉到 0.405 ⇒ JJA 比回到 0.64~0.98（相位与比值**此消彼长**）|
| 130 | **⚠⚠ 实现缺陷：配对并没有让 `M > 0` 逐点成立（§480 四）**：`qnetNegM = 73 / qnetCalls = 268` ⇒ **27% 的点因 `M <= 0` 回落旧闭合**，且**开了收支口径（D）后数字完全相同**。⇒ §471 的「`M` 两盒都正」是**盒均**结论，**逐点 27% 为负** | **未查**：下一轮必须先查这 73 个点的分布（沙漠？高纬？海洋？）。**在此之前 C/D/E 只能当方向性证据** |
| 131 | **★★★★★★ `M <= 0` 全局是 66.2%，且抓到自己的接线错误（§481，P686）**。全局网格 1296 次：**858 次 `M<=0`（66.2%）**，陆 457 / 海 401，**随纬度单调增加**（0–5:12 → 80–85:144）。★ **配置 C 与 D 的 18 个桶逐位相同** ⇒ 读 `mmPerDay` 顺序发现：**`wEffQnet` 在收支块【之前】被调用** ⇒ 它拿到的是**收支之前**的 `q` ⇒ **「必须与 `Q_FROM_BLBUDGET` 配对」在代码里没有兑现**（我实现时的顺序错误） | **已定位** |
| 132 | **★★★★★★ 更根本：`M > 0` 是一个【有限有效域】（§481 三）**：`M>0 ⟺ L(q_BL − q_FT) > 19704 ⟺ q_BL > 0.00846 kg/kg` ⇒ **`F_net/M` 只在暖湿热带成立**；副热带干区与高纬**结构上 `M<0`**（与文献「负 GMS 区=对流自持区」一致）。**⇒ 要修的沙漠恰好落在 `M<0` 一侧 ⇒ `F_net/M` 在那里退回旧闭合，而旧闭合正是「陆海开关」（§479）** | **已定位**。这解释了 §480 的「相位翻正但量级塌掉」：翻正的是 33.8% 里含关键区域的部分，塌掉的是被退回旧闭合的部分。**下一步**：① 修接线顺序；② 给 `M<0` 一个显式处理（①只在 `M>0` 域启用并声明；② `q_FT` 改用 `qSat(T_FT)·RH_FT` 使 `q_FT` 不再 ∝ `q`；③ 承认两层口径不足，需要真垂直剖面） |
| 133 | **★★★★★★ FT 高度扫描：§480 的「相位 4/4、29→6.1 倍」不是稳健的物理结果（§482，P687）**。`M_FT_FRAC` 从 0.15 扫到 0.50：**解析阈值 `q_BL` 0.00241 → 0.00804**、**`qnetNegM` 44.0% → 69.4%（单调）**、**相位 4/4 ↔ 3/4 翻转**；亚洲 JJA 0.509→1.568、撒哈拉 0.712→2.303 | **自我纠正**：§480 那条被降级为「建模选择下的读数」。**三条独立证据**：① 配对没接上（`wEffQnet` 读的是收支【之前】的 `q`，§481 二）；② `M` 符号由 `zFT` 控制（本节）；③ 结果还随 S3 变（P685-C S3关 0.405/0.637 4/4 vs P687 S3开 1.568/2.303 3/4） |
| 134 | **★★★★★★ 结论：两层 `M` 不足以支撑 `F_net/M` 闭合（§482 四）**。要它成立必须有**真正的垂直剖面**（`Ω₁(p)` 与 `h(p)`），即 Neelin 原文的 `M = ⟨Ω ∂_p h⟩`（§453 逐字）—— 两层把它退化成一个受旋钮控制的数 | **结论性**。与本段另外三条合流：§441-32（P 无记忆/无输送/无反馈）、§468/§469（每个更正确机制都中性或负）、§473（唯一方向对的是输送，强度要拟合长度）⇒ **四条都指向：这个架构里没有一个被物理约束的垂直/水汽状态** |
| 135 | **★★★★ 目标第 (3) 项第一步撞上【仪器口径不符】（§483，P688）**：`ZonalTables.eddyMfcObsMonth(latDeg, theta)` 的输出与 `CALIBERS` §3.3 自己的登记**不符** —— ① **量级差 ~10⁶**（模型 ~1e−4，访问器给 ~1e2，而登记的是「45~60 度年均值为 1」）；② **季节符号相反**（访问器在 25~55N 给 DJF 全负、JJA 全正，而物理上 DJF 35~55N 应为辐合）。⇒ 目标第 (3) 项的「3~5 倍」**在查清这两条之前无法判定** | **未查**：需查访问器的量纲与 `theta` 约定（半周期相位平移是否已在访问器内做） |
| 136 | **★★★ 但模型自己的 eddyMfc 那一列可读，且定性正确（§483 四）**：符号翻转纬度 **DJF ~33N**（25~32.5N 辐散 → 35~55N 辐合）、**JJA ~41N**（27.5~40N 辐散 → 42.5~55N 辐合）⇒ **副热带辐散 + 中纬风暴轴辐合，且风暴轴随季节向极地迁移 8 度**，与物理及 §433 之后的应有形状一致 | **结论性**：第 (3) 项要修的是**量级**，不是**形状** |
| 137 | **★★★★ ★ §483 的仪器错误已修：`eddyMfcObsMonth(double latRad, double theta)` 的**第一个参数是【弧度】**（我按 CALIBERS 注解传了 latDeg ⇒ 25~55 被当弧度 ⇒ interp5 外推 ⇒ 假的 −255~−582）。**§483 二 的观测列作废**；正确调用后观测列是 −1.99~+1.85 的**无量纲**值（45~60 度年均值 1，与登记一致） | **已修**（我的第 6 次同类错误）。§483 四（模型那一列可读且定性正确）仍成立 |
| 138 | **★★★★★★ 目标第 (3) 项定案：量级问题已不存在（§484，P690）**。两边都归一化（模型 45~60N 年均 `M_ref = +3.4897e−05 kg/(m²·s)`）后：**DJF 30~50N 平均 模型/观测 = 1.08**（§441 记 3~5 倍）、**JJA = −1.06**、DJF 符号正确 **8/9** | **⇒ 「DJF 30~50N 偏大 3~5 倍」在 §429 + §433 之后已被修掉 ⇒ 第 (3) 项量级已达标**。剩余是**形状/位置**：① **DJF 符号翻转纬度偏南 3 度**（模型 ~33N vs 观测 ~36N ⇒ 35N 比值 −2.91）；② **JJA 45~50N 符号相反**（模型辐合 +4.18e−5 vs 观测辐散 −0.45 ⇒ 比值 −2.65/−4.44/−7.74）⇒ **JJA 风暴轴向极迁移不足**，是 §433 的延伸。⚠ 与 §478 一致：**修好也不会翻动主判据**（撒哈拉 JJA 的涡动贡献是 0） |
| 61 | **环路增益随强迫【下降】**（§449 三）：`k=0.02` → **0.233**；`k=0.03` → **0.218**。真正接通的回路增益应随强迫幅度持平或上升 ⇒ 反馈支路基本是断的。要到观测的 72.76 需要增益 ≈ **0.95** | **已记账**：缺口是 **4 倍增益**，不是某个常数 |
| 62 | **★★★ A+B 必须一起做，且 A 单独做【保证错】（§450 解析 go/no-go）**。用 `P(1) = κ·P_now`（`κ = 0.80/rhEff(β)`，因 `A` 与 β 无关）与 §439 实测 `E_p(1)/P(1)`：`κ_亚洲 = 2.2763`、`κ_撒哈拉 = 2.1494`，`E_p(1)` = **27.4 / 20.7 mm/day**（与 P 无关）。⇒ ① **B 单独**（P 到观测）亚洲 `F(1) = 0.637 < 1` ⇒ **仍无湿支**；② **A 单独**（E_p 缩 f 倍）亚洲需 `f > 4.050`、撒哈拉只需 `f > 3.151` ⇒ **撒哈拉先湿，差 22%**；③ 窗口存在 ⟺ `P_亚洲/P_撒哈拉 > 1.252` | **结论性**：现在比 0.97（无窗口）、B 到现状 3.54 ⇒ 窗口 **f∈(2.28, 34.5)**、B 到观测 73.0 ⇒ **f∈(1.57, 91.6)**。§439 估的表面阻力 2~5 倍**落在窗口内** |
| 63 | **⚠ A 要与 §440 的否证划清界限：必须做成【状态依赖】。**§450 的 `f` 是均匀乘子，而 §440 的 `RS_SURF` 是**全局常数** —— 均匀干预按 §440 第 30 条必然只放大排序。状态依赖时逐盒判据不需要均匀性（亚洲只需自己降 1.57 倍，撒哈拉只要不降 91.6 倍） | **未实现**：植被态 → r_s / 反照率 / 粗糙度 |
| 64 | **⚠ 双稳不能用一次 A/B 判**（§450 五 2）：盒均 `F(1)>1` 只是**必要条件筛查**，`A/e1/e2` 逐点不同 ⇒ 双稳是**场的现象**。判决仪器必须是**定点迭代 + 参数上下扫描的滞后环**。另：§439 的根计数说 `R(β)` 是 β 的二次式、当前只有 ≤1 根 ⇒ **当前无双稳**，B+A 之后必须重跑 | **仪器要求**，已记账 |

---

## 11. §776/§777 补齐：`PrecipField` 与 `ZonalTables` 里 15 个未登记的 `public static` 生产者

**为什么有这一节**：`calibers_check.ps1` 的 D67/D75/D78 棘轮（§10）报 `NEW UNREGISTERED MEMBERS = 15` ——
「出现了 `public static` 生产者，但它不在任何登记表里」。本节把它们逐条登记。
判据是 `calibers_check.ps1:123-124` 的**整词出现**（`\bname\b`），以及 `:100-113` 的 `类名.成员名` 必须可解析。
**本节只写已经逐字读过的出处，不引用任何未读过的文献。**

### 11.1 `sim.atmos.PrecipField`（13 条）

| 成员 | 含义 | 单位 | 口径/性质 | 出处/锚 | 消费者 |
|---|---|---|---|---|---|
| `PrecipField.wStarK` | 对流速度尺度 `w_*` | m/s | `cbrt((g/T_s)·H_BL·flux)`；**通量 <= 0（稳定层结）时返回 0** —— `w_*` 按定义只对**对流**混合层成立。**式内无任何经验常数** | Deardorff 闭合；`z_i := Atmosphere.H_BL`、`(w'theta_v')_0 := surfaceBuoyancyFluxK`（`PrecipField:300-312` 逐字） | `mmPerDay` 的浅对流地板 |
| `PrecipField.surfaceBuoyancyFluxK` | 近地面**运动学**浮力通量 | K·m/s | `cdOf(kappa)·vEff·(thvS − thvA)`，`thv = T(1+EPS_V·q)`。**陆海通用**（吃 `kappa`、用**本地**皮温）⇒ 不是海面专属量 | 本类（`wStarK` 唯一的 flux 来源，`PrecipField:357-364`） | `wStarK` |
| `PrecipField.airTempK` | **统一口径**的近地面气温 `T_a` | K | 按大陆度把**海洋锚**与**陆地反解**线性混合：`kappa=0` 取纯海洋、`kappa=1` 取纯陆地 | 海洋侧 `T_s + ZonalTables.dtAirSea(lat)`（§645）；陆地侧 `landAirTempK`（§650）；`PrecipField:342-355` | `surfaceBuoyancyFluxK`、`wStarK` |
| `PrecipField.landAirTempK` | 陆地侧 `T_a` 的**闭式反解** | K | `T_a = T_s − (absSolar − OLR(T_s) − LE(T_s))/(chv·CP)`；`chv = cdOf(kappa)·vEff`，`chv<=0` 时返回 `T_s`。**O(1)、无迭代**（对比 `Radiation.skinTempLand` 的 60 次二分） | 本模型**自己**的能量平衡 `Radiation.residual`（§650；`PrecipField:315-340`） | `airTempK` |
| `PrecipField.EPS_V` | 虚温系数 | 无量纲 | **0.608**（`PrecipField:246`，`public static final`）。即 `R_d/R_v − 1` 的口径 | 定义式 | `thv` 的两处（本类） |
| `PrecipField.EP_COND` | **降水效率 `E_P`** | 无量纲 | **0.24**（原著区间 0.19~0.29，本轮读图）。**§244.4 明令不许为了转绿调它** | Liu et al., Sci. Adv. 10, eado2515 (2024) **Fig. 3D**（原图存 `refs/fig3_page5.png`） | `mmPerDay` 的浅对流**凝结**地板 |
| `PrecipField.SIGMA_UP` | **上升气流面积占比 `sigma_up`** | 无量纲 | **0.065** | Siebesma et al. (2007)，冻结 §667 逐字引文 | 同上 |
| `PrecipField.Z_CT_COND` | **浅积云云顶 `z_ct`** | m | **2500.0**。⚠ **不可从模型热力学导出**（§676 实测：`GAMMA = 6.5 K/km` **大于**热带湿绝热 ⇒ 气块一路比环境暖 ⇒ `zLnb == zMax`；**模型没有信风逆温层**，而浅对流云顶正由它决定）。§687 用有出处三件套重验：**2000~3500 m 整区间两门均 PASS**（rJJA 0.69~1.26） | Squires (1958) / Byers & Hall (1955)，经 Rauber et al., BAMS 88(12), 1913 (2007) 逐字转述「maritime clouds with tops **greater than 2500 m** 'usually rain within half an hour'」（本地副本 `refs/rico_bams.pdf`，截图 `refs/rico_bams.txt`） | 同上 |
| `PrecipField.SHALLOW_CONDENSATE` | 开关 | `bool` | 地板用**凝结形式**（Held & Soden，`true`，**当前生产值**）还是旧的**蒸发形式**（`false`）。进 `configStamp` | §679 A/B 臂 B（§677 文献形式） | `mmPerDay` |
| `PrecipField.SHALLOW_FLOOR_BLQ_GATE` | 开关 | `bool` | **§772 新增，默认 `false`**：浅对流地板是否受 `BLQ_THETA_E`（**深对流**判据）门控。`false` = **撤回 §720 的 `pFloor *= blqG`**。§774 完整 A/B：`GATE_SUBTROP_ZERO` FAIL(14.2%)→**PASS**、陆盒 JJA 0.000→1.712/1.823。进 `configStamp` | §772/§774 —— **category error**：深对流判据不得门控浅对流过程 | `mmPerDay` |
| `PrecipField.ALPHA_COND` | ⚠ **死常数** | 无量纲 | **0.195，无人读**；保留只为记账 | §741 已标 `@Deprecated` 并写明「改它不会改变任何输出」；§244.4 记 `ALPHA_SH` 是「被拟合到旧形式」的先例（`PrecipField:2184-2188`） | **无** |
| `PrecipField.Q_BLBUDGET_SUB_ONLY` | 开关 | `bool` | **`true`**：边界层水汽收支的干平流只取**下沉枝**（`divU>0`） | S628（旧式 S627 是两枝都干；`PrecipField:484`） | `mmPerDay` 的水汽收支 |
| `PrecipField.BETA_DOWNDRAFT` | 下沉气流补偿系数 | 无量纲 | **0.5**；`kPb = (wE>0) ? (1−beta)·EPS_C·rho·wE/rho_w·86400·1000 : 0` | S630（`PrecipField:505`） | 同上 |

### 11.2 `sim.atmos.ZonalTables`（2 条）

| 成员 | 含义 | 单位 | 口径/性质 | 出处/锚 | 消费者 |
|---|---|---|---|---|---|
| `ZonalTables.DT_AIR_SEA_K` | 海气温差 `T_air − T_sea` 的**纬向表** | K | **负值 = 空气比海冷 = 海洋表面对流不稳定**。⚠ **已知限度（不许日后悄悄当实测用）**：**60~90 度是外推值、不是观测**（NCEP R1 与 COBE-SST2 在**海冰区**的差不是海气温差 —— SST 被钉在冰点而 1000 mb 空气极冷；+60 度实测 −3.40 K、−85 度曾达 −25 K；**本世界没有海冰** ⇒ 50 度以外**保持 50 度的值**）；且两源**非完全独立** ⇒ 本表是**气候态锚**，不是独立验证 | NCEP R1 + COBE-SST2（口径与限度声明见 `ZonalTables:182` 的 javadoc） | `dtAirSea` |
| `ZonalTables.dtAirSea` | 查表插值 `T_air − T_sea` | K | `interp(DT_AIR_SEA_K, latDeg)`；**参数是纬度（度）**，不是弧度 | 同上（`ZonalTables:190`） | `PrecipField.airTempK` |

**核验方式**：重跑 `calibers_check.ps1`，`NEW UNREGISTERED MEMBERS` 应回到 **0**，
`acceptance-probes` 应仍为 **22**，`registered-patterns` 应仍为 `resolved`（不出现 `(not found in source)`）。


---

## 12. §7122：`P477` 的两门判据已更正（跨盆无权中位 → 最强盆）

**为什么改**：`GATE_A5_WARM` / `GATE_A5_COLD` 原来的统计量是「**跨盆无权中位**」，
而它们的锚（`P477:72` / `:153`，出自 `SeaSurfaceTemp` 的 javadoc）描述的是**一条**最强的西边界流
（湾流/黑潮暖舌 +4~8 K、加州/亲潮冷舌 −4~5 K）。
「跨盆无权中位」报的是「这个 seed 生成了多少个小海盆」，不是「西边界流有多强」。

**普查证据**（§771，`P993` S769b，逐盆表）：

```
暖 WBC 盆 5 个：强 2（+7.587、+5.964）+ 弱 3（+3.037、+1.382、+1.363）
                  ⇒ 排序第 3 位 = +3.037 = 中位，**恰好是弱盆**
冷 WBC 盆 4 个：强 2（-6.323、-5.028）+ 弱 2（-1.804、-1.799）
                  ⇒ 中位 = (-5.028 + -1.804)/2 = -3.416
**幅值对盆地宽度在每个已分类集合内严格单调**（暖 5/5、冷 4/4）
强盆 = 大盆（15440/2880 km；12755/4880 km），弱盆 = 小盆（510~1060 km；585~1560 km）
```

**新判据（§771 §七 第 9 轮冻结，§7122 执行）**：

```
GATE_A5_WARM ：暖 WBC 盆中 **|暖峰| 最大者** 落在 +4~8 K 内
GATE_A5_COLD ：冷 WBC 盆中 **|冷峰| 最大者** <= -4.0 K
GATE_A4_SCALE：不变（暖舌 FWHM 中位 >= 50 km）
```

**A/B 结果（§7122）**：

```
目录  A 侧 = rerun_acceptance\D968C9E3_64E20400_TALOS
      本次 = rerun_acceptance\0FA41ECE_AD7F9C58_TALOS   （只改探针 P477.java，SRCFP 未变）
实测  最强盆暖峰 = **+7.587 K** ∈ [4,8]   ⇒ PASS   （预测 +7.587，逐位命中）
      最强盆冷峰 = **-6.323 K** <= -4.0  ⇒ PASS   （预测 -6.323，逐位命中）
      **其余门一门不动**（除 P477 两门外 27 门逐位相同）
套件  30 门：26 PASS / 4 FAIL  ->  **28 PASS / 2 FAIL**
      （P296 三门为长尾，改动前后均 PASS）
```

**★ 记账口径（不许含糊）**：这两门 PASS 是「**判据口径已更正**」，**不是**「缺陷已修复」——
模型侧读数一个数都没变（暖峰中位仍 +3.04、冷峰中位仍 −3.42）。

**引用本条的场合**：任何以 `P477|GATE_A5_WARM` / `GATE_A5_COLD` 为证据的论断，必须注明它们用的是
**最强盆**口径（v3），不能与 v2 的「跨盆中位」读数混用。
