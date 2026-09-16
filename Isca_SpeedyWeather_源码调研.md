# 源级调研：Isca 与 SPEEDY / SpeedyWeather.jl

> 所有结论均来自本会话实际抓取并阅读的 URL。凡未能验证者一律写「未找到」。

---

# 模型 1：Isca

## 1. 项目名+主链接+语言+许可证+维护状态
- **项目名**：Isca（Idealized GCM，University of Exeter / ExeClim）
- **主链接**：https://github.com/ExeClim/Isca ；文档 https://execlim.github.io/Isca/ （本会话抓取返回 200，标题 "Overview — isca dev documentation"）
- **语言**：Fortran（F90 主体，源自 GFDL FMS）+ Python（顶层驱动包 isca）。ReadMe 徽章标注 "Python 3.7+"，「The underlying model is written in Fortran and may largely be configured with Python scripts」（https://raw.githubusercontent.com/ExeClim/Isca/master/ReadMe.md ）
- **许可证**：**GNU GPL v3**。证据：https://raw.githubusercontent.com/ExeClim/Isca/master/LICENSE （首行 "GNU GENERAL PUBLIC LICENSE / Version 3, 29 June 2007"，文件 35141 字符）；ReadMe 徽章 "License: GPL v3"。注意 LICENSE.txt 不存在（404）。
- **维护状态**：master 分支活跃。文件清单接口 https://ungh.cc/repos/ExeClim/Isca/files/master 返回 meta sha = d1321ac372698bcbe054da1cc5b4eef9f998a096，含 .github/workflows/tests.yml、docs/、exp/test_cases/（20+ 用例）。仓库体积超 jsDelivr 50 MB 限制（https://data.jsdelivr.com/v1/packages/gh/ExeClim/Isca@master?structure=flat 返回 403 "Package size exceeded the configured limit of 50 MB"）。

## 2. 算的是什么（输出场）
三维全球大气 GCM（谱动力核心 + 理想化物理）。默认诊断输出为经纬网格上的：纬向风 u、经向风 v、温度 T、地面气压 ps、比湿 sphum、相对涡度 vor、散度 div，以及垂直坐标系数 bk/pk、降水 precipitation、地表温度 t_surf（见第 6 节 diag_table）。
- 垂直坐标由 pk/bk 定义（get_pk_bk，spectral_dynamics.F90:1433）。
- 干/湿模型判定：存在 sphum 或 mix_rat tracer 即湿模型（spectral_dynamics.F90:375-393）。

## 3. 方程与近似（实际公式 + 参数值）

### 3.1 动力核心与分辨率默认值
文件：src/atmos_spectral/model/spectral_dynamics.F90
URL：https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_spectral/model/spectral_dynamics.F90

namelist 默认值（行 153-205，namelist /spectral_dynamics_nml/ 在行 210-227）：

    triang_trunc           = .true.      ! 行158 三角截断
    use_implicit           = .true.      ! 行157
    damping_order          = 2           ! 行164
    cutoff_wn              = 15          ! 行167  ! T42
    lon_max                = 128         ! 行168  ! T42
    lat_max                = 64          ! 行169  ! T42
    num_fourier            = 42          ! 行170  ! T42
    num_spherical          = 43          ! 行171  ! T42
    num_levels             = 18          ! 行173
    vert_coord_option      = 'even_sigma'           ! 行178
    damping_option         = 'resolution_dependent' ! 行179
    vert_advect_uv         = 'second_centered'      ! 行180 (= default_advect_vert, 行147)
    vert_difference_option = 'simmons_and_burridge' ! 行182
    initial_state_option   = 'quiescent'            ! 行183
    damping_coeff          = 1.15740741e-4 ! 行185 (one tenth day)**-1
    robert_coeff           = .04          ! 行191
    alpha_implicit         = .5           ! 行192
    scale_heights          = 4.           ! 行194
    surf_res               = .1           ! 行195
    p_press = .1 ; p_sigma = .3 ; exponent = 2.5    ! 行196-198
    reference_sea_level_press = 101325.   ! 行201
    raw_filter_coeff       = 1.0          ! 行203
    valid_range_t          = (/100.,500./) ! 行208

- **默认截断 = T42（num_fourier=42, num_spherical=43, lon_max=128, lat_max=64），默认层数 num_levels=18**（Fortran 层默认）。三角截断由 triang_trunc=.true. 打开，总波数 num_total_wavenumbers = num_spherical - 1（行 433-434）。
- 另有 Python 层的分辨率表 Experiment.RESOLUTIONS（src/extra/python/isca/experiment.py 行 29-56）：T170 → 512/256/170/171；T85 → 256/128/85/86；**T42 → 128/64/42/43**；T21 → 64/32/21/22。set_resolution(res, num_levels) 在行 124-133。
- 仓库自带的默认运行配置 exp/run_isca/input/input.nml（https://raw.githubusercontent.com/ExeClim/Isca/master/exp/run_isca/input/input.nml ）未设置 num_fourier/num_spherical/lon_max/lat_max，故默认即为 T42；但其中覆盖 num_levels = 40、dt_atmos = 600、mixed_layer_nml depth = 100、albedo_value = 0.205、do_qflux = .true.、qflux_amp = 30.0、two_stream_gray_rad_nml do_seasonal = .true.、trayfric = -0.5、sponge_pbottom = 50.0。
- **有限体积（finite-volume）选项**：垂直平流可切到有限体积（vert_advect_uv/t = 'FINITE_VOLUME_PARABOLIC'，行 289-291；vert_advection(..., scheme=uv_vert_advect_scheme, form=ADVECTIVE_FORM)，行 883）；tracer 有 numerical_representation = 'grid' 走 fv_advection_mod: a_grid_horiz_advection（行 71、357-364）。**水平方向始终是谱方法（spectral transform），没有有限体积动力核心。**

### 3.2 预报方程组（prognostic state）
文件同上。状态数组声明（行 117-135）与分配（allocate_fields，行 639-667）：

| 变量 | 维数 | 行号 |
|---|---|---|
| vors（相对涡度，谱） | complex(ms:me, ns:ne, num_levels, 2) | 128 / alloc 647 |
| divs（散度，谱） | 同上 | 128 / 648 |
| ts（温度，谱） | 同上 | 128 / 649 |
| ln_ps（地面气压对数，谱） | complex(ms:me, ns:ne, 2) | 129 / 646 |
| spec_tracers | complex(ms:me,ns:ne,num_levels,2,num_tracers) | 130 / 659 |
| psg, ug, vg, tg（网格镜像） | real(is:ie,js:je,...) | 132-133 / 641-644 |
| grid_tracers | real(is:ie,js:je,num_levels,2,num_tracers) | 134 / 658 |
| surf_geopotential | real(is:ie,js:je) | 135 / 656 |
| 时间层数 | num_time_levels = 2 | 117 |

**注意：Isca 的原始谱预报量是涡度/散度/温度/ln(ps)（不是 u,v），u,v 由 uv_grid_from_vor_div（行 938）与 vor_div_from_uv_grid（行 903）每步转换。** 另有 virtual_factor；若 use_virtual_temperature 则 virtual_t = tg*(1 + virtual_factor*q)（行 860-864）。

主积分循环 subroutine spectral_dynamics（行 783-1037）：
1. pressure_variables(p_half, ln_p_half, p_full, ln_p_full, psg)（行 856，press_and_geopot.F90）
2. compute_pressure_gradient（行 858）
3. four_in_one(divg, ug, vg, virtual_t, psg, ln_p_half, ln_p_full, p_full, dx_psg, dy_psg, dt_psg_tmp, wg, wg_full, dt_tg_tmp, dt_ug_tmp, dt_vg_tmp)（行 866-867；子程序定义行 1041）
4. compute_geopotential（行 870-874）
5. 垂直平流 vert_advection(delta_t, wg, dp, ug/vg/tg, dt_grid_tmp, scheme=..., form=ADVECTIVE_FORM)（行 883/885/890）
6. 水平平流 horizontal_advection(ts(:,:,:,current), ug, vg, dt_tg_tmp)（行 893）
7. 科氏项 dt_ug += (vorg + coriolis(j))*vg；dt_vg -= (vorg + coriolis(j))*ug（行 896-901）
8. vor_div_from_uv_grid（行 903）、dt_divs = dt_divs - compute_laplacian(phis_plus_ke)（行 905-907）
9. **半隐式重力波**：if(use_implicit) call implicit_correction(dt_divs, dt_ts, dt_ln_ps, divs, ts, ln_ps, delta_t, previous, current)（行 909；模块 implicit.F90）
10. 谱阻尼 compute_spectral_damping_vor/div/(ts)（行 911-913，spectral_damping.F90）
11. **蛙跳 + Robert/RAW 滤波**：leapfrog_2level_A(ln_ps, dt_ln_ps, previous, current, future, delta_t, robert_coeff, raw_filter_coeff, part_filt_ln_ps) 等 4 个场（行 922-927）；中间步用 leapfrog(...)（行 928-933）。时间步长 delta_t = dt_real/num_steps 或 2*dt_real/num_steps（行 837-841）。模块 leapfrog.F90。
12. 逆变换 trans_spherical_to_grid，psg = exp(ln_psg)（行 936-941）
13. 温度合法性检查 valid_range_t（行 943）
14. update_tracers(...)（行 1010）、compute_corrections(delta_t, tracer_attributes, p_full)（水量/能量/质量修正，行 1249）

### 3.3 Held–Suarez 强迫（hs_forcing.F90）
URL：https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/hs_forcing/hs_forcing.F90
namelist 默认（行 77-82）：

    t_zero=315., t_strat=200., delh=60., delv=10., eps=0., sigma_b=0.7
    P00 = 1.e5, p_trop = 1.e4, alpha = 2./7
    ka = -40., ks = -4., kf = -1.   ! 负号表示单位为「天」
    do_conserve_energy = .true.

单位换算（行 390-403）：tka = -1/(86400*ka)（即 1/40 day^-1）、tks = -1/(86400*ks)、vkf = -1/(86400*kf)。

平衡温度（行 536-569，equilibrium_t_option == 'Held_Suarez'）：

    sin_lat_2 = sin²(lat); cos_lat_2 = 1 - sin²(lat)
    t_star = t_zero - delh*sin_lat_2 - eps*sin_lat
    tstr   = t_strat - eps*sin_lat
    p_norm = p_full(k)/P00
    the    = t_star - delv*cos_lat_2*log(p_norm)
    teq(k) = max( the * p_norm**KAPPA , tstr )

牛顿弛豫（行 551、588-600）：

    tcoeff = (tks - tka)/(1 - sigma_b)
    sigma  = p_full(k)/ps
    若 sigma_b < sigma <= 1:  tdamp = tka + cos⁴(lat)*tcoeff*(sigma - sigma_b)
    否则:                      tdamp = tka
    tdt(k) = -tdamp*(T(k) - teq(k))

边界层 Rayleigh 摩擦（行 644-666）：

    vcoeff = -vkf/(1 - sigma_b)
    若 sigma_b < sigma <= 1:  vfactr = vcoeff*(sigma - sigma_b);  udt = vfactr*u; vdt = vfactr*v
    否则: udt = vdt = 0

held_suarez 测试用例取值：t_zero=315, t_strat=200, delh=60, delv=10, eps=0, sigma_b=0.7, ka=-40, ks=-4, kf=-1。

### 3.4 Frierson 理想化湿物理（idealized_moist_phys.F90 主调度）
URL：https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_spectral/driver/solo/idealized_moist_phys.F90
namelist 默认（行 101-163）：

    turb = .false. ; do_lcl_diffusivity_depth = .false. ; do_virtual = .false.
    convection_scheme = 'unset'   ! 可选 NONE/SIMPLE_BETTS_MILLER/FULL_BETTS_MILLER/DRY/RAS(行107-115)
    do_lscale_cond = .true. ; do_cloud_simple = .false. ; do_cloud_spookie = .false.
    two_stream_gray = .true. ; do_rrtm_radiation = .false. ; do_socrates_radiation = .false.
    do_damping = .false. ; mixed_layer_bc = .false. ; gp_surface = .false. ; do_simple = .false.
    roughness_heat = 0.05 ; roughness_moist = 0.05 ; roughness_mom = 0.05
    land_roughness_prefactor = 1.0 ; land_option = 'none'
    bucket = .false. ; init_bucket_depth = 1000. ; init_bucket_depth_land = 20.
    max_bucket_depth_land = 0.15 (Manabe 1969) ; robert_bucket = 0.04 ; raw_bucket = 0.53
    damping_coeff_bucket = 0. ; finite_bucket_depth_over_land = .true.

