### 条目 21 · Wavelet Noise（Cook & DeRose 2005）

**1. 名称 + 链接**
- DOI：https://doi.org/10.1145/1073204.1073264 （经 Crossref API 验证：`DOI=10.1145/1073204.1073264 | Wavelet noise | ACM Transactions on Graphics | 2005`）
- SIGGRAPH 版：https://doi.org/10.1145/1186822.1073264 （Crossref 验证）
- 作者主页 PDF：https://graphics.pixar.com/library/WaveletNoise/paper.pdf （**本次抓取被 cross-origin 重定向拦截，未能验证正文；该 URL 由 Red Blob Games 页面逐字引用**）

**2. 一句话原理**
用**带限（band-limited）小波**构造噪声：先在低分辨率上采样噪声再逐级上采样，用满足带限条件的滤波器叠加，从而**从根本上消除走样（aliasing）**；同时论文指出用 3D 噪声切 2D 曲面会破坏带限性。

**3. 类别**
噪声基元（频域/带限构造），实时程序化。

**4. 输出范围**
无限程序化（论文给出可平铺变体）。

**5. 实时性**
运行时 O(1)，但比 Perlin **贵**（每倍频要做上采样与滤波，不是简单求和）。

**6. 双峰高程：否。**

**7. / 8.：不适用。**

**9. 语言 / 依赖 / 许可证 / star**
论文（ACM / Pixar）。无官方 Java 实现。

**10. 对本项目的可借鉴点**

1. **本项目最该抄的不是算法，而是它的诊断结论。** Red Blob 页面引用论文原文："it is common to texture 2D surfaces by sampling a 3D noise function, but the resulting 2D texture will in general not be band-limited, even if the 3D function is perfectly band-limited."
   **对本项目的直接后果**：若你用"圆柱嵌入"（3D 噪声实现 Z 周期，见条目 17 第 6 点 / 附录 B），**沿 Z 切的这一片不是带限的**，块级采样会出现走样（表现为块状花纹或随机噪点）。
2. **对策（工程化，而非改用 wavelet noise）：** 保证 3D 噪声的最高倍频波长 ≥ 32 格（块宽 16 格的两倍），并且**在圆柱映射里保持半径 1/TAU 的等距性**，避免 Z 方向被压缩导致有效频率升高。
3. **"每层倍频之间的频率渗漏（bleed）"概念值得知道。** 普通 fbm 的各层之间有频谱重叠，最高层会在低频层上留下"噪声底噪"。若你追求"干净的大陆台地"，可以考虑**把最上面 2 层倍频单独用于山地区域**，而不是全图叠加。
4. **完整实现成本偏高，本项目判定为"不需要"**：CubicNoise / OpenSimplex2 的质量已足够，且便宜得多。**记录为备选，不推荐。**
5. 其余字段：不产生独立地貌。

---

### 条目 22 · Sparse Convolution Noise（Lewis 1989）

**1. 名称 + 链接**
- DOI：https://doi.org/10.1145/74333.74360 （经 Crossref API 验证：`DOI=10.1145/74333.74360 | Algorithms for solid noise synthesis | Proceedings of the 16th annual conference on Computer graphics and interactive techniques | 1989`）
- 期刊版：https://doi.org/10.1145/74334.74360 （Crossref 验证）

**2. 一句话原理**
把噪声定义为"稀疏随机脉冲与一个核函数的卷积"，用**随机访问 + 局部支撑核**实现 O(1) 求值（不必存整张图），并可通过换核函数任意控制频谱。

**3. 类别**
噪声基元（稀疏卷积 / 频域构造），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)（这正是该论文的贡献：可随机访问，无需全局纹理）。

**6. 双峰高程：否。**

**7. / 8.：不适用。**

**9. 语言 / 依赖 / 许可证 / star**
论文（ACM）。无官方实现。

**10. 对本项目的可借鉴点**

