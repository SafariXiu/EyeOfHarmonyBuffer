# climlab 与 MITgcm 源码级调研报告

调研方式：所有结论均来自本会话实际抓取的 raw.githubusercontent.com 源码 / GitHub HTML / readthedocs 页面。行号由抓取到的文件原文按换行切分后计数得到（与 raw 文件一致）。未验证项统一列在末尾。

---

# 模型一：climlab

## 1. 项目名 + 主链接 + 语言 + 许可证 + 维护状态

| 项 | 值 |
|---|---|
| 项目名 | climlab（Python package for process-oriented climate modeling） |
| 主链接 | https://github.com/climlab/climlab ・ https://climlab.readthedocs.io/ |
| 语言 | 纯 Python（v0.8.1 起 Fortran 全部外移，README L164-168：all the Fortran code has been moved into external companion packages ... Climlab is now (once again!) a pure Python package） |
| 许可证 | MIT（setup.py L19 / L24；README L340-343） |
| 维护状态 | 活跃。setup.py L3 VERSION = '0.10.0.dev'；README L131 "Version 0.9.2 (released March 2025)"；docs 首页标题 "climlab 0.10.0.dev0 documentation" |
| Python | README L101：testing on versions 3.10, 3.11, 3.12, 3.13 |
| 依赖 | README L99-111：numpy / scipy / pooch（远程数据访问与缓存）/ xarray；推荐 numba>=0.43.1。environment.yml：conda-forge + climlab-rrtmg>=0.4.1, climlab-emanuel-convection, climlab-cam3-radiation>0.2, climlab-sbm-convection |
| 安装 | conda-forge（README L62）。README L72-73：Windows 目前不支持 |

**包树更正**：`climlab/ebm/`、`climlab/column/`、`climlab/seaice/`、`climlab/atm/`、`climlab/ocean/` 五个路径在 master 上全部 404。实际为 `climlab/{model,domain,process,radiation,dynamics,surface,convection,solar,utils}`。

## 2. 算的是什么（输出场）

- EBM 族：纬向平均（可选经向）地表温度 `Ts`（°C）。诊断：OLR、ASR、net_radiation、albedo、icelat、ice_area、diffusive_flux、flux_convergence、heat_transport(PW)、heat_transport_convergence(W/m²)。（ebm.py L207-210；meridional_heat_diffusion.py L30-31）
- 柱模式族：`Ts` + `Tatm`。诊断：OLR、ASR、LW_down_sfc、LW_up_sfc、LW_absorbed_sfc、SW_absorbed_sfc、SW_up_sfc、SW_up_TOA、SW_down_TOA、SW_down_sfc、planetary_albedo、LW_emission、LW_absorbed_atm、SW_absorbed_atm（column.py L87-102）
- 输出为 climlab.Field（numpy 子类 + domain 元数据），可经 climlab.to_xarray 转 xarray。

## 3. 方程与近似（实际公式 + 参数值）

### 3.1 EBM 主方程（climlab/model/ebm.py L7，模块 docstring）

C ∂T_s(φ,t)/∂t = (1-α)S(φ,t) - [A + B·T_s] + (1/cosφ) ∂/∂φ [ cosφ · D · ∂T_s/∂φ ]

### 3.2 OLR 线性化 AplusBT（climlab/radiation/aplusbt.py）

- L13：R↑ = A + B·T；L148 实现 `self.OLR[:] = self.A + self.B * value`
- 类自身默认（L85）：A=200., B=2.
- **climlab.EBM 实际生效值**：ebm.py L232-233 A=210., B=2.，经 L265 `AplusBT(..., **self.param)` 覆盖类默认 → A=210 W/m², B=2 W/m²/°C
- AplusBT_CO2（L170-174, L187）：A(c) = -326.4 + 9.161c - 3.164c² + 0.5468c³；B(c) = 1.953 - 0.04866c + 0.01309c² - 0.002577c³；c = log(p/300)，p 单位 ppm，默认 CO2=300.0

### 3.3 反照率（climlab/surface/albedo.py）

- P2Albedo._compute_fixed L174：`albedo = self.a0 + self.a2 * P2(np.sin(phi))`，P2(x) = (3x²-1)/2
- P2Albedo 默认 L120：a0=0.33, a2=0.25
- Iceline.find_icelines L244-279：`ice = np.where(Ts < Tf, True, False)`；ice_area = global_mean(ice)；icelat 由 boolean 数组 np.diff 跳变位置在 lat_bounds 上取值（L260）；全冰 → [-0., 0.]，无冰 → [-90., 90.]
- StepFunctionAlbedo._get_current_albedo L362-369：`Field(np.where(ice, cold_albedo, warm_albedo))`；cold = ConstantAlbedo(albedo=ai)，warm = P2Albedo(a0,a2)（L351-352）
- climlab.EBM() 默认（ebm.py L236-239）：Tf=-10.0 °C, a0=0.3, a2=0.078, ai=0.62
- climlab.EBM_seasonal() 默认（ebm.py L366）：a0=0.33, a2=0.25, ai=None；ai is None 时**关闭冰反照率反馈**并从 param 删除 ai/Tf、albedo 换成纯 P2Albedo（L426-440）

### 3.4 日照

- P2Insolation._calc_insolation（radiation/insolation.py L280-282）：S(φ) = (S0/4)(1 + s2·P2(sinφ))
  默认（L251）：S0 = const.S0 = 1365.2 W/m²（constants.py L33），s2 = -0.48
- AnnualMeanInsolation._calc_insolation L439-440 → annual_insolation；solar/insolation.py L398-401 用 **10000 个等距采样点**数值平均：`days = np.arange(0., 1., 0.0001) * days_per_year`，再 `Fsw.mean(dim='day')`
- DailyInsolation L556-591：对一整年按 timestep 预计算 coszen 与 irradiance_factor 数组，当前时刻取列；L587 `insolation[:] = S0 * coszen * irradiance_factor`
- 天文公式（solar/insolation.py）：
  - 赤纬 L411：δ = arcsin(sin(obliquity)·sin λ)
  - 日没时角 L423-426：h0 = arccos(-tanφ·tanδ)；极昼/极夜取 π / 0
  - 日平均 coszen（time weighting）L451：coszen = [h0·sinφ·sinδ + cosφ·cosδ·sin h0] / π，再 np.maximum(...,0)
  - insolation weighting L462-467；sunlit weighting L477-478
  - 日地距离 L264（Berger 1978）：ρ = (1-e²)/(1 + e·cos(λ - ϖ))；radiation/insolation.py L57 `irradiance_factor = rho**(-2)`
  - 太阳黄经 L518-528（Berger 1978 §3 近似）：`delta_lambda = (day - 80.) * 2*pi/days_per_year`，含 e、e²、e³ 修正项；λ=0 定义为春分
- 现今天文参数**硬编码**（constants.py L70）：`orb_present = {'ecc': 0.017236, 'long_peri': 281.37, 'obliquity': 23.446}`；L71-74 注释明确说明这样设计是为了避免读数据文件

### 3.5 热扩散与热容量

- MeridionalHeatDiffusion.__init__（meridional_heat_diffusion.py L65-68）默认 D=0.555（W/m²/°C，注释 "same as B"）
- 单位换算 L85-89：`self.K = self.D / heat_capacity * const.a**2`，K 单位 m²/s
- heat_capacity = rho_w·cw·dz（utils/heat_capacity.py L84）；rho_w=1000.、cw=4181.3（constants L38-39）
- climlab.EBM() 默认 water_depth=10.0 m → **C = 1000 × 4181.3 × 10 = 4.1813×10⁷ J m⁻² K⁻¹**（与 domain.py L374-375 docstring 示例 heat_capacity → array([41813000.]) 一致）
- a = 6.373E6 m（constants L15）
- **由此推出的等效扩散系数：K = 0.555 / 4.1813e7 × (6.373e6)² ≈ 5.39×10⁵ m²/s**（本报告由源码常量计算，climlab 源码未直接写出该数）
- 球面算子（meridional_advection_diffusion.py）：`_Xcenter = phi*const.a`、`_Xbounds = phi_stag*const.a`、`_weight_bounds = np.cos(phi_stag)`、`_weight_center = np.cos(phi)`
- 诊断 L96-99：heat_transport = diffusive_flux · C · 2π a cosφ · 1E-15（PW）；heat_transport_convergence = flux_convergence · C（W/m²）