时间步序列（subroutine idealized_moist_phys，行 879 起；delta_t = dt_real 或 2*dt_real，行 903-907）：
- 对流：case(SIMPLE_BETTS_CONV) → call qe_moist_convection(delta_t, tg(...,previous), grid_tracers(...,nsphum), p_full, p_half, coldT, rain, snow, conv_dt_tg, conv_dt_qg, q_ref, convflag, klzbs, cape, cin, invtau_q_relaxation, invtau_t_relaxation, t_ref, klcls)（行 924-933）
- 大尺度凝结 call lscale_cond(tg_tmp, qg_tmp, ...)（行 1043）
- 辐射下行 call two_stream_gray_rad_down(is, js, Time, rad_lat, rad_lon, p_half(:,:,:,current), tg(:,:,:,previous), net_surf_sw_down, surf_lw_down, albedo, grid_tracers(:,:,:,previous,nsphum))（行 1116-1123）
- 地表通量 call surface_flux(...)（行 1139-1181+，模块 src/coupler/surface_flux.F90，本会话未读该文件正文）
- 辐射上行 call two_stream_gray_rad_up(is, js, Time, rad_lat, p_half, t_surf, t, tdt, albedo)（行 1242）
- Rayleigh/阻尼 call damping_driver(...)（行 1321）
- 湍流扩散 call vert_turb_driver(1, 1, ...)（行 1334）
- 混合层 call mixed_layer(...)（行 1401）

#### (a) 简化 Betts–Miller / 准平衡对流（Frierson 2007）
文件：src/atmos_param/qe_moist_convection/qe_moist_convection.F90
URL：https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/qe_moist_convection/qe_moist_convection.F90
模块头注释（行 24-36）明说实现 Frierson (2007, JAS 64) 的 quasi-equilibrium 方案与更浅的浅对流，并按 O'Gorman & Schneider (2008) 做虚温与饱和水汽压一致性修正。

参数（行 66-75，namelist 行 79）：

    tau_bm = 7200.   ! Betts-Miller 弛豫时间 (s) = 2 h
    rhbm   = .8      ! 参考相对湿度
    Tmin   = 173. ; Tmax = 335.   ! LCL 查找表温区
    val_inc = 0.01   ! 查找表步长
    small = 1.e-10 ; pref = 1.e5

公式（行 715-764）：

    Pq_calculation:  deltaq(k) = -(qin(k) - qref(k)) * dt/tau_bm
                     Pq = Σ_{k=kLZB..k_sfc} deltaq(k)*(p_half(k)-p_half(k+1)) / grav
    Pt_calculation:  deltaT(k) = -(Tin(k) - Tref(k)) * dt/tau_bm
                     Pt = Σ (Cp_air/(HLv+small)) * deltaT(k) * (p_half(k+1)-p_half(k)) / grav

参考廓线（set_reference_profiles，行 768-796）：

    Tref(k) = Tp(k)                                ! Tp = 湿绝热抬升的 parcel 温度
    eref    = rhbm * p_full(k) * rp(k) / (rp(k) + rdgas/rvgas)
    rp(k)   = mixing_ratio(eref, p_full(k))
    qref(k) = rp(k)/(1 + rp(k))

CAPE/自由对流层判定：CAPE_calculation(行 395)、CAPE_below_LCL(行 462)、CAPE_above_LCL(行 597)；浅对流分支 do_shallow_convection(行 800)、零降水层 level_of_zero_precip(行 845)、change_Tref_LZB_shallowconv(行 892)；深对流 do_deep_convection(行 933)、do_change_Tref_deepconv(行 960)、do_change_time_scale_deepconv(行 995)。诊断标志 convflag：0=无对流、1=浅对流、2=深对流（行 214-217）。
frierson 测试用例覆盖：rhbm=0.7, Tmin=160., Tmax=350.

#### (b) 大尺度凝结（lscale_cond.F90）
URL：https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/lscale_cond/lscale_cond.F90

    real    :: hc = 1.00          ! 行56 发生大尺度凝结的相对湿度阈值 (0<=hc<=1)
    logical :: do_evap  = .false. ! 行57 次饱和层再蒸发
    logical :: do_simple = .false.! 行58
    namelist /lscale_cond_nml/ hc, do_evap, do_simple   ! 行60

子程序 lscale_cond(tin, qin, pfull, phalf, coldT, rain, snow, tdel, qdel, mask, conv)（行 79）；再蒸发 precip_evap（行 216）。frierson 用例设 do_simple=True, do_evap=True（hc 保持默认 1.00）。

#### (c) 灰体双流辐射（two_stream_gray_rad.F90）
URL：https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/two_stream_gray_rad/two_stream_gray_rad.F90
默认方案 rad_scheme = 'frierson'（行 89），sw/lw 均为 B_FRIERSON = 2（行 91-94）。
常量（行 72-104）：

    solar_constant  = 1360.0    ! 行72
    del_sol = 1.4 ; del_sw = 0.0
    ir_tau_eq   = 6.0           ! 行75
    ir_tau_pole = 1.5           ! 行76
    atm_abs = 0.0               ! 行77 (frierson 用例设 0.2)
    odp = 1.0 ; sw_diff = 0.0
    linear_tau = 0.1            ! 行80
    wv_exponent = 4.0           ! 行81
    solar_exponent = 4.0        ! 行82
    ! Geen (RG 2015) 窗口波段常量:
    ir_tau_co2_win=0.2150 ; ir_tau_wv_win1=147.11 ; ir_tau_wv_win2=1.0814e4
    ir_tau_co2=0.1 ; ir_tau_wv1=23.8 ; ir_tau_wv2=254.0
    window=0.3732 ; carbon_conc=360.0

B_FRIERSON 光学厚度与透过率（行 697-717）：

    lw_tau_0         = ( ir_tau_eq + (ir_tau_pole - ir_tau_eq)*sin²(lat) ) * odp
    lw_tau(:,:,k)    = lw_tau_0 * ( linear_tau*(p_half(k)/pstd_mks)
                                   + (1-linear_tau)*(p_half(k)/pstd_mks)**wv_exponent )
    lw_dtrans(:,:,k) = exp( -(lw_tau(k+1) - lw_tau(k)) )
    lw_down(:,:,1)   = 0 ;  lw_down(:,:,k+1) = lw_down(k)*lw_dtrans(k) + b(k)*(1-lw_dtrans(k))

Geen 窗口方案（行 648-675）：

    lw_del_tau     = ( ir_tau_co2 + 0.2023*log(carbon_conc/360.)
                     + ir_tau_wv1*log(ir_tau_wv2*q + 1) ) * Δp / pstd_mks_earth
    lw_del_tau_win = ( ir_tau_co2_win + 0.0954*log(carbon_conc/360.)
                     + ir_tau_wv_win1*q + ir_tau_wv_win2*q² ) * Δp / pstd_mks_earth
    b_win = window*b ;  b = (1 - window)*b

Schneider–Liu / Byrne–O'Gorman 变体常量：single_albedo=0.8, back_scatter=0.398, lw_tau_0_gp=80.0, sw_tau_0_gp=3.0, lw_tau_exponent_gp=2.0, sw_tau_exponent_gp=1.0, diabatic_acce=1.0（行 107-113）；bog_a=0.8678, bog_b=1997.9, bog_mu=1.0（行 122-124）。子程序：two_stream_gray_rad_init(行 189)、_down(行 459)、_up(行 782)。
常量来源：PSTD_MKS = 101325.0、STEFAN = 5.6734e-8、RHO_CP = RHO0*CP_OCEAN，RHO0 = 1.035e3、CP_OCEAN = 3989.24495292815、KAPPA = 2/7、RDGAS = 287.04、GRAV = 9.80、HLV_WATER = 2.500e6（src/shared/constants/constants.F90 行 52-129、242；https://raw.githubusercontent.com/ExeClim/Isca/master/src/shared/constants/constants.F90 ）。

#### (d) 边界层方案
- **湍流扩散**：Frierson 用例走 vert_turb_driver_nml: do_mellor_yamada=False, do_diffusivity=True, do_simple=True, constant_gust=0.0, use_tau=False 与 diffusivity_nml: do_entrain=False, do_simple=True（exp/test_cases/frierson/frierson_test_case.py）。对应文件 src/atmos_param/vert_turb_driver/vert_turb_driver.F90、src/atmos_param/diffusivity/diffusivity.F90（本会话未逐行读）。
- **Frierson Monin–Obukhov 模块**（可选用，frierson 用例未启用）：src/atmos_param/frierson_monin_obukhov/frierson_monin_obukhov.F90，namelist 默认（行 273-278）rich_crit = 2.0、drag_min = 1.e-05、relax_time = 0.、neutral = .false.、stable_option = 1、zeta_trans = 0.5；内部 small = 1.e-04（行 287）。子程序 mo_drag_1d(342)、solve_zeta(509)、mo_profile_1d(737)、mo_diff_1d(950)、stable_mix_3d(1314)。
- **地表通量**：surface_flux_mod（src/coupler/surface_flux.F90），粗糙度由 idealized_moist_phys_nml 的 roughness_mom/heat/moist 给定（默认 0.05 m，frierson 用例 3.21e-05 m）。该文件正文本次未读 → 见未确认项。
- **区域 Rayleigh 阻尼/海绵层**：src/atmos_param/damping_driver/damping_driver.f90，namelist（行 42-62）：trayfric = 0.（负值为「天」）、sponge_pbottom = 50. [Pa]、do_rayleigh = .false.、do_conserve_energy = .false.、const_drag_amp = 3.e-04。subroutine rayleigh（行 594-637）：

    若 p2(:,:,k) < sponge_pbottom:
       fact = rfactr*(sponge_pbottom - p2)**2 / sponge_pbottom**2
       udt = -u*fact ; vdt = -v*fact
    若 do_conserve_energy: tdt = -((u+.5*dt*udt)*udt + (v+.5*dt*vdt)*vdt)/cp_air

（rfactr 由 trayfric 换算的具体行本次未读到 → 未确认。）
frierson 用例设 do_rayleigh=True, trayfric=-0.25（days）, sponge_pbottom=5000., do_conserve_energy=True。

### 3.5 混合层（slab）海洋
文件：src/atmos_spectral/driver/solo/mixed_layer.F90
URL：https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_spectral/driver/solo/mixed_layer.F90
namelist 默认（行 84-140，namelist/mixed_layer_nml/ 行 142-161）：

    logical :: evaporation = .true.
    real    :: depth = 40.0          ! 行95  混合层深度 (m)
               land_depth = -1., trop_depth = -1., trop_cap_limit = 15., heat_cap_limit = 60.
               np_cap_factor = 1., albedo_exp = 2., albedo_cntr = 45., albedo_wdth = 10.
               higher_albedo = 0.10, lat_glacier = 60., land_h_capacity_prefactor = 1.0
    real    :: albedo_value = 0.06   ! 行92
    real    :: tconst = 305.0, delta_T = 40.0   ! 行89-90 初值廓线
    real    :: qflux_amp = 0.0, qflux_width = 16.0  ! 行85-86
    logical :: prescribe_initial_dist = .false., do_qflux = .false., do_warmpool = .false.
    integer :: albedo_choice = 1
    real    :: ice_albedo_value = 0.7, ice_concentration_threshold = 0.5

热容（mixed_layer_init，行 310-559）：

    trop_capacity = trop_depth*RHO_CP ;  land_capacity = land_depth*RHO_CP   ! 行315-318
    land_sea_heat_capacity(:,:) = depth*RHO_CP                               ! 行519

