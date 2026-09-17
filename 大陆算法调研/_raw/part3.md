### 条目 9 · Inigo Quilez —— Domain Warping（域弯曲）经典文章

**1. 名称 + 链接**
- https://iquilezles.org/articles/warp/ （HTTP 200，已抓取全文）
- 相关：https://iquilezles.org/articles/ （文章总目录，HTTP 200）

**2. 一句话原理**
把 `f(p)` 换成 `f(p + h(p))`：用一层噪声当"位移量"去推开采样坐标，再在推开后的坐标上求值。文章给出的经典形式是**三层嵌套**。

**3. 类别**
域弯曲（domain warp），实时程序化。

**4. 输出范围**
无限程序化（纯函数，无状态）。

**5. 实时性**
运行时 O(1)，但常数因子高：经典三层嵌套 = **7 次 fbm 调用**。

**6. 双峰高程：否。** 域弯曲是**形状修饰**，不改变值的分布。这一点必须反复强调。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：全无。**

**8. 内海与孤岛：不处理，且会加剧**（弯曲会把细碎轮廓揉得更碎）。

**9. 语言 / 依赖 / 许可证 / star**
- 语言：GLSL（文章用着色器写法，公式与语言无关）
- 许可证：**文章，未标注代码许可**。公式本身是公有领域的数学事实，但**不要逐字复制文章的大段文字**。
- star：不适用

**10. 对本项目的可借鉴点**

1. **文章原文配方（逐字抄录）：**
   
       // 基础
       float pattern(vec2 p) { return fbm(p); }
   
       // 一层弯曲
       vec2 q = vec2(fbm(p + vec2(0.0, 0.0)),
                     fbm(p + vec2(5.2, 1.3)));
       return fbm(p + 4.0*q);
   
       // 两层弯曲
       vec2 q = vec2(fbm(p + vec2(0.0, 0.0)),
                     fbm(p + vec2(5.2, 1.3)));
       vec2 r = vec2(fbm(p + 4.0*q + vec2(1.7, 9.2)),
                     fbm(p + 4.0*q + vec2(8.3, 2.8)));
       return fbm(p + 4.0*r);
   
   注意 `vec2(0.0,0.0)` 与 `vec2(5.2,1.3)`：**两个分量要用不同的偏移**，否则 q 的两个分量完全相关（退化成 1D 弯曲）。
2. **弯曲幅度 4.0 是相对值，不是绝对值。** 在 iq 的归一化坐标里域是 ~[0,1]，所以"4.0"相当于特征尺度的 4 倍——**非常大的弯曲**。你要做大陆级弯曲必须把这个比例重新算：弯曲幅度应 ≈ 目标特征波长的 0.1 ~ 0.3 倍。
3. **本项目最优：把二维域弯曲降成一维。** 因为 Z 是纬度不能弯，你只需要 `q = fbm(p + offset)` 这一个标量，然后 `fbm(x + A*q, z)`。**成本从 3 次 fbm 降到 2 次**，而且 Z 的周期性完好无损。
4. **嵌套层数不要超过 2 层。** 文章明确只有 3 张图（0/1/2 层）。每多一层成本翻倍且视觉收益递减。本项目建议：**1 层**。
5. **域弯曲的副产品正好是海岸线分形维数。** 弯曲会把一条单调的海岸线揉成自相似的曲折线，**D 恰好落在 1.2 ~ 1.4 区间**。这是本项目达到海岸线指标最省力的手段。
6. **警告：域弯曲会让噪声不再是带限的。** 见条目 21（Wavelet Noise 引文）与条目 17 的引用（"the resulting 2D texture will in general not be band-limited"）。后果是块级采样（16 格一个点）时**块内可能出现高于采样率的结构**，表现为块状阶梯。**对策：弯曲幅度 × 最高倍频波长 ≥ 32 格**。

---

### 条目 10 · Inigo Quilez —— fBm 权重变体与 voronoise

**1. 名称 + 链接**
- fBm 基础与变体：https://iquilezles.org/articles/fbm/ （HTTP 200，已抓取）
- Voronoi 变体（voronoise）：https://iquilezles.org/articles/voronoise/ （HTTP 200，已抓取）

**2. 一句话原理**
分形叠加不只是"频率翻倍、振幅减半"：给每一层换不同的**权重函数**（abs / 取反 / 平滑最小）就能得到 turbulence、ridged、billow 等完全不同的地貌质感；voronoise 则把 cell 噪声与 value 噪声用一个参数连续混合。

**3. 类别**
分形叠加（fractal summation）权重变体，实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)。每层权重函数的额外开销可忽略。

**6. 双峰高程：否。** 但 **ridged 变体是造山脉的必需品**。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
- 山脊（山脉）：**有**，ridged/turbulence 就是干这个的。
- 大陆架 / 岛弧 / 海沟 / 洋中脊：无。

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
GLSL 文章，未标注代码许可。公式为公有领域数学。

**10. 对本项目的可借鉴点**