1. **"稀疏脉冲 + 局部核"的思想是现代所有可随机访问噪声的祖先**，包括 Gabor noise（条目 23）与 FastNoiseLite 的 BasicGrid 域弯曲。**理解了它，就理解了"为什么噪声可以 O(1)"。**
2. **对本项目的实际价值：换核函数 = 换地貌质感。** 若你发现某片大陆"太光滑"或"太毛"，不必改倍频结构，**换一个核**即可（例如把高斯核换成更尖锐的核 → 更粗糙的地表）。
3. **论文的稀疏性意味着"可先算少量脉冲再插值"**，这与本项目的块级采样天然契合：**每 16×16 块只需处理落在该块内的少量脉冲**。
4. **但实现复杂度高于梯度噪声，且没有 Java 现成实现 → 只作理论参考，可借鉴度"中"。**

---

### 条目 23 · Gabor Noise（Lagae et al. 2009）

**1. 名称 + 链接**
- DOI：https://doi.org/10.1145/1531326.1531360 （经 Crossref API 验证：`DOI=10.1145/1531326.1531360 | Procedural noise using sparse Gabor convolution | ACM Transactions on Graphics | 2009`）
- SIGGRAPH 版：https://doi.org/10.1145/1576246.1531360 （Crossref 验证）

**2. 一句话原理**
用**稀疏的 Gabor 核**（高斯包络 × 正弦载波）卷积，得到频谱可控、**各向异性**、可随机访问的噪声；通过控制核的方向分布可以造出"木纹""沙纹""条带"等高度定向的纹理。

**3. 类别**
噪声基元（稀疏 Gabor 卷积），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)，但比梯度噪声贵（每个脉冲要算高斯 + 正弦）。

**6. 双峰高程：否。**

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无。**

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
论文（ACM）。有多个 GPU 实现，无标准 Java 库。

**10. 对本项目的可借鉴点**

1. **各向异性噪声正是"纬度带"需要的工具。** 本项目 Z 轴代表纬度（南极 → 北极 → 回南极），**纬度方向的地貌天然应该有条带性**（例如极地冰盖边缘、温带的东西向山脉带）。Gabor 噪声可以通过"让核方向沿 X 分布"直接造出**沿纬度带延伸的地形**，这是 fbm 做不到的。
2. **"沿经度拉伸"的实现思路（不必真用 Gabor）：** 只需把噪声采样坐标做成 `(x * fx, z * fz)` 且 `fx ≠ fz`（例如 `fz = fx / 4`），就让地形特征沿 Z 拉长 4 倍。**一行代码，等效于各向异性核。** 这对"东西向绵延的山脉带"极其有效。
3. **⚠️ 各向异性会破坏 Z 周期的实现难度。** 若用圆柱嵌入，拉伸 Z 意味着椭圆截面（半径不再相等），会破坏 `1/TAU` 的等距条件。**对策：用整数晶格取模（附录 B 方案 A）实现 Z 周期，而不是圆柱嵌入**——取模方案对任意 `fz` 都成立，只要 `fz × 周期` 是整数。
4. **成本偏高，本项目判定为"备选"**：先用"坐标缩放"实现各向异性，只有在发现不够自然时才考虑真 Gabor。
5. 其余字段：不产生独立地貌。

---

### 条目 24 · A Survey of Procedural Noise Functions（Lagae et al. 2010）

**1. 名称 + 链接**
- DOI：https://doi.org/10.1111/j.1467-8659.2010.01827.x （经 Crossref API 验证：`DOI=10.1111/j.1467-8659.2010.01827.x | A Survey of Procedural Noise Functions | Computer Graphics Forum | 2010`）
- 开放获取（Eurographics Digital Library）：https://diglib.eg.org/items/e269a0cc-a17a-4edb-ab4c-246be546ec00 （在本次搜索中返回该条目）

**2. 一句话原理**
系统梳理程序化噪声的全家族：格点噪声、稀疏卷积噪声、显式频谱噪声，并按"频谱特性 / 是否各向同性 / 是否可随机访问"给出分类与选型建议。