### 3.6 柱模式物理

- GreyRadiationModel.__init__（model/column.py L46-57）默认：num_lev=30, num_lat=1, water_depth=1.0, albedo_sfc=0.299, timestep=const.seconds_per_day, Q=341.3, abs_coeff=1.229E-4 m²/kg
- RadiativeConvectiveModel L143-150：额外 adj_lapse_rate=6.5 K/km 对流调整
- BandRCModel L153-179：absorber_vmr['CO2']=380.E-6，absorber_vmr['O3']=zeros，H2O 用 ManabeWaterVapor
- FixedRelativeHumidity / ManabeWaterVapor（radiation/water_vapor.py L8, L47）：relative_humidity=0.77，qStrat=5.E-6
- 湍流表面通量（surface/turbulent.py）：
  - _SurfaceFlux.__init__ L66 `Cd=3E-3, resistance=1.`；L72 `add_input('U', 5.*ones_like(Ts))`
  - SensibleHeatFlux L126：flux = resistance · cp · rho · Cd · U · ΔT
  - LatentHeatFlux L181：flux = resistance · Lhvap · rho · Cd · U · Δq；诊断 LHF、evaporation(kg/m²/s)
  - 加热率分配 L79-81：heating_rate['Ts'] = -flux；heating_rate['Tatm'][...,-1,:] = +flux

### 3.7 时间离散（关键）

- EnergyBudget._temperature_tendencies（process/energy_budget.py L58-68）：tendencies[var] = heating_rate[var] / C
- 父进程把所有子进程 tendency **相加**后做一次前向欧拉（time_dependent_process.py L165 compute / L314 step_forward）
- 扩散子进程是 implicit（process/implicit.py L20 `time_type='implicit'`；L47-57 把 (新状态 − 旧状态)/Δt 当 tendency 返回）
- **EBM 实际格式 = 隐式扩散 + 显式辐射/反照率的算子分裂**：T^{n+1} = T^{n+1}_{隐式扩散} + Δt · (显式 tendency 之和)。并非把所有项放进同一个隐式矩阵。
- 默认时间步（ebm.py L240）`timestep = const.seconds_per_year/90.` = 31 556 926.08 / 90 = **350 632.5 s ≈ 4.058 天**（constants L53 days_per_year=365.2422、L57）→ **90 步/年**

## 4. 是否需要全局迭代（★ 本题最关键）

**不需要，而且 climlab 根本不具备全局 2D 椭圆求解能力。**

- 扩散算子只在**单一一根轴**上工作。advection_diffusion.py L41-42：The state variable ψ may be multi-dimensional, but the diffusion will operate along a single dimension only.
- _guess_diffusion_axis（同文件 L235-259）：若存在多于一根长度 > 1 的轴，**直接 raise ValueError('More than one possible diffusion axis.')**。lat-lon 2D 状态在自动推断下会直接报错。
- 矩阵构建 advdiff_tridiag（dynamics/adv_diff_numerics.py L269-336）：返回 (..., J, J) 的**三对角矩阵**（只有 3 条对角线非零），或 (3, J) 的 banded 打包形式。
- 时间推进 implicit_step_forward（同文件 L382-429）求解 (I - T·Δt)·ψ^{n+1} = ψ^n + S·Δt：
  - L420（banded 分支）：`scipy.linalg.solve_banded((1, 1), IminusTdt, RHS)`
  - L426-429（默认分支）：构造满 J×J 单位阵与三对角阵，`numpy.linalg.solve(IminusTdt, RHS[..., None])[..., 0]`
- **climlab.EBM 走默认分支**：ebm.py L273 显式写 `use_banded_solver=False`。每步是一次 **J×J 稠密 LU（O(J³)）**，J = num_lat = 90（默认）。
- 若用 num_lon 开 2D 域（domain/domain.py L499 surface_2D），状态形状 (lat, lon, depth)，矩阵堆叠为 (..., J_lat, J_lat)，numpy.linalg.solve 对每个经度**独立**求解 —— **经向无任何耦合项，不存在 2D 联立椭圆解**。
- 唯一的"迭代"在外层平衡态搜索：TimeDependentProcess.integrate_converge(crit=1e-4)（time_dependent_process.py L463-498）——每次积分 1 年，直到 `np.max(np.abs(value_old - value)) <= 1e-4` 才停。这不是每步的迭代求解器，而是长时间推进到平衡。
- 网格（domain/axis.py L155-193）：lat 轴默认端点 (-90., 90.)，num_points=90 → 均匀 2° 间隔，91 个边界点、90 个中心点（-89°…89°）；domain/domain.py L486 `Axis(axis_type='lat', num_points=num_lat)`。

**对 Minecraft 模组的直接含义**：climlab 的 EBM 每步只需解一个 90×90 的三对角（实际是稠密 LU）线性系统，甚至可用 Thomas 算法 O(J) 手写，完全可移植到 Java。这正好反证"模组做不了全局 2D 椭圆解"——因为 climlab 也没做。

## 5. 时间复杂度或实测性能

- **未找到任何官方实测性能数字**（README、docs API 页、各模块 docstring 均无 benchmark/timing/GFLOPS 类内容）。
- 由源码可确定的复杂度：默认 EBM（use_banded_solver=False）每步 O(J³)，J=90 → 约 7.3×10⁵ flops；若 use_banded_solver=True（仅限 1D）则每步 O(J)；积分 1 年 = 90 步；annual_insolation 每次固定 10000 个采样点（solar/insolation.py L398）。
- README L48-49 仅功能性描述："2D latitude-pressure models with radiation, horizontally-varying meridional diffusion, and fixed relative humidity"。

## 6. 输出格式

- 内存对象 climlab.Field（numpy 子类 + domain 元数据：axes / heat_capacity / bounds）；model.state 为 AttrDict；model.diagnostics 为 dict
- climlab.to_xarray(model) / Field.to_xarray() 转 xarray.Dataset / DataArray
- 无自带文件输出格式；落盘由用户用 numpy/xarray 自行完成

## 7. 可直接借鉴什么

1. **EBM 常数集可直接抄**：A=210 W/m²、B=2 W/m²/°C、D=0.555 W/m²/°C、S0=1365.2 W/m²、s2=−0.48、a0=0.3、a2=0.078、ai=0.62、Tf=−10 °C、water_depth=10 m → C=4.1813e7 J/m²/K。
2. **反照率模型可直接抄**：暖态 α(φ) = a0 + a2·P2(sinφ)；Ts < Tf 处切换到 α = ai。纯逐格点判断，无迭代、无全局耦合。
3. **Budyko 型 OLR 就是直线** OLR = A + B·Ts。需要 CO2 敏感性时用 AplusBT_CO2 的三次多项式（系数见 3.2），输入只是 c = ln(CO2/300)。
4. **日照模型可完全离线硬编码**：orb_present 三个数（ecc=0.017236, long_peri=281.37, obliquity=23.446）让 DailyInsolation / AnnualMeanInsolation 不需要任何数据文件；annual_insolation 的 10000 点数值积分是纯函数，可在生成期跑一遍存成纬度表。
5. **隐式扩散 + 显式辐射的算子分裂写法值得抄**：父进程只做 tendency 求和 + 一步欧拉，实现极简，且允许 Δt≈4 天而不被显式扩散稳定性条件卡死。
6. **热传输诊断公式可直接抄**：heat_transport = −2π a² cosφ D ∂T/∂φ（PW），heat_transport_convergence 为其散度（meridional_heat_diffusion.py L30-31）。
7. **不要抄的点**：use_banded_solver=False 的稠密 LU。矩阵是三对角的，用 Thomas 算法即可，O(J) 且无需线性代数库。

## 8. 关键文件路径 + 行号 + URL