1. **四种权重函数的用途分工（本项目建议）**：
   - **普通 fBm**（振幅 1/2 衰减）→ 大陆台地内部的缓坡起伏
   - **turbulence**（每层取 `abs(n)`）→ 侵蚀地貌，制造沟壑感
   - **ridged**（`1 - abs(n)` 或 `(1-abs(n))²`）→ 山脉主脊
   - **billow**（`abs(n)` 后不平移）→ 圆丘，适合丘陵
2. **ridged 必须做平方。** `ridged = (1 - abs(n))²` 比 `1 - abs(n)` 尖得多，山脊线才像山。**这一步是"山脉看起来是不是山"的分水岭。**
3. **不要全图用 ridged。** 把 ridged 的振幅乘一个"大陆内部掩码"（例如 `smoothstep(-0.1, 0.2, continentalness)`），否则海里长山。这是本项目最容易犯的错。
4. **voronoise 的 `u` 参数是造岛弧的现成工具。** 论文/文章里 `u` 控制"cell 感 vs 噪声感"的连续过渡：`u→0` 是纯噪声，`u→1` 是纯 Voronoi。**取中间值可以得到"一串沿弧线排列的岛屿"的观感**，正是岛弧。见条目 29。
5. **振幅衰减率可以不是 1/2。** 常见还有 0.4（更平滑）与 0.6（更粗糙）。本项目为了"大陆台地平坦 + 山脉突起"的强对比，建议**用两层不同衰减率的分形**：低频层用 0.5（平滑大陆），山脉层用 0.35（山脊更突出）。
6. **层数控制在 5 ~ 7。** 超过 7 层，波长会低于 16 格的块级采样率，纯粹浪费算力还制造走样。

---

### 条目 11 · FastNoiseLite —— 库级域弯曲 API（Single / Progressive / Independent）

**1. 名称 + 链接**
- https://github.com/Auburn/FastNoiseLite （HTTP 200，README 已抓取）
- 文档：https://github.com/Auburn/FastNoiseLite/wiki/Documentation （HTTP 200，已抓取）
- 源码（本次抓取的 C# 主实现，含全部域弯曲分支）：https://raw.githubusercontent.com/Auburn/FastNoiseLite/master/CSharp/FastNoiseLite.cs （HTTP 200）
- 在线预览：https://auburn.github.io/FastNoiseLite （README 中逐字列出）

**2. 一句话原理**
把域弯曲做成库的一等公民：一个 `DomainWarp(ref x, ref y)` 直接原地修改坐标，内部按 `FractalType` 分派到 Single / Progressive / Independent 三种模式。

**3. 类别**
域弯曲（工程实现），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)，且是本文档里**常数因子最透明**的实现（README 给出逐算法吞吐量表）。

**6. 双峰高程：否**（库只提供噪声基元，不提供样条重映射）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无**（但 cellular 变体可做板块，见条目 29）。

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：**C#、C++98、C99、HLSL、GLSL、Go、Java、JavaScript/TypeScript、Rust、Fortran、Zig、PowerShell、Odin、Haxe、Pascal、GML**（README 逐字列出，共 16 种）
- 依赖：无（单文件库，这是它的设计目标）
- 许可证：**MIT**（LICENSE 原文："MIT License / Copyright(c) 2020 Jordan Peck (jordan.me2@gmail.com) / Copyright(c) 2020 Contributors"，已抓取验证）
- star：未验证（未读取）

**10. 对本项目的可借鉴点**

1. **`DomainWarpProgressive` vs `DomainWarpIndependent` 的区别要理解对**（源码枚举原文：`DomainWarpProgressive`、`DomainWarpIndependent`）：
   - **Progressive**：第 k 层在**已被第 k-1 层弯曲过的坐标**上再弯 → 与 iq 的嵌套等价，形状更有机、更"顺"。
   - **Independent**：每层都从**原始坐标**独立弯曲一次再叠加 → 更粗糙、更"碎"。
   本项目造大陆用 **Progressive**（避免碎），造山脉细节可用 Independent。