**3. 类别**
综述（选型地图）。

**4. 输出范围**
不适用。

**5. 实时性**
不适用。

**6. ~ 8. 不适用。**

**9. 语言 / 依赖 / 许可证 / star**
论文（Eurographics / Wiley）。

**10. 对本项目的可借鉴点**

1. **用它来"证明选型合理"。** 你要在项目文档里论证"为什么选梯度噪声 + 样条重映射"，这篇综述的分类表就是依据。**它把"可随机访问（random access）"作为一等属性**——这与本项目的 O(1) 约束直接对应。
2. **它会把本文档里的条目 20/21/22/23 串成一条脉络**（1985 Perlin → 1989 稀疏卷积 → 2005 小波 → 2009 Gabor），便于你快速判断"还有没有漏掉的类别"。
3. **阅读建议：只读分类表与结论章节**，正文的数学推导对本项目过重。
4. **⚠️ 与本项目的一个已知张力：** 综述偏向"离线渲染质量"，而本项目要"运行时速度"。**读的时候要带着"这个能不能 O(1) 且够便宜"的过滤器。**

---

### 条目 25 · Non-periodic Tiling of Procedural Noise Functions（2018）—— 无限世界可见周期的解药

**1. 名称 + 链接**
- DOI：https://doi.org/10.1145/3233306 （经 Crossref API 验证：`DOI=10.1145/3233306 | Non-periodic Tiling of Procedural Noise Functions | Proceedings of the ACM on Computer Graphics and Interactive Techniques | 2018`）
- 图书馆条目（含题录，可核对）：https://katalog.slub-dresden.de/en/id/ai-49-aHR0cDovL2R4LmRvaS5vcmcvMTAuMTE0NS8zMjMzMzA2
- 第三方索引：https://trj-dd.sagepub.com/lp/association-for-computing-machinery/non-periodic-tiling-of-procedural-noise-functions-KBE7fAAGjk

**2. 一句话原理**
用**非周期铺贴（aperiodic tiling）**的方式把有限的一组噪声块拼成无限空间，使得**整体不呈现任何平移周期**，但在每个块内部仍可 O(1) 求值。

**3. 类别**
噪声 + 铺贴（tiling），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)（论文的核心卖点：既无限、又不重复、又不需预计算）。

**6. 双峰高程：否**（是"如何把噪声铺到无限"的机制，不是分布整形）。

**7. / 8.：不适用。**

**9. 语言 / 依赖 / 许可证 / star**
论文（ACM PACMCGIT）。本次未找到官方开源实现。

**10. 对本项目的可借鉴点**

1. **这一条与本项目的 Z 轴周期约束存在直接张力，必须想清楚：**
   - 你**需要** Z 方向严格周期 20,000,000（为了纬度环绕无缝）。
   - 但你也**不希望** X 方向出现可见重复。
   - 这篇论文解决的正是"避免重复"，而你需要"在 Z 上主动重复"。
   **结论：Z 方向用"整数晶格取模"强制周期（附录 B），X 方向绝不能取模**（X 取模 = X 变成周期，直接违反"X 无限非周期"）。
2. **它给出的警告对本项目极其重要：** 一旦你在某个轴上取模实现周期，**就必须确保"周期的长度"远大于玩家可能连续探索的距离**，否则玩家会察觉到重复。20,000,000 格在 MC 里是完整世界尺寸，**玩家不可能走完，所以 Z 取模是安全的**——这是本项目约束的一个隐藏优势。
3. **"块间无缝"的构造思路可借：** 论文需要保证块与块拼接处连续。你在 Z 方向取模时，**接缝处（Z = ±10,000,000）必须连续**，做法是保证周期是晶格单元数的整数倍（附录 B 有验证方法）。
4. **本项目可实现的简化版：** 不需要论文的完整非周期铺贴，只需**给不同区域用不同的噪声偏移**（例如按低频噪声扰动坐标偏移），就能显著推迟"玩家察觉重复"的时间。成本几乎为零。
5. 其余字段：机制性论文，不产生地貌。**可借鉴度"高"，但借鉴的是"何时不该用周期"。**

