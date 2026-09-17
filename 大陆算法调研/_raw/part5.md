## 附录 A · 把「噪声 → 类地大陆」落成 Java 的具体配方（含参数）

> 本附录是本文档的落地核心，综合了条目 1 / 3 / 17 / 27 / 29 的做法。所有参数为**建议起始值**，需按附录 C 的脚本标定。

### A.1 指标 → 参数 对应表

| 目标指标 | 由哪一层负责 | 具体参数 |
|---|---|---|
| 双峰高程分布 | **样条层**（唯一负责） | 样条折点中的两段水平段 |
| 陆地占比可标定 | **大陆掩码层** | 掩码阈值 `seaThreshold`，或渗流覆盖率 |
| 内海数 = 0 | **掩码层（结构性保证）** | 用"圆盘并集"构造，见 A.3 |
| 无散落小岛 | **掩码层（结构性保证）** | 同上 + 最小圆盘半径 |
| 海岸线 D ≈ 1.2 ~ 1.4 | **域弯曲层 + 细节层** | 弯曲幅度 / 高频振幅 |
| 大陆架 / 大陆坡 | **样条层** | 折点 -0.35 → -0.22 的斜率段 |
| 山脉 | **山脉层** | ridged + 山脉掩码 |
| 岛弧 / 海沟 | **cell 层**（条目 29） | 板块边界窄带重映射（可选，见 A.6） |

**关键分工原则：一个指标只由一个层负责。** 混在一起调参必然失败。

### A.2 五层管线

    L0 坐标层      ：(x, z) → 周期性坐标（附录 B），域弯曲只作用于 x
    L1 掩码层      ：M(x,z) ∈ [-1,1]，波长 8000~20000 格，决定"这块地方是陆还是海"
    L2 样条层      ：C = blended(M, 高频大陆噪声)  →  S(C) = 相对海平面的高度（格）
    L3 山脉层      ：h += mountainMask(C, erosion) × ridgedAmp × ridged(x,z)
    L4 细节层      ：h += 低频侵蚀噪声 + 高频微起伏（振幅 ≤ 2 格）

### A.3 内海 = 0 的**结构性**构造（本附录最重要的部分）

**纯噪声 + 样条无法保证内海为 0。** 原因：continentalness 是逐点独立求值的标量场，它的下水平集（海洋）可以分裂成任意多个连通分量。任何靠"调参"压制内海的做法都只是降低概率，不是消除。

**能结构性保证的做法：用"圆盘并集"定义陆地。**

设 `F1(x,z)` = 到最近**核心点**的距离（即条目 29 的 Cellular F1），`R` = 该核心点的半径（由低频噪声决定，使大陆有大有小）。定义：

    陆地  ⟺  F1(x,z) < R(x,z)

**数学性质：**
1. 陆地 = 一族圆盘的并集。**每个连通分量都是单连通的**（若圆盘不围成环）。
2. **海洋 = 圆盘并集的补集。在平面上，有限个不围成环的拓扑圆盘的并集的补集是道路连通的。** → **内海数 = 0，是定理，不是调参。**
3. **孤岛 = 不与主大陆圆盘团重叠的孤立圆盘。** 只要把"孤立圆盘"的半径抬到渗流阈值以上（或直接合并到最近的大陆团），孤岛数 = 0，同样是结构性的。
4. **圆盘重叠 → 大陆连通。** 覆盖率（`λπR²`，λ 为核心点密度）超过**连续渗流阈值**时，圆盘团会连通成无限（或极长）的大陆。2D 圆盘布尔模型的临界覆盖率是一个经典结果，**约 0.67 ~ 0.68**；**建议你自己用离线脚本测出你实现下的实际阈值**，因为抖动网格与泊松点过程的阈值不同。
5. **唯一需要小心的情况**：3 个以上圆盘围成一圈会**围出一个洞 = 一个内海**。**对策**：让半径分布不要有"恰好围成环"的尺度，或者运行时做一个极廉价的检测（对每个 16×16 块，检查 3×3 邻域的核心点是否围成环）——这仍是 O(1)。