2. **默认参数可以直接抄：`mDomainWarpAmp = 1.0f`，`DomainWarpType = OpenSimplex2`。** 源码原文：`private float mDomainWarpAmp = 1.0f;`、`private DomainWarpType mDomainWarpType = DomainWarpType.OpenSimplex2;`。幅度 1.0 是"以噪声坐标为单位"的，不是格。
3. **Java 移植已经存在。** README 明确列出 `/Java/` 目录。**这是本项目最省事的起点**：直接把它的 Java 单文件搬进 1.7.10（MIT 允许），你立刻拥有 OpenSimplex2 + 域弯曲 + 全部 fractal 变体。
4. **DomainWarpType 有三种**（源码枚举原文：`DomainWarpType` 含 `OpenSimplex2`、以及 BasicGrid 等）。**BasicGrid 版用网格梯度域弯曲，比 OpenSimplex2 版快**，适合性能吃紧时降级。
5. **它有 3D 域弯曲（`DomainWarp(ref x, ref y, ref z)`），本项目不要用。** 你的 Z 是纬度，3D 弯曲会破坏周期性。**只用 2D 版本，且只用它改 x。**
6. **README 的性能表可直接用来做预算**（单位：百万点/秒，2D，Intel 7820X @4.9GHz，clang-cl -O2）：
   
       FastNoise Lite   Value 114.01 | Perlin 92.83 | Simplex 71.30 | Cellular 39.15
       FastNoise Legacy Value 102.12 | Perlin 87.99 | Simplex 65.29 | Cellular 36.84
       FastNoise 2(AVX2)Value 776.33 | Perlin 624.27| Simplex 466.03| Cellular 194.30
       libnoise               —     | Perlin 27.35 |      —        | Cellular 0.65
   
   **换算到你的场景**：16×16 块 = 256 格，若每格一次 Simplex 采样，约 256/71.3e6 ≈ 3.6 微秒/块——**预算极其宽裕**。你可以用 5×5 采样网格 × 7 倍频 × 3 张图 ≈ 525 次采样 ≈ 7 微秒/块。**"接近纯噪声"的性能目标完全可达。**
7. **libnoise 的 Cellular 只有 0.65 M/s**（比 FastNoiseLite 慢 60 倍）。**若你打算用 cellular 做板块，别用 libnoise。** 这条数据是选型的关键依据。

---

### 条目 12 · FastNoise2 —— SIMD 节点图噪声

**1. 名称 + 链接**
- https://github.com/Auburn/FastNoise2 （HTTP 200，README 已抓取）

**2. 一句话原理**
把噪声算子的组合方式做成**节点图（node graph）**，底层用 SIMD 批量求值，一次调用算一整块（而不是一个点），从而把吞吐提高一个数量级。

**3. 类别**
噪声库 + 批量求值（SIMD），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)，但**面向"整块批量"而非"单点"**，这与本项目的块级生成天然契合。

**6. 双峰高程：否。**

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无。**

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：C++（含 C API），**无 Java 版**（README 未列出 Java，与 FastNoiseLite 不同）
- 依赖：需编译原生库
- 许可证：**MIT**（LICENSE 原文："MIT License / Copyright (c) 2020 Jordan Peck"，已抓取验证）
- star：未验证

**10. 对本项目的可借鉴点**

1. **不能直接用（无 Java 版），但"节点图 + 批量求值"这个架构思想可抄。** 你的 Java 实现应暴露 `fillChunk(int cx, int cz, float[] out)` 而不是 `float sample(float x, float z)`——**按块批量算能复用循环不变量**（倍频频率、坐标缩放因子），实测常有 2 ~ 3 倍收益，无需 SIMD。
2. **README 的定位原文值得记：** "It provides large performance gains thanks to SIMD and uses a node graph structure to allow complex noise configurations with lots of flexibility." —— "复杂配置"与"高性能"的代价是架构复杂度，本项目**不需要**这个复杂度。
3. **README 的性能表（见条目 11）显示 FastNoise2 比 Lite 快 5 ~ 8 倍**，这是 SIMD 的收益上限参考。Java 的 Panamá/Vector API 在 1.7.10 时代不可用，所以**不要指望这条路**；走"按块批量 + 查表"的路线更现实。
4. 其余字段同条目 11。**由于无 Java 版，可借鉴度评"中"。**

---

### 条目 13 · OpenSimplex2 / OpenSimplex2S —— 梯度噪声基元

**1. 名称 + 链接**
- https://github.com/KdotJPG/OpenSimplex2 （HTTP 200，README 已抓取）

**2. 一句话原理**
Simplex 噪声的现代改良：2D/3D/4D 都用"一致布局"的晶格，避免老版 OpenSimplex 在 3D/4D 上对比度不均与 5D+ 的对角带伪影；OpenSimplex2S 是"更像 2014 版 OpenSimplex"的大支撑域变体。

**3. 类别**
噪声基元（梯度噪声），实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)。README 原文性能定位："Is about as fast as common Simplex implementations."

**6. 双峰高程：否**（基元，不涉及分布形状）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无。**

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：**Java（官方含 Java 实现）**、C#、C++ 等
- 依赖：无
- 许可证：**CC0-1.0**（LICENSE 原文："Creative Commons Legal Code / CC0 1.0 Universal"，已抓取验证）——**公有领域奉献，商用与闭源均无限制，是本项目最安全的依赖**
- star：未验证

**10. 对本项目的可借鉴点**

