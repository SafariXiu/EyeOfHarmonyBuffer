# V2 群系系统设计：气候驱动 + 变体框架

> 状态：设计稿（待实施）。前置：D38（L1c 群系场 V2BiomeSelect 已落地）。
> 目标：群系分布由**物理气候量**推出（风带 / 洋流 / 气团 / 地形），并且能持续追加变体群系而不改选择器代码。

---

## 一、现状诊断（2026-09）

`V2BiomeSelect` 目前消费 4 个标量 + 纬度带：

| 输入 | 现状 |
|---|---|
| `samplePressure` | → 动力干湿 `dry = 0.5+p/3.2` |
| `sampleAirTemp` | → 温度坐标（0.70·纬度带 + 0.30·气团温度） |
| `sampleHumidity` | → 湿度坐标（权重 0.50） |
| `sampleMaritime` | → 湿度坐标（权重 0.28） |
| `bandD` | → 纬度带（温度坐标权重 0.70） |

**缺口**：

1. **风带没进**：`sampleWind` 未被消费 → P1b 的迎风抬升雨 / 背风焚风只进了 `/talosmap` 与 `GlobalClimate.rainfallBase`，群系拿不到 → 没有"西风带迎风坡湿润、背风坡荒漠"。
2. **洋流/海温没进**：只有 `maritime` 一个间接量 → 没有"寒流海岸荒漠（纳米布/阿塔卡马）""暖流海岸湿热（亚马逊）"。
3. **气团标签没进**：`AirMassType`(cP/mP/mT/cT) 已算出但无人消费。
4. **权重是手调的**：`0.50·q + 0.28·mar + 0.32·(1−dry)` 没有物理含义，难解释也难扩展。
5. **海拔只在高山分支里特判**：`temp ≤ 0.20 + 0.60·elev`，高平原/高原不会变冷 → 高原上没有寒带群系。
6. **硬切**：单一 argmax、无权重 → 群系边界生硬（视觉复核："雪山紧邻沙漠、过渡带缺失"）。
7. **无变体框架**：16 个群系写死在 if/else 与 switch 里，加一个变体要改选择器 + 映射 + 表面 + 装饰四处。

---

## 二、目标架构

```
L0 环流层（RelaxedClimate / GlobalClimate P1b）
        │  派生「群系气候坐标」（本设计新增 ClimateCoords）
        ▼
   tempEff  = 温度：纬度带 + 气团温度 + 海拔直减
   moistEff = 湿度：空气湿度 + 海洋性 + 迎风抬升 − 背风焚风 − 寒流海岸干燥 + 气团签名
   continent= 内陆度（海岸距离）
   season   = 季节振幅（预留，当前为 0）
        │
        ▼
L1c 群系场 V2BiomeSelect
   Tier-1 基群系：气候空间 (tempEff, moistEff) 的**软分配**（取最近 2~3 个，带权重）
   Tier-2 变体：地形变体（山/台地/盆地，来自 L1b）+ 特殊变体（沼泽/盐沼/绿洲/火山…）
        │
        ▼
   输出：主群系 + 次群系 + 混合权重 w_i + heightBias/heightScale（按 w_i 插值）
        │
        ├─→ 地形（V2TerrainGen：高度带调制，连续）
        ├─→ 表面（TalosSurfaceRegistry：主群系）
        └─→ 装饰（BiomeDecoratorTalos2：主群系 + 变体规则）
```

**原则**：
- 群系选择**单向依赖**气候与类型场，绝不读最终高度（避免循环）。
- 气候坐标**集中在一处**计算（`ClimateCoords`），选择器只做"坐标 → 群系"的查表/软分配。
- 高度倾向**按权重插值**，所以即使群系 ID 硬切，地形也是连续的。

---

## 三、每个气候量的物理接法

### 3.1 温度：tempEff = tempBase − LAPSE · elevation01

```
tempBase = W_LAT·(1 − bandD) + (1 − W_LAT)·(0.5 + 0.5·airTemp)      // 纬度带 + 气团温度
tempEff  = tempBase − LAPSE·elevation01                             // 海拔直减
```

- `elevation01` 是 L1b **类型量**（残差 q93 归一），不是最终高度 → 不产生循环依赖。
- `LAPSE ≈ 0.55`：elevation01 从 0 → 1 相当于降温 0.55（温度坐标量程），足以让高原顶上出现寒带群系。
- **收益**：高山/高原的 Alpine、Tundra 由同一条公式自然产出，删掉 ALPINE_ELEV_GAIN 特判。