**为什么这一招对本项目特别合适：**
- 全部 O(1)：F1 只需查 3×3 = 9 个抖动网格单元（条目 29）。
- 双峰由 **L2 样条**保证，与本层无关，**互不干扰**。
- 陆地占比 = 直接调 `R` 的均值或覆盖率，**标定极其直观**。
- 海岸线形状由圆盘并集的边界给出，**再用域弯曲（只弯 X）揉一次**，D 自然进入 1.2 ~ 1.4。

**与"少数几块互相连通的大陆"的对应：** 覆盖率略高于渗流阈值时，圆盘团会形成**一两个巨大的连通分量 + 若干小的**；把小于阈值的分量直接并入最近的大分量（离线标定 R 的分布），即得"少数几块互相连通的大陆"。

### A.4 建议的默认参数（1.7.10 / 世界高度 256）

**世界层级**（照抄条目 3 的 TerraForged 结构）：

| 常量 | 值 | 说明 |
|---|---|---|
| `MIN_Y` | 0 | 1.7.10 硬约束 |
| `SEA_LEVEL` | 64 | 海平面 |
| `SEA_FLOOR` | 24 | 深海盆地底（-40 格） |
| `BASE_HEIGHT` | 76 | 大陆台地（+12 格） |
| `MAX_Y` | 200 | 山脉上限 |

> **垂向压缩说明**：地球双峰高差 4.3 km，本项目只有 52 格可用（-40 到 +12），压缩比约 1:83。这是 1.7.10 的 256 高度限制决定的，**不可回避**。好在玩家感知的是"海底很深、台地很平"的比例关系，不是绝对海拔。

**L2 样条折点 S(C)（单位：格，相对海平面）**：

| C (continentalness) | S(C) | 地貌 |
|---|---|---|
| -1.00 | -40 | 深海盆地 |
| -0.60 | -40 | ← **盆地平台（水平段）** |
| -0.35 | -18 | 大陆坡（陡降段） |
| -0.22 | -6 | ← **大陆架平台（水平段）** |
| -0.10 | -3 | 浅海 |
| -0.02 | 0 | 海岸线（海平面） |
| 0.00 | +3 | 海滩 |
| 0.30 | +12 | ← **大陆台地平台（水平段）** |
| 0.65 | +34 | 内陆抬升 |
| 1.00 | +52 | 交给山脉层 |

**这三段水平段就是双峰 + 大陆架 + 大陆台地的全部来源。** 折点间的插值用**单调三次（monotone cubic / Fritsch–Carlson）**，不要用普通 Catmull-Rom——后者会过冲，在平台段产生假的高低起伏。

**各层频率与振幅**：

| 层 | 噪声 | 基准波长（格） | 倍频数 | lacunarity | 振幅表 | 备注 |
|---|---|---|---|---|---|---|
| L1 掩码 | Cell F1（条目 29）的 R 场 | 16000 | 3 | 2.0 | [1, 0.5, 0.25] | 只在核心点半径上做低频调制 |
| L2 大陆 | CubicNoise（条目 14） | 8000 | 5 | 2.0 | [1,1,0.8,0.5,0.3] | 用于把掩码边界揉出细节 |
| L3 山脉 | OpenSimplex2**S**（条目 13） | 1200 | 6 | 2.0 | [1,0.5,0.25,…] ridged | **必须用 2S** |
| L4 侵蚀 | OpenSimplex2 | 600 | 3 | 2.0 | [1,0.5,0.25] | 决定山脉"碎不碎" |
| L4 细节 | CubicNoise | 64 | 2 | 2.0 | [1,0.4] | 振幅 ≤ 2 格 |