1. **README 原文给出一条直接影响本项目的选型建议：** "Recommended choice for ridged noise (if passing individual layers into `abs(x)`)." —— **要做 ridged 山脉，请用 OpenSimplex2S，不要用 OpenSimplex2(F)。** 原因：ridged 会把每一层送进 `abs()`，此时噪声值在 0 附近的分布均匀性直接决定山脊线的质量，2S 的支撑域更大、更均匀。
2. **性能取舍已由官方给出：** 2S 的 4D 用"普通 skew + 预生成 4×4×4×4 查找表"，2(F) 的 4D 用"5 份偏移副本"。**2S 查表更快但内存大**；本项目只用 2D/3D，这个差异可忽略。
3. **CC0 意味着你可以把它的 Java 源码直接内联进 mod，无需署名。** 对于 1.7.10 这种老平台（构建工具链老旧、加依赖麻烦），**零依赖的单文件 CC0 源码是理想形态**。
4. **3D 噪声的用途提醒：** 本项目 Z 是纬度。若你为了 Z 的周期性而采用"圆柱嵌入"（附录 B），你会需要 **3D 噪声**（把 Z 映射到圆周上）。**此时 OpenSimplex2 的 3D 正好用得上**，且它比"4D 环面嵌入"便宜一维。
5. **注意 README 里 4D 的定位：** 4D 是为"两轴都需要周期"准备的。你只有 Z 需要周期，**所以 3D 圆柱嵌入就够，不要上 4D**（4D 既慢又难调）。
6. 其余字段：基元库，不产生独立地貌。

---

### 条目 14 · CubicNoise —— 原生支持 tiling 的双三次 value noise

**1. 名称 + 链接**
- https://github.com/jobtalle/CubicNoise （README 已抓取，HTTP 200）
- 配套博客：http://jobtalle.com/cubic_noise.html （README 中逐字列出；**本次未抓取验证**）

**2. 一句话原理**
用双三次（bicubic）插值代替线性插值的 value noise，视觉质量接近 Perlin 但实现极简（无需梯度表），且**原生支持 tiling 与 scaling 参数**。

**3. 类别**
噪声基元（value noise 变体），实时程序化。

**4. 输出范围**
无限程序化，**且可指定周期**。

**5. 实时性**
运行时 O(1)，且是本文档中**常数因子最小的噪声之一**（无梯度点积，只有 16 个哈希 + 插值）。

**6. 双峰高程：否。**

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无。**

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：**Java**（这是它的原始语言）——**对本项目是重大加分项**
- 依赖：无
- 许可证：**Unlicense（公有领域）**（LICENSE 原文："This is free and unencumbered software released into the public domain."，已抓取验证）
- star：未验证

**10. 对本项目的可借鉴点**

1. **README 原文只有一句关键信息："Tiling and scaling are supported."** —— 这是本文档里**唯一一个把 tiling 写进 API 的 Java 噪声库**，直接对应本项目的 Z 轴周期需求。
2. **Java + 公有领域 = 可直接内联。** 与 OpenSimplex2（CC0）同理，但 CubicNoise 更小更简单，**适合作为"低开销后备噪声"**：大陆掩码这种只需要低频平滑的场，用双三次 value noise 就够，省下的算力留给山脉。
3. **value noise 的代价是方形格点感。** 所以它**不适合**直接做山脉主脊（会看到格子），**适合**做低频大陆场（格点被后续的样条重映射与域弯曲洗掉）。
4. **tiling 参数的用法（一般化，非该库专有）：** 周期必须与插值格点数对齐，即"`tile` 参数 = 晶格单元数"，且**每一层倍频的 tile 参数要相应缩放**（详见条目 27）。CubicNoise 的 `scaling` + `tiling` 两参数正是在处理这件事。
5. **本项目建议的组合：CubicNoise（大陆低频场） + OpenSimplex2S（山脉 ridged） + 自写样条（双峰重映射）**。三者许可证分别为 公有领域 / CC0 / 自写，**完全无法律风险**。

---

### 条目 15 · libnoise —— 模块化分形库与 RidgedMulti

**1. 名称 + 链接**
- 官网与文档：https://libnoise.sourceforge.net/docs/ （HTTP 200；Red Blob 页面亦逐字引用该地址）
- RidgedMulti 模块文档：https://libnoise.sourceforge.net/docs/classnoise_1_1module_1_1RidgedMulti.html （HTTP 200，已抓取）

**2. 一句话原理**
把噪声发生器与各种"修改器模块"（scale/bias/terrace/turbulence/ridged）串成树，每个模块是标准 C++ 对象，用 setter 组合出复杂地形。

**3. 类别**
噪声库 + 分形模块组合，实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)。**但性能很差**：见条目 11 引用的基准表，libnoise 的 Perlin 仅 27.35 M点/秒、Cellular 仅 0.65 M点/秒，比 FastNoiseLite 慢 3 ~ 60 倍。

**6. 双峰高程：否**（库不含样条重映射模块）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
- 山脊：**有**（`RidgedMulti` 模块就是教科书级实现）。
- 其余：无。

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：**C++**（无官方 Java 版）
- 依赖：无
- 许可证：**未验证**。常见说法是 LGPL，但**本次未抓取到 LICENSE 文件，故不作断言**。若要移植请先自行核实。
- star：未验证

**10. 对本项目的可借鉴点**

