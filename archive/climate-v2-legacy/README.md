# climate-v2-legacy（2026-09 归档）

V2 气候层重构过程中被**网格解（RelaxedClimate）取代**的解析链与旧环流模型。
均为不可编译的文本快照（.bak.java），仅作历史/算法参考——**不要引用或恢复编译**。

| 文件 | 原角色 | 被谁取代 |
|---|---|---|
| PressureField.java | M2 解析气压诊断场 + 地转风 | RelaxedClimate（网格解） |
| OceanCurrentField.java | M4 解析洋流（埃克曼+岸墙折射+SST 输运） | RelaxedClimate.fu/fv + sampleSst |
| AdvectedAirField.java | M3 解析气团上游输运 | RelaxedClimate.tAir/q/mar |
| AirMassField.java | 旧 isLand×band 气团分类 | GlobalClimate 溯源派生 + q 网格 |
| CirculationSample.java / PressureSystemType.java | 旧半固定气压系统 DTO/枚举 | RelaxedClimate.p 网格（残差行动中心） |
| GlobalCirculation.java（快照） | 含三圈基底+半固定系统旧模型 | 已瘦身为纯纬度/环面工具（fold/bandD） |

关键算法（高斯动力剖面 / 地转+摩擦 / 埃克曼转向 / 岸墙折射 / 上游输运弛豫）
在 RelaxedClimate / GlobalClimate 内为唯一权威实现。