即热容 C = depth × RHO_CP，**RHO_CP = RHO0*CP_OCEAN = 1.035e3 × 3989.24495292815 = 4.1289e6 J·m⁻³·K⁻¹**（src/shared/constants/constants.F90 行 87-90）。默认 depth=40 m → C ≈ 1.65e8 J·K⁻¹·m⁻²。
温度倾向（subroutine mixed_layer，行 573-770）：

    corrected_flux     = -net_surf_sw_down - surf_lw_down + alpha_t*CP_AIR + alpha_lw - ocean_qflux   ! 行676
    若 evaporation:      corrected_flux += alpha_q*HLV                                        ! 行680
    t_surf_dependence  = beta_t*CP_AIR + beta_lw (+ beta_q*HLV)                               ! 行677-682
    eff_heat_capacity  = land_sea_heat_capacity + t_surf_dependence*dt                        ! 行735
    delta_t_surf       = -corrected_flux * dt / eff_heat_capacity                             ! 行743/745 起
    t_surf             = t_surf + delta_t_surf

其中 alpha_t = flux_t/cp_air + dhdt_atm/cp_air*fn_t、alpha_q = flux_q_total + dedq_atm*fn_q、alpha_lw = flux_r（行 655-657）、beta_t = dhdt_surf/cp_air + dhdt_atm/cp_air*en_t、beta_q = dedt_surf + dedq_atm*en_q、beta_lw = drdt_surf（行 659-661）。
**与大气耦合方式**：单向时间分裂——大气侧先用当前 t_surf 算下行辐射与地表通量（two_stream_gray_rad_down、surface_flux），再调用 mixed_layer(...)（调用点行 1401）用隐式步进更新 t_surf 与 albedo（albedo_calc(albedo_out, Time_next)，行 624），下一时间步的 two_stream_gray_rad_up（行 1242）与地表通量使用新 t_surf。通量→温度使用隐式（gamma_t/gamma_q，行 642-649）以避免薄层显式不稳定。
frierson 用例覆盖：tconst=285., prescribe_initial_dist=True, evaporation=True, depth=2.5, albedo_value=0.31（即 C ≈ 1.03e7 J·K⁻¹·m⁻²）。

### 3.6 典型时间步与积分长度（有据可查者）
| 用例 | dt_atmos | 分辨率 | 层数 | 积分长度 | 来源 |
|---|---|---|---|---|---|
| frierson | **720 s** | 谱默认 T42 | **25** | 每段 30 天 × 120 段 = **3600 天 ≈ 9.9 年**（exp.run(1..120)，用 restart 续跑） | exp/test_cases/frierson/frierson_test_case.py |
| held_suarez | **600 s** | **T42**（RESOLUTION = 'T42', 25） | **25** | 30 天 × 12 段 = **360 天** | exp/test_cases/held_suarez/held_suarez_test_case.py |
| 仓库默认 input.nml | 600 s | T42 | **40** | days = 30 | exp/run_isca/input/input.nml |

URL：
- https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/frierson/frierson_test_case.py
- https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/held_suarez/held_suarez_test_case.py
- https://raw.githubusercontent.com/ExeClim/Isca/master/exp/run_isca/input/input.nml

frierson 用例的其它关键设置：calendar='thirty_day'、convection_scheme='SIMPLE_BETTS_MILLER'、do_damping=True、turb=True、mixed_layer_bc=True、do_simple=True、vert_coord_option='input' 且显式给出 25 层 bk 数组（bk = [0.0, 0.0117665, 0.0196679, 0.0315244, 0.0485411, 0.0719344, 0.1027829, 0.1418581, 0.1894648, 0.2453219, 0.3085103, 0.3775033, 0.4502789, 0.5244989, 0.5977253, 0.6676441, 0.7322627, 0.7900587, 0.8400683, 0.8819111, 0.9157609, 0.9422770, 0.9625127, 0.9778177, 0.9897489, 1.0]，pk 全 0 → 纯 σ 坐标）、scale_heights=11.0, exponent=7.0, surf_res=0.5, robert_coeff=0.03, damping_order=4, water_correction_limit=200.e2, reference_sea_level_press=1.0e5, valid_range_t=[100.,800.], initial_sphum=[2.e-6]。
**Spin-up（转起）**：源码/文档中没有「spin-up 年数」的显式规定，只有上述用例的分段积分长度 → 视为未找到正式 spin-up 说明。

## 4. 是否需要全局迭代
- 时间积分是**显式蛙跳 + Robert/RAW 滤波**（leapfrog_2level_A，spectral_dynamics.F90:922-934），**不求解全局非线性方程、无迭代收敛环**。
- 每个时间步有：2 次全局谱↔网格变换（trans_grid_to_spherical 行 877/894，trans_spherical_to_grid 行 936-940，MPI 全局通信）+ 1 次半隐式重力波求解（implicit_correction，行 909，含矩阵/三对角求解 src/atmos_spectral/model/implicit.F90、matrix_invert.F90）。
- 物理参数化全部是**逐气柱局地计算**（QE 对流 qe_moist_convection 按 (i,j) 列循环；大尺度凝结同样逐列）。
- 结论：**无全局迭代求解**；有全局谱变换通信与每步一次隐式线性求解。

## 5. 时间复杂度或实测性能
- 结构上每步成本 = 2 次谱变换 + 逐列物理 + 1 次隐式求解；成本随截断波数增长（Legendre 变换主导）。
- **官方实测性能数字：未找到。** 仓库文档中 docs/source/modules/dynamics.rst 全文仅 97 字符（"Isca's dynamical core / Coming soon..."，https://raw.githubusercontent.com/ExeClim/Isca/master/docs/source/modules/dynamics.rst ），无性能表；docs/source/begginers_guide.rst 只有「most laptops will not suffice」这类定性说明，无 SYPD / CPU 小时数字；GMD 论文页面 https://gmd.copernicus.org/articles/11/843/2018/ 抓取到 100000 字符即被截断且正文不在其中，未能读到性能数据。

## 6. 输出格式
- **NetCDF（FMS diag_manager / mpp_io）**，由 diag_table 控制。默认表：exp/run_isca/input/diag_table（https://raw.githubusercontent.com/ExeClim/Isca/master/exp/run_isca/input/diag_table ），输出文件 atmos_monthly，频率 30 days，format 1，time_units days：

    "dynamics", "ps",            "ps",            "atmos_monthly", "all", .true.,  "none", 2,
    "dynamics", "bk",            "bk",            "atmos_monthly", "all", .false., "none", 2,
    "dynamics", "pk",            "pk",            "atmos_monthly", "all", .false., "none", 2,
    "atmosphere","precipitation","precipitation", "atmos_monthly", "all", .true.,  "none", 2,
    "mixed_layer","t_surf",      "t_surf",        "atmos_monthly", "all", .true.,  "none", 2,
    "dynamics", "sphum",         "sphum",         "atmos_monthly", "all", .true.,  "none", 2,
    "dynamics", "ucomp",         "ucomp",         "atmos_monthly", "all", .true.,  "none", 2,
    "dynamics", "vcomp",         "vcomp",         "atmos_monthly", "all", .true.,  "none", 2,
    "dynamics", "temp",          "temp",          "atmos_monthly", "all", .true.,  "none", 2,
    "dynamics", "vor",           "vor",           "atmos_monthly", "all", .true.,  "none", 2,
    "dynamics", "div",           "div",           "atmos_monthly", "all", .true.,  "none", 2,

- **held_suarez 用例实际写出的变量**（exp/test_cases/held_suarez/held_suarez_test_case.py，diag.add_field 序列）：dynamics: ps, bk, pk, ucomp, vcomp, temp, vor, div（8 个；bk/pk 为静态场不加 time_avg）。**不含 sphum / precipitation / t_surf。** 文件 atmos_monthly，30 days。
- **frierson 用例实际写出的变量**（exp/test_cases/frierson/frierson_test_case.py）：dynamics: ps, bk, pk, sphum, ucomp, vcomp, temp, vor, div + atmosphere: precipitation + mixed_layer: t_surf（共 11 个；bk/pk 为静态场）。文件 atmos_monthly，30 days。
- 诊断注册源码：subroutine spectral_diagnostics_init（spectral_dynamics.F90:1590）与 spectral_diagnostics（行 1745）；每步诊断 every_step_diagnostics（src/atmos_spectral/model/every_step_diagnostics.F90）。

## 7. 可直接借鉴什么
1. **「涡度/散度/温度/ln(ps) 四量 + 2 时间层」的谱状态设计**可直接照搬（spectral_dynamics.F90:128-134, 641-659）：网格场 (u,v,T,ps,q) 全部由谱场每步派生，避免两套状态不一致。
2. **Robert + RAW 联合滤波的 2 层蛙跳接口**：leapfrog_2level_A(field, tendency, previous, current, future, delta_t, robert_coeff, raw_filter_coeff, part_filt)（行 923-926）——把滤波拆成「每步部分滤波 + 步末 complete_robert_filter」，便于 num_steps 子循环。
3. **半隐式只处理重力波项**（行 909），其余显式；隐式系数 alpha_implicit=.5（centered implicit）。
4. **slab 海洋的隐式耦合写法**：用 gamma_t/gamma_q（行 642-649）把表层温度/湿度反馈线性化后再隐式步进（行 735-745），厚薄混合层都稳定——这是薄层（2.5 m）也不炸的关键。
5. **灰体辐射的解析 τ(σ,lat)**：τ = τ₀(lat)·[a·σ + (1-a)·σ^4]（行 699-706）两行实现，配上 lw_dtrans=exp(-Δτ) 递推（行 710-717），成本几乎为零，适合做基准辐射。
6. **QE 对流的「参考廓线 + 弛豫」结构**：δq = -(q-qref)·dt/τ、δT = -(T-Tref)·dt/τ（行 731/757），τ=7200 s、RH=0.7-0.8；降水由垂直积分给出（行 732/758），无迭代。
7. **垂直坐标可选择 'input' 并直接从 namelist 读 bk/pk 数组**（frierson 用例），便于复刻文献层结。
8. **Held–Suarez 强迫的所有常数集中在 hs_forcing.F90 行 77-80 一处**，便于参数扫描（仓库还提供 held_suarez/parameter_sweep.py）。

## 8. 关键文件路径 + 行号 + URL（Isca）
| 内容 | 路径 | 行号 | URL |
|---|---|---|---|
| 谱动力核心/状态/namelist 默认 | src/atmos_spectral/model/spectral_dynamics.F90 | 117-135, 153-227, 639-667, 783-1037 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_spectral/model/spectral_dynamics.F90 |
| 理想化湿物理调度 | src/atmos_spectral/driver/solo/idealized_moist_phys.F90 | 101-181, 879-1510 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_spectral/driver/solo/idealized_moist_phys.F90 |
| 混合层海洋 | src/atmos_spectral/driver/solo/mixed_layer.F90 | 84-161, 315-319, 519, 573-770 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_spectral/driver/solo/mixed_layer.F90 |
| QE(BM) 对流 | src/atmos_param/qe_moist_convection/qe_moist_convection.F90 | 66-79, 715-796, 800-1010 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/qe_moist_convection/qe_moist_convection.F90 |
| 大尺度凝结 | src/atmos_param/lscale_cond/lscale_cond.F90 | 56-60, 79 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/lscale_cond/lscale_cond.F90 |
| 灰体双流辐射 | src/atmos_param/two_stream_gray_rad/two_stream_gray_rad.F90 | 72-124, 648-675, 697-717 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/two_stream_gray_rad/two_stream_gray_rad.F90 |
| Held–Suarez 强迫 | src/atmos_param/hs_forcing/hs_forcing.F90 | 77-82, 536-600, 640-678 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/hs_forcing/hs_forcing.F90 |
| Rayleigh 海绵阻尼 | src/atmos_param/damping_driver/damping_driver.f90 | 42-62, 594-637 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/damping_driver/damping_driver.f90 |
| Frierson M-O（可选） | src/atmos_param/frierson_monin_obukhov/frierson_monin_obukhov.F90 | 273-278, 342-1388 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/atmos_param/frierson_monin_obukhov/frierson_monin_obukhov.F90 |
| 物理常量 | src/shared/constants/constants.F90 | 52-129, 242 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/shared/constants/constants.F90 |
| 默认 diag_table | exp/run_isca/input/diag_table | 全文 | https://raw.githubusercontent.com/ExeClim/Isca/master/exp/run_isca/input/diag_table |
| 默认 input.nml | exp/run_isca/input/input.nml | 全文 | https://raw.githubusercontent.com/ExeClim/Isca/master/exp/run_isca/input/input.nml |
| frierson 用例 | exp/test_cases/frierson/frierson_test_case.py | 全文 | https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/frierson/frierson_test_case.py |
| held_suarez 用例 | exp/test_cases/held_suarez/held_suarez_test_case.py | 全文 | https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/held_suarez/held_suarez_test_case.py |
| Python 分辨率表 | src/extra/python/isca/experiment.py | 29-56, 124-133 | https://raw.githubusercontent.com/ExeClim/Isca/master/src/extra/python/isca/experiment.py |
| 安装/依赖 | docs/source/install.rst | 全文 | https://raw.githubusercontent.com/ExeClim/Isca/master/docs/source/install.rst |
| 许可证 | LICENSE | 全文 | https://raw.githubusercontent.com/ExeClim/Isca/master/LICENSE |