| 文件 | 行号 | 内容 | URL |
|---|---|---|---|
| climlab/model/ebm.py | 7 | EBM 主方程 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/model/ebm.py |
| 同上 | 227-244 | EBM.__init__ 全部默认值 | 同上 |
| 同上 | 273 | MeridionalHeatDiffusion(..., use_banded_solver=False) | 同上 |
| 同上 | 290-292 | net_radiation = ASR - OLR | 同上 |
| 同上 | 365-366 / 426-443 / 454-506 | EBM_seasonal 默认 / 无冰反馈反照率替换 / EBM_annual | 同上 |
| climlab/radiation/aplusbt.py | 13, 85, 148 | OLR=A+B·T / 默认 A,B / 实现 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/aplusbt.py |
| 同上 | 170-174, 187 | AplusBT_CO2 三次多项式；CO2 默认 300 ppm | 同上 |
| climlab/surface/albedo.py | 174 / 244-279 / 343, 351-352, 368 | P2Albedo 公式 / Iceline / StepFunctionAlbedo | https://raw.githubusercontent.com/climlab/climlab/master/climlab/surface/albedo.py |
| climlab/radiation/insolation.py | 219, 251, 282 / 439-440 / 556-591 | P2Insolation / AnnualMean / Daily | https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/insolation.py |
| climlab/solar/insolation.py | 264, 398, 411, 423-426, 451, 462-467, 477-478, 518-528 | 全部天文公式 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/solar/insolation.py |
| climlab/dynamics/adv_diff_numerics.py | 201, 269-336, 382-429 | 隐式三对角方程 / 矩阵构造 / 求解器 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/adv_diff_numerics.py |
| climlab/dynamics/advection_diffusion.py | 41-42, 235-259 | 仅一根轴 / 多轴报错 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/advection_diffusion.py |
| climlab/dynamics/meridional_advection_diffusion.py | 全文 | 球面加权 W=cosφ, X=φa | https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/meridional_advection_diffusion.py |
| climlab/dynamics/meridional_heat_diffusion.py | 66, 89, 96-99 | D=0.555 / K=D/C·a² / 热输送诊断 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/meridional_heat_diffusion.py |
| climlab/utils/heat_capacity.py | 44, 84 | 大气/海洋热容量 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/utils/heat_capacity.py |
| climlab/utils/constants.py | 15,33,38-39,53,57,70 | a, S0, rho_w/cw, days_per_year, seconds_per_year, orb_present | https://raw.githubusercontent.com/climlab/climlab/master/climlab/utils/constants.py |
| climlab/domain/axis.py | 155-193 | lat 轴 −90…90 均匀网格 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/domain/axis.py |
| climlab/domain/domain.py | 459-497, 499-551 | zonal_mean_surface / surface_2D | https://raw.githubusercontent.com/climlab/climlab/master/climlab/domain/domain.py |
| climlab/domain/initial.py | 84-162 | surface_state（Ts = T0 + T2·P2(sinφ)） | https://raw.githubusercontent.com/climlab/climlab/master/climlab/domain/initial.py |
| climlab/process/energy_budget.py | 58-68 | tendency = heating_rate / C | https://raw.githubusercontent.com/climlab/climlab/master/climlab/process/energy_budget.py |
| climlab/process/implicit.py | 20, 47-57 | implicit 标志与 adjustment | https://raw.githubusercontent.com/climlab/climlab/master/climlab/process/implicit.py |
| climlab/process/time_dependent_process.py | 463-498 | integrate_converge(crit=1e-4) | https://raw.githubusercontent.com/climlab/climlab/master/climlab/process/time_dependent_process.py |
| climlab/model/column.py | 46-57, 143-150, 153-179 | 三种柱模式默认值 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/model/column.py |
| climlab/radiation/rrtm/rrtmg_lw.py | 12, 16, 18 | RRTMG Fortran 扩展导入与初始化 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/rrtm/rrtmg_lw.py |
| climlab/radiation/rrtm/utils.py | 17-25 | RRTMG 所需气体 VMR 字段 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/rrtm/utils.py |
| climlab/solar/orbital/table.py | 10, 15-16 | pooch 远程下载 orbit91 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/solar/orbital/table.py |
| climlab/utils/__init__.py | 4 | _datapath_http 远端数据根 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/utils/__init__.py |
| climlab/surface/turbulent.py | 66-81, 126, 181 | 感热/潜热通量公式 | https://raw.githubusercontent.com/climlab/climlab/master/climlab/surface/turbulent.py |
| climlab/radiation/water_vapor.py | 8, 47 | FixedRelativeHumidity / ManabeWaterVapor | https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/water_vapor.py |
| environment.yml | 全文 | conda-forge 依赖清单 | https://raw.githubusercontent.com/climlab/climlab/master/environment.yml |
| README.rst | 99-111, 131-138, 340-343 | 依赖 / 版本 / 许可证 | https://raw.githubusercontent.com/climlab/climlab/master/README.rst |

**完整模型/组件类清单（按 __init__.py 导出，均本会话实读）**

- climlab/__init__.py：GreyRadiationModel, RadiativeConvectiveModel, BandRCModel, EBM, EBM_annual, EBM_seasonal, Field, global_mean, Axis, column_state, surface_state, Process, TimeDependentProcess, ImplicitProcess, DiagnosticProcess, EnergyBudget, process_like, get_axes, couple, to_xarray
- climlab/model/__init__.py：GreyRadiationModel, RadiativeConvectiveModel, BandRCModel, EBM, EBM_annual, EBM_seasonal
- climlab/radiation/__init__.py：AplusBT, AplusBT_CO2, SimpleAbsorbedShortwave, Boltzmann, GreyGas, GreyGasSW, FixedInsolation, P2Insolation, AnnualMeanInsolation, DailyInsolation, InstantInsolation, NbandRadiation, ThreeBandSW, FourBandLW, FourBandSW, ManabeWaterVapor, CAM3, CAM3_LW, CAM3_SW, RRTMG, RRTMG_LW, RRTMG_SW
- climlab/dynamics/__init__.py：BudykoTransport, AdvectionDiffusion, Diffusion, MeridionalAdvectionDiffusion, MeridionalDiffusion, MeridionalHeatDiffusion, MeridionalMoistDiffusion, LargeScaleCondensation
- climlab/surface/__init__.py：SensibleHeatFlux, LatentHeatFlux, ConstantAlbedo, P2Albedo, Iceline, StepFunctionAlbedo
- climlab/convection/__init__.py：ConvectiveAdjustment, EmanuelConvection, SimplifiedBettsMiller
- climlab/process/__init__.py：Process, process_like, get_axes, TimeDependentProcess, couple, ImplicitProcess, DiagnosticProcess, EnergyBudget, ExternalForcing, Limiter
- climlab/domain/__init__.py：single_column, zonal_mean_surface, surface_2D, zonal_mean_column, box_model_domain, column_state, surface_state, Field, global_mean, Axis（domain.py 内另有 _Domain, Atmosphere, Ocean, SlabOcean, SlabAtmosphere —— 这些是**域**而非模式）
- climlab/solar/：insolation.py（free functions）；orbital/（OrbitalTable）
- **没有** climlab/seaice/、climlab/atm/、climlab/ocean/、climlab/column/、climlab/ebm/（全部 404 实测）

**状态变量清单**

| 组件 | 状态变量 | 关键诊断/输入 |
|---|---|---|
| EBM / EBM_annual / EBM_seasonal | Ts，形状 (num_lat, 1)（1D）或 (num_lat, num_lon, 1)（surface_2D） | OLR, ASR, net_radiation, albedo, icelat, ice_area, heat_transport |
| GreyRadiationModel / RadiativeConvectiveModel / BandRCModel | Ts, Tatm（(num_lat, num_lev, 1)） | OLR, ASR, planetary_albedo, LW_*/SW_* 系列 |
| SensibleHeatFlux / LatentHeatFlux | Ts, Tatm | 输入 Cd=3E-3, resistance=1., U=5 m/s；诊断 SHF / LHF / evaporation |
| FixedRelativeHumidity / ManabeWaterVapor | Tatm | 诊断 q；参数 relative_humidity=0.77, qStrat=5.E-6 |
| Iceline / StepFunctionAlbedo | 无（DiagnosticProcess，读 Ts） | 诊断 icelat, ice_area, albedo |
| **海冰** | **不存在独立海冰模块**；冰仅为 EBM 反照率阶跃函数（Iceline + StepFunctionAlbedo），无冰厚/冰温/盐度状态量 | — |