1. **`RidgedMulti` 的公式与参数结构值得读。** 它把 ridged 做成"每层 `(1 - abs(noise))` 再平方、再按倍频衰减"的独立模块，且暴露 octaveCount / lacunarity / frequency / seed 四个参数。**你的山脉层照这个四参数设计即可。**
2. **`terrace` 模块对应条目 17 的梯田重映射**，可以用来做"高原台地"，与本项目"大陆台地"的目标吻合。
3. **性能太差，不要用。** 这条是**负面结论**，但很有价值：它解释了为什么 MC / TerraForged / Larion 都自己写噪声而不用 libnoise。
4. **模块化树的架构可作为设计参考**：把"发生器"与"修饰器"分离，便于在配置里换组合。
5. 许可证未验证 → **若打算移植，先解决许可证问题**；只读公式则无风险。

---

### 条目 16 · Accidental Noise Library —— 可组合的噪声表达式

**1. 名称 + 链接**
- https://github.com/JTippetts/accidental-noise-library （HTTP 200，README 已抓取）

**2. 一句话原理**
把噪声函数、组合子与分形策略抽象成可任意嵌套的表达式树，支持 2/3/4/6 维与多种基元，用"函数对象"而非固定管线组合。

**3. 类别**
噪声库 + 函数组合，实时程序化。

**4. 输出范围**
无限程序化。

**5. 实时性**
运行时 O(1)。

**6. 双峰高程：否。**

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无**（但支持 cellular 基元，可拼出板块感）。

**8. 内海与孤岛：不处理。**

**9. 语言 / 依赖 / 许可证 / star**
- 语言：**C++**（有 C 接口；无官方 Java 版）
- 依赖：无（README 强调自包含）
- 许可证：**zlib 风格**（LICENSE 原文："Accidental Noise Library / Copyright (C) 2011 Joshua Tippetts / This software is provided 'as-is', without any express or implied warranty." —— 这是 zlib/libpng 风格的措辞，**宽松、可商用**）
- star：未验证

**10. 对本项目的可借鉴点**

1. **它的核心价值是"组合子的设计模式"。** 你的 Java 实现可以照抄这个抽象：`interface NoiseFn { float sample(float x, float z); }`，然后 `Add`/`Mul`/`Warp`/`Ridged`/`Spline` 各是一个实现类。**这样样条重映射就是一个普通节点，而不是特例代码。**
2. **它支持 6D 噪声**——本项目只需要 2D/3D，**不要被高维特性带偏**。
3. **zlib 风格许可证 = 可商用 + 可闭源 + 需保留版权声明**，是比 GPL 友好得多的选项。
4. **无 Java 版 → 只能借架构，不能借代码。可借鉴度评"中"。**
5. 其余字段同条目 15。

---

### 条目 17 · Red Blob Games —— Making maps with noise functions（本项目最该精读的配方文）

**1. 名称 + 链接**
- https://www.redblobgames.com/maps/terrain-from-noise/ （HTTP 200，已抓取全文 39k 字符）
- 引用格式原文：Patel, Amit J., "Making maps with noise functions", Red Blob Games, 2015.

**2. 一句话原理**
给出从"一张噪声"到"一张能看的地形图"的**完整重映射配方链**：噪声 → 重分布（`pow`）→ 岛屿掩码（距离函数混合）→ 梯田化 → 生物群系。核心思想是**噪声只是原料，形状全靠重映射函数**。

**3. 类别**
噪声 → 地形的教学配方（重映射 / 掩码），实时程序化。

**4. 输出范围**
无限程序化（文章明确有一节"To infinity and beyond"，原文："The calculation of the biome at position (x,y) is independent of calculations at any other position. This local calculation results in two nice properties: it can be calculated in parallel, and it can be used for infinite terrain."）

**5. 实时性**
运行时 O(1)。全文所有配方都是纯函数。

**6. 双峰高程：是（概念上）。** 文章的重分布 + 岛屿掩码正是"把单峰噪声压成双峰"的手段，只是它面向有限岛屿地图，需要改造（见下面第 4 点）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊**
- 大陆架：**有**。岛屿掩码 + 重分布天然产生"浅海平台 + 中心陆地"。
- 岛弧 / 海沟 / 洋中脊：无。

**8. 内海与孤岛：部分处理。** 文章的岛屿掩码强制"边界是水、中心是陆"，因此**不会出现贴边大陆**，但也因此**只适用于有限地图**。

**9. 语言 / 依赖 / 许可证 / star**
- 语言：JavaScript（示例）+ 伪代码，公式与语言无关
- 依赖：示例用 `simplex-noise.js`（文章引用 `https://github.com/jwagner/simplex-noise.js`）
- 许可证：文章与示例代码由作者发布，**未标注正式开源许可证**；引用请按其给出的 citation 格式
- star：不适用

**10. 对本项目的可借鉴点（这一条最多，因为文章最贴近）**