**混合公式**：

    // L1: 掩码（结构性构造，见 A.3）
    F1, cellId  = cellularF1(x, z)                 // 查 3×3 抖动网格
    R           = Rbase * (1 + 0.6 * lowFreqNoise(cellId))   // 大陆有大有小
    M           = smoothstep(R + W, R - W, F1) * 2 - 1        // W ≈ 300 格，边界宽带
    //   ⚠️ smoothstep 让 M 快速饱和到 ±1 → 减少"半陆半海"的过渡带 → 减少内海与小岛

    // L2: 大陆度 = 掩码 + 高频细化
    C           = 0.75 * M + 0.25 * fbmContinental(x, z)      // 0.75 是关键：掩码占比越高，海陆越连片
    S           = monotoneCubicSpline(C)                       // A.4 的折点表，单位：格
    h           = SEA_LEVEL + S

    // L3: 山脉（只在陆地上）
    erosion     = fbmErosion(x, z)                             // ∈ [-1,1]
    mountainMask= smoothstep(0.05, 0.45, C) * (1 - 0.7 * (erosion * 0.5 + 0.5))
    h          += mountainMask * ridgedAmp * ridged(x, z)      // ridgedAmp ≈ 40 格

    // L4: 细节
    h          += 2.0 * fbmDetail(x, z)

    // 最终
    height     = clamp(h, MIN_Y, MAX_Y)

### A.5 域弯曲（只弯 X，保护 Z 周期）

    // 位移场：低频、平滑、只影响 x
    double q = fbmWarp(x - 1234.5, z + 6789.0);   // ∈ [-1,1]
    double xw = x + WARP_AMP * q;                 // WARP_AMP ≈ 150 格
    double zw = z;                                // ← Z 一动不动
    // 之后所有噪声都用 (xw, zw) 采样

**幅度规则**：`WARP_AMP × 最高倍频波长 ≥ 32 格`（保证不破坏带限性），且 `WARP_AMP ≤ 0.3 × 被修饰特征的最小尺度`（保证不把大陆揉碎）。按 A.4 的参数：最高倍频波长 = 1200/32 = 37.5 格，`150 × 37.5` 远超 32，安全。

### A.6 岛弧与海沟（可选增强）

若必须满足"岛弧"指标，在 L1 之上加一层（完整配方见条目 29 第 10 点第 2 小点）：

    edge   = F2 - F1                                        // 板块边界带
    band   = smoothstep(0.15 * spacing, 0.0, edge)           // 只作用在边界窄带
    kind   = hash(cellId1, cellId2)                          // 边界类型
    arc    = (kind < 0.5) ? band * (+25 格) : 0              // 汇聚边界 → 岛弧抬升
    trench = (kind < 0.5) ? band * (-15 格) : 0              // 同一带外侧 → 海沟下沉
    ridge  = (kind >= 0.5) ? band * (+8 格) : 0              // 离散边界 → 洋中脊
    h     += arc + trench + ridge

**注意**：`band` 是**窄带**（宽度 ≈ 0.15 × 板块间距），它只在样条给出的背景高度上加一个局部凸起/凹陷，**不会破坏双峰分布**。

### A.7 常见错误清单（按踩坑概率排序）

1. **把域弯曲幅度设成"几格"或"几千格"。** 前者看不见效果，后者把大陆揉碎。正确量级 = 特征尺度的 5% ~ 20%。
2. **lacunarity 用 2.0137 这类"去伪影小数"。** 在无限世界没问题，但在**周期轴（Z）上会导致各层周期不一致**，接缝处出现断层。**周期轴必须用 lacunarity = 2.0。**
3. **全图叠加 ridged 噪声。** 海里会长山。必须乘山脉掩码。
4. **样条用普通 Catmull-Rom。** 平台段会过冲，双峰被破坏。用**单调三次**。
5. **忘了高维噪声的振幅补偿（×√1.5）。** 若用圆柱嵌入，海陆比例会整体偏移。
6. **用 float 运算到远坐标。** X 无限时，`float` 在 |x| > 2^24 后无法表示 1 格精度。**必须用 double，或把坐标按块分割。** 见附录 B.4。
7. **梯田化（terracing）的台阶宽度 < 块宽。** 台阶会在块级采样中消失或闪烁。台阶宽度 ≥ 64 格。
8. **用 value noise 做山脉主脊。** 会看到方形格点。value noise 只用于低频场。
9. **没有单调性检查。** 写完样条后**必须**验证 `S(C)` 在折点区间内单调（除非刻意要海沟回升）。见附录 C。
10. **把"陆地占比"当独立参数调。** 陆地占比是掩码层的**输出**，不是输入。改的是覆盖率/阈值，然后测量占比。