**RRTMG / 灰体辐射文件与外部数据**

- 灰体：climlab/radiation/greygas.py（GreyGas, GreyGasSW），纯 Python。
- RRTMG：在**子包** climlab/radiation/rrtm/（__init__.py, rrtmg.py, rrtmg_lw.py, rrtmg_sw.py, utils.py）。注意 climlab/radiation/rrtm.py 与 rrtmg.py 均 404 —— 必须是目录形式。
- **RRTMG 需要编译好的 Fortran 扩展**：rrtmg_lw.py L12 `from climlab_rrtmg import rrtmg_lw as _rrtmg_lw`，L16 `_rrtmg_lw.climlab_rrtmg_lw_ini(const.cp)`，L18 失败时仅 `warnings.warn('Cannot import and initialize compiled Fortran extension, RRTMG_LW module will not be functional.')`。缺失时模块静默失效，不报错。
- **RRTMG 运行时不需要外部查找表文件**：climlab-rrtmg 仓库的 climlab_rrtmg/rrtmg_lw/meson.build 列出的 Fortran 源含 rrlw_kg01.f90 … rrlw_kg16.f90、rrlw_ref.f90、rrlw_tbl.f90、rrtmg_lw_init.f90 —— 吸收系数表以 Fortran 模块形式编译进扩展，初始化函数不接受任何文件路径参数。
- **climlab 中唯一需要联网的组件是轨道数据表**：climlab/solar/orbital/table.py L15-16 `pooch.retrieve(path, known_hash="3afc20dd...")`，`path = climlab.utils._datapath_http + 'orbital/orbit91'`（climlab/utils/__init__.py L4 `http://www.atmos.albany.edu/facstaff/brose/resources/climlab_data/`）。orbital/long.py 则从 http://vo.imcce.fr/... 下载 La2004 数据。**但 climlab.EBM / EBM_annual / EBM_seasonal 及其 DailyInsolation 默认使用硬编码的 const.orb_present，不会触发下载**（constants.py L71-74 注释明确说明该设计）。
- CAM3 辐射：climlab/radiation/cam3.py（CAM3, CAM3_LW, CAM3_SW），依赖外部包 climlab-cam3-radiation > 0.2（environment.yml），细节未读。

**SDM / 3D 动力核心：完全没有。** dynamics 包文档字符串原文：Other modules are 1D advection-diffusion solvers (implemented using implicit timestepping)。没有任何谱动力核心、没有原始方程动力核心、没有 SDM。
**最接近 GCM 的东西**：climlab.model.RadiativeConvectiveModel / BandRCModel（单柱或纬向平均柱，num_lat>1 时是 (纬度, 气压) 2D），配合 MeridionalHeatDiffusion + 交互水汽 + 对流调整。README L48-49 称之为 "2D latitude-pressure models with radiation, horizontally-varying meridional diffusion, and fixed relative humidity"。这是**辐射-对流-扩散模式**，不是 GCM：没有动量方程、没有风场、没有纬向环流。

## 9. 未确认项（climlab）

- 无任何实测性能/基准数字（README、docs、docstring 中均未找到）。
- climlab/radiation/cam3.py 内容未逐行阅读（只确认文件存在、导出类名、外部依赖包）；其是否需要臭氧/气溶胶数据文件未确认。
- 未确认 climlab 在 conda-forge 之外是否提供 Windows 二进制（README L72-73 只说明"早版本有部分 Windows 二进制，当前不支持"）。
- 未逐行阅读 climlab/process/process.py（33 951 字符）与 time_dependent_process.py 全部实现，只读了 integrate_converge 等关键方法。

---

# 模型二：MITgcm

## 1. 项目名 + 主链接 + 语言 + 许可证 + 维护状态

| 项 | 值 |
|---|---|
| 项目名 | MITgcm（MIT General Circulation Model） |
| 主链接 | https://mitgcm.org/ ・ 手册 https://mitgcm.readthedocs.io/ ・ 代码 https://github.com/MITgcm/MITgcm ・ 测试汇总 https://mitgcm.org/testing-summary |
| 语言 | Fortran（F77 风格源码 + Fortran 90 module；构建用 bash + make，tools/genmake2 生成 Makefile） |
| 许可证 | MIT（LICENSE.txt L1 "Copyright (c) 2018 MITgcm Developers and Contributors"，正文为标准 MIT 许可 "Permission is hereby granted, free of charge ... without restriction ... THE SOFTWARE IS PROVIDED 'AS IS'"） |
| 维护状态 | 活跃。readthedocs 当前构建版本 d861cd5；testing-summary 显示 2026-09-12 的每日回归（villon / linux_amd64_gfortran.dvlp：forward 130:130、adjoint-taf 34:34、tanglin-taf 24:24）；mitgcm.org 有 2026 年新闻 |
| 构建依赖 | Fortran 编译器（gfortran/ifort 等，见 tools/build_options/）、bash、make、makedepend；**MPI 可选**（getting_started 有独立 "Building with MPI" 小节）；**NetCDF 可选** —— 原文：NetCDF only requires a 'yes' if you want to write netCDF output; more specifically, a 'no' response to "Can we create NetCDF-enabled binaries" will disable including pkg/mnc and switch to output plain binary files |

## 2. 算的是什么（输出场）

- 通用水动力核心预报量：水平速度 v_h=(u,v)、垂直速度 ṙ、位温 θ、示踪量 S（大气=比湿 / 海洋=盐度）、位势 φ；密度由状态方程给出（overview 公式 1.1–1.6）。
- 大气配置额外有地面气压 p_s 预报方程：∂p_s/∂t + ∇_h·∫₀^{p_s} v_h dp = 0。
- 输出：pkg/diagnostics 提供的任意诊断场（如 AtPhOLR、AtPhSens、AtPhEvap、RELHUM、AtPhCAPE，见 pkg/atm_phys/atm_phys_driver.F 的 DIAGNOSTICS_FILL 调用），经 pkg/mdsio（二进制 .data/.meta）或 pkg/mnc（netCDF）落盘。

## 3. 方程与近似（实际公式 + 参数值）

### 3.1 大气能用同一套核心吗？能。

- overview 1.1 节原文：it can be used to study both atmospheric and oceanic phenomena; **one hydrodynamical kernel is used to drive forward both atmospheric and oceanic models** - see Figure 1.1
- overview 1.3 节原文：the vertical coordinate "r" is interpreted as pressure, p, if we are modeling the atmosphere ... and height, z, if we are modeling the ocean；并称这是 atmosphere/ocean 方程集之间的 "isomorphisms"。
- 因此：**不是独立动力核心**，而是同一个静力/非静力求解器 + 不同常量与垂直坐标解释。

### 3.2 垂直坐标（大气）

- overview/atmosphere.html 公式 (1.10)：r = p（气压）；(1.11)：ṙ = Dp/Dt = ω；(1.12)：φ = gz；(1.13)：b = (∂Π/∂p)·θ；(1.14)：θ = T(p_c/p)^κ；(1.15)：S = q（比湿）。
- Exner 函数 (1.16)：Π(p) = c_p (p/p_c)^κ，κ = R/c_p。
- 边界条件 (1.17)(1.18)：顶部 p_top = 0 处 ω = 0；底部 ω = Dp_s/Dt。
- 非静力能力存在（overview 1.3.4），大气另有 "Quasi-nonhydrostatic Atmosphere" 小节（1.3.4.3.2）。
- **不是 eta 坐标。** 教程使用**再缩放气压坐标 p\***（Adcroft & Campin 2004）：held_suarez 教程原文 "The set-up uses the rescaled pressure coordinate (p*) of Adcroft and Campin (2004) ... with 20 equally-spaced levels"，并说明 "without topography, the p* coordinate and the normalized pressure coordinate (σ_p) coincide exactly"。对应 namelist：nonlinFreeSurf=4, select_rStar=2。
- getting_started 参数表原文：For atmospheric simulations, buoyancyRelation needs to be set to ATMOSPHERIC, which also uses pressure as the vertical coordinate.