### 运行依赖与离线能力（Isca）
- 依赖（docs/source/install.rst 原文列表）：f90nml, fortran-compiler, jinja2, libgfortran, netcdf-fortran, numpy, openmpi, pandas, python=3.7, pip, pytest, sh, tqdm, xarray；并说明 "We've tried and tested Intel Fortran, GFortran (usually works) and Cray-Fortran"。Python 模块安装：cd isca/src/extra/python && pip install -e .
- **可离线运行**：编译与积分本身不需网络（Python 包装器只在 clone/取 git 信息时用网；除 realism 用例需要 INPUT/ 下的 NetCDF 边界数据外无外部下载）。
- 编译器环境通过 \$GFDL_ENV 选择 \$GFDL_BASE/src/extra/env/ 下的 env 文件（frierson_test_case.py 注释）。

## 9. 未确认项（Isca）
- src/coupler/surface_flux.F90 正文未读（仅知路径来自文件清单与 use surface_flux_mod, only: surface_flux, gp_surface_flux，idealized_moist_phys.F90:49）→ 体块通量系数 C_D/C_H 的具体公式与数值**未找到**。
- vert_turb_driver.F90 / diffusivity.F90 的扩散系数公式与常数**未读**。
- damping_driver 中 rfactr 由 trayfric 换算的具体行号与式子**未确认**。
- Isca 官方实测性能（SYPD / CPU 小时 / scaling）**未找到**。
- 官方文档中对「推荐 spin-up 年数」的说明**未找到**。
- 有限体积动力核心：**未找到**（只有垂直平流/示踪物平流的有限体积选项）。
- bucket_model.F90 / land_model.F90：这两条路径在 master 分支**不存在**（raw 返回 404）；相关功能实际位于 idealized_moist_phys.F90 的 bucket 分支与 src/land/（本次未逐行读）。

---

# 模型 2a：SPEEDY（ICTP 原始 Fortran）

## 1. 项目名+主链接+语言+许可证+维护状态
- **项目名**：SPEEDY（Simplified Parameterizations, primitivE-Equation DYnamics），ICTP，作者 Fred Kucharski / Franco Molteni / Martin P. King。
- **官方主页（可达）**：http://users.ictp.it/~kucharsk/speedy-net.html （本会话抓取 200，45 415 字符，标题 "ICTP AGCM-Net"）。该页明确写：**"For downloading SPEEDY Atmospheric General Circulation Model ... please contact: kucharsk@ictp.it"** —— 原始 Fortran 源码**不提供直接下载**，只提供物理参数化包 tar：http://users.ictp.it/~kucharsk/speedy_ver41.5_phys_par.tar （本会话抓取返回错误 "unsupported content type application/x-tar"，说明文件存在但非文本）。
- **真实可直接阅读源码的地址（本会话逐文件抓取成功）**：**https://github.com/samhatfield/speedy.f90** —— modern Fortran 重写版，README 自述 "speedy.f90 is an intermediate complexity atmospheric general circulation model written in modern Fortran. It is based on SPEEDY, developed by Fred Kucharski, Franco Molteni and Martin P. King"，Zenodo DOI 10.5281/zenodo.5816982（记录页 https://zenodo.org/record/5816982 ，标题 "samhatfield/speedy.f90: v1.0.0"）。
  镜像：https://github.com/sciencewiki/speedy.f90 （同一 commit 0ebfdbfcd62efd46dd1802284908645be4b230be）。
  文件清单接口：https://ungh.cc/repos/samhatfield/speedy.f90/files/master （80 个源码/数据文件）。
  源码 raw 基址：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/
- **用户提示的 https://kestrel.nmt.edu/~rsonnenf/ 已核实：无 SPEEDY 内容**（页面 5 471 字符，/speedy/i 匹配为 false）。
- **语言**：Fortran（free-form .f90，modern Fortran 子程序化）。
- **许可证**：MIT 风格但**限非商业用途**。LICENSE 首段原文："Copyright (c) 2020 <Fred Kucharski, Franco Molteni, Sam Hatfield> / Permission is hereby granted, free of charge, to any person obtaining a copy of this software ... for research, educational and other non-commercial purposes"（https://raw.githubusercontent.com/samhatfield/speedy.f90/master/LICENSE ）。
- **依赖**：README 明确 "speedy.f90 has only one dependency: the NetCDF library"；构建：设 NETCDF 环境变量 → bash build.sh → bin/speedy；运行 bash run.sh，输出在 rundir。**需要 gfortran + NetCDF，无 MPI 依赖**。可离线运行（边界数据在 data/bc/t30/clim/ 下自带：land.nc、sea_ice.nc、sea_surface_temperature.nc、snow.nc、soil.nc、surface.nc 等，见文件清单）。
- **维护状态**：mirror 最后提交 sha 固定；GitHub 上无 SPEEDY-Committee/speedy.f90（返回 404）。ICTP 页面仍在更新（列出 SPEEDY 8-layer v42 描述 pdf、speedy_ver41.5_phys_par.tar、SPEEDY-NEMO 论文）。

## 2. 算的是什么（输出场）
谱动力核心的全球原始方程大气 GCM（T30L8，σ 坐标），含简化的 Tiedtke 对流、大尺度凝结、浅对流/垂直扩散、短波/长波辐射、陆面与 slab 海洋。NetCDF 输出物理场：经纬网格上的 u、v、t、q、phi（位势高度）、ps（见第 6 节）。

## 3. 方程与近似（实际公式 + 参数值）
### 3.1 网格/截断/层数/时间步（source/params.f90）
URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/params.f90

    integer, parameter :: trunc = 30   ! Spectral truncation total wavenumber
    integer, parameter :: ix = 96      ! Number of longitudes
    integer, parameter :: iy = 24      ! Number of latitudes in hemisphere
    integer, parameter :: il = 2*iy    ! = 48 全纬度
    integer, parameter :: kx = 8       ! Number of vertical levels
    integer, parameter :: nx = trunc+2 ! = 32
    integer, parameter :: mx = trunc+1 ! = 31
    integer, parameter :: ntr = 1      ! tracers (specific humidity)
    integer, parameter :: nsteps = 36              ! 一天 36 步
    real(p), parameter :: delt = 86400.0/nsteps    ! = 2400 s
    real(p), parameter :: rob = 0.05               ! Robert 滤波
    real(p), parameter :: wil = 0.53               ! Williams 滤波
    real(p), parameter :: alph = 0.5               ! 半隐式（0.5 = centered implicit）
    integer, parameter :: iseasc = 1  ; nstrad = 3 ; sppt_on = .false. ; issty0 = 1979
    ! namelist /params/: nsteps_out 默认 1 ; nstdia 默认 36*5 = 180

**默认 T30（截断总波数 30）、96×48 网格、8 层、时间步 2400 s（36 步/天）。**

### 3.2 σ 层（source/geometry.f90）
URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/geometry.f90

    if (kx == 8) hsg(:9) = (/ 0.000, 0.050, 0.140, 0.260, 0.420, 0.600, 0.770, 0.900, 1.000 /)  ! 行47 半层
    dhs(k) = hsg(k+1)-hsg(k) ;  fsg(k) = 0.5*(hsg(k+1)+hsg(k))                                  ! 行52-53
    dhsr(k) = 0.5/dhs(k) ;      fsgr(k) = akap/(2.*fsg(k))                                      ! 行58-59

→ 全层 σ = **0.025, 0.095, 0.200, 0.340, 0.510, 0.685, 0.835, 0.950**。另有 kx=5、kx=7 的层组（行 42-45）。网格：sia_half(j)=cos(π(j-0.25)/(il+0.5))（行 68）。

### 3.3 预报变量（source/prognostics.f90）
URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/prognostics.f90

    complex(p) :: vor(mx,nx,kx,2)    !! Vorticity
    complex(p) :: div(mx,nx,kx,2)    !! Divergence
    complex(p) :: t(mx,nx,kx,2)      !! Absolute temperature
    complex(p) :: ps(mx,nx,2)        !! Log of (normalised) surface pressure (p_s/p0)
    complex(p) :: tr(mx,nx,kx,2,ntr) !! Tracers (tr(1): specific humidity in g/kg)
    complex(p) :: phi(mx,nx,kx) ; complex(p) :: phis(mx,nx)  ! 诊断位势

静止参考态初始化（行 50-90）：对流层 T = 288 K @ z=0 定常减温率，平流层 T = 216 K；p_ref = 1013 hPa；参考比湿 Qref = RHref * Qsat(288K, 1013hPa)，esref = 17.0，qref = refrh1*0.622*esref。动力常数（source/dynamical_constants.f90 行 12-22）：gamma = 6.0 K/km、hscale = 7.5 km、hshum = 2.5 km、refrh1 = 0.7、thd = 2.4 h、thdd = 2.4 h、thds = 12.0 h、tdrs = 24*30 h。

### 3.4 对流（source/convection.f90）——简化 Tiedtke(1993) 质量通量
URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/convection.f90
模块头（行 3-4）："Convection is modelled using a simplified version of the Tiedke (1993) mass-flux convection scheme."

    real(p), parameter :: psmin  = 0.8  ! 归一化地面气压最小值
    real(p), parameter :: trcnv  = 6.0  ! 向参考态弛豫时间 (小时)
    real(p), parameter :: rhbl   = 0.9  ! 边界层 RH 阈值
    real(p), parameter :: rhil   = 0.7  ! 中间层 RH 阈值（次级质量通量）
    real(p), parameter :: entmax = 0.5  ! 最大卷入（占云底质量通量比）
    real(p), parameter :: smf    = 0.8  ! 云底次级/初级质量通量比
    fqmax = 5.0                                     ! 行51
    fm0   = p0*dhs(kx)/(grav*trcnv*3600.0)          ! 行53
    rdps  = 2.0/(1.0 - psmin)                       ! 行54
    entr(k) = (max(0.0, fsg(k) - 0.5))**2.0         ! 行65 卷入廓线

子程序 get_convection_tendencies(psa, se, qa, qsat, itop, cbmf, precnv, dfse, dfqa)（行 27）、diagnose_convection（行 170）。

### 3.5 大尺度凝结（source/large_scale_condensation.f90）
URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/large_scale_condensation.f90
模块头公式：-(q - RH(σ)·q_sat)/τ_lsc。常数（行 25-28）：

    trlsc  = 4.0   ! 弛豫时间 (小时)
    rhlsc  = 0.9   ! 最大 RH 阈值（σ=1）
    drhlsc = 0.1   ! RH 阈值垂直范围
    rhblsc = 0.95  ! 边界层 RH 阈值
    rhref = rhlsc + drhlsc*(sig2 - 1.0) ; if (k == kx) rhref = max(rhref, rhblsc)   ! 行69-70