---

## 附录 B · Z 轴周期 20,000,000 的实现方案

### B.1 方案 A：整数晶格取模（**本项目推荐**）

**原理**：梯度噪声的值由"所在晶格单元 + 小数偏移"决定。只要把**周期轴上的晶格索引取模**，晶格本身就循环，噪声因而严格周期。

    // 采样点 (x, z) 格坐标；f = 当前倍频的晶格密度（单元/格）
    // 周期 P = 20_000_000 格；要求 N = P * f 为整数
    int ix = (int) Math.floor(x * f);
    int iz = (int) Math.floor(z * f);
    iz = Math.floorMod(iz, N);          // ← 周期轴唯一需要的一行
    // ix 不做任何取模（X 无限非周期）

**为什么它是对的：**
- 晶格索引循环 ⟹ 该索引上的哈希值循环 ⟹ 噪声值循环，周期恰好为 `N / f = P` 格。
- **接缝处数学上完全连续**：因为 `iz = N-1` 的右邻居就是 `iz = 0`，与内部任何一对相邻单元的处理方式完全相同，**不存在特殊情况**。
- **零额外成本**：一次整数取模（N 取 2 的幂时可用 `&` 代替，更快）。

**必须满足的条件：**
1. **每个倍频都要取模，且周期单元数要整除。** 第 k 层：`N_k = N_0 / L^k` 必须是整数 ⟹ **取 `L = 2.0`，`N_0` 取 2 的幂**。这是最省心的组合。
2. **`N_0 = P * f_0` 要为整数。** 取 `f_0 = 1/512`（即第 0 层晶格间距 512 格），则 `N_0 = 20,000,000 / 512 = 39,062.5` —— **不是整数！** 
   **修正**：选 `f_0` 使 `P * f_0` 为 2 的幂。`P = 20,000,000 = 2^7 × 156,250 = 2^8 × 78,125`。`78,125 = 5^7`。所以 `P = 2^8 × 5^7`。
   **最简单的做法**：令 `N_0 = 2^k` 且 `f_0 = N_0 / P`。例如取 `N_0 = 2^16 = 65,536`，则 `f_0 = 65536 / 20,000,000 = 0.0032768`（晶格间距 ≈ 305 格）。**这不是整数比，但没关系——取模用的是 `N_0`，只要 `N_0` 是整数且 `N_0` 随倍频整除即可。真正的周期 = `N_0 / f_0 = P`，恒成立。**
   **也就是说：你不需要 `P` 是 2 的幂，只需要 `N_0` 是 2 的幂，且 `f_0` 由 `N_0 / P` 反推。**
3. **域弯曲必须同样周期化，或干脆不弯 Z。** 推荐后者（附录 A.5）。

### B.2 方案 B：圆柱嵌入（需要绕 Z 做弯曲时使用）

见条目 17 第 6 点的 Red Blob 代码。要点：
- 半径必须取 `1/TAU`（等距性）。
- 需要 **3D 噪声**。
- 输出乘 **√1.5** 做振幅补偿。
- 破坏带限性 → 最高倍频波长须 ≥ 32 格。

**本项目建议**：**默认用方案 A**。方案 B 只在"必须让地形特征绕 Z 弯曲"时启用。

### B.3 方案 C：4D 环面嵌入

只有 **X 和 Z 都需要周期** 时才需要。本项目 X 无限非周期，**所以永远不需要 4D**。记录在此仅为排除。

### B.4 X 无限带来的精度陷阱（**必须处理**）