### 3.3 Held-Suarez 教程的真实 URL 与内容

- **URL：https://mitgcm.readthedocs.io/en/latest/examples/held_suarez_cs/held_suarez_cs.html**（手册 4.7 节）
- rst 源：https://mitgcm.readthedocs.io/en/latest/_sources/examples/held_suarez_cs/held_suarez_cs.rst.txt
- 实验目录：verification/tutorial_held_suarez_cs/（input/data 实测 200）
- 内容要点（全部来自该页）：
  - 用途："illustrates the use of the MITgcm as an atmospheric GCM, using simple Held and Suarez (1994) forcing to simulate atmospheric dynamics on global scale"
  - 水平网格："conformal cube-sphere grid (C32) ... Each of the 6 faces has the same resolution, with 32×32 grid points"；"The resolution at the equator or along the Greenwich meridian is similar to a 128×64 equally spaced longitude-latitude grid, but requires 25% less grid points"
  - 垂直："20 equally-spaced levels (20 × 50 mb, from p*=1000 mb to 0)"，Δp* = 50×10² Pa
  - 强迫公式 (4.48)-(4.51)：
    - F_v = −k_v(p)·v_h
    - F_θ = −k_θ(φ,p)·[θ − θ_eq(φ,p)]
    - k_v = k_f · max[0, (p*/P_s⁰ − σ_b)/(1 − σ_b)]，σ_b = 0.7，k_f = 1/86400 s⁻¹
    - θ_eq = max{ 200·(P_s⁰/p*)^κ, 315 − ΔT_y·sin²φ − Δθ_z·cos²φ·log(p*/P_s⁰) }
    - k_θ = k_a + (k_s − k_a)·cos⁴φ·max{0, (p*/P_s⁰ − σ_b)/(1 − σ_b)}
    - ΔT_y = 60 K，Δθ_z = 10 K，k_a = 1/(40·86400) s⁻¹，k_s = 1/(4·86400) s⁻¹，P_s⁰ = 10⁵ Pa
  - 稳定性数：S_inert = f²Δt² = 4×10⁻³（f = 1.45×10⁻⁴ s⁻¹）；S_adv = |u|Δt/Δx = 0.37（|u| = 90 m/s，Δx = 1.1×10⁵ m）；S_c = c_g Δt/Δx = 4×10⁻¹（c_g = 100 m/s）
  - 原文性能说明："At this resolution, the configuration can be integrated forward for many years on a single processor desktop computer."
- verification/tutorial_held_suarez_cs/input/data 实际 namelist 关键行：
  - buoyancyRelation='ATMOSPHERIC', eosType='IDEALG', rotationPeriod=86400.
  - implicitFreeSurface=.TRUE., exactConserv=.TRUE., nonlinFreeSurf=4, select_rStar=2
  - saltStepping=.FALSE., momViscosity=.FALSE., vectorInvariantMomentum=.TRUE., staggerTimeStep=.TRUE.
  - cg2dMaxIters=200, cg2dTargetResWunit=8.E-16
  - deltaT=450., abEps=0.1, readBinaryPrec=64, writeBinaryPrec=64
  - usingCurvilinearGrid=.TRUE., horizGridFile='grid_cs32', radius_fromHorizGrid=6370.E3, delR=20*50.E2
  - tRef = 295.2 … 573.8（20 层参考位温）

### 3.4 Aquaplanet

- **没有名为 "aquaplanet tutorial" 的独立教程页**；aquaplanet 以两个 verification 实验存在，均列在 4.15 节 "Additional Example Experiments: Forward Model Setups"（https://mitgcm.readthedocs.io/en/latest/examples/examples.html）：
  1. **atm_gray** —— 原文："gray atmospheric physics configuration using atm_phys package, on cube sphere grid (32x32 grid points per face) with 26 pressure levels. **This aquaplanet-like experiment has interactive SST with a prescribed, time-invariant Q-flux.** Also contains a secondary setup (input.ape) with prescribed idealized SST from Aqua-Planet Experiment (APE) project (Neale and Hoskins, 2001)."
     - verification/atm_gray/code/packages.conf（实读）：exch2, gfd, shap_filt, atm_phys, diagnostics
     - verification/atm_gray/code/SIZE.h（实读）：sNx=32, sNy=32, OLx=4, OLy=4, nSx=6, nSy=1, nPx=1, nPy=1, Nr=26 → C32 单进程
     - verification/atm_gray/input/data（实读）：buoyancyRelation='ATMOSPHERIC', atm_Cp=1004.64, atm_Rq=0.6078, gravity=9.80, integr_GeoPot=2, selectFindRoSurf=1, useAbsVorticity=.TRUE., selectVortScheme=3, selectKEscheme=3, addFrictionHeating=.TRUE., saltAdvScheme=77, deltaT=384., cg2dMaxIters=200, cg2dTargetResWunit=8.E-16, usingCurvilinearGrid=.TRUE., horizGridFile='dxC1_dXYa', delR = 1500., 2122., ... 434.（26 层非均匀），输入 hydrogThetaFile='ini_theta_26l.bin'、hydrogSaltFile='ini_specQ_26l.bin'
  2. **fizhi-cs-aqualev20** —— 原文："Global atmospheric simulation on an aqua planet with full atmospheric physics. Run is perpetual March with an analytical SST distribution. This is the configuration used for the Aqua-Planet Experiment Project (APE)."
     - verification/fizhi-cs-aqualev20/input/data（实读）：buoyancyRelation='ATMOSPHERIC', eosType='IDEALG', gravity=9.81, rigidLid=.FALSE., implicitFreeSurface=.TRUE., nonlinFreeSurf=4, select_rStar=2, useAbsVorticity=.TRUE., SadournyCoriolis=.TRUE., selectKEscheme=3, deltaT=120.0, cg2dMaxIters=200, cg2dTargetResWunit=8.E-16, delR=20*5054., 输入 U.input.20Lev / V.input.20Lev / T.input.20Lev / RH.input.20Lev

### 3.5 大气物理包

| 包 | 是否存在 | 证据 | 手册页面 |
|---|---|---|---|
| pkg/atm_phys | **存在** | pkg/atm_phys/ATM_PHYS_OPTIONS.h（200，含 #ifdef ALLOW_ATM_PHYS）、pkg/atm_phys/atm_phys_driver.F（200，20 740 字符，536 行） | **未找到独立手册章节**（phys_pkgs.rst.txt 检索 "atm_phys" 无命中）；只能从 examples.rst.txt 的 atm_gray 条目与源码了解 |
| pkg/aim_v23 | **存在** | pkg/aim_v23/AIM_OPTIONS.h（200） | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/aim.html （8.5.1 Atmospheric Intermediate Physics: AIM） |
| pkg/land | **存在** | pkg/land/LAND_OPTIONS.h（200） | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/land.html （8.5.2） |
| pkg/fizhi | **存在** | 手册 8.5.3 "Fizhi: High-end Atmospheric Physics" | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/fizhi.html |
| pkg/gfdl_cloud_microphys | **未找到（不存在）** | 三个候选路径全部 404：pkg/gfdl_cloud_microphys/GFDL_CLOUD_MICROPHYS_OPTIONS.h、.../gfdl_cloud_microphys.F90、.../GFDL_CLOUD_MICROPHYS.h；phys_pkgs.rst.txt 检索 "microphys" 零命中 | — |

- pkg/atm_phys/atm_phys_driver.F 的 use 语句（L14-25）显示它调用 GFDL 风格的 FMS 模块：radiation_mod、lscale_cond_mod、dargan_bettsmiller_mod、surface_flux_mod、vert_turb_driver_mod、vert_diff_mod、mixed_layer_mod、constants_mod。驱动内依次调用 LSCALE_COND（大尺度凝结）、RADIATION_DOWN / RADIATION_UP、SURFACE_FLUX、VERT_TURB_DRIVER、GCM_VERT_DIFF_DOWN。
- 诊断短名（DIAGNOSTICS_FILL 调用）：AtPhCnvP, AtPhCAPE, AtPhCnIn, AtPhKlzb, AtPhConv, AtPhRlxT, AtPhRlxQ, AtPh_Trf, AtPh_Qrf, AtPhdTcv, AtPhLscP, RELHUM, AtPhDLR, AtPhInSR, AtPhNTSR, AtPhOLR, AtPhDSSR, AtPhNSSR, AtPhDSLR, AtPhUSLR, AtPhSens, AtPhEvap, AtPhTauX, AtPhTauY, AtPhdTlc, AtPhdtTg, AtPhdtQg, AtPhDisH。
- 手册第 8 章目录页（https://mitgcm.readthedocs.io/en/latest/phys_pkgs/phys_pkgs.html）只列 3 个：AIM、Land、Fizhi。**atm_phys 未列入手册第 8 章。**

