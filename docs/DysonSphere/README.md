# 塔罗斯戴森球：模块化巨构机器系统

> 本文件是戴森球**机器系统**的唯一规格记录，随开发实时更新。
> 天空盒渲染、全局赛跑语义与三计数器见同目录 [design.md](./design.md)。

## 一、定位

戴森球是游戏旅程最后的唯一超级巨构：每队一台**戴森核心**，核心通过 32 个**模块槽**挂载可插拔模块。
三计数器（云/框架/贴片）、每日结算、完工规则全部沿用 `design.md` 的定稿语义，本文件只负责机器侧。

## 二、全局约定

- **队伍语义**：`OrundumEnergyService.getTeamIdForUser(playerUUID)`（空间工程队长），不使用 `/ocgroup`。
- **能量交互**：核心与所有模块**不设能量仓、不接能量 hatch**。一切耗电/发电只走两套无线账本：
  - 无线 EU：`addEUToGlobalEnergyMap(userUUID, BigInteger)`
  - Orundum：`OrundumEnergyService.changeOrundumForTeam(teamId, BigInteger)`
- **拆分系数 `euShare`**（0~1，全局配置，默认 0.5）：所有能量进出统一按它拆两本账——
  消耗与产出都满足 `EU 部分 = 总量 × euShare`、`Orundum 部分 = 总量 × (1 - euShare)`。全部使用 BigInteger。
- **单位约定**：所有功率以 **EU/t** 计；无线 EU 账本单位为 EU，Orundum 账本按 **1 EU ≡ 1 Orundum** 换算。
  接收模块**每 1 秒（20 tick）结算一次**，单次入账 = 功率 × 20；制造/发射模块按各自无线周期结算。
- **进度读写**：机器只调用 `DysonSphereSystem.addModules(...)`、`settleDaily(...)`，读取 `DysonSphereWorldData.getTeam(teamId)`；
  天空盒照旧渲染领先者，机器层不感知渲染。

## 三、戴森核心

- 每队**限 1 台**（当前为规则约定，代码强制的唯一性检查列入待办）。
- **无燃料、无电池、自身不耗电**；核心是纯枢纽：识别模块、按贴片进度激活模块槽、把队伍三计数器推给系统。
- 结构：先用**手写 shape 字符串的占位结构**跑通逻辑（约 15×15×21，含 32 个模块位），最终形态后续替换。
- 32 个模块位**自始就物理存在**，但按本队贴片数逐步激活：

| 本队贴片数 | 激活槽位 |
| --- | --- |
| 0 | 8 |
| 500,000 | 12 |
| 1,000,000 | 16 |
| 1,500,000 | 20 |
| 完工（2,000,000） | 32 |

- 激活规则：控制器定期重算，超出激活数的模块被 `disconnect()`；后 12 槽**只在完工后启用**。

## 四、模块体系

所有模块继承 `DysonModuleBase`（基于 `OrundumWirelessMultiMachineBase`），模块位用自定义结构元素收集（参考 God Forge 的 `addModuleToMachineList` 模式）。

`DysonModuleBase` 契约：

- `getModuleType()`：`MANUFACTURING / LAUNCH / RECEIVER / FUNCTIONAL`；
- `getRequiredPaste()`：启用所需的贴片数，默认 `0`（保留给未来“功能性模块”设门槛）；
- `connect() / disconnect() / isConnected()`：由核心控制，断开时不 tick、不结算；
- 自身业务走 `doWirelessBusinessOnce()` 等无线基类钩子，能量只走第二节的两本账。

当前三种模块门槛均为 **0 贴片**。

## 五、三种模块

### 1. 制造模块

- 配方：原料 → **戴森云组件** / **框架组件**（配方材料先用占位，数值待定）。
- 成本：按配方 `EUt × 时长 × 并行`（单位 EU）结算，经 `euShare` 拆到两本账（Orundum 按 1:1）。
- 0 贴片门槛；完工后仍可工作（组件可作他用）。

### 2. 发射模块

- 输入：戴森云组件或框架组件（同槽自动判定）。
- 效果：`DysonSphereSystem.addModules(teamId, cloudDelta=+1 或 frameDelta=+1)`，1 组件 = 1 计数。
- 成本：每次发射 `C_launch = 10,000 EU`（可配置），经 `euShare` 拆两本账（Orundum 按 1:1）。
- 0 贴片门槛；**完工后永久失效**（`addModules` 返回 false）。
- 多台发射模块可并行，共同推进本队计数器。

### 3. 接收模块

- **每核心至多 1 台**：核心连接阶段只允许 1 个接收模块，多余的保持断开（防止叠接收机刷功率）。
- 输出：本队当前功率池全额，单位 **EU/t**，每 1 秒（20 tick）结算一次，单次入账 = `P × 20`：
  - 完工前：`P = cloudCount × 2^41 + pasteCount × 2^79`；
  - 完工后（仅占领者）：`P = 10^200` 固定。
- 拆分：无线 EU 账户 `+P × euShare`（单位 EU），Orundum 账户 `+P × (1 - euShare)`（1 EU ≡ 1 Orundum）。
- 0 贴片门槛；戴森云无需框架即可发电，纯云路线可用。

## 六、完工后

- 发射模块与一切发射行为永久锁死（含占领者）。
- 后 12 槽解锁，供**接收模块与未来功能性模块**使用。
- 占领者剩余轨道云按每日掉落递减至 0；球壳以 10^200/tick 供能。

## 七、升级树

- 模仿 God Forge 的 `ForgeOfGodsUpgrade` + `UpgradeStorage` 结构：节点枚举声明前置/花费/UI，存储负责解锁与 NBT。
- **货币占位**：当前用原版圆石；后续替换为专属物品。
- 首批节点（效果数值均为占位，待定）：

| 节点 | 前置 | 花费（圆石占位） | 效果（占位） |
| --- | --- | --- | --- |
| START 核心激活 | 无 | 0 | 激活核心 |
| 制造效率 I | START | 1 | 制造模块并行 +50% |
| 制造效率 II | 制造效率 I | 2 | 制造模块并行再 +50% |
| 发射效率 I | START | 1 | 发射成本 -10% |
| 发射效率 II | 发射效率 I | 2 | 发射成本再 -10% |
| 贴片加速 | START | 2 | 每日结算加成（待定） |
| 接收强化 | 贴片加速 | 3 | 接收输出 +10%（待定） |

## 八、接口清单

| 机器侧调用 | 作用 |
| --- | --- |
| `DysonSphereSystem.addModules(world, teamId, teamName, cloudDelta, frameDelta)` | 发射模块推进本队云/框架 |
| `DysonSphereWorldData.getTeam(teamId)` | 读本队 cloud/frame/paste |
| `DysonSphereSystem.settleDaily(world)` | 每日结算（由每日处理器触发） |
| `OrundumEnergyService` / `addEUToGlobalEnergyMap` | 能量出入两本账 |

## 九、待定 / 占位清单

- [ ] 核心唯一性（每队一台）的代码强制。
- [ ] 核心与模块的最终结构（当前为手写占位结构）。
- [ ] 组件配方材料与制造/发射成本数值。
- [ ] 升级树效果数值与专属货币物品。
- [ ] 本地化文案。

## 十、变更记录

- 2026-08-13：定稿核心+模块架构、32 槽贴片曲线、三模块规格、能量走无线账本、升级树占位。