- `float` 有 24 位尾数，|x| > 2^24 ≈ 16,777,216 后**无法表示 1 格的间隔**。X 无限意味着迟早越界。
- **MC 的"远地（Far Lands）"就是这个问题的体现。**
- **对策（按推荐度排序）：**
  1. **全程用 `double`。** Java 的 `double` 有 53 位尾数，在 |x| < 2^53 内都能表示 1 格。**这是最简单且足够的方案。**
  2. **噪声内部用相对坐标。** 在算噪声前把坐标减去一个"局部原点"（例如所在块的中心），差值永远是小数，精度无损。**这是 MC 自己用的方案。**
  3. **绝对禁止**把 `x * frequency` 的结果存进 `float`。`x` 大时这一步就丢精度了。
- **自检方法**：在 `x = 10^6, 10^7, 10^8, 10^9` 处采样同一个"局部图案"，比较是否一致。若 `float` 实现，`10^8` 处必然崩坏。

### B.5 Z 周期自检（写代码时必做）

    // 1) 连续性自检：接缝两侧必须连续
    for (double dz = -8; dz <= 8; dz += 1.0) {
        double a = height(12345.0, P/2.0 + dz);
        double b = height(12345.0, -P/2.0 + dz);   // 环绕后同一点
        assert Math.abs(a - b) < 1e-9;
    }
    // 2) 周期性自检：相隔整数倍周期的点必须完全相同
    assert height(x, z) == height(x, z + P);
    assert height(x, z) == height(x, z - P);
    // 3) 各倍频自检：每一层单独测，别只测总和（总和的误差会被掩盖）

**第 3 点是关键**：如果只测总和，某一层的周期错误可能被其他层掩盖，直到玩家走到接缝才发现。

---

## 附录 C · 指标自检工具（离线标定，不影响运行时 O(1)）

> 本附录的工具**只在开发期运行**，用来标定参数。它们**不进入运行时**，因此不违反"零预计算"约束。
> 建议实现为独立的 Java `main` 或 Python 脚本，输入是同一个噪声函数。

### C.1 采样网格

在 `[-1,000,000, +1,000,000] × [纬度带切片]` 上取 **1024 × 1024** 网格（每点间隔约 2000 格）。为了覆盖 Z 的整个周期，另做一组 `32 × 4096` 的"全纬度"采样。

### C.2 双峰高程分布

    histogram(heights, bins=256)  →  画直方图

**判定标准**：直方图应出现**两个明显的峰**，分别在海平面以下约 40 格与以上约 12 格附近；两峰之间（海岸带）应有明显的低谷。
**失败信号**：单峰、或三峰以上 → 样条的水平段没生效，或域弯曲把平台揉碎了。

### C.3 陆地占比

    陆地占比 = count(height > SEA_LEVEL) / total

**标定方法**：二分搜索掩码阈值，使占比命中目标（例如 0.30）。**不要把占比写死在噪声参数里。**

### C.4 内海数与孤岛数（**最容易做错的一步**）

    // 1) 二值化：高度 > SEA_LEVEL → 陆
    boolean[][] land = ...;
    // 2) 连通分量标记（4 邻域或 8 邻域，务必固定一种）
    // 3) 找出"接触采样边界"的分量 → 它们是外海
    // 4) 不接触边界的"水"分量 = 内海
    // 5) 不与最大陆分量相连的"陆"分量 = 孤岛

    ⚠️ 陷阱：采样网格的**分辨率决定了你能看到多小的内海/孤岛**。
    2000 格间隔的网格看不到 200 格的内海。**必须做多尺度检测**：
    用 16 格间隔采样一小块区域（例如 2048×2048 格 = 128×128 网格）做精细检测。

**判定标准**：内海数 = 0（或 ≤ 1 且面积占比 < 0.1%）；孤岛数 = 0（或都是"有意设计的大岛"）。

### C.5 海岸线分形维数 D（目标 1.2 ~ 1.4）