### 3.2 湿度：把 P1b 的物理降水搬进来

现在的 P1b（`GlobalClimate`）已经算过完整降水，但为"出图/单点诊断"设计，每列调用偏贵。做法：**抽出共享算子** `ClimateCoords.orographicTerm(x,z,wind)`：

```
wind  = RelaxedClimate.sampleWind(x, z)                    // 1 次查表
g     = ∇elevation01（±STEP 四点差分，STEP ≈ 6000）        // 4 次 elevation01
up    = max(0,  g·ŵ) / UPLIFT_SCALE                        // 迎风抬升
lee   = max(0, -g·ŵ) / UPLIFT_SCALE                        // 背风下沉
moist = moistBase + 2.0·up·q − 0.8·lee·q                   // 抬升成雨 / 焚风减雨
```

- 这就是「**西风带迎风坡多雨、背风坡荒漠**」的判据来源。
- 与 `GlobalClimate` 共用同一算子 → 群系与 `/talosmap`、降水场口径一致。

### 3.3 洋流 / 海温：海岸干燥（寒流）与海岸增湿（暖流）

真实机制：**寒流**上空空气稳定 → 少对流 → 沿岸荒漠；**暖流**提供水汽与对流 → 沿岸湿热。判据用「离岸风 + 海温距平」：

```
// 取上风方向的海点（若上风是海）：p = (x,z) + ŵ·D，D ≈ 60km
if (上风方向是海) {
    sstAnom = sst(p) − 同纬度海温均值(bandD)      // M5 解已涌现西岸暖/东岸冷
    moist  += 0.9·sstAnom·onshoreWeight            // 暖流岸 +湿
    moist  −= 0.9·max(0, −sstAnom)·onshoreWeight   // 寒流岸 −湿
    onshoreWeight = 上风是海 ? 1 : 0（可用 coastDist 平滑）
}
```

- 只需要 `sampleSst`（1 次查表）+ 上风点判定（复用 3.2 的 wind）。
- 无风/内陆时该项为 0。

### 3.4 气团签名（最便宜、最物理的兜底）

`AirMassType` 已是 M3 的产物，直接当**湿度/温度的微调**：

| 气团 | Δtemp | Δmoist | 说明 |
|---|---|---|---|
| mT 海洋性热带 | +0.05 | **+0.10** | 湿热 |
| cT 大陆性热带 | +0.08 | **−0.10** | 干热 |
| mP 海洋性极地 | −0.05 | **+0.06** | 湿冷 |
| cP 大陆性极地 | −0.08 | **−0.08** | 干冷 |

### 3.5 内陆度（已有）
`continent = clamp01(−coastDist / 40k)`；用于大陆性干旱（距海 > 40km 且无地形雨 → 草原/荒漠）。

### 3.6 汇总

```
tempEff  = W_LAT·(1−bandD) + (1−W_LAT)·(0.5+0.5·airTemp) − LAPSE·elevation01 + Δtemp_气团
moistEff = 0.55·q + 0.25·mar + 0.20·(1−dry)
           + 2.0·up·q − 0.8·lee·q          // 风带 + 地形
           ± 0.9·sstAnom·onshore            // 洋流
           + Δmoist_气团
           − 0.25·continent·(1 − 0.5·mar)   // 内陆干燥（海洋性记忆抵消）
```

所有系数集中为 `ClimateCoords` 的静态可调字段，探针可扫参。

---

## 四、变体群系架构（关键）

### 4.1 三层结构

| 层 | 决定什么 | 依据 | 例子 |
|---|---|---|---|
| **Tier-1 基群系** | 气候带 | (tempEff, moistEff) 软分配 | 沙漠 / 草原 / 平原 / 森林 / 雨林 / 苔原 |
| **Tier-2 地形变体** | 同一气候下的高度/坡度变体 | L1b：relief01 / elevation01 / kind | 山地 / 高原 / 盆地 / 丘陵 |
| **Tier-3 特殊变体** | 局地成因 | 独立判据（水体/盐碱/火山/河谷…） | 沼泽 / 盐沼 / 绿洲 / 温泉谷 |

最终群系 = Tier1 ⊕ Tier2 ⊕ Tier3（⊕ = 按优先级覆写 + 权重混合）。

### 4.2 规则表（数据驱动）