### 3.6 耦合

- 大气侧包：pkg/atm_compon_interf/，CPP 开关 ALLOW_ATM_COMPON_INTERF（ATM_CPL_OPTIONS.h），核心例程 atm_store_my_data.F → SUBROUTINE ATM_STORE_MY_DATA(myTime, myIter, myThid)，文件头注释："Routine for controlling storage of some coupling data (e.g. fluxes) to coupler layer ... This version interfaces to the MITgcm AIMPHYS package."，内部调用 ATM_STORE_SURFFLUX、ATM_STORE_AIM_WNDSTR、ATM_STORE_AIM_FIELDS、ATM_STORE_LAND。
- 海洋侧包：pkg/ocn_compon_interf/，CPP 开关 ALLOW_OCN_COMPON_INTERF（OCN_CPL_OPTIONS.h），ocn_store_my_data.F 文件头注释："This version talks to the MIT Coupler. It uses the MIT Coupler **'checkpoint1'** library calls."，导出量：SSTocn2cpl = theta(i,j,1,bi,bj)、SSSocn2cpl = salt(i,j,1,bi,bj)、ocMxlD2cpl（混合层深度，由 hFacC*drF(1) 或 r* 情形下 h0FacC*rStarFacC*drF(1) 得到）、vSqocn2cpl（格点四角速度平方均值 × 0.5）。
- 大气驱动中的耦合调用：atm_phys_driver.F 末尾 #ifdef COMPONENT_MODULE ... IF ( useCoupler ) THEN ... CALL ATM_STORE_MY_DATA( myTime, myIter, myThid ) ENDIF #endif。
- CPP 开关语义（getting_started 表格原文）：COMPONENT_MODULE — control use of communication with other components, i.e., sets component to work with a coupler interface。
- 耦合器本体："This coupler can be a specially configured build of MITgcm itself; see, for example, verification experiment cpl_aim+ocn"（原文，链接 https://github.com/MITgcm/MITgcm/tree/master/verification/cpl_aim+ocn）。构建耦合器需 genmake_local；verification/cpl_aim+ocn/build_cpl/genmake_local 实测 200。getting_started 原文亦点名："this genmake_local file is required for a special setup, building a 'MITgcm coupler' executable"。
- 耦合实验 cpl_aim+ocn（examples.rst.txt 原文）："Coupled ocean-atmosphere realistic configuration on cubed-sphere cs32 horizontal grid, using intermediate atmospheric physics (pkg/aim_v23) thermodynamic seaice (pkg/thsice) and land packages. Also contains an additional setup with seaice dynamics (input_cpl.icedyn, input_atm.icedyn, input_ocn.icedyn)."

### 3.7 水平网格类型

- algorithm/horiz-grid.html（2.11.4）：网格是 orthogonal curvilinear，每个格点由边长与面积描述；三种初始化方式：
  1. **Cartesian**：usingCartesianGrid=.TRUE.，dXspacing/dYspacing 或 DELX/DELY，单位米
  2. **Spherical-polar（经纬度）**：usingSphericalPolarGrid=.TRUE.，dXspacing/dYspacing，单位度
  3. **Curvilinear**：usingCurvilinearGrid=.TRUE.，网格间距不能由 namelist 指定，必须**从数据文件读取**每个描述符
- **立方球（cube sphere）= curvilinear + pkg/exch2**。phys_pkgs/exch2.html 原文："The exch2 package extends the original cubed sphere topology configuration to allow more flexible domain decomposition and parallelization. Cube faces (also called subdomains) may be divided into any number of tiles..."；"The default files provided in the release configure a cubed sphere topology of **six tiles, one per subdomain, each with 32×32 grid points, with all tiles running on a single processor**"；拓扑文件 W2_EXCH2_TOPOLOGY.h 与 w2_e2setup.F；"Files containing grid parameters, named tile00$n$.mitgrid where n=(1:6) (one per subdomain), must be in the working directory when the MITgcm executable is run."
- 经纬度网格实例：hs94.128x64x5（3-D atmosphere dynamics on lat-lon grid）、aim.5l_LatLon（latitude-longitude grid with 128x64x5 grid points (2.8° resolution)）、global_ocean.90x40x15（4°×4°）。

### 3.8 教程分辨率汇总（取自 examples.rst.txt / 各 input/data / SIZE.h）

| 实验 | 网格 | 层数 | Δt |
|---|---|---|---|
| tutorial_held_suarez_cs | C32 cube-sphere（6×32×32，≈128×64 等效） | 20（×50 hPa） | 450 s |
| atm_gray（aquaplanet-like） | C32 cube-sphere（SIZE.h: nSx=6, sNx=sNy=32, nPx=nPy=1） | 26（非均匀 delR） | 384 s |
| fizhi-cs-aqualev20（aquaplanet, APE） | cube-sphere "cs" | 20（×5054 Pa） | 120 s |
| aim.5l_cs | C32 cube-sphere | 5 | 未读 |
| aim.5l_LatLon | 128×64 经纬度 | 5 | 未读 |
| hs94.128x64x5 | 128×64 经纬度 | 5 | 未读 |
| aim.5l_Equatorial_Channel | 3D 赤道通道 | 5 | 未读 |
| cpl_aim+ocn | cs32 cube-sphere | 未读 | 未读 |

## 4. 是否需要全局迭代

**需要，而且每个时间步都需要。** 证据：

- Held-Suarez 教程：地面气压用隐式自由面形式求解 —— "The implicit free surface form of the pressure equation described in Marshall et al. (1997) is employed to solve for p_s"。
- namelist 中 cg2dMaxIters=200、cg2dTargetResWunit=8.E-16 就是 **2D 预条件共轭梯度椭圆求解器 CG2D** 的最大迭代次数与收敛容差（held_suarez 页原文："Sets maximum number of iterations the 2-D conjugate gradient solver will use, irrespective of convergence criteria being met"；"Sets the tolerance (in units of ω) which the 2-D conjugate gradient solver will use to test for convergence"）。
- atm_gray 与 fizhi-cs-aqualev20 的 input/data 同样是 cg2dMaxIters=200 + cg2dTargetResWunit=8.E-16。
- 求解器源码：model/src/cg2d.F（getting_started 引用 global_sum_singlecpu.F 用于 "the key part of MITgcm algorithm CG2D that relies on global sum"）。
- 即**每步都要解一次全球 2D 椭圆问题**，并行下需要全局归约（MPI_Allreduce），这正是 GLOBAL_SUM_ORDER_TILES 性能讨论的背景。

## 5. 时间复杂度或实测性能

- **手册的性能章节是空的。** _sources/software_arch/software_arch.rst.txt 中 6.4.2 / 6.4.3 两节正文原文只有一行：**"TO BE DONE (CNH)"**。
- **未找到 mitgcm.org 上的专门 benchmark 页面**：https://mitgcm.org/benchmarks 与 https://mitgcm.org/public/benchmarks/ 均 404；首页只有 ABOUT / CODE / DOC / CONTRIBUTE / NEWS / PUBS / VIDEO / **TESTING** / CONTACT，无 benchmark 入口。
- https://mitgcm.org/testing-summary 存在，但内容是**回归测试通过率**（villon / linux_amd64_gfortran.dvlp：forward 130:130、adjoint-taf 34:34、tanglin-taf 24:24，日期 20260912），**不是性能数据**。
- **手册中确实存在的实测吞吐数字**（getting_started 脚注，llc_540 案例，Pleiades 机器，模拟 20 天）：
  - 767 MPI ranks：fall-through 方案（#undef GLOBAL_SUM_ORDER_TILES）**799.0 模拟日/日历日（dd/d）**；默认方案 **781.0 dd/d**
  - 2819 MPI ranks：fall-through **1300 dd/d**；默认 **800.0 dd/d**（"this case did not scale at all from 767p to 2819p unless the fall-though approach was utilized"）
  - MPI_Allreduce 内存流量：22.456 T → 32.596 G；303.70 T → 121.08 G