1. **重分布公式原文（逐字抄录）：**
   
       elevation[y][x] = Math.pow(e, exponent);
       // 实践上更好用：
       elevation[y][x] = Math.pow(e * fudge_factor, exponent);   // fudge_factor 约 1.2
   
   文章原文："To make flat valleys, we can raise the elevation to a power. Move the slider to try different exponents." 与 "In practice it may work better to use `Math.pow(e * fudge_factor, exponent)`, where the fudge factor is some number near 1. For the above demo I used 1.2."
   **意义：`exponent > 1` 把中低海拔压平 → 这正是一段"大陆台地平台"。** 本项目建议 exponent = 2.0 ~ 3.0，fudge = 1.2。
2. **岛屿形状的距离函数（文章引用 KdotJPG 的建议，逐字抄录）：**
   
       d = 1 - (1-nx²) * (1-ny²)      // 方形地图，岛屿尽量填满
       d = min(1, (nx² + ny²) / sqrt(2))   // 圆形岛屿，便于嵌入更大世界
   
   其中 `nx = 2*x/width - 1`，`ny = 2*y/height - 1`，d 从中心 0 到边界 1。
   **⚠️ 本项目是无限世界，没有"边界"，所以这两个式子不能直接用。** 但**思想可搬**：用一张极低频噪声当 d，`d = smoothstep(-1, 1, continentNoise(x,z))`，就得到一个"无限版的岛屿掩码"。
3. **塑形函数（shaping function）的混合写法是拿来即用的：**
   
       // 中心用恒定陆地，边界用恒定水，中间混合
       e = mix(e, 1 - d, shapingAmount)
   
   **本项目对应做法**：`continentalness = mix(fbmHighFreq, 1 - dLowFreq, 0.7)` —— **用 70% 的低频大陆掩码 + 30% 的高频噪声**。这个比例是本项目"内海数 = 0"的关键：**低频掩码占比越高，海陆越连片、内海越少**。
4. **梯田化（terracing）——大陆台地的直接实现：**
   
       e = Math.round(e * levels) / levels
   
   文章原文："If we round the elevation to the nearest of levels we get terraces"，并指出这是 `e = f(e)` 形式的重分布函数。
   **本项目建议**：只对 `continentalness ∈ [-0.05, 0.25]`（大陆台地范围）做梯田化，levels 取 3 ~ 5，台地立刻出现"一级级台阶"，非常像大陆架/阶地。
5. **⚠️ 梯田化与块级采样冲突。** `Math.round` 是不连续函数，会在台阶边缘产生**整格的垂直断层**。块级（16 格）采样时，台阶宽度若 < 16 格就会消失或闪烁。**本项目的对策：台阶宽度必须 ≥ 64 格**，即 `levels` 要少（3 级），或改用 `smoothstep` 软化台阶边缘。
6. **可平铺噪声的现成代码（对本项目 Z 轴周期极重要，逐字抄录）：**
   
       const TAU = 2 * M_PI;
   
       function cylindernoise(double nx, double ny) {
         double angle_x = TAU * nx;
         /* In "noise parameter space", we need nx and ny to travel the
            same distance. The circle created from nx needs to have
            circumference=1 to match the length=1 line created from ny,
            which means the circle's radius is 1/2π, or 1/tau */
         return noise3D(cos(angle_x) / TAU, sin(angle_x) / TAU, ny);
       }
   
       function torusnoise(double nx, double ny) {
         double angle_x = TAU * nx, angle_y = TAU * ny;
         return noise4D(cos(angle_x) / TAU, sin(angle_x) / TAU,
                        cos(angle_y) / TAU, sin(angle_y) / TAU);
       }
   
   **这就是"X 无限 + Z 周期"的标准解法**：把 Z（纬度）映射到圆周上，用 **3D 噪声**采样。半径取 `1/TAU` 是为了让圆的周长与另一轴的单位长度一致（避免各向异性拉伸）。
7. **高维噪声的振幅补偿（文章原文，极易踩坑）：**
   "Higher dimensional noise tends to have a narrower range of values than lower dimensional noise, so if your biome constants are tuned for 2D noise, then you can try multiplying noise3D by √1.5, and noise4D by √2."
   **即：用 3D 噪声做 Z 周期时，输出要乘 √1.5 ≈ 1.2247。** 如果你沿用 2D 调好的阈值而不补偿，海陆比例会整体偏移——**这是本项目最容易静默出错的地方。**
8. **振幅归一化的深入参考（文章引用）：**
   - Rudi Chen，"range of Perlin noise"：https://digitalfreepen.com/2017/06/20/range-perlin-noise.html （HTTP 200，已验证）
   - noiseposting，"Normalizing Gradient Noise"：https://noiseposti.ng/posts/2021-03-22-Normalizing-Gradient-Noise.html （HTTP 200，已验证）
