# SummitField · 峰核场设计草案（L1c）

> 目的：让「Alpine Peaks 群系」必然出现在**真正的最高点**上，且与高度链、地图、雪线同源。
> 状态：**已拍板并实施完成（S1~S4）**。实施中的算法修订见文末「实施记录」。

---

## 1. 问题（一句话）

现在 Alpine 的判据是「kind=PEAK + 雪线」，而 kind=PEAK 是**脊线邻近度标签**（离中频脊线最近的 3.5% 陆地），不是高度排序；真正的高点由 **DLA 自己的锚点生长**决定（ridgeH = valley + (peak-valley)·elev01^0.85），两套几何互不相关 → 群系出现在山地上，但不是最高点上。

**不采用**「事后按高度检测最高点」：群系管理器常在 DLA 状态构建之前/之外被查询，依赖 DLA 会导致同坐标先后口径不一致（漂移）；且每次查询都算高度+查 DLA 太贵。

## 2. 核心思想：反转因果

**先指定峰位，再让高度链保证那里就是最高点**，群系跟着峰位走。

```
L1b 类型场（kind / elevation01 / relief01 / beltMask01）
        │
        ▼
L1c SummitField（峰核场）     每 512 格粗网格找「峰势」局部极大 → 稀疏峰核
        │                     （惰性计算 + 缓存，无全局预构建，不依赖 DLA）
        ├──► 高度链：DLA 抬升之后再叠加「峰核锥」 → 峰核中心必为局部最高
        └──► 群系：Alpine = 峰核影响 ≥0.55 且 峰核高度 ≥ 该纬度雪线
```

## 3. 峰核场算法

| 项 | 取值（初版，均可调） |
|---|---|
| 网格边长 CELL | 512 格 |
| 峰势 P | clamp01(0.45·elevation01 + 0.35·relief01 + 0.20·beltMask01) |
| 峰势门 | 仅当 kind∈{MOUNTAIN,PEAK} 或 (beltMask01≥0.45 且 elevation01≥0.20) 才参与；否则 P=0（低地不产生峰核） |
| 局部极大 | P(中心) > P(8 邻域)，并列用坐标 hash 打破（确定性） |
| 非极大抑制 | 3×3 内只保留最大者（等效最小峰距 ≈1024 格；可关） |
| 阈值 P_MIN | 0.42 |
| 峰核抬升 boost | BOOST_MIN + (BOOST_MAX-BOOST_MIN)·P^1.2，默认 14 ~ 42 格 |
| 影响半径 R | 1.15×CELL ≈ 590 格，径向 f = smoothstep(R, 0.18R, dist) |
| 每格缓存 | {isNucleus, P, boost, topEst}，CHM 键 = 打包 (cellX,cellZ) |

**查询 summit01(x,z)**：取 3×3 邻域内所有峰核，返回 max f 及对应 boost。
**topEst** = 峰核中心 V2TerrainGen.landBaseHeight(...) + boost（纯类型场，不含 DLA）——用于群系判据，避免依赖 DLA。

## 4. 高度链接入

```
h = V2TerrainGen.landBaseHeight(...)             // 类型档案 × 三层噪声
h = TalosMountainSystem.applyMountainUplift(h)   // DLA 山带（若在带内）
h = h + summitBoost(x, z)                        // ← 新增：峰核锥，最后叠加
h = clamp(h, seaLevel+1, worldHeight-2)
```

**为什么峰核必然最高**：DLA 脊面在 600 格尺度上的变化量估算 ≈ (peak-valley)/山带宽度 × 600 ≈ (252-78)/30k×600 ≈ **3.5 格**，远小于最小 boost 14 格 → 峰核中心对 3×3 网格邻域（±512 格）内任意点都高出 ≥10 格。
（若实测出现反例，旋钮优先调大 BOOST_MIN，其次缩小 R。）

## 5. 群系接入

V2BiomePicker.biomeAt：

```
summit01 ≥ 0.55  &&  topEst ≥ snowLineY(z)  →  TALOS_ALPINE
否则维持现有：kind PEAK/MOUNTAIN → MOUNTAINS，台地 → PLATEAU，其余 → PLAINS
```

- **赤道低峰**（已拍板）：topEst < 当地雪线 → 保持 **Mountains**（岩石山），峰核仍然抬高，只是不标 Alpine；
- 极地雪线低 → 峰核普遍满足 → Alpine 覆盖极地雪峰（面积仍受峰核稀疏限制，不会连片）；
- 块级「真雪」仍由地表填充按 实际 h vs 雪线 决定，与群系判据同源。

## 6. 与旧 kind PEAK 的关系

- kind=PEAK **保留**为「最贴脊线」的结构标签（地图/其他层可能用），不再承担「最高点/Alpine」语义；
- /talos_here 增加峰核信息：summit01 / 峰核中心 / topEst / 是否 Alpine。

## 7. 性能与内存