新增 `V2BiomeRules`：每条规则是一个数据条目，选择器只做「打分 + 取前 N」：

```java
final class BiomeRule {
    Kind kind;
    Tier tier;                 // BASE / TERRAIN / SPECIAL
    int priority;              // 同层内覆写顺序
    // 气候中心 + 容差（Tier-1 用椭圆隶属度；其它层可忽略）
    double tempC, moistC, tempR, moistR;
    // 硬条件（Tier-2/3 用）：返回 0~1 的连续隶属度
    DoubleBinaryOperator gate; // (climate, type) → weight
    double heightBias, heightScale;
    String surfaceProfile, decorProfile;
}
```

**加分系群 = 加一行数据**，不改选择器、不改映射、不改装饰分发。

### 4.3 软分配（解决硬边界）

Tier-1 不用 argmax，而是：
1. 对每条 Tier-1 规则算隶属度 `m_i = exp(−d²/2σ²)`（d = 气候空间距离）；
2. 取 `m` 最大的 2~3 条，归一化成权重 `w_i`；
3. **高度倾向按 `w_i` 加权** → 地形连续；
4. **群系 ID** 取 `w` 最大者（离散，供表面/装饰），可选后续再做 tile 平滑（T2.2）。

这样即使 ID 在边界翻转，地形不会有台阶；后续做群系平滑时也有现成的权重场可用。

### 4.4 变体的三个典型场景（验收用例）

| 场景 | 判据 | 结果 |
|---|---|---|
| 寒流海岸 | 上风是海 + `sstAnom < −0.15` + 干旱带 | DESERT（纳米布型） |
| 西风带迎风坡 | `up` 大 + 温带 | TEMPERATE_FOREST（背风坡是 STEPPE） |
| 高原寒化 | `elevation01 > 0.7` + 温带 | SUBPOLAR_TUNDRA / ALPINE（不是"暖高原"） |

---

## 五、性能方案

群系是**低频场**（气候 LUT 2 km 格、L1b 类型场 km 级）。因此：

- **每区块按 4×4 或 8×8 粗采样**（每 4 或 2 格一次），群系 ID 用最近邻、heightBias/heightScale 用双线性插值；
- 成本从 256 次/区块 → 16 次/区块（16×↓），每次新增 ~1 µs（风 + 梯度 + 海温查表）→ **≈16 µs/区块**，可忽略；
- `WorldChunkManagerTalos2` 已有按点缓存，不受影响；
- 若仍要更省：把 `ClimateCoords` 结果按 512 格 tile 缓存（tile 内 8×8 采样）。

---

## 六、实施步骤（每步可独立验证）

| 步 | 内容 | 验收 |
|---|---|---|
| **B1** | 抽 `ClimateCoords`（tempEff/moistEff/continent + 风带地形项 + 气团签名）；`V2BiomeSelect` 改吃这两个坐标 | 探针：气候坐标分布 + 群系占比与现状对比 |
| **B2** | 洋流/海温海岸项（寒流荒漠、暖流湿热） | 探针：海岸线剖面，验证迎风岸 vs 背风岸的 wet 差 |
| **B3** | 规则表 V2BiomeRules + 软分配（Top-2/3 权重） | 探针：边界处高度倾向连续（无台阶） |
| **B4** | 变体层骨架（Tier-2 复用现有；Tier-3 留接口 + 2 个样例：沼泽/盐沼） | 加一个变体 = 只加一条规则数据 |
| **B5** | 性能：区块内粗采样 + 插值 | 计时对比：区块生成耗时变化 < 5% |
| **B6** | 群系平滑（T2.2）：权重场邻域混合 / tile 连通分量吞并 | 视觉复核：无硬边 |

---

## 七、风险

1. **过拟合调参**：物理项多了，系数容易互相抵消。对策：每项单独可开关（`ClimateCoords.ENABLE_ORO / ENABLE_SST / ENABLE_AIRMASS`），探针逐项开关对照。
2. **寒流项在无风时退化**：上风方向不稳时用海岸法线兜底；内陆直接关掉。
3. **规则表膨胀**：Tier-1 超过 ~12 条后软分配会出现「谁都不像」的中间态。对策：Tier-1 保持粗粒度（气候带），细分交给 Tier-2/3。
4. **与 L1b 类型场的耦合**：山/高原仍由类型场定，若未来改 PlateField 需要同步规则表的 gate。