9. **可平铺噪声的专门指南（文章引用）：** Ron Valstar，https://ronvalstar.nl/creating-tileable-noise-maps （**本次抓取返回 HTTP 503，服务器故障，不代表链接失效**；来源为 Red Blob 页面逐字引用）。
10. **文章明确否定"用 3D 噪声纹理化 2D 表面"的做法**（引 Wavelet Noise 论文原文）："it is common to texture 2D surfaces by sampling a 3D noise function, but the resulting 2D texture will in general not be band-limited, even if the 3D function is perfectly band-limited." —— **这是对你采用圆柱嵌入（第 6 点）的警告：切片不是带限的。** 对策是保证 3D 噪声的最高倍频波长 ≥ 32 格。
11. **文章还给出 ridgenoise 的示例函数**（在 "Ridged noise" 一节），可直接作为山脉层模板。
12. **Terracing / 重分布的通用提示原文：** "Try applying the elevation reshaping to only the lower frequency octaves of the noise generator, and allow the high frequencies to work equally across the map." —— **只对低频层做重映射**，高频细节不受影响。本项目应照做：**样条只作用于 continentalness，山脉细节另行叠加。**

---

### 条目 18 · Red Blob Games —— mapgen4

**1. 名称 + 链接**
- https://www.redblobgames.com/maps/mapgen4/ （HTTP 200，已抓取；页面标注 "Jul 2018, Apr 2023"）

**2. 一句话原理**
把"程序化生成的骨架（山脉/山谷/海洋）"与"物理模拟的细节（蒸发、风、降雨、河流）"分层，并**允许用户手绘修正骨架**后再重新模拟细节。

**3. 类别**
混合式（噪声骨架 + 水文模拟 + 可编辑），实时偏离线。

**4. 输出范围**
有限地图（可手绘，有边界）。

**5. 实时性**
**需全局模拟**（降雨、河流流量需要全图迭代）。**不能搬进本项目的零预计算约束**，但"分层"思想可搬。

**6. 双峰高程：否**（面向山脉/河谷观感，不是海陆双峰）。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无**（有河流与海岸线，无海洋地貌）。

**8. 内海与孤岛：可手绘修正**，但算法本身不保证。

**9. 语言 / 依赖 / 许可证 / star**
- 语言：JavaScript / TypeScript
- 许可证：页面原文："Treat this as a tool. Everything you create with it is yours to do with as you please. Credit is appreciated but not required."（**针对产出物，不一定是源码许可**）
- star：不适用

**10. 对本项目的可借鉴点**

1. **"骨架 / 细节"分层是本项目必须采用的架构**：低频 continentalness（骨架，决定海陆，走样条）与高频细节（山脉、沟壑）**分开管理和调参**。mapgen4 用不同的 UI 滑杆分别控制它们，你也应该在配置里分开。
2. **`lg_min_flow` 这类"阈值参数"的暴露方式值得学**：原文提到 "try decreasing lg_min_flow to see more small streams"。**本项目对应的是"最小岛屿面积阈值"**——把"多小的陆地算孤岛并抹掉"做成一个显式参数，直接服务"无散落小岛"指标。
3. **它的水文模拟需要全局迭代 → 明确不适合本项目。** 记录为**负面结论**：任何"流量累积/汇流"类算法（河流、湖泊）在 O(1) 约束下都不可行，除非改成"局部启发式"。
4. **手绘修正机制**对应本项目的"seed 选择 / 出生点保证"需求：若某 seed 的大陆质量不达标，可以在**离线标定阶段**筛选 seed（不违反运行时零预计算），而不是在运行时修复。

---

### 条目 19 · Red Blob Games —— 草图→细节 / 多边形地图 / 星球生成

**1. 名称 + 链接**
- 从草图生成细节地形：https://www.redblobgames.com/x/1729-generate-details/ （HTTP 200，已抓取）
- 多边形地图生成（经典文章）：http://www-cs-students.stanford.edu/~amitp/game-programming/polygon-map-generation/ （在 Red Blob 页面被逐字引用为 [1]/[44]/[51]）
- 球面 Delaunay/Voronoi：https://www.redblobgames.com/x/1842-delaunay-voronoi-sphere/ （HTTP 200）
- 星球生成：https://www.redblobgames.com/x/1843-planet-generation/ （HTTP 200）

**2. 一句话原理**
先由用户（或低频噪声）画出一张"骨架图"（海岸线、山脊），再用高频算法在骨架上"长出"细节高度与河流；骨架保证大格局可控，细节保证观感自然。

**3. 类别**
混合式（低频草图 + 高频细节），有限地图。

**4. 输出范围**
有限地图。

**5. 实时性**
细节生成可实时；但**河流/高程传播需要全图**（作者自述 "The page may be a little flaky… I'm including the javascript from two separate projects"）。

**6. 双峰高程：是（概念上）**，因为骨架本身就把海陆分开了。

**7. 大陆架 / 岛弧 / 海沟 / 洋中脊：无**（有海岸线、山脊、湖泊）。

**8. 内海与孤岛：显式支持内海！** 原文："To make an interior lake, draw the lake as a coastline. By default the interior will be land. Draw inside the coastline with boundary (-1) to make it a lake instead." —— **这是"内海"被当成一等公民处理的罕见例子**，但代价是**手工绘制**。

**9. 语言 / 依赖 / 许可证 / star**
JavaScript 示例；文章未标注正式开源许可证。

**10. 对本项目的可借鉴点**