用**盒计数法（box counting）**：

    for (int boxSize : {2, 4, 8, 16, 32, 64, 128, 256}) {
        int count = 覆盖到的"含海岸线的盒子"数;
        log(count) 对 log(1/boxSize) 做最小二乘拟合，斜率即 D
    }

**关键细节：**
1. **必须在高分辨率网格上做**（盒最小到 2 格）。用 2000 格间隔的粗网格测不出 D。
2. **只统计海岸线像素**（陆地与水相邻的点），不要统计整张图。
3. **对多个不同区域分别测**，取中位数。单一区域的 D 波动很大。
4. **D 太高（> 1.5）** → 域弯曲过头，或高频振幅过大。**D 太低（< 1.15）** → 海岸线太平直，加大域弯曲幅度（附录 A.5）。
5. **D 与块级采样的张力**：如果 D 是靠"16 格以下的抖动"堆出来的，那么块级生成时这些抖动会消失，实际 D 会掉到 1.0 附近。**所以 D 必须在"16 格精度的最终高度场"上测，不是在连续函数上测。** 这是本项目最容易自欺的地方。

### C.6 单调性自检（样条）

    for (double C = -1.0; C <= 1.0; C += 0.001) {
        double d = S(C + 0.001) - S(C);
        if (d < -1e-9) record_violation(C);   // 非单调点
    }

**允许的非单调点只有一处**：海沟回升段（若你启用了条目 29 的海沟）。其余位置出现非单调 = 样条过冲，双峰被破坏。

### C.7 接缝自检

见附录 B.5。**每次改参数后都要重跑**，因为改频率可能破坏整除关系。

### C.8 性能自检

    // 生成 1000 个 16×16 块，测总耗时
    long t0 = System.nanoTime();
    for (int i = 0; i < 1000; i++) generator.fillChunk(i, 0, buffer);
    double usPerChunk = (System.nanoTime() - t0) / 1000.0 / 1000.0;

**预算参考**（按条目 11 引用的 FastNoiseLite 基准外推）：纯 Java 实现下，**每块 20 ~ 100 微秒**是合理区间。若超过 500 微秒，检查是否有重复采样或对象分配（**块内生成绝对不能 new 对象**）。

---

## 附录 D · 链接验证状态表

> 图例：✅ 本次抓取 HTTP 200 并已存档于 `_raw/`；📄 经 Crossref API 验证 DOI；🔗 由已抓取页面逐字引用（可信但未独立抓取）；⚠️ 本次抓取失败（已注明原因）