| 项 | 估算 |
|---|---|
| 每格首次构建 | 9 次 orography 采样 + 1 次 landBaseHeight(中心) ≈ 3~5µs，缓存后不再算 |
| 每次查询 | 9 次 CHM 查表 + 距离/插值 ≈ 0.3~0.6µs（群系管理器已有 per-坐标缓存） |
| chunk 生成新增 | getBiomesForGeneration 256 点/chunk ≈ +0.1~0.15ms/chunk |
| 内存 | 每 512 格网格 ≈ 2.4MB/seed（惰性，仅算访问过的格） |

## 8. 验收标准

1. /talos_here 站在 Alpine 上：summit01 ≥ 0.55，且该列高度 > 同 3×3 网格内任意点（抽 20 个峰核验证）；
2. /talosmap terrain 出图：峰核呈明显锥形高点 + 雪帽，山带内峰间有谷；
3. /talosBiome 「Talos Alpine Peaks」传送后确实站在雪峰顶部；
4. 峰间距统计：沿山带抽样，相邻峰核间距 ~1~4k 格；
5. 性能：chunk 生成增量 ≤0.2ms/chunk，群系查询 P99 ≤1µs；
6. 赤道低峰不出现 Alpine（雪线以下峰核 → Mountains）。

## 9. 实施步骤（小步、每步可验证）

| 步 | 内容 | 验证 |
|---|---|---|
| S1 | 新增 SummitField（网格 + 缓存 + 查询），出图（峰核点叠加在 terrain 图上） | 峰核分布图 |
| S2 | 高度链叠加峰核锥（Provider + 地图/渲染同口径） | /talosmap terrain 见锥形峰；高度排序验证 |
| S3 | 群系挂接（Alpine 判据改峰核）+ /talos_here 展示 + 旧 Alpine 判据下线 | /talosBiome 可搜到且落在峰顶 |
| S4 | 游戏内验收 + 调参（CELL / P_MIN / BOOST / R / 阈值） | 验收清单 §8 全过 |

## 10. 风险与回退

| 风险 | 缓解 |
|---|---|
| 峰核落在 DLA 坡面而非脊线上 | boost 主导（≥14 格）；仍不理想时再考虑「DLA 锚点偏向峰核」（动 DLA 内部，风险高，后置） |
| 极地 Alpine 面积偏大 | 提高 P_MIN / Alpine 阈值，或对高纬加「仅 topEst 前 X%」限制 |
| 世界高度上限 | clamp worldHeight-2（254） |
| 回退 | 开关 talos.v2.summitEnabled（默认 true），关掉即回到当前行为 |

## 11. 旋钮一览（集中一处常量表）

CELL=512、P_MIN=0.42、BOOST_MIN=14、BOOST_MAX=42、R=1.15×CELL、NMS=3×3、ALPINE_SUMMIT_MIN=0.55、ALPINE_TOPEST_MIN=snowLineY(z)。
---

## 12. 实施记录（2026-09 · S1~S4 完成）

**算法修订（相对草案）**：

1. **绝对目标收敛**（关键修订）：草案的"叠加锥"在验证中失败——438 个峰核里 426 个中心不是 ±512 邻域最高点（基底噪声在 512 格内起伏 ±10~20，而 boost 在锥缘只剩 1~4 格）。改为：`T = 支配半径圆盘内基底最大值 + boost`，高度链做 `h = min(h, T) + f·(T − min(h, T))` → 盘内恒 ≤ T、中心恒 = T。
2. **支配半径 1024**（草案为锥半径 588）：T 必须高于校验窗口对角半径（512×√2≈724）内的所有基底；取 1024 同时覆盖 DLA 带内多数点位。
3. **锥半径收窄到 220 / 平台 45**：草案 588 半径下 14~42 格升幅只有 5% 坡度（像圆盘）；220 半径给出 6~19% 坡度。
4. **DLA 双重封顶**：`peak = min(peakForKind, base + 55, T)` —— 既防 DLA 252 越顶，也防远离峰核处山脊失控抬高（旧轨 peakCap=+∞，行为不变）。
5. **锥腰微起伏** `4f(1−f)·±3 格`（λ≈140）：让雪线边界破碎；中心权重为 0，不破坏最高点性质。

**验收结果（seed 12345）**：

- 峰核数 438（x 41k~358k、z 20k~170k），`violations = 0`，`minMargin ≥ 0`（中心恒为 ±512 邻域最高点）；
- 出图：`run/talos_maps/v2_summit_nuclei_full_s12345.png`（白点=峰核分布）、`v2_summit_zoom3k_s12345.png`、`v2_summit_prom_s12345.png`；
- 性能：单次采样 ~0.3~0.6µs（9 格查表）；峰核首次构建 ~25 次基底采样（缓存后归零）。

**已知 / 待调**：

- 中纬（雪线 ~134-155）峰核 T 多在 130~165 → Alpine 主要出现在高纬与最高山体；若要中纬雪峰，调 `BOOST_MIN/MAX` 或雪线曲线；
- 极地雪线低，整片高山都在雪线以上（自然结果，非 bug）；
- 关掉 `talos.v2.summitEnabled` 即回到无峰核行为（Alpine 消失，峰位不再保证最高）。