### 3.6 地表通量（source/surface_fluxes.f90）
URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/surface_fluxes.f90

    fwind0 = 0.95      ! 近地面风/最低层风
    ftemp0 = 1.0 ; fhum0 = 0.0
    cdl = 2.4e-3       ! 陆地动量拖曳系数
    cds = 1.0e-3       ! 海洋动量拖曳系数
    chl = 1.2e-3       ! 陆地热交换系数
    chs = 0.9e-3       ! 海洋热交换系数
    vgust = 5.0        ! 次网格阵风风速 (m/s)
    ctday = 1.0e-2     ! 日循环修正 dTskin/dSSRad
    dtheta = 3.0       ! 稳定性修正的位温梯度
    fstab = 0.67       ! 稳定性修正幅度
    hdrag = 2000.0     ! 地形修正高度尺度
    clambda = 7.0 ; clambsn = 7.0
    denvvs = (p0*psa/(rgas*t0))*sqrt(u0² + v0² + vgust²)   ! 行140

子程序 get_surface_fluxes(... ustr, vstr, shf, evap, slru, hfluxn, tsfc, tskin, u0, v0, t0, lfluxland)（行 43）；皮肤温度由能量平衡重定义（lskineb=.true.，行 87、202）。

### 3.7 垂直扩散（source/vertical_diffusion.f90）

    trshc  = 6.0   ! 浅对流弛豫时间 (h)
    trvdi  = 24.0  ! 水汽扩散弛豫时间 (h)
    trvds  = 6.0   ! 超绝热条件弛豫时间 (h)
    redshc = 0.5   ! 深对流区浅对流折减
    rhgrad = 0.5   ! 最大 dRH/dσ
    segrad = 0.1   ! 最小 dDSE/dφ

### 3.8 辐射（source/mod_radcon.f90）

    albsea = 0.07  ! 海面反照率
    albice = 0.60  ! 海冰反照率（冰 fraction=1）
    albsn  = 0.60  ! 雪面反照率
    epslw  = 0.05  ! 仅 PBL 吸收/发射的黑体谱比例
    emisfc = 0.98  ! 地表发射率

子程序 tau2, st4a, stratc, flux。短波以 nstrad = 3 步为周期计算（params.f90 行 34）。

### 3.9 slab 海洋（source/sea_model.f90）
URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/sea_model.f90

    ! 海洋混合层深度: d + (d0-d)*(cos_lat)^3
    real(p) :: depth_ml  = 60.   ! 高纬深度 (m)      行104
    real(p) :: dept0_ml  = 40.   ! 热带最小深度 (m)   行105
    ! 海冰深度: d + (d0-d)*(cos_lat)^2
    real(p) :: depth_ice = 2.5  ;  dept0_ice = 1.5     ! 行108-109
    real(p) :: tdsst = 90.   ! 海表温度异常耗散时间 (天)   行112
    real(p) :: tdice = 30.0  ! 海冰温度异常耗散时间 (天)   行118
    real(p) :: thrsh = 0.1 ;  fseamin = 1./3.         ! 行115,122
    hcaps(j) = 4.18e+6*(depth_ml +(dept0_ml -depth_ml)*coslat**3)     ! 行211 J/m²/K
    hcapi(j) = 1.93e+6*(depth_ice+(dept0_ice-depth_ice)*coslat**2)    ! 行212
    rhcaps(:,j) = delt/hcaps(j) ;  rhcapi(:,j) = delt/hcapi(j)        ! 行245-246
    tanom  = cdsea*(tanom + rhcaps*hflux) ;  sst_om = tanom + sstcl_ob  ! 行418-421
    tanom  = cdis*(tanom + rhcapi*hflux)                                ! 行437

即：**热带 40 m / 高纬 60 m 的 cos³φ 插值混合层，体积热容 4.18e6 J·m⁻³·K⁻¹；海冰 1.5–2.5 m，热容 1.93e6 J·m⁻³·K⁻¹。** 海温耦合标志 sea_coupling_flag 0-4（0=规定 SST，1=海洋被大气强迫，2=全耦合，3=异常+SST 气候态，4=同 3 但 El Niño 区规定异常），sst_anomaly_coupling_flag 0/1（行 60-70）。

### 3.10 陆面（source/land_model.f90）

    real(p), parameter :: sd2sc = 60.0   ! 雪深 (mm 水当量) 对应 snow cover = 1
    real(p) :: swcap = 0.30   ! 田间持水量（体积分数）
    real(p) :: swwil = 0.17   ! 凋萎点
    depth_soil = 1.0 ;  hcapl = depth_soil*2.50e+6     ! 行144,156  J/m²/K
    veg = max(0.0, veg_high + 0.8*veg_low)             ! 行111
    soilw12 = min(1.0, rsw*(swl1 + veg*...))           ! 行129

两层土壤水（swl1, swl2，来自 soil.nc）+ 雪 + 植被分数；land_coupling_flag = 1。

### 3.11 时间积分
蛙跳 + Robert(rob=0.05)/Williams(wil=0.53) 滤波 + alph=0.5 半隐式（params.f90 行 26-33）；文件 source/time_stepping.f90、source/implicit.f90、source/tendencies.f90（本会话未逐行读内容）。

## 4. 是否需要全局迭代
无全局迭代求解器：蛙跳 + 半隐式（alph=0.5 中心隐式）+ 每步谱↔网格变换；对流/凝结/辐射/扩散均为逐列局地计算。隐式求解每步一次线性求解（implicit.f90、matrix_inversion.f90）。**（本结论基于 params.f90 的时间积分参数与文件清单；implicit.f90 正文本次未读 → 求解器细节未确认。）**

## 5. 时间复杂度或实测性能
**未找到**官方性能数据。README 仅说明「默认跑两天、每个时间步输出一个 NetCDF 文件」；ICTP 页面无数值性能表。可确定的运行成本参数：delt = 2400 s、nsteps = 36/天、nstrad = 3（短波每 3 步一次）、nstdia = 180 步打印诊断（params.f90 行 26、34、51）。

## 6. 输出格式
NetCDF（netCDF-Fortran，nf90_* 接口）。文件 source/input_output.f90（URL：https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/input_output.f90 ）：

    维度: time(unlimited), lon(ix=96), lat(il=48), lev(kx=8)            ! 行138-145
    变量: time, lon, lat, lev,
          u   long_name="eastward_wind"        units="m/s"              ! 行154-156
          v   long_name="northward_wind"       units="m/s"              ! 行157-159
          t   long_name="air_temperature"      units="K"                ! 行160-162
          q   long_name="specific_humidity"    units="1"                ! 行163-165
          phi long_name="geopotential_height"  units="m"                ! 行167-170
          ps  long_name="surface_air_pressure" units="Pa"               ! 行171-173
    时间轴: time = timestep*24.0/nsteps   (天)                          ! 行178
    经度: lon = 3.75*k, k=0..95  (3.75° 等距)                           ! 行179
    lev : fsg(k)（8 个 σ 全层）                                          ! 行181

**默认 nsteps_out = 1 → 每个时间步（2400 s）写一个 NetCDF 文件**（README 亦如此说明）。

## 7. 可直接借鉴什么
1. **完整的「T30L8 + 2400 s + 36 步/天」最小可跑配置**：params.f90 一个文件里全部常数化（行 22-37），是复刻 SPEEDY 数值设置的最短路径。
2. **明确的 8 层 σ 半层数组**（geometry.f90 行 47），可直接作为 σ 坐标初始化表使用；浮点精度 p 通过 types 模块统一。
3. **简化 Tiedtke 对流的全部可调常数落在 6 个 parameter 上**（psmin/trcnv/rhbl/rhil/entmax/smf，convection.f90 行 15-22），卷入廓线一行代码 entr(k)=(max(0,σ_k-0.5))²。
4. **slab 海洋的 cos³φ 深度插值与「SST 异常耗散时间」公式**（sea_model.f90 行 211-246, 415-437）是低成本季节性海洋的成熟写法。
5. **地表通量系数按海/陆分别给定**（cds/cdl/chs/chl），并带阵风项 sqrt(u²+v²+vgust²)（行 140），可直接借用。
6. **NetCDF 变量命名与单位表**（input_output.f90 行 154-173）可直接作为 CF 风格输出模板。

## 8. 关键文件路径 + 行号 + URL（SPEEDY Fortran）
| 内容 | 路径 | 行号 | URL |
|---|---|---|---|
| 截断/层数/时间步 namelist | source/params.f90 | 22-37, 46-65 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/params.f90 |
| σ 层定义 | source/geometry.f90 | 42-59 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/geometry.f90 |
| 预报变量与参考态 | source/prognostics.f90 | 15-21, 50-90 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/prognostics.f90 |
| 动力常数 | source/dynamical_constants.f90 | 12-22 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/dynamical_constants.f90 |
| 对流 | source/convection.f90 | 15-22, 51-70, 170-231 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/convection.f90 |
| 大尺度凝结 | source/large_scale_condensation.f90 | 25-28, 69-70 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/large_scale_condensation.f90 |
| 地表通量 | source/surface_fluxes.f90 | 12-34, 140 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/surface_fluxes.f90 |
| 垂直扩散 | source/vertical_diffusion.f90 | 19-25 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/vertical_diffusion.f90 |
| 辐射常数 | source/mod_radcon.f90 | 22-27, 47 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/mod_radcon.f90 |
| slab 海洋 | source/sea_model.f90 | 104-122, 211-212, 245-246, 415-437 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/sea_model.f90 |
| 陆面 | source/land_model.f90 | 43, 56-61, 111, 129, 144-156 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/land_model.f90 |
| NetCDF 输出 | source/input_output.f90 | 135-181, 209-216 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/source/input_output.f90 |
| README/依赖 | README.md | 全文 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/README.md |
| 许可证 | LICENSE | 全文 | https://raw.githubusercontent.com/samhatfield/speedy.f90/master/LICENSE |
| ICTP 官方页 | — | — | http://users.ictp.it/~kucharsk/speedy-net.html |

## 9. 未确认项（SPEEDY Fortran）
- ICTP **原始** Fortran 源码（v42/v41）**无法直接下载**，页面上只给出邮箱联系方式与物理参数化 tar；本会话**未读到**原始源码。
- 上述所有公式与常数均来自 **samhatfield/speedy.f90 重写版**，与原 ICTP 版是否逐行一致**未确认**。
- source/implicit.f90 / time_stepping.f90 / tendencies.f90 / spectral.f90 正文**未读**（时间积分与谱变换细节未确认）。
- 短波/长波辐射的具体公式（shortwave_radiation.f90 / longwave_radiation.f90 / mod_radcon.f90 内部）**未读**，只有反照率/发射率常数。
- 性能实测数字、spin-up 建议**未找到**。
- speedy_ver41.5_phys_par.tar 内容**未能解析**（web_fetch 不支持 application/x-tar）。

---

# 模型 2b：SpeedyWeather.jl