| 链接 | 状态 |
|---|---|
| https://minecraft.wiki/w/Density_function （含 ?action=raw） | ✅ |
| https://raw.githubusercontent.com/misode/mcmeta/data/data/minecraft/worldgen/density_function/overworld/offset.json | ✅ |
| https://raw.githubusercontent.com/misode/mcmeta/data/data/minecraft/worldgen/noise/continentalness.json | ✅ |
| https://github.com/misode/mcmeta | ✅（GitHub API 验证分支与目录） |
| https://misode.github.io/worldgen/ | ✅ |
| https://deepwiki.com/xpple/cubiomes/3.4-biome-noise-system | ✅ |
| https://github.com/Cubitect/cubiomes | ✅（README + LICENSE） |
| https://github.com/TerraForged/TerraForged | ✅（GitHub API：MIT，345 star，默认分支 0.3.x） |
| .../TerraForged/0.3.x/.../terrain/TerrainLevels.java | ✅ |
| .../TerraForged/0.3.x/.../noise/NoiseLevels.java | ✅ |
| https://github.com/ViciousBadger/larion-world-generation | ✅（README） |
| https://modrinth.com/datapack/larion-one-continent | 🔗（Larion README 逐字） |
| https://modrinth.com/datapack/tectonic | ✅ |
| https://modrinth.com/datapack/deeper-oceans | ✅ |
| https://modrinth.com/mod/more-density-functions | ✅ |
| https://www.planetminecraft.com/data-pack/eldor/ | ⚠️ HTTP 403（反爬）；由 Larion README 逐字引用 |
| https://iquilezles.org/articles/warp/ | ✅ |
| https://iquilezles.org/articles/fbm/ | ✅ |
| https://iquilezles.org/articles/voronoise/ | ✅ |
| https://github.com/Auburn/FastNoiseLite | ✅（README + MIT LICENSE） |
| https://github.com/Auburn/FastNoiseLite/wiki/Documentation | ✅ |
| https://raw.githubusercontent.com/Auburn/FastNoiseLite/master/CSharp/FastNoiseLite.cs | ✅（域弯曲源码） |
| https://auburn.github.io/FastNoiseLite | 🔗（README 逐字） |
| https://github.com/Auburn/FastNoise2 | ✅（README + MIT LICENSE） |
| https://github.com/KdotJPG/OpenSimplex2 | ✅（README + CC0 LICENSE） |
| https://github.com/jobtalle/CubicNoise | ✅（README + Unlicense） |
| http://jobtalle.com/cubic_noise.html | 🔗（CubicNoise README 逐字） |
| https://libnoise.sourceforge.net/docs/ | 🔗（Red Blob 页面逐字） |
| https://libnoise.sourceforge.net/docs/classnoise_1_1module_1_1RidgedMulti.html | ✅ |
| https://github.com/JTippetts/accidental-noise-library | ✅（README + LICENSE） |
| https://www.redblobgames.com/maps/terrain-from-noise/ | ✅（全文 39k） |
| https://www.redblobgames.com/maps/mapgen4/ | ✅ |
| https://www.redblobgames.com/x/1729-generate-details/ | ✅ |
| https://www.redblobgames.com/x/1842-delaunay-voronoi-sphere/ | ✅ |
| https://www.redblobgames.com/x/1843-planet-generation/ | ✅ |
| http://www-cs-students.stanford.edu/~amitp/game-programming/polygon-map-generation/ | 🔗（Red Blob 页面逐字，引用 3 次） |
| https://catlikecoding.com/unity/tutorials/procedural-meshes/cube-sphere/ | ✅ |
| https://github.com/Tloru/planetGen | ✅（README + MIT LICENSE） |
| https://thebookofshaders.com/12/ | ✅ |
| https://thebookofshaders.com/13/ | ✅ |
| https://github.com/tuxalin/procedural-tileable-shaders | ✅（README；LICENSE 未找到） |
| https://www.ronja-tutorials.com/post/029-tiling-noise/ | ✅ |
| https://ronvalstar.nl/creating-tileable-noise-maps | ⚠️ HTTP 503（服务器故障）；由 Red Blob 页面逐字引用 |
| https://digitalfreepen.com/2017/06/20/range-perlin-noise.html | ✅ |
| https://noiseposti.ng/posts/2021-03-22-Normalizing-Gradient-Noise.html | ✅ |
| https://blog.pkh.me/p/42-sharing-everything-i-could-understand-about-gradient-noise.html | ✅ |
| https://undiscoveredworlds.blogspot.com/2019/01/the-global-map-fractals.html | ✅（HTTP 200；正文抽取失败，内容以空白为主） |
| https://blog.runevision.com/2026/03/fast-and-gorgeous-erosion-filter.html | ✅（HTTP 200，46k） |
| https://graphics.pixar.com/library/WaveletNoise/paper.pdf | ⚠️ 跨域重定向被拦截；由 Red Blob 页面逐字引用 |
| https://factorio.com/blog/post/fff-390 | ✅（HTTP 200，50k） |
| https://github.com/Mindwerks/worldengine | ✅（README + MIT LICENSE） |
| https://github.com/Azgaar/Fantasy-Map-Generator | ✅（README + MIT LICENSE） |
| https://github.com/Terasology/PolyWorld | ✅（README；LICENSE 未找到） |
| https://www.gdcvault.com/play/1024265/Continuous_World_Generation_in__No_Man_s_Sky_ | 🔗（Red Blob 页面逐字） |
| https://www.gdcvault.com/play/1024514/Building-Worlds-Using | 🔗（Red Blob 页面逐字） |