- 定性表述：software_arch 6.2.1："numerical code, operating within the WRAPPER, performs and scales very competitively with equivalent numerical code that has been modified to contain native optimizations for a particular system (see Hoe et al. 1999)"；目标算力区间 "10⁹ floating point operations to more than 10¹⁷ floating point operations"（Figure 6.1）。
- held_suarez 教程的性能表述仅为："At this resolution, the configuration can be integrated forward for many years on a single processor desktop computer."

## 6. 输出格式

- **默认二进制 MDSIO**：pkg/mdsio / pkg/rw，每个场一对 <name>.data + <name>.meta（getting_started 3.6.3 "Output files"）。readBinaryPrec=64 / writeBinaryPrec=64 控制精度。
- **netCDF（可选）**：pkg/mnc，需编译时检测到 NetCDF；检测失败则自动退回二进制（原文见第 1 节）。
- **文本**：每个进程的 STDOUT.0000 等标准输出，pkg/monitor 打印的监控行；eedata 配置。
- **诊断框架**：pkg/diagnostics（data.diagnostics + DIAGNOSTICS_SIZE.h），按短名输出任意物理量；useSingleCpuIO=.TRUE. 可让主进程统一写盘。
- 后处理：MITgcmutils Python 包（mds, mnc, diagnostics, cs, llc 等，手册第 11 章 https://mitgcm.readthedocs.io/en/latest/utilities/utilities.html）。

## 7. 可直接借鉴什么

1. **Held-Suarez 强迫是完备、参数化、可抄的"干大气"**：牛顿冷却 + Rayleigh 摩擦，全部常数见 3.3（k_f=1/86400、k_a=1/(40·86400)、k_s=1/(4·86400)、σ_b=0.7、ΔT_y=60 K、Δθ_z=10 K、P_s⁰=10⁵ Pa）。**对 Minecraft 模组极有借鉴价值**：它是纯局地逐格点计算（θ_eq 与 k_θ 只依赖纬度和气压），**不需要任何全局求解**。
2. **气压坐标 + Exner 函数的大气方程组**（overview 1.3.2、1.4.1）是标准教科书形式，可直接作为模组"垂直结构"的定义。
3. **大气物理的组件划分**（atm_phys 的 RADIATION_DOWN/UP、SURFACE_FLUX、VERT_TURB_DRIVER、GCM_VERT_DIFF_DOWN、LSCALE_COND、DARGAN_BETTSMILLER）是成熟的"单柱物理"接口设计，模组若做一维柱物理可照此顺序组织。
4. **立方球网格拓扑数据外置的做法**（tile00n.mitgrid × 6 + W2_EXCH2_TOPOLOGY.h）说明"网格几何从文件读"可维护 —— 但对模组过于笨重，不如直接解析公式。
5. **不要借鉴**：全局 CG2D 椭圆求解、WRAPPER 并行框架、genmake2 构建体系。

## 8. 关键文件路径 + 行号 + URL

| 路径/页面 | 内容 | URL |
|---|---|---|
| LICENSE.txt | MIT 许可证全文 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/LICENSE.txt |
| overview/overview.html | "one hydrodynamical kernel"、1.1 节、方程 1.1–1.6 | https://mitgcm.readthedocs.io/en/latest/overview/overview.html |
| overview/atmosphere.html | 公式 (1.10)–(1.18)，r=p | https://mitgcm.readthedocs.io/en/latest/overview/atmosphere.html |
| overview/hydro_prim_eqn.html | 1.4.1 气压坐标静力原始方程 | https://mitgcm.readthedocs.io/en/latest/overview/hydro_prim_eqn.html |
| examples/held_suarez_cs/held_suarez_cs.html | Held-Suarez 教程全文 | https://mitgcm.readthedocs.io/en/latest/examples/held_suarez_cs/held_suarez_cs.html |
| _sources/.../held_suarez_cs.rst.txt | 同上 rst 源 | https://mitgcm.readthedocs.io/en/latest/_sources/examples/held_suarez_cs/held_suarez_cs.rst.txt |
| examples/examples.html | 4.15 全部附加实验清单 | https://mitgcm.readthedocs.io/en/latest/examples/examples.html |
| _sources/examples/examples.rst.txt | 同上 rst 源（含 filelink 目标路径） | https://mitgcm.readthedocs.io/en/latest/_sources/examples/examples.rst.txt |
| algorithm/horiz-grid.html | 2.11.4 三种水平网格 | https://mitgcm.readthedocs.io/en/latest/algorithm/horiz-grid.html |
| phys_pkgs/phys_pkgs.html | 8.5 Atmosphere Packages（仅 AIM / Land / Fizhi） | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/phys_pkgs.html |
| phys_pkgs/aim.html / land.html / fizhi.html | AIM 8.5.1 / Land 8.5.2 / Fizhi 8.5.3 | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/aim.html 等 |
| phys_pkgs/exch2.html | 立方球 / 6 tiles × 32×32 / tile00n.mitgrid | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/exch2.html |
| _sources/getting_started/getting_started.rst.txt | 构建流程、NetCDF 可选、COMPONENT_MODULE 表、llc_540 性能脚注 | https://mitgcm.readthedocs.io/en/latest/_sources/getting_started/getting_started.rst.txt |
| _sources/software_arch/software_arch.rst.txt | 6.4.2/6.4.3 正文 = "TO BE DONE (CNH)" | https://mitgcm.readthedocs.io/en/latest/_sources/software_arch/software_arch.rst.txt |
| pkg/atm_phys/ATM_PHYS_OPTIONS.h | ALLOW_ATM_PHYS | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/atm_phys/ATM_PHYS_OPTIONS.h |
| pkg/atm_phys/atm_phys_driver.F | L14-25 use 模块；LSCALE_COND / RADIATION_DOWN/UP / SURFACE_FLUX / VERT_TURB_DRIVER / GCM_VERT_DIFF_DOWN；末尾 COMPONENT_MODULE + ATM_STORE_MY_DATA | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/atm_phys/atm_phys_driver.F |
| pkg/atm_compon_interf/ATM_CPL_OPTIONS.h | ALLOW_ATM_COMPON_INTERF | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/atm_compon_interf/ATM_CPL_OPTIONS.h |
| pkg/atm_compon_interf/atm_store_my_data.F | ATM_STORE_MY_DATA 子程序全文 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/atm_compon_interf/atm_store_my_data.F |
| pkg/ocn_compon_interf/OCN_CPL_OPTIONS.h | ALLOW_OCN_COMPON_INTERF | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/ocn_compon_interf/OCN_CPL_OPTIONS.h |
| pkg/ocn_compon_interf/ocn_store_my_data.F | MIT Coupler "checkpoint1" 调用；SST/SSS/混合层深度/速度平方导出 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/ocn_compon_interf/ocn_store_my_data.F |
| pkg/aim_v23/AIM_OPTIONS.h | AIM 包存在 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/aim_v23/AIM_OPTIONS.h |
| pkg/land/LAND_OPTIONS.h | Land 包存在 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/land/LAND_OPTIONS.h |
| verification/tutorial_held_suarez_cs/input/data | Held-Suarez namelist 全文（1406 B） | https://raw.githubusercontent.com/MITgcm/MITgcm/master/verification/tutorial_held_suarez_cs/input/data |
| verification/atm_gray/input/data | atm_gray namelist（2224 B） | https://raw.githubusercontent.com/MITgcm/MITgcm/master/verification/atm_gray/input/data |
| verification/atm_gray/code/SIZE.h | sNx=32 sNy=32 nSx=6 nSy=1 nPx=1 nPy=1 Nr=26 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/verification/atm_gray/code/SIZE.h |
| verification/atm_gray/code/packages.conf | exch2 gfd shap_filt atm_phys diagnostics | https://raw.githubusercontent.com/MITgcm/MITgcm/master/verification/atm_gray/code/packages.conf |
| verification/fizhi-cs-aqualev20/input/data | aquaplanet / APE 配置 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/verification/fizhi-cs-aqualev20/input/data |
| verification/cpl_aim+ocn/build_cpl/genmake_local | 耦合器构建脚本 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/verification/cpl_aim+ocn/build_cpl/genmake_local |
| mitgcm.org/testing-summary | 每日回归测试汇总（非性能） | https://mitgcm.org/testing-summary |