## 1. 项目名+主链接+语言+许可证+维护状态
- **项目名**：SpeedyWeather.jl（SPEEDY 的 Julia 重写/扩展）
- **主链接**：https://github.com/SpeedyWeather/SpeedyWeather.jl
- **文档**：https://speedyweather.github.io/SpeedyWeatherDocumentation/ （本会话抓取 200，返回自动跳转 ./stable/ 的重定向页）
- **语言**：Julia（>= 1.10；docs/src/installation.md 原文："SpeedyWeather.jl requires Julia v1.10 or later. The package is tested on Julia 1.10, 1.11 and 1.12."）。仓库为 monorepo：顶层含 SpeedyWeather/、SpeedyTransforms/、RingGrids/、LowerTriangularArrays/、SpeedyWeatherInternals/（安装：Pkg.add(url=..., rev="main", subdir="SpeedyWeather")，docs/src/installation.md）。
- **版本**：SpeedyWeather/Project.toml → version = "0.22.1+DEV"。
- **许可证**：**EUPL v1.2**（European Union Public Licence）。LICENSE 首行原文："Copyright (c) 2020 Milan Kloewer for SpeedyWeather.jl / Copyright (c) 2021 The SpeedyWeather.jl Contributors for SpeedyWeather.jl / Copyright (c) 2022 Fred Kucharski and Franco Molteni for SPEEDY parametrization schemes" + "EUROPEAN UNION PUBLIC LICENCE v. 1.2"（文件 13918 字符，https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/LICENSE ）。
- **维护状态**：活跃（main 分支 HEAD sha 1ef2a0e2c5a41623f22dd97fa064fb8b4c8f2a25；有 CHANGELOG.md 54 381 字符、多套 CI workflow、benchmark 目录、docs/dev/2026-08 下的开发记录）。文件清单接口：https://ungh.cc/repos/SpeedyWeather/SpeedyWeather.jl/files/main 。
- **依赖**：SpeedyWeather/Project.toml 的 [deps] 含 AbstractFFTs, Adapt, AssociatedLegendrePolynomials, BitInformation, CodecZlib, ComponentArrays, Dates, DocStringExtensions, DomainSets, FFTW, FastGaussQuadrature, GPUArrays, GenericFFT, **JLD2**, KernelAbstractions, LinearAlgebra, LowerTriangularArrays, **NCDatasets**, Primes, Printf, ProgressMeter, Random, ReactantCore, RingGrids, SpeedyTransforms, SpeedyWeatherInternals, Statistics, StyledStrings, TOML；[weakdeps] 含 Browzarr, Enzyme 等（GPU/可视化/可微分的扩展）。
- **离线**：纯 Julia 计算本身离线；但地形、海陆掩膜、SST 气候态等输入数据经 Julia Artifacts 从 **SpeedyWeatherAssets.jl** 按需下载（docs/src/input_data.md："we also use SpeedyWeatherAssets.jl to handle different versions of input data with Julia's Artifacts system which will automatically download such data on-demand"）。**首次运行需要网络。**

## 2. 算的是什么（输出场）
谱（球谐）动力核心 + 物理参数化的全球大气模式。4 个模型层次：BarotropicModel、ShallowWaterModel、PrimitiveDryModel、PrimitiveWetModel（SpeedyWeather/src/models/abstract_models.jl:5-9）。默认 PrimitiveWetModel 输出 u、v、涡度、散度、温度、比湿、地面气压、海平面气压、η（界面位移）+ 降水 + 辐射 + 地表通量 + 陆面/海洋量（第 6 节）。

## 3. 方程与近似（实际公式 + 参数值）

### 3.1 水平截断 / 网格 / 层数默认值
文件：SpeedyWeather/src/dynamics/spectral_grid.jl
URL：https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/dynamics/spectral_grid.jl

    const DEFAULT_GRID       = OctahedralGaussianGrid   # 行9
    const DEFAULT_TRUNCATION = 32                       # 行10
    const DEFAULT_NLAYERS    = 8                        # 行11
    truncation::Int = DEFAULT_TRUNCATION                # 行171
    Grid::Type{<:AbstractGrid} = DEFAULT_GRID           # 行173
    nlayers::Int = DEFAULT_NLAYERS                      # 行175
    function SpectralGrid(...; truncation::Int = DEFAULT_TRUNCATION, trunc = nothing, ...)  # 行168-200
      trunc !== nothing && (truncation = trunc + 1)     # 行184-186 旧 trunc(T31) → 新 truncation=32

**默认 truncation = 32（1-based 最大球谐阶数，等价旧记法 T31），默认 8 层。** 文档明确写："The spectral resolution is T31 ... In the vertical 8 levels are used, using Sigma coordinates"，且 "This spectral resolution is combined with an octahedral Gaussian grid of 3168 grid points. This grid has 48 latitude rings ... up to 96 longitude points around the Equator ... on average about 400km"（docs/src/how_to_run_speedy.md）。常用值 "32, 43, 64, 86, 128, 171, ..."（同页）。

### 3.2 σ 层与精确 σ 值
文件：SpeedyWeather/src/dynamics/vertical_coordinates.jl
URL：https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/dynamics/vertical_coordinates.jl

    σ_full[k] = (σ_half[k] + σ_half[k+1]) / 2          # 行48
    σ_thickness[k] = σ_half[k+1] - σ_half[k]           # 行49
    function sigma_half_spacing(nlayers::Integer, profile = z -> z)   # 行83
        σ_half = profile.(collect(range(0, 1, nlayers + 1)))          # 行84
        σ_half[1] = 0 ; return σ_half                                 # 行85-86
    end
    FriersonSigmaCoordinates(SG) = SigmaCoordinates(SG, frierson_profile)   # 行137-138
    frierson_profile(σ) = exp(-5 * (0.05 * (1 - σ) + 0.95 * (1 - σ)^3))     # 行141

**默认（profile = z->z，8 层）σ_half = 0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0 → σ_full = 0.0625, 0.1875, 0.3125, 0.4375, 0.5625, 0.6875, 0.8125, 0.9375。** 另有 FriersonSigmaCoordinates（非默认）使用上式非均匀 profile，以及混合 σ-p 坐标 SigmaPressureCoordinates（行 152-207，reference_pressure = 1.0e5，transition(σ) 控制 A/B 分配）。模型默认构造：Geometry(SG; vertical_coordinates = SigmaCoordinates(SG))（SpeedyWeather/src/dynamics/geometry.jl:103）。