---

### 条目 26 · Wang Tiles / Stochastic Tiling（Cohen et al. 2003）

**1. 名称 + 链接**
- DOI：https://doi.org/10.1145/882262.882265 （经 Crossref API 验证：`DOI=10.1145/882262.882265 | Wang Tiles for image and texture generation | ACM Transactions on Graphics | 2003`）
- SIGGRAPH 版：https://doi.org/10.1145/1201775.882265 （Crossref 验证）
- 后续工作（同一思想的扩展，本次搜索命中）：GSWT: Gaussian Splatting Wang Tiles，https://lbnx03.ust.hk/ir/Record/1783.1-169933

**2. 一句话原理**
预先准备少量"边缘带颜色标签"的图块，铺贴时只允许**边缘标签匹配**的图块相邻，从而用极少的图块组合出**永不重复**的大范围图案；若图块用随机偏移而非固定内容，则称为 stochastic tiling。

**3. 类别**
铺贴（tiling），实时程序化。

**4. 输出范围**
无限程序化（理论上），或用在有限贴图上。

**5. 实时性**
运行时 O(1)（查表 + 边缘匹配，无需预计算整图）。**但需要准备图块集合（离线工作量）。**

**6. 双峰高程：否**（是铺贴机制，不是高程模型）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
不直接产生，但**可以用不同图块承载不同地貌**（例如"深海块""大陆架块""山地块"），从而**在铺贴层面显式控制地貌类型**。这是它与纯噪声最本质的差别。

**8. 内海与孤岛：可显式控制！** 因为你可以**禁止某些图块相邻**（例如"内海块"不允许被"陆地块"完全包围），或者干脆不提供会造出内海的图块。**这是本文档中少数能"结构性保证"内海 = 0 的方法。**

**9. 语言 / 依赖 / 许可证 / star**
论文（ACM）。有大量第三方实现（本次搜索命中的多为渲染/纹理方向，**未找到专注地形且许可证明确的可引用仓库，故不列具体 star**）。

**10. 对本项目的可借鉴点**

1. **⚠️ 最大的冲突：Wang tiles 的块是"有限尺寸"的。** 20,000,000 格的纬度周期意味着 Z 方向需要天文数字的图块（除非图块尺寸极大）。**对本项目而言，纯 Wang tiles 不可行。**
2. **但"边缘约束"的思想可以降维使用：** 把 Z 轴切成少量"气候带"（例如每 2,500,000 格一个带，共 8 带），带与带的**衔接处用约束保证地形连续**（例如低纬度带与中纬度带的 continentalness 参数渐变的过渡区）。**这就是一维的 Wang tile。**
3. **更实际的用法：用"参数集"而非"图块"做铺贴。** 你可以在低频噪声的控制下，让不同区域使用不同的**参数组**（大陆度频率、振幅表、样条折点），并通过平滑过渡避免突变。这等价于"连续版的 Wang tiles"，且完全 O(1)。
4. **"禁止相邻"这一机制的启发：** 本项目要达到"内海 = 0"，可以在规则层面规定"凡是被判定为封闭水体的单元格，强制抬升为陆地"。这在运行时是**局部判定**（只需判定 16 格邻域），仍是 O(1)。**这是附录 A 的连通性约束的具体实现方式之一。**
5. **离线准备图块的代价要计入：** 论文方案需要设计图块集合，这是**离线工作量**。用户已放宽"零预计算"，所以这条路**在放宽后可行**，但需标注："需要离线烘焙图块集合；运行时 O(1) 但内存需常驻图块集"。
6. 其余字段：机制性论文。

---

### 条目 27 · 可平铺 / 周期噪声配方（本项目 Z 轴周期的核心技术）

> 这一条不是单一来源，而是把本次抓取到的多份资料**归纳成可直接落地的配方**。所有引用的原始链接均已验证或逐字引用。