**论文 DOI（全部经 Crossref API 逐条验证，非猜测）**

| 论文 | DOI |
|---|---|
| Perlin 1985, An image synthesizer | 10.1145/325165.325247 |
| Perlin 2002, Improving noise | 10.1145/566654.566636 |
| Cook & DeRose 2005, Wavelet noise | 10.1145/1073204.1073264 |
| Lagae et al. 2009, Procedural noise using sparse Gabor convolution | 10.1145/1531326.1531360 |
| Lagae et al. 2010, A Survey of Procedural Noise Functions | 10.1111/j.1467-8659.2010.01827.x |
| Lewis 1989, Algorithms for solid noise synthesis | 10.1145/74333.74360 |
| Fournier, Fussell & Carpenter 1982, Computer rendering of stochastic models | 10.1145/358523.358553 |
| Musgrave et al. 1989, The synthesis and rendering of eroded fractal terrains | 10.1145/74333.74337 |
| Cohen et al. 2003, Wang Tiles for image and texture generation | 10.1145/882262.882265 |
| （2018）Non-periodic Tiling of Procedural Noise Functions | 10.1145/3233306 |
| Génevaux et al. 2013, Terrain generation using procedural models based on hydrology | 10.1145/2461912.2461996 |
| Cordonnier et al. 2016, Large Scale Terrain Generation from Tectonic Uplift and Fluvial Erosion | 10.1111/cgf.12820 |
| Paris et al. 2019, Terrain Amplification with Implicit 3D Features | 10.1145/3342765 |
| Mei et al. 2007, Fast Hydraulic Erosion Simulation and Visualization on GPU | 10.1109/pg.2007.15 |
| （2024）Terrain Amplification using Multi Scale Erosion | 10.1145/3658200 |

**未能验证、故本文件未采信的条目**：Larion 的 star 数；Tectonic / Eldor 的许可证；libnoise 的许可证；PolyWorld 的许可证；tuxalin tileable shaders 的许可证；FastNoiseLite / FastNoise2 / OpenSimplex2 / CubicNoise / cubiomes / ANL 的 star 数（本会话 GitHub API 触发限流）。以上均已在正文标注为"未验证"，未作臆测。

---

## 附录 E · 一页速览：本项目的最短路径

1. **基元**：OpenSimplex2**S**（CC0，Java，ridge 推荐）+ CubicNoise（公有领域，Java，可平铺）—— 条目 13 / 14
2. **海陆大格局**：Cellular F1 的"圆盘并集"掩码，结构性保证内海 = 0、无孤岛 —— 附录 A.3 / 条目 29
3. **双峰高程**：单调三次样条，三段水平段（盆地 / 大陆架 / 台地）—— 条目 1 / 附录 A.4
4. **山脉**：ridged（`(1-|n|)²`）× 山脉掩码，只作用于陆地 —— 条目 10 / 附录 A.4
5. **海岸线分形**：只弯 X 的域弯曲，幅度 ≈ 150 格 —— 条目 4 / 9 / 附录 A.5
6. **Z 周期**：整数晶格取模 + lacunarity = 2.0 + `N_0` 取 2 的幂 —— 附录 B.1
7. **X 无限精度**：全程 `double` + 相对坐标 —— 附录 B.4
8. **性能**：按块批量求值，块内零对象分配 —— 条目 12 / 附录 C.8
9. **标定**：C.2 双峰直方图 / C.3 陆地占比 / C.4 内海与孤岛 / C.5 分形维数（**必须在 16 格精度下测**）—— 附录 C
10. **岛弧 / 海沟（可选）**：cell 边界窄带重映射 —— 条目 29 / 附录 A.6

---

*文件：`02-噪声与域弯曲.md` ｜ 条目数：29 ｜ 所有链接均标注验证状态 ｜ 抓取原文留档于同目录 `_raw/`*