## 9. 未确认项（MITgcm）

- **未找到专门的 MITgcm benchmark 页面**：mitgcm.org/benchmarks、mitgcm.org/public/benchmarks/ 均 404；手册性能章节为 "TO BE DONE (CNH)"。
- **pkg/gfdl_cloud_microphys 未找到**：三个候选路径 404，手册 phys_pkgs.rst 无 "microphys"。所以"MITgcm 支持 GFDL 云微物理包"这一命题在本会话内**无法证实**；atm_phys 内确有 LSCALE_COND 与 DARGAN_BETTSMILLER，但不能据此断言它就是 GFDL 云微物理。
- **pkg/atm_phys 没有手册章节**：只能从源码与 examples.rst.txt 了解。pkg/atm_phys/ 目录完整文件清单未取得（GitHub API 被限流：api.github.com 返回 403 "API rate limit exceeded for 155.254.122.236"；GitHub HTML tree 页面被截断在 100 000 字符）。因此只逐文件确认了 ATM_PHYS_OPTIONS.h 与 atm_phys_driver.F。
- verification/cpl_aim+ocn/ 完整文件清单与 README 未取得（README 实测 404，可能文件名不同）。
- fizhi-cs-aqualev20、aim.5l_cs、cpl_aim+ocn 的 SIZE.h / 层数 / Δt 未逐一读取。
- 未确认 pkg/exch2 的 32×32 立方球网格生成工具（utils/exch2/matlab-topology-generator）的具体输出格式。
- 未阅读 MITgcm 的 model/src/cg2d.F 源码本体（只从文档和 namelist 确认其为 2D 共轭梯度椭圆求解器）。

---

# 未找到 / 未确认（总表）

| 项目 | 状态 |
|---|---|
| climlab 的实测性能数字 | **未找到**（README / docs / docstring 均无） |
| climlab 的 climlab/ebm/、climlab/column/、climlab/seaice/、climlab/atm/、climlab/ocean/ 五个包 | **未找到（不存在）**，实际为 climlab/model/、climlab/domain/ 等 |
| climlab 独立海冰模块 | **未找到**；冰仅以 EBM 反照率阶跃函数（Iceline）形式存在 |
| climlab SDM / 3D 动力核心 | **未找到（不存在）**；dynamics 仅有 1D advection-diffusion |
| climlab RRTMG 运行时外部数据文件 | **不需要**（吸收系数以 Fortran module rrlw_kg01..16 编译进 climlab_rrtmg），但**需要编译好的 Fortran 扩展**，缺失时仅 warning |
| climlab 轨道数据表 | **需要联网**（pooch 下载 orbit91 / La2004），但 climlab.EBM 族默认用硬编码 orb_present，不触发下载 |
| climlab cam3.py 是否需要臭氧/气溶胶数据文件 | 未确认（文件未逐行阅读） |
| MITgcm 专门 benchmark 页面 | **未找到**（mitgcm.org/benchmarks 404；手册 6.4.2/6.4.3 为 "TO BE DONE (CNH)"） |
| MITgcm pkg/gfdl_cloud_microphys | **未找到（不存在）**（3 个候选路径 404；手册无 "microphys" 字样） |
| MITgcm pkg/atm_phys 手册章节 | **未找到**（手册第 8 章只列 AIM / Land / Fizhi） |
| MITgcm pkg/atm_phys 完整文件清单 | 未确认（GitHub API 403 限流、HTML tree 被截断） |
| MITgcm verification/cpl_aim+ocn/README | 未找到（404） |
| MITgcm fizhi-cs-aqualev20 / aim.5l_cs / cpl_aim+ocn 的 SIZE.h 与 Δt | 未确认（未逐一读取） |
| MITgcm 的 eta 垂直坐标 | **未找到**；大气使用 p（r=p）与再缩放 p*，文档提到无地形时 p* 与 σ_p 重合 |

---

# 结论：MITgcm 是否适合做 Minecraft 模组的离线表格生成？—— **NO**

理由（全部基于本会话已核实的事实）：

1. **它是编译型 Fortran HPC 程序，不是库。** 必须经 tools/genmake2 -mods ../code -optfile ... → make depend → make 生成 mitgcmuv 可执行文件（getting_started.rst.txt）。没有 Python API，没有可嵌入的库接口，无法移植到 Java。
2. **每个时间步都要做一次全球 2D 椭圆求解。** cg2dMaxIters=200 / cg2dTargetResWunit=8.E-16 出现在 held_suarez、atm_gray、fizhi-cs-aqualev20 的全部 namelist 中，对应隐式自由面气压方程。并行下还需全局归约（MPI_Allreduce），这正是 MITgcm 自己承认的伸缩性瓶颈。**这恰恰是题面所说"Minecraft 模组做不到的全局 2D 椭圆解"。**
3. **算力规模不在一个量级。** Held-Suarez C32 是 6×32×32 = 6144 个水平点 × 20 层 = 122 880 个网格单元，Δt=450 s → 1 年 ≈ 70 126 步，每步一次全球 CG2D 迭代（≤200 次）。生成 10 年气候态即 7×10⁵ 步 × 每步全球椭圆解。手册只说"单处理器台式机上可推进很多年"，但没有任何可引用的吞吐数字（性能章节是空的），而唯一有数字的案例（llc_540，767–2819 MPI ranks 达 799–1300 模拟日/日历日）是在 Pleiades 集群上跑的。
4. **输入输出链路沉重。** 需要把 namelist（data, eedata, data.pkg, data.shap, data.diagnostics）、二进制网格文件（grid_cs32 或 tile00n.mitgrid × 6）、初始条件二进制（ini_theta_26l.bin, ini_specQ_26l.bin, U.input.20Lev 等）、SIZE.h、packages.conf、CPP_OPTIONS.h 全部准备齐全；输出是 MDSIO 二进制 + 可能需 netCDF 才能方便读。对"模组内置/定期重算"的场景而言链路太长。
5. **对比之下 climlab 的 EBM 只需一个 90×90 三对角（实为稠密）线性解，每步 O(J)，90 步/年，Δt≈4 天，纯 Python 常数硬编码，无网络、无编译、无查找表。** 这才是可移植进模组的形态。
6. **MITgcm 唯一合理的用法**：作为一次性的**离线"真值"参考**（例如在集群上跑 atm_gray 或 tutorial_held_suarez_cs 得到纬向平均温度/OLR/热输送），用于校准模组自研的降阶模型；而**不是**作为模组的表格生成器。若确实要走这条路，务必使用无需任何 physics 包的 tutorial_held_suarez_cs（只用 exch2 + shap_filt + diagnostics，牛顿冷却 + Rayleigh 摩擦全部是局地计算），它把物理复杂度降到最低。

**可直接落地的替代方案（由本次调研支撑）**：以 climlab 的 EBM 常数集（A=210, B=2, D=0.555, S0=1365.2, s2=−0.48, a0=0.3, a2=0.078, ai=0.62, Tf=−10 °C, water_depth=10 m, C=4.1813e7 J/m²/K, K≈5.39e5 m²/s）为基础，用 Thomas 算法解 90 点纬度三对角隐式扩散 + 显式辐射/反照率算子分裂，Δt≈4 天，在 Java 里逐时间步推进到平衡；日照用硬编码的 orb_present（ecc=0.017236, long_peri=281.37, obliquity=23.446）配合 Berger 1978 的赤纬/日没时角公式，或直接预计算 90 点年平均值表。全程无全局椭圆求解、无外部数据文件、无网络。