**1. 名称 + 链接**
- Red Blob Games 的 cylindernoise / torusnoise 代码：https://www.redblobgames.com/maps/terrain-from-noise/ （HTTP 200，已抓取，代码逐字见条目 17）
- Ronja's Tutorials "Tiling Noise"（晶格取模技巧）：https://www.ronja-tutorials.com/post/029-tiling-noise/ （HTTP 200，已抓取）
- Ron Valstar，"Creating tileable noise maps"：https://ronvalstar.nl/creating-tileable-noise-maps （**本次抓取 HTTP 503，服务器故障**；由 Red Blob 页面逐字引用）
- Perlin 1985 原始论文的 repeat 参数：https://doi.org/10.1145/325165.325247
- 高维振幅补偿：https://digitalfreepen.com/2017/06/20/range-perlin-noise.html （HTTP 200）、https://noiseposti.ng/posts/2021-03-22-Normalizing-Gradient-Noise.html （HTTP 200）

**2. 一句话原理**
让噪声在指定轴上严格周期，有三种互相独立的正交手段：(A) **整数晶格取模**，(B) **把周期轴卷成圆周（圆柱嵌入）**，(C) **谱域/带限构造**。fbm 层面还需额外满足 **lacunarity 与周期的整除关系**。

**3. 类别**
噪声构造（周期性），实时程序化。

**4. 输出范围**
无限程序化（在非周期轴上无限，在周期轴上循环）。

**5. 实时性**
- 方案 A（取模）：**运行时几乎零成本**（一次 `%` 或位与）。
- 方案 B（圆柱）：**需要 3D 噪声**，成本约 1.5 倍 2D。
- 方案 C：成本最高。

**6. ~ 8.：机制，不直接产生地貌。**

**9. 语言 / 依赖 / 许可证 / star**
Ronja 文章为 GLSL 教程（未标注正式许可证）；其余为论文/文章。

**10. 对本项目的可借鉴点（这是本文档最关键的一节）**

**方案 A · 整数晶格取模（本项目首选）**

Ronja 文章原文（抓取所得）："Then we make the cell positions wrap according to our new period variable. We do that by taking the modulo of the cell variables."  并给出：

    float2 modulo(float2 divident, float2 divisor) { ... }
    float perlinNoise(float2 value, float2 period) {
        // 计算晶格单元
        cellsMimimum = modulo(cellsMimimum, period);
        cellsMaximum = modulo(cellsMaximum, period);
    }

对应到 Java：

    // Z 周期 P（格），基频 f（每格的晶格单元数）
    // 要求：P * f 是整数 N
    int iz = (int)Math.floor(z * f);
    iz = Math.floorMod(iz, N);          // ← 唯一的改动
    int ix = (int)Math.floor(x * f);    // X 不取模

**优点**：零额外成本；**严格周期，接缝处数学上完全连续**（因为晶格本身循环）；对任意 lacunarity 友好（见下）。

**必须注意的三件事**：
1. `P * f` 必须是整数。若 `P = 20,000,000` 且 f 是 2 的幂倒数，这很容易满足。
2. **fbm 的每一层都要单独取模，且周期要随之缩小。** 设第 0 层周期为 N 个晶格单元，lacunarity = L，则第 k 层周期为 `N / L^k`，**必须仍是整数** → **取 L = 2.0（或任何整除 N 的整数）**。这是本项目最容易踩的坑：**lacunarity 用 2.0 而不要用 2.0137 这类"去伪影"的小数**，否则各层周期不一致，接缝处会出现明显断层。
3. **域弯曲也要周期化**，或者更省事——**只弯曲 X**（见条目 4），Z 完全不碰，周期性自动保持。**本项目强烈推荐后者。**

**方案 B · 圆柱嵌入（需要 X 方向也做域弯曲时使用）**