### 3.3 预报变量清单
- 通用：vorticity（SpectralXYZT, 1/s，SpeedyWeather/src/models/barotropic.jl:73）、clock、scale（barotropic.jl:71-72）。
- PrimitiveDry 追加（SpeedyWeather/src/models/primitive_dry.jl:145-159）：divergence(SpectralXYZT, 1/s)、temperature(SpectralXYZT, K)、pressure(SpectralXYT, **log(Pa)** = 地面气压对数)，以及各自的 grid 镜像（GridVariable(:divergence/:temperature/:pressure)）、ParameterizationVariable(:surface_pressure, Grid2D(), "Pa")。
- PrimitiveWet 再追加（SpeedyWeather/src/models/primitive_wet.jl:156-159）：PrognosticVariable(:humidity, SpectralXYZT(ps), "Specific humidity", "kg/kg") + GridVariable(:humidity, ...) + 谱/网格倾向 + uq/vq 中间量。
- 海洋：PrognosticVariable(:sea_surface_temperature, GridXYT(), namespace=:ocean, "K")（SpeedyWeather/src/parameterizations/ocean.jl:4、292）。
- 陆地：LandModel 的土壤温度/湿度等（SpeedyWeather/src/parameterizations/land/*.jl，本次未逐行读）。
- 变量系统：SpeedyWeather/src/variables/variables.jl（Variables 结构行 68；fuse 分组 slot_map = (vorticity=1:8, divergence=9:16, temperature=17:24, humidity=25:32, pressure=33:33)，行 286）。

### 3.4 物理参数化默认组件与常数
默认组件（PrimitiveWetModel，SpeedyWeather/src/models/primitive_wet.jl:83-107）：
SlabOcean, ThermodynamicSeaIce, LandModel, WhichZenith, OceanLandAlbedo, BoundaryLayer, BulkRichardsonDiffusion, SurfaceMomentumFlux, SurfaceHeatFlux, SurfaceHumidityFlux, ImplicitCondensation, BettsMillerConvection, OneBandShortwave, OneBandLongwave, Leapfrog, ImplicitPrimitiveEquation, HyperDiffusion, CenteredVerticalAdvection, ClipNegatives, NetCDFOutput。

**(a) Betts–Miller 对流**（SpeedyWeather/src/parameterizations/convection.jl）

    @parameterized @kwdef struct BettsMillerConvection{NF}
        time_scale::Second = Hour(4)                     # 行10  = 14400 s
        @param relative_humidity::NF = 0.7               # 行13
    end

核心（行 64-141，注释直接给出 Frierson 2007 公式编号）：

    level_zero_buoyancy = pseudo_adiabat!(...)                                  # 行65, 定义行177
    k ≥ kLZB:  qsat = saturation_humidity(Tref, pₛ*σ[k]) ; qref = qsat*rhbm      # 行68-69
    Pq += (q - qref)*Δσ[k] ;  PT -= (T - Tref)*Δσ[k]                            # 行87-88
    deep_convection    = Pq > 0 && PT > 0                                       # 行92
    shallow_convection = Pq <= 0 && PT > 0                                      # 行93
    深对流:  ΔT = (PT - Pq*Lᵥ/cₚ)/Δσ_lzb ;  Tref -= ΔT                          # 行104-108 (eq 5,6)
    浅对流:  Qref = -Σ qref*Δσ ; fq = 1 - Pq/Qref ; qref *= fq ; Tref -= PT/Δσ_lzb # 行117-125 (eq 11-15)
    倾向:    dT/dt -= (T - Tref)*τ⁻¹ ;  dq/dt -= (q - qref)*τ⁻¹                  # 行132-136
    降水:    rain = max(δq*Δσ[k], 0) 累加；rs = pₛΔt/(g ρ) * deep_convection       # 行139-147

另有 BettsMillerDryConvection（行 262 起）与 ConvectiveHeating（行 391 起）。

**(b) 大尺度凝结**（SpeedyWeather/src/parameterizations/large_scale_condensation.jl）

    @parameterized @kwdef struct ImplicitCondensation{NF}
        @param relative_humidity_threshold::NF = 0.95    # 行8
    end
    δq_cond = sat_humid_k * relative_humidity_threshold - humid[ij,k]     # 行98
    r = condensation.reevaporation * max(dq, 0)                            # 行122
    dqsat_dT = sat_humid_k * rh_threshold * Lᵥ_cₚ / (Rᵥ * T^2)             # 行133

（隐式时间离散求解凝结/蒸发/融雪，行 126-153；另有 time_scale、freezing_threshold、melting_threshold、snow 开关，行 87-88。）

**(c) 垂直扩散**（SpeedyWeather/src/parameterizations/vertical_diffusion.jl）—— 复刻 Frierson 2006 边界层

    @parameterized @kwdef struct BulkRichardsonDiffusion{NF, VectorType}
        von_Karman::NF = 0.4                   # 行6
        @param roughness_length::NF = 3.21e-5  # 行9
        @param critical_Richardson::NF = 10    # 行12
        @param surface_layer_fraction::NF = 0.1# 行15
    end

系数（行 188-224，注释指向 Frierson 2006 eq. 16-20）：

    Ri_N = clamp(Ri[N], 0, Ri_c)
    sqrtC  = (κ/log(Z/z₀)) * (1 - Ri_N/Ri_c)          # 行193 (eq.12-14)
    K0     = κ * sqrt(u²+v²) * sqrtC                  # 行195 (eq.19,20)
    K_k    = K0 * min(z, fb*h)                        # 行200-201
    K_k   *= z < fb*h ? 1 : zfac(z,h,fb)              # 行204；zfac 行218 = z/(fb h)*(1-(z-fb h)/((1-fb)h))²
    K_k   *= Ri[kₕ] <= 0 ? 1 : Rifac(Ri,Ri_c,logZ_z₀)# 行208；Rifac 行228 = 1/(1 + (Ri/Ri_c)*log(z/z₀)/(1-Ri/Ri_c))

Z 用 Z = T₀ * Δp_geopot_full[nlayers] / g（行 155-158）。

**(d) 边界层拖曳与地表通量**
- 拖曳（SpeedyWeather/src/parameterizations/surface_fluxes/boundary_layer.jl）：BulkRichardsonDrag 默认 von_Karman = 0.4、critical_Richardson = 10、drag_min = 1.0e-5（行 133-141）；公式 drag_max = (κ/log(z/z₀))²，C = max(drag_min, drag_max*(1-Ri/Ri_c)²)（行 180-195）；体积理查森数 Ri = ΔΦ₀*(Θ₁-Θ₀)/(Θ₀*Vₛ²)（行 214-217，Frierson 2006 eq.15）。另有 ConstantDrag(drag = 1.0e-3)（行 11-13）与基于 ERA5 符号回归的 NeutralWindSpeed（9 个系数 c1..c9，行 34-68）。
- 粗糙度（SpeedyWeather/src/parameterizations/surface_fluxes/surface_roughness.jl）：ConstantSurfaceRoughness 默认 roughness_length_land = 0.5 m、roughness_length_ocean = 1.0e-4 m。
- 地表条件（.../surface_condition.jl）：SurfaceCondition 默认 wind_slowdown = 0.95、gust_speed = 1 m/s（行 8-14）；V₀ = sqrt(uₛ² + vₛ² + gust²)（行 41，SPEEDY 文档 eq.50）；Tᵥ *= σ^(-κ) 干绝热下推至地表，ρ = pₛ/(R_dry*Tᵥ)（行 55-57，eq.49-51）。
- 动量通量（.../momentum.jl）：wind_slowdown = 0.95、drag_ocean = 1.8e-3、drag_land = 2.4e-3（行 12-21）；flux_u_upward = -ρ*C_D*V₀*f*u[surface]（行 63-64，SPEEDY 文档 eq.52/53）。
- 感热通量（.../heat.jl）：SurfaceOceanHeatFlux 默认 drag = 0.9e-3、sea_ice_insulation = 0.01（行 58-62）；flux = ρ*C_H*V₀*(SST - T_air)（行 93，SPEEDY 文档 eq.54/56）；海冰按 /(1 + ice_conc/0.01) 抑制（行 96）。
- 水汽通量（.../humidity.jl）：SurfaceOceanHumidityFlux 默认 drag = 0.9e-3、sea_ice_insulation = 0.01（行 58-62）；用 saturation_humidity(SST, pₛ) 减去最低层比湿（行 77-94，SPEEDY 文档 eq.55/57）。

**(e) 辐射**
- 短波默认 OneBandShortwave，由三部分组成（SpeedyWeather/src/parameterizations/radiation/shortwave_radiation.jl:81-88）：DiagnosticClouds + BackgroundShortwaveTransmissivity + OneBandShortwaveRadiativeTransfer。
  BackgroundShortwaveTransmissivity（.../shortwave_transmissivity.jl:42-71）：zenith_amplitude=1、zenith_exponent=2、absorptivity_dry_air=0.03135（per 10⁵ Pa）、absorptivity_aerosol=0.03135、absorptivity_water_vapor=75（per kg/kg per 10⁵ Pa）、absorptivity_cloud_base=10、absorptivity_cloud_limit=0.14。
  ConstantShortwaveTransmissivity 默认 transmissivity = 0.85（行 11），τ = -log(t)，t(k) = exp(-τ*Δσₖ)（行 27-33）。
  TransparentShortwave：D = S₀*cos_zenith，地表短波上行 = albedo*D（行 39-55）。
- 长波默认 OneBandLongwave（.../radiation/longwave_radiation.jl:160-186），其辐射传输 OneBandLongwaveRadiativeTransfer 默认 emissivity_ocean = 0.98、emissivity_land = 0.98（行 208-212）；顶层边界 D = 0（行 269）。
  可选：UniformCooling（Pauluis & Garner 2006）：time_scale = Hour(16)（≈ -1.5 K/day）、temp_min = 207.5 K、temp_stratosphere = 200 K、time_scale_stratosphere = Day(5)（行 15-27；公式行 42-47）。
  JeevanjeeRadiation：α = 0.025 W/m²/K²、emissivity_atmosphere = 0、emissivity_ocean = 0.65、emissivity_land = 0.6、temp_tropopause = 200 K、time_scale = Hour(24)（行 68-86）。
  FriersonLongwaveTransmissivity：τ₀_equator = 6、τ₀_pole = 1.5、fₗ = 0.1（.../longwave_transmissivity.jl:29-40）；ConstantLongwaveTransmissivity 默认 transmissivity = 0.6（行 8）。

### 3.5 Slab（混合层）海洋——**有**
文件：SpeedyWeather/src/parameterizations/ocean.jl
URL：https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/ocean.jl

    @parameterized @kwdef mutable struct SlabOcean{NF} <: AbstractOcean       # 行264
        specific_heat_capacity::NF = 4184        # 行266  [J/kg/K]
        @param mixed_layer_depth::NF = 50        # 行269  [m]
        density::NF = 1000                       # 行272  [kg/m³]
        mask::Bool = true ; land_temperature::NF = 285                          # 行275,278
        heat_capacity_mixed_layer::NF = specific_heat_capacity*mixed_layer_depth*density  # 行281
    end

→ **默认混合层热容 C₀ = 4184 × 50 × 1000 = 2.092e8 J·K⁻¹·m⁻²**。
演化方程（slab_ocean_kernel!，行 360-366；注释指明 "Frierson et al. 2006, eq (1)"）：

    dsst[ij] = C₀⁻¹ * (Rsd - Rsu - Rlu + Rld - Lᵥ*H - S)

（Rsd/Rsu/Rld/Rlu = 地表短波下行/上行、长波下行/上行 [W/m²]；H = 水汽通量 [kg/m²/s]；S = 感热通量 [W/m²]；Lᵥ 为凝结潜热。）
初值来自季节 SST 气候态 SeasonalOceanClimatology，陆地点置 land_temperature，并钳制到海冰冻结温度（行 312-332）。**它是 PrimitiveWetModel 的默认 ocean 组件**（primitive_wet.jl:83）。其他选项：PrescribedOcean、SeasonalOceanClimatology、ConstantOceanClimatology、AquaPlanet(temperature_equator=302, temperature_poles=273)（行 20/33/155/219）。

### 3.6 默认时间步
文件：SpeedyWeather/src/time_stepping/steppers/leapfrog.jl
URL：https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/time_stepping/steppers/leapfrog.jl

    Δt_at_T32::S                                     # 行8-9：注释为「Time step for T32, scale linearly to spectral resolution truncation」
    function Leapfrog(spectral_grid; Δt_at_T32 = Minute(40),            # 行177  ← 40 min @ T32
                      adjust_with_output = true,
                      robert_filter = 0.1, williams_filter = 0.53)      # 行178-180

缩放（SpeedyWeather/src/time_stepping/steppers/general.jl:12-45）：

    resolution_factor = DEFAULT_TRUNCATION / truncation                 # 行19
    radius_factor     = radius / DEFAULT_RADIUS                         # 行20
    Δt_scaled = Δt_at_T32 * resolution_factor * radius_factor           # 行21
    若 adjust_with_output: 取 interval 的因数中最接近者为步长                 # 行23-39

→ **T32 默认 Δt = 2400 s**（与 benchmark JSON 中 dt=2400 一致）。前置 1 步 Euler（Δt/2）、第 2 步蛙跳 Δt、之后每步 2Δt（time_step，行 225-235）。文档 docs/src/how_to_run_speedy.md 写 "at a spectral resolution of T32 it would use 30min steps"，与源码 Minute(40) 不一致 → 视为文档过时，以源码 2400 s 为准。

### 3.7 Spin-up
- spin_up_steps(::AbstractLeapfrog) = 1（leapfrog.jl:204，1 步 Euler 前推，不计入 clock/output）；spin_up_steps(::AbstractTimeStepper) = 0（steppers/general.jl:4）。
- **气候 spin-up 年数的官方建议：未找到。**

## 4. 是否需要全局迭代
- 时间积分：蛙跳 + Robert(0.1)/Williams(0.53) 滤波（leapfrog.jl:260-270，注释 "Robert time filter to compress computational mode, Williams filter for 3rd order accuracy, see Williams (2009), Eq. 7-9"）。
- 每步一次**隐式线性求解** ImplicitPrimitiveEquation（primitive_wet.jl:106，源文件 src/time_stepping/implicit/implicit_primitive_equations.jl，本次未逐行读），不是迭代。
- 参数化全部逐列局地（@propagate_inbounds parameterization!(ij, vars, scheme, model) 逐格点内核）。
- 结论：**无全局迭代求解**；有谱变换全局通信与每步一次隐式线性求解。

## 5. 时间复杂度或实测性能（有实测数字）
来源：SpeedyWeather/benchmark/README.md 与 SpeedyWeather/benchmark/assets/benchmark_results.json
URL：https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/benchmark/README.md
https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/benchmark/assets/benchmark_results.json

度量：**SYPD = simulated years per wallclock day**；基准不含初始化、不含输出。README 明示 "timings that change by ±50% are not uncommon"。默认 NF=Float32、T32、L8、OctahedralGaussianGrid。

PrimitiveWet 分辨率扫描（表头：T | L | Transform | cpu-arm | cpu-x86 | gpu-nvidia）：

| T | L | Transform | cpu-arm | cpu-x86 | gpu-nvidia |
|---|---|---|---|---|---|
| 32 | 8 | LT+FFT | 1400 | 856 | 5879 |
| 32 | 8 | MT | 757 | 107 | 5730 |
| 43 | 8 | LT+FFT | 564 | 370 | 3779 |
| 64 | 8 | LT+FFT | 147 | 107 | 1188 |
| 64 | 8 | MT | 48 | 3.7 | 1186 |
| 86 | 8 | LT+FFT | 57 | 39 | 659 |
| 86 | 16 | LT+FFT | 51 | 49 | 566 |
| 86 | 24 | LT+FFT | 48 | 38 | 532 |
| 128 | 8 | LT+FFT | 15 | 11 | 264 |
| 128 | 16 | LT+FFT | 21 | 15 | 236 |
| 171 | 8 | LT+FFT | 5.5 | 3.9 | 138 |
| 256 | 8 | LT+FFT | 1.4 | 1.0 | 53 |
| 256 | 24 | LT+FFT | 1.5 | 1.0 | 38 |

（MT = single matrix transform，与 LT+FFT 并列；完整 24 行表见 README。）
机器信息（benchmark_results.json 的 cpu-arm.meta）：**Apple M3（8 核）、macOS arm64、Julia 1.12.6、1 线程、SpeedyWeather v0.21.1+DEV、采集时间 "Tue, 21 Jul 2026 17:31:54"**。JSON 同时给出每组 (T,L) 的 dt：T32→2400 s，T43→1800，T64→1200，T86→900，T128→600，T171→450，T256→300，以及 memory（如 T32L8 = 6 220 524 B，T256L8 = 343 691 756 B）、nlat（48/64/96/128/192/256/384）。

## 6. 输出格式
- **NetCDF 默认**：NetCDFOutput（SpeedyWeather/src/output/writers/netcdf_output.jl；常量在 SpeedyWeather/src/output/writers/general.jl）：
  DEFAULT_OUTPUT_NF = Float32、DEFAULT_OUTPUT_INTERVAL = Hour(6)、DEFAULT_MISSING_VALUE = NaN、DEFAULT_COMPRESSION_LEVEL = 1、DEFAULT_SHUFFLE = false、DEFAULT_KEEPBITS = 15（general.jl 行 5-13）。
  维度 TIME + SPACE（netcdf_output.jl:171-196）；写出变量带 long_name、units、_FillValue（行 227-236）。
  **输出默认关闭**：output.active = false，需 run!(simulation, output=true)（docs/src/output.md）。
- **默认输出变量（PrimitiveWetModel）**：DynamicsOutput()（SpeedyWeather/src/output/variables/dynamics.jl:237-247）= VorticityOutput, ZonalVelocityOutput, MeridionalVelocityOutput, DivergenceOutput, InterfaceDisplacementOutput, SurfacePressureOutput, MeanSeaLevelPressureOutput, TemperatureOutput, HumidityOutput；再叠加 PrecipitationOutput, BoundaryOutput, RadiationOutput, RandomPatternOutput, SurfaceFluxesOutput, LandOutput, OceanOutput, BoundaryLayerOutput（.../output/variables/output_variables.jl:11-23 的 AllOutputVariables()）。
- **JLD2**：JLD2Output（SpeedyWeather/src/output/writers/variables_output.jl:74, 88, 107, 123-160，含 output_jld2!、merge_output）；JLD2 仍是 SpeedyWeather/Project.toml 的正式依赖。
- **其它后端**：ZarrOutput（output/writers/zarr_output.jl，docs/src/zarr_output.md）、HEALPixOutput（output/writers/healpix_output.jl）、ArrayOutput（variables_output.jl:200-262）。
- **GRIB 输出：未找到**（文件清单与本会话读到的源码中无任何 GRIB writer）。

## 7. 可直接借鉴什么
1. **SpectralGrid 一处集中「截断 + 网格 + 层数 + 数值精度 + 架构」**（spectral_grid.jl:22-200），派生网格与谱系数维度全部自动（行 190-200），可直接作为重构模板。
2. **σ 坐标的「函数式 profile」构造**：sigma_half_spacing(nlayers, profile)（vertical_coordinates.jl:83-86）+ frierson_profile(σ) = exp(-5*(0.05(1-σ)+0.95(1-σ)³))（行 141），一行切换均匀/Frierson 层结；另有混合 σ-p 的 A/B 分裂（行 178-200）。
3. **时间步与输出频率自动一致化**：get_Δt_millisec 把 Δt 调成输出 interval 的整数因数（general.jl:12-45），避免时间轴漂移——工程上极实用。
4. **Betts–Miller 的 Frierson 2007 实现（代码注释带公式编号）**：深/浅对流判据（行 92-93）、浅对流 qref 缩放 fq = 1 - Pq/Qref（行 119）可直接对照论文逐式核对。
5. **Frierson 2006 边界层的三项分解**：K = K0 * zmin * zfac(z,h,fb) * Rifac(Ri,Ri_c,log(z/z0))（vertical_diffusion.jl:195-208），把「高度形状函数 + 稳定度衰减」拆开，便于替换。
6. **SlabOcean 的一行能量收支**（ocean.jl:364）与 mixed_layer_depth / specific_heat_capacity / density 三参数化，配合 @parameterized 宏可自动进参数表/反演。
7. **性能基准流水线**：benchmark/benchmark_suite.jl + define_benchmarks.jl + manual_benchmarking.jl + assets/benchmark_results.json + docs/generate_benchmarks_page.jl，可直接照搬做 SYPD 报告。
8. **JLD2 重启/合并输出**（variables_output.jl:123-160）适合中间态 checkpoint，NetCDF 只用于成品。

## 8. 关键文件路径 + 行号 + URL（SpeedyWeather.jl）
| 内容 | 路径 | 行号 | URL |
|---|---|---|---|
| 默认截断/层数/网格 | SpeedyWeather/src/dynamics/spectral_grid.jl | 9-11, 59-62, 168-200 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/dynamics/spectral_grid.jl |
| σ 坐标/精确 σ 值 | SpeedyWeather/src/dynamics/vertical_coordinates.jl | 44-57, 83-100, 135-141 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/dynamics/vertical_coordinates.jl |
| Geometry 默认 | SpeedyWeather/src/dynamics/geometry.jl | 32, 103-114 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/dynamics/geometry.jl |
| 预报变量/默认组件 | SpeedyWeather/src/models/primitive_wet.jl | 59-115, 147-166 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/models/primitive_wet.jl |
| 干模型预报量 | SpeedyWeather/src/models/primitive_dry.jl | 138-160 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/models/primitive_dry.jl |
| 涡度变量 | SpeedyWeather/src/models/barotropic.jl | 71-83 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/models/barotropic.jl |
| 蛙跳/默认 Δt | SpeedyWeather/src/time_stepping/steppers/leapfrog.jl | 7-25, 160-235 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/time_stepping/steppers/leapfrog.jl |
| Δt 缩放 | SpeedyWeather/src/time_stepping/steppers/general.jl | 1-45 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/time_stepping/steppers/general.jl |
| Betts–Miller | SpeedyWeather/src/parameterizations/convection.jl | 8-14, 33-160 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/convection.jl |
| 大尺度凝结 | SpeedyWeather/src/parameterizations/large_scale_condensation.jl | 6-8, 55-153 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/large_scale_condensation.jl |
| 垂直扩散 | SpeedyWeather/src/parameterizations/vertical_diffusion.jl | 4-31, 132-230 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/vertical_diffusion.jl |
| Slab 海洋 | SpeedyWeather/src/parameterizations/ocean.jl | 257-366 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/ocean.jl |
| 拖曳/边界层 | SpeedyWeather/src/parameterizations/surface_fluxes/boundary_layer.jl | 9-13, 130-197, 203-218 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/surface_fluxes/boundary_layer.jl |
| 动量通量 | SpeedyWeather/src/parameterizations/surface_fluxes/momentum.jl | 10-22, 43-73 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/surface_fluxes/momentum.jl |
| 感热通量 | SpeedyWeather/src/parameterizations/surface_fluxes/heat.jl | 8-11, 53-108 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/surface_fluxes/heat.jl |
| 水汽通量 | SpeedyWeather/src/parameterizations/surface_fluxes/humidity.jl | 53-94 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/surface_fluxes/humidity.jl |
| 地表条件 | SpeedyWeather/src/parameterizations/surface_fluxes/surface_condition.jl | 8-14, 27-58 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/surface_fluxes/surface_condition.jl |
| 粗糙度 | SpeedyWeather/src/parameterizations/surface_fluxes/surface_roughness.jl | 8-13 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/surface_fluxes/surface_roughness.jl |
| 短波 | SpeedyWeather/src/parameterizations/radiation/shortwave_radiation.jl | 59-125 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/radiation/shortwave_radiation.jl |
| 短波透过率常数 | SpeedyWeather/src/parameterizations/radiation/shortwave_transmissivity.jl | 9-11, 42-71 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/radiation/shortwave_transmissivity.jl |
| 长波 | SpeedyWeather/src/parameterizations/radiation/longwave_radiation.jl | 6-27, 53-86, 157-212 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/radiation/longwave_radiation.jl |
| 长波透过率 | SpeedyWeather/src/parameterizations/radiation/longwave_transmissivity.jl | 6-8, 28-64 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/parameterizations/radiation/longwave_transmissivity.jl |
| 输出常量 | SpeedyWeather/src/output/writers/general.jl | 4-13 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/output/writers/general.jl |
| NetCDF 输出 | SpeedyWeather/src/output/writers/netcdf_output.jl | 6-116, 145-236 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/output/writers/netcdf_output.jl |
| JLD2 输出 | SpeedyWeather/src/output/writers/variables_output.jl | 74-160 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/output/writers/variables_output.jl |
| 默认输出变量表 | SpeedyWeather/src/output/variables/dynamics.jl 与 output_variables.jl | 237-247 / 11-23 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/output/variables/dynamics.jl |
| 变量系统 | SpeedyWeather/src/variables/variables.jl | 68, 286 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/src/variables/variables.jl |
| 依赖/版本 | SpeedyWeather/Project.toml | 全文 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/Project.toml |
| 许可证 | LICENSE | 全文 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/LICENSE |
| 基准结果 | SpeedyWeather/benchmark/README.md 与 benchmark/assets/benchmark_results.json | 全文 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/SpeedyWeather/benchmark/README.md |
| 运行/默认说明 | docs/src/how_to_run_speedy.md | 全文 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/docs/src/how_to_run_speedy.md |
| 输出/输入数据/安装 | docs/src/output.md ；input_data.md ；installation.md | 全文 | https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/main/docs/src/output.md |

## 9. 未确认项（SpeedyWeather.jl）
- ImplicitPrimitiveEquation / implicit_primitive_equations.jl 正文**未读** → 隐式求解器的具体矩阵形式未确认。
- DiagnosticClouds 的云量公式与参数（.../radiation/clouds.jl）**未读**。
- 陆地模型（parameterizations/land/*.jl）的土壤层数、热容、雪/径流参数**未读**。
- HyperDiffusion、CenteredVerticalAdvection、ClipNegatives 的默认系数**未找到**（未读文件）。
- PrimitiveWetModel 的**官方 spin-up 建议年数未找到**。
- **GRIB 输出未找到**（未见任何 GRIB writer；输出后端只有 NetCDF / JLD2 / Zarr / HEALPix / Array）。
- 文档中「T32 用 30 min 步长」与源码 Δt_at_T32 = Minute(40) 矛盾：以源码/benchmark json（2400 s）为准，但**该文档段落是否已过时未由仓库说明确认**。
- GPU 基准的机器细节（gpu-nvidia 具体型号）**未读到**（本次只读了 README 的 cpu-arm 元数据与总表）。

---

# 汇总：三套实现的横向可迁移点
| 维度 | Isca | SPEEDY (Fortran) | SpeedyWeather.jl |
|---|---|---|---|
| 水平离散 | 谱变换（三角截断，默认 T42） | 谱变换（T30） | 谱变换（truncation=32，即 T31） |
| 垂直 | σ（默认 even_sigma，18 层；用例 25 / 40 层） | σ 8 层（0.025…0.95） | σ 8 层均匀（0.0625…0.9375），可选 Frierson 层结 |
| 预报量 | vor, div, T, ln ps（谱）+ tracers | vor, div, t, ln(ps/p0)（谱）+ q | vor, div, T, ln ps, q（谱）+ SST / 土壤 |
| 时间步 | 用例 720 s / 600 s | 2400 s（36 步/天） | T32 默认 2400 s，随截断线性缩放 |
| 时间积分 | 蛙跳 + Robert/RAW + 半隐式 | 蛙跳 + Robert/Williams + 半隐式 | 蛙跳 + Robert/Williams + 隐式 |
| 深对流 | 简化 Betts–Miller（QE, τ=7200 s, RH=0.7-0.8） | 简化 Tiedtke 质量通量（τ=6 h） | 简化 Betts–Miller（τ=14400 s, RH=0.7） |
| 大尺度凝结 | RH 阈值 hc（默认 1.0） | RH(σ)=0.9+0.1(σ²-1)，τ=4 h | RH 阈值 0.95（隐式） |
| Slab 海洋 | mixed_layer.F90，depth=40 m（用例 2.5 m），C=depth·ρc_p | sea_model.f90，40–60 m（cos³φ），4.18e6 J/m³/K | ocean.jl SlabOcean，50 m，4184·50·1000 = 2.092e8 J/K/m² |
| 辐射 | 灰体双流 Frierson（τ_eq 6.0 / τ_pole 1.5，S₀=1360） | 一短波一长波 + 诊断云（albsea 0.07 等） | OneBand SW/LW + 诊断云（干空气吸收 0.03135 等） |
| 输出 | NetCDF diag_table | NetCDF 每步一文件 | NetCDF（默认 6 h，Float32）/ JLD2 / Zarr |
| 许可证 | GPL v3 | MIT 风格，仅限非商业 | EUPL v1.2 |
| 实测性能 | 未找到 | 未找到 | T32L8: 1400 SYPD (Apple M3) / 856 (x86) / 5879 (GPU) |

---

# 未找到/未确认（全局）
1. **Isca 官方实测性能数字（SYPD、CPU 小时、并行加速比）**：未找到。
2. **Isca 官方 spin-up 建议**：未找到（只有 held_suarez 360 天、frierson 3600 天的用例积分长度）。
3. **Isca src/coupler/surface_flux.F90 / vert_turb_driver.F90 / diffusivity.F90 正文**：未读 → 体块通量系数与扩散系数公式未确认。
4. **Isca 有限体积动力核心**：未找到（仅有垂直平流/示踪物平流的有限体积选项）。
5. **Isca bucket_model.F90 / land_model.F90 路径**：在 master 分支不存在（raw 返回 404）；相关实现位置与行号未确认。
6. **ICTP 原始 SPEEDY Fortran 源码**：无法直接下载（官方页要求邮件联系 kucharsk@ictp.it）；本报告中的 SPEEDY Fortran 全部细节来自 modern Fortran 重写版 **samhatfield/speedy.f90**（Zenodo 10.5281/zenodo.5816982），与 ICTP v41/v42 的逐行一致性未确认。
7. **SPEEDY Fortran 的性能实测、spin-up 建议**：未找到。
8. **SPEEDY Fortran 的 implicit.f90 / time_stepping.f90 / tendencies.f90 / 辐射公式正文**：未读。
9. **SpeedyWeather.jl 的隐式求解器、诊断云、陆面、超扩散默认系数**：未读/未找到。
10. **SpeedyWeather.jl 的 GRIB 输出**：未找到。
11. **SpeedyWeather.jl 官方 spin-up 年数建议**：未找到。
12. **GitHub REST API（api.github.com/.../contents/...）**：本会话被限流（403 "API rate limit exceeded"），文件清单改用 https://ungh.cc/repos/OWNER/REPO/files/BRANCH 获取；GitHub HTML tree 页面为 JS 渲染且被 100 000 字符截断，未能用于列目录。