1. **"边界值语义"这个设计很聪明。** 用 `boundary = +1` 表示山脊、`0` 表示海岸线、`-1` 表示内海边界，**同一套算法用不同边界条件产出不同地貌**。你的实现里可以照搬这个"符号约定"：正数 = 陆地约束，负数 = 水体约束，0 = 自由。
2. **"由外向内"的高程构造天然无内海。** PolyWorld（条目见游戏类文件）自述其做法："Starting at the border of the rectangle, the height of the island increases towards the center. Lake areas are flattened afterwards."（引自 https://raw.githubusercontent.com/Terasology/PolyWorld/develop/README.md ，已抓取）。**本项目无限世界没有"边界"，但可以把"到最近大陆核心的距离"当作那个单调上升的方向**——这正是附录 A 里"连通性约束"的做法。
3. **草图法不能搬（需要人工或全图），但"离线标定 + 运行时只用噪声"的两段式架构可以搬。** 即：**离线**画出你想要的大陆轮廓 → 用它去**拟合**一组噪声/样条参数 → **运行时**只跑噪声。这正好落在用户放宽后的"离线/有限范围方案"里，且**运行时仍然是 O(1)**。
4. **球面 Delaunay/Voronoi 的链接（条目 28 相关）**：如果你考虑过"把世界卷成球"，Red Blob 这两篇给出了 Voronoi 球面的工程细节。**本项目不需要球面（Z 是纬度带，不是球），但"避免极点奇点"的思路与"Z 周期接缝"是同一类问题**：都是把开区间卷成闭合环。
5. 其余字段：面向有限地图，**本项目只借架构与语义约定**，可借鉴度评"中"。

---

### 条目 20 · Perlin 1985 / 2002 与 Simplex 谱系 —— 噪声基元的原点

**1. 名称 + 链接**
- Perlin 1985 "An image synthesizer"：DOI https://doi.org/10.1145/325165.325247 （经 Crossref API 验证：`DOI=10.1145/325165.325247 | An image synthesizer | ACM SIGGRAPH Computer Graphics | 1985`）
- Perlin 2002 "Improving noise"：DOI https://doi.org/10.1145/566654.566636 （经 Crossref API 验证）
- 另见 ACM TOG 版本：https://doi.org/10.1145/566570.566636 （Crossref 验证）

**2. 一句话原理**
1985 年提出用"晶格上的伪随机梯度 + 平滑插值"合成连续无重复纹理；2002 年修正梯度表，去掉原始实现的轴向偏好与不连续。

**3. 类别**
噪声基元（梯度噪声）的原始论文。

**4. 输出范围**
无限程序化（1985 版**原生支持周期性**：其查表与哈希带 `repeat` 参数，见下）。

**5. 实时性**
运行时 O(1)。这是"O(1) 实时噪声"这门技术的起点。

**6. 双峰高程：否。**

**7. / 8.：不适用。**

**9. 语言 / 依赖 / 许可证 / star**
论文（ACM 版权）。实现公式属公有领域。

**10. 对本项目的可借鉴点**

1. **1985 年原始论文的噪声定义就是"可周期的"**：Perlin 用一张固定大小的随机表并按 `repeat` 取模，因此**周期性噪声不是后来的技巧，而是原始设计的一部分**。本项目 Z 轴周期 20,000,000 正是这个 `repeat` 参数的现代用法（见附录 B）。
2. **"梯度表只有 12 个向量"这个细节要理解。** Perlin 2002 把梯度限制在立方体棱中点方向（12 个），是为了让 `dot` 只用整数运算就能算——**在老硬件上这是关键优化**。Java 的 1.7.10 运行时同样吃这个优化，**照抄 12 梯度表**而不是用随机单位向量。
3. **Java 参考实现可以直接用。** Perlin 2002 随论文给出了 Java 的 `ImprovedNoise` 类（约 70 行），**这是本领域最广为流传的公有领域代码**。MC 1.7.10 自己的 `NoiseGeneratorPerlin` 就是它的变体。**你的实现若与它对齐，就等于与 MC 对齐。**
4. **Simplex 谱系（Gustavson 2005 与 KdotJPG 的 OpenSimplex2，见条目 13）的动机是"维度灾难"**：Perlin 的 `2^d` 个角点在 3D 以上太贵。**本项目只用 2D/3D，Perlin 与 Simplex 的成本差异不大**——但若采用圆柱嵌入（3D），Simplex 的优势才显现（Perlin 3D 要算 8 个角点，Simplex 3D 只需 4 个）。
5. **"Improving noise" 修正的具体缺陷值得知道**：原版梯度表存在**轴向偏好**（沿轴的噪声值系统性偏大），在**低频大陆场上会表现为沿坐标轴的拉丝**。本项目的大陆场是最低频的那种，**这个 bug 会直接可见**。所以：**不要用 1985 年的原始梯度表，用 2002 修正版或 OpenSimplex2 的梯度表。**
6. **引用价值**：论文 DOI 可直接写进你的设计文档，作为"为什么用梯度噪声而不是 value noise"的依据。

---