Red Blob 代码逐字（见条目 17 第 6 点）：把 Z 映射到圆周，**半径取 1/TAU**（原文注释解释：圆的周长要等于另一轴的长度，半径才是 1/2π），用 **3D 噪声**采样。

    // Z 归一化到 [0,1)，半径务必取 1/TAU
    double ang = TAU * (z / P);
    double nz1 = Math.cos(ang) / TAU;
    double nz2 = Math.sin(ang) / TAU;
    double v = noise3D(x, nz1, nz2);

**必须注意的两件事**：
1. **振幅补偿 ×√1.5**（Red Blob 原文：multiplying noise3D by √1.5）。不补偿会导致海陆比例整体偏移。
2. **带限性会被破坏**（Wavelet Noise 论文的结论，见条目 21）：块级采样可能出现走样。**对策：3D 噪声最高倍频波长 ≥ 32 格。**

**方案 B 的退化用法（本项目更可能用到）**：因为 Z 周期极大（20,000,000），圆周半径在噪声空间里是 `1/TAU ≈ 0.159`。**若你的噪声频率足够低，Z 方向在一小块区域内几乎是直线**，此时方案 B 与方案 A 的结果几乎相同。**所以直接用方案 A 即可，方案 B 只在需要"绕 Z 做弯曲"时才用。**

**关于 lacunarity 与周期的整除关系（本项目必守规则）**

设 Z 周期为 P 格，第 0 层频率为 `f0`（周期 `P*f0 = N0` 个晶格单元，必须为整数），lacunarity = L。则第 k 层：

    频率 f_k = f0 * L^k
    周期单元数 N_k = N0 / L^k     ← 必须仍是整数

**结论：L 必须整除 N0 的每一次幂。取 L = 2 且 N0 取 2 的幂，是最省心的组合。**

**额外汇总：几种"防止玩家察觉周期"的廉价手段**（呼应条目 25）
1. **周期足够大**：20,000,000 格远超玩家活动范围，本身已足够安全。
2. **非周期轴上加偏移扰动**：让不同区域的噪声采样带一个低频偏移。
3. **不要在 X 上取模**：X 取模是唯一会让玩家立刻发现重复的做法。

---

### 条目 28 · Cube-sphere / Cube-to-sphere 噪声（球面星球生成）

**1. 名称 + 链接**
- Catlike Coding，Cube Sphere 教程：https://catlikecoding.com/unity/tutorials/procedural-meshes/cube-sphere/ （HTTP 200，已抓取）
- planetGen（立方体细分 + 噪声，MIT）：https://github.com/Tloru/planetGen （HTTP 200，README 已抓取）
- 立方体贴图到球面的问题讨论（StackOverflow，本次搜索命中）：https://stackoverflow.com/posts/4090107/revisions
- Red Blob 的球面 Voronoi 方案：https://www.redblobgames.com/x/1842-delaunay-voronoi-sphere/ （HTTP 200）

**2. 一句话原理**
不用经纬度网格（会在极点退化），而是从一个立方体的 6 个面出发，把面上的点规范化到球面，使采样在球面上分布均匀；噪声直接采在球面坐标上。

**3. 类别**
域映射（参数化）+ 噪声，实时程序化。

**4. 输出范围**
球面（有限但无边界），每次采样仍是 O(1)。

**5. 实时性**
运行时 O(1)。

**6. 双峰高程：否**（是参数化方案，与高程分布无关）。

**7. / 8.：取决于噪声层，本身不产生。**

**9. 语言 / 依赖 / 许可证 / star**
- planetGen：**MIT**（LICENSE 原文："MIT License / Copyright (c) 2017 Isaac C."，已抓取验证）；语言：C++/OpenGL
- Catlike Coding：教程站点，Unity/C#

**10. 对本项目的可借鉴点**

1. **本项目不需要球面**——你的世界是平面 + Z 纬度环绕，不是球。**所以 cube-sphere 本身不可借鉴。**
2. **但两者面对的是同一类数学问题：把开区间卷成闭合环。** Z 周期 20,000,000 的本质就是"纬度方向卷成一个环"。**cube-sphere 的教训是：在闭合方向上的采样必须避免奇点与拉伸。**
   - 经纬度网格的**极点奇点** ≈ 你的 **Z 接缝**。
   - cube-sphere 的解法是"用立方体面代替经纬网格"；**你的解法更简单：整数晶格取模（条目 27 方案 A），因为 Z 是一维环，不是二维球面。**
3. **planetGen README 原文的工艺描述值得注意**（"starting out with a 8-vertex cube, apply random noise to each of the verticies… divide each of the faces in to fourths and repeat"）：这是**多分辨率细分**，与本项目的**块级 LOD** 是同类需求。若你未来要做远景 LOD（配合 Distant Horizons 式模组），**"不同 LOD 级别共享同一噪声函数、只改采样密度"**是正确做法——这与 cube-sphere 的递归细分同构。
4. **"等距映射"这个要素要记住：** Red Blob 在 cylindernoise 里特意取半径 `1/TAU` 就是为了**避免各向异性拉伸**。cube-sphere 的规范化（normalize）也是同一目的。**你做 Z 周期时，如果 Z 方向被拉伸或压缩，地形会在接缝附近出现方向性走样。**
5. 综合：**可借鉴度"中"——只借"闭合方向的等距性"这一条原则，其余不适用。**

---

### 条目 29 · Cellular / Voronoi 板块化（造岛弧与海沟的现成工具）

**1. 名称 + 链接**
- Inigo Quilez，voronoise：https://iquilezles.org/articles/voronoise/ （HTTP 200，已抓取）
- The Book of Shaders，第 12 章 Cellular Noise：https://thebookofshaders.com/12/ （HTTP 200，已抓取）
- FastNoiseLite Cellular（含 F1/F2 与距离度量，MIT）：https://github.com/Auburn/FastNoiseLite
- FastNoiseLite 可平铺的 cellular 实现（含 3D 版本）：https://github.com/tuxalin/procedural-tileable-shaders （HTTP 200，README 已抓取，含 "celullar noise (with derivatives and phase)" 与 "voronoi (edges, cells)"）

**2. 一句话原理**
用抖动网格（jittered grid）算每个点到最近特征点的距离：F1（最近距离）给出板块内部，F2（次近距离）与 `F2 - F1` 给出**板块边界**；把 F1 或 F2-F1 经过重映射，就能得到"板块内部平、边界陡"的地貌，以及沿海沟/岛弧分布的地形。

**3. 类别**
噪声基元（细胞/Voronoi）+ 距离场重映射，实时程序化。

**4. 输出范围**
无限程序化（cell 噪声是标准 O(1) 随机访问）。

**5. 实时性**
运行时 O(1)，但在同一"每次采样"层级里比梯度噪声**贵 2 ~ 3 倍**。按条目 11 的基准表：FastNoiseLite 2D Cellular 39.15 M点/秒（对比 Value 114、Perlin 92.8、Simplex 71.3）；libnoise Cellular 仅 0.65 M点/秒。

**6. 双峰高程：否**（它提供的是"板块几何"，双峰仍需样条重映射）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊 —— 本条目的核心价值**
- **岛弧：可以。** 把特征点按**弧线**（而不是随机）布置，或者对 `F2 - F1` 做窄带重映射 → 得到"沿边界排列的一串岛屿"。
- **海沟：可以。** `F2 - F1` 在边界处接近 0；对边界邻域做**下沉**重映射（而不是抬升），即得海沟。
- **洋中脊：可以。** 对**部分**边界（按边界两侧的"板块类型"随机标记）做**抬升**重映射，即得洋中脊。
- **大陆架 / 大陆坡：可以。** 对 `F1` 做单调重映射（内部平台 + 边缘陡降），即得大陆架 + 大陆坡。

**8. 内海与孤岛**
- **孤岛：可以结构性抑制。** 因为 Voronoi 板块是**连通的凸多边形**，不会像纯噪声那样撒芝麻。
- **内海：取决于板块划分**，但可以在 cell 层面显式规定"每个板块只有一种属性（陆/海）"，从而**从根本上消除内海**（一个板块内部不可能同时有陆和海）。

**9. 语言 / 依赖 / 许可证 / star**
- FastNoiseLite：**MIT**，含 Java 实现（见条目 11）
- tileable shaders：本次未抓取到 LICENSE 段落，**许可证未验证**
- iq / Book of Shaders：文章

**10. 对本项目的可借鉴点（本条目的配方最接近"类地大陆"）**

1. **这是唯一能在纯 O(1) 噪声框架内造出"岛弧 / 海沟 / 洋中脊"的手段。** 纯 fbm + 样条做不到，因为它没有"边界"这个概念。**本项目若想满足"岛弧"这一指标，必须引入 cell 噪声。**
2. **具体配方（本项目建议，全部 O(1)）：**
   
       // 1) 板块几何：用 jittered-grid cell 噪声
       F1 = 到最近特征点距离
       F2 = 到次近特征点距离
       edge = F2 - F1        // 边界处 → 0，板块内部 → 大
       id1  = 最近特征点的随机 ID（决定板块类型：陆/海）
       
       // 2) 大陆架与大陆坡：对 F1 做单调样条
       shelf = spline(F1)    // F1 小 → 台地；F1 中等 → 大陆坡；F1 大 → 深海盆地
       
       // 3) 岛弧与海沟：只作用在边界窄带
       arc   = smoothstep(w, 0, edge) * isConvergent(id1, id2)   // 汇聚边界 → 抬升（岛弧）
       trench= smoothstep(w, 0, edge) * isConvergent(id1, id2) * (−1)  // 同一带的外侧下沉
       ridge = smoothstep(w, 0, edge) * isDivergent(id1, id2)     // 离散边界 → 洋中脊
       
       // 4) 合成进 continentalness，再走双峰样条（条目 1 / 附录 A）
       C = shelf + arc + trench + ridge + 0.3 * fbmDetail
   
   **关键：`edge` 只用来做"边界带的局部修饰"，海陆大格局仍由 `shelf`（即 F1）决定。** 这样双峰分布不会被破坏。
3. **"每个板块一个类型"是消灭内海的杀手锏。** 对每个特征点随机决定它是"陆板块"还是"海板块"（用低频噪声控制陆海比例以标定陆地占比）。**同一个板块内部不可能出现海陆混杂 → 内海数 = 0 的结构性保证。** 这比条目 1 的样条方案更强。
4. **⚠️ 缺点：Voronoi 板块边缘太直。** 纯 cell 噪声的多边形边界是折线，海岸线会显得"几何化"。**对策：对采样坐标做域弯曲（条目 9），把折线揉成自然海岸线。** 而且**只弯 X 不弯 Z**，周期性无损。
5. **⚠️ 性能要注意。** Cell 噪声比梯度噪声贵 2 ~ 3 倍。本项目的做法：**cell 噪声只在低频层用一次**（算板块几何），高频细节全部用便宜的 CubicNoise / OpenSimplex2。总成本仍可控。
6. **可平铺的 cellular 实现已经存在**（tuxalin 的 tileable shaders，README 明确列出 "celullar noise"、"voronoi (edges, cells)"、与 "domain warping (fbnm and gradient curl)"）。**若你选圆柱嵌入方案（附录 B 方案 B），可以读它的 3D cellular 实现处理接缝。**
7. **Book of Shaders 第 12 章是 cell 噪声最好的入门**，包含 F1/F2 与多种距离度量（曼哈顿/切比雪夫/三角），**换距离度量 = 换板块形状**（三角形度量会给出六边形板块，很有意思）。
8. **iq 的 voronoise 的 `u` 参数可以做"边界锐利度"的连续控制**：`u` 小 → 边界模糊（像自然海岸），`u` 大 → 边界锐利（像断裂带）。**这个滑杆值得在你的配置里暴露出来。**

---

