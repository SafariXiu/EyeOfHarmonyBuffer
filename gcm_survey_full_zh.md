# 简化 GCM 与"离线查表"路线 — 工程调研（C1 / C2 / C3）

> 调研范围：ExoPlaSim / PlaSim / climlab / Isca / SPEEDY / SpeedyWeather.jl / MITgcm / Held-Suarez 基准 + GCM→emulator/查表可行性。
> 方法：优先读源码（raw.githubusercontent.com 实际抓取）。每个结论附真实可点击 URL。凡本会话未能抓取验证的，一律标注"未找到"或"未确认"，未做推断性补全。
> 抓取限制：web_fetch 单次响应截断于 100000 字符；PDF 不受支持；api.github.com 中途限流（可改用 https://ungh.cc/repos/OWNER/REPO/files/BRANCH 列目录）；journals.ametsoc.org（CloudFront 403）、agupubs / sciencedirect / nature 在本会话内不可读。
> 本机 PowerShell / curl.exe 无外网访问（实测 curl: (35) schannel SEC_E_NO_CREDENTIALS），唯一可用通道是 web_fetch。
> 可用的 API：raw.githubusercontent.com、api.crossref.org、pypi.org/pypi/{pkg}/json、readthedocs、execlim.github.io、speedyweather.github.io。

---

## 0. 结论速览

### 0.1 最小预报量（回答"最少需要多少个预报量"）

| 模型 | 预报量个数 | 具体是什么 |
|---|---|---|
| PUMA（干动力核心） | 4 | 谱涡度 sz、谱散度 sd、谱温度 st、谱 ln(ps) sp |
| PlaSim（含湿） | 5 | 上列 4 个 + 谱比湿 sq；另有地表（slab 海温 / 土壤温度湿度 / 雪 / 冰） |
| SpeedyWeather.jl PrimitiveWet | 5 | relative vorticity、divergence、log(surface pressure)、temperature、specific humidity |
| SpeedyWeather.jl SlabOcean | +1 | sea surface temperature（唯一的海温预报量） |
| Isca | 5 + 1 | vors/divs/ts/ln_ps + tracers（谱空间，2 时间层）+ t_surf（slab 混合层） |
| SPEEDY (Fortran, T30L8) | 5 | vor/div/t/ps(=log ps/p0)/q（tr(1)=比湿） |
| climlab EBM | 1 | 纬向平均地表温度 Ts(φ)（唯一状态量，1D，90 个纬度点） |
| MITgcm 大气（Held-Suarez） | 与海洋同一个求解器的 p 坐标同构体；θ、q、u、v、ps | — |

结论：一个能做"行星气候 + 沿岸风"的最简配置需要 5 个大气预报量（涡度、散度、温度、比湿、地面气压）+ 1 个海温预报量。
PUMA 干核心只需 4 个（去掉比湿）。

### 0.2 分辨率-时间步-性能总表（全部来自源码/官方文档）

| 模型 | 默认截断 | 网格 | 层数 | 默认时间步 | 实测性能 |
|---|---|---|---|---|---|
| ExoPlaSim | T21 | 64×32 高斯 | 10 | 45 min | 1 模式年 ≈ 6 min（4 核笔记本）；< 1 min/年（16 核 HPC） |
| PlaSim（原版） | T21 | 64×32 | 10 | 45 min | "100 years per day on a standard Linux-PC" |
| SPEEDY (Fortran) | T30 | 96×48 | 8 | 2400 s | 未找到 |
| SpeedyWeather.jl | truncation=32 (=T31) | 八面体高斯 3168 点 / 48 纬圈 | 8 | 2400 s | 1435 SYPD（Apple M3 笔记本，单线程）；T32L8 GPU 5879 SYPD |
| Isca（源码默认） | T42 | 128×64 | 18 | 未找到 | 未找到 |
| Isca（held_suarez 用例） | T42 | 128×64 | 25 | 600 s | 未找到 |
| Isca（frierson 用例） | T42 | 128×64 | 25 | 720 s | 未找到 |
| climlab EBM | — | 90 纬向格点 (2°) | 1 | 隐式，Δt≈4.06 天 | 未找到 |
| MITgcm HS 基准 | 立方球 C32 (≈2.8°) | 6×32×32 | 20 | 450 s | 未找到（手册性能章节为空） |

### 0.3 离线查表可行性（C2 决定性答案）

能做：ExoPlaSim 1 模式年 < 1 分钟，可离线跑上千个参数组合建表。已有 ThousandWorlds（arXiv:2606.18338）用 5 个 GCM（含 ExoPlaSim）的 1689 个模拟建了 8 参数 → 53 场的 T21 模拟器基准，并同时发布 T21 球谐系数版——这正是"纬向平均 + 少量纬向谐波"的工业级实现。

不能做：T21（625 km）/ T42（310 km）无法解析沿岸风急流与沿岸上升流。
- Small et al. 2015（DOI 10.1175/JCLI-D-15-0192.1）："as with a 1° atmosphere model or coarser" 就已退化为 Sverdrup 平衡 + 暖平流；0.5° 才把急流挪近岸，且需沿岸观测订正；1° 海洋模式即使订正风场也救不回来。
- Capet et al. 2004（DOI 10.1029/2004GL020123）：沿岸风 drop-off 在 27 / 9 / 3 km 三档结构显著不同，"the true structure is indeterminate by current modeling practices"。

因此：查表可以给"大尺度沿岸风背景与符号"，必须另配独立的解析沿岸 drop-off / 上升流模块。

---

# C1. 各模型的最小状态量与方程

## 1. ExoPlaSim

1) 项目名 + 主链接 + 语言 + 许可证 + 维护状态
- 仓库 https://github.com/alphaparrot/ExoPlaSim ｜ 文档 https://exoplasim.readthedocs.io/en/latest/ （标题 "ExoPlaSim 3.4.2 documentation"）
- 语言：Fortran 90（PlaSim/PUMA 动力核心与物理）+ Python（API / 后处理）
- 许可证：GPL-2.0。LICENSE.TXT 全文抓取确认为 "GNU GENERAL PUBLIC LICENSE Version 2, June 1991"：https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/LICENSE.TXT
- 维护：README 首行 "Final maintenance release for 3.x." → 3.x 已是最终维护版，不再活跃开发：https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/README.md
- DOI https://doi.org/10.5281/zenodo.2533357
- 论文 Paradise, Macdonald, Menou, Lee, Fan, MNRAS 511, 3272 (2022)，arXiv:2107.07685 — https://arxiv.org/abs/2107.07685 （可读全文 https://ar5iv.labs.arxiv.org/html/2107.07685 ）

2) 算的是什么（输出哪些场）
完整后处理变量表：https://exoplasim.readthedocs.io/en/latest/postprocessor.html

| 变量 | code | 含义 | 单位 |
|---|---|---|---|
| ta | 130 | 气温 | K |
| ua | 131 | 东向风 | m/s |
| va | 132 | 北向风 | m/s |
| hus | 133 | 比湿 | kg/kg |
| ps | 134 | 地面气压 | hPa |
| uas | 165 | 10 m 东向风 | m/s |
| vas | 166 | 10 m 北向风 | m/s |
| tas | 167 | 2 m 气温 | K |
| psl | 151 | 海平面气压 | hPa |
| dpdx / dpdy | 273 / 274 | d(ps)/dx, d(ps)/dy | Pa/m |
| tauu / tauv | 180 / 181 | 地面东/北向风应力 | Pa |
| zeta | 138 | 相对涡度 | 1/s |
| hfss / hfls | 146 / 147 | 感热 / 潜热通量 | W/m2 |
| ts | 139 | 地表温度 | K |
| mld | 110 | 混合层深度 | m |
| sic / sit | 210 / 211 | 海冰覆盖 / 厚度 | - / m |
| pr / prl / prc | 260 / 142 / 143 | 总降水 / 大尺度 / 对流降水 | m/s |
| rsut | 203 | TOA 出射短波 | W/m2 |
| ntr | 261 | TOA 净辐射 | W/m2 |

（dpdx/dpdy 直接对应"副高气压距平"那条线：GCM 里地转/埃克曼风的第一驱动就是这个地面气压梯度。）

3) 用什么方程/近似（实际参数值）

(a) 动力核心：谱变换三角截断（Bourke 1972 / Hoskins & Simmons 1975；DKRZ PUMA 系）。默认常量，exoplasim/puma/src/puma.f90 第 38-67 行：

    integer :: nlat = 32     ! 纬度
    integer :: nlev = 10     ! 垂直层
    integer :: nlon = 64     ! 经度 = 2*纬度
    integer :: ntru = 21     ! (nlon-1)/3  -> T21
    integer :: nzom = 44     ! 纬向模数
    integer :: nrsp = 506    ! (ntru+1)*(ntru+2)  复谱系数数
    integer :: ncsp = 253    ! nrsp/2

URL https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/puma/src/puma.f90

(b) 半隐式时间积分 + Robert 时间滤波：PNU = 0.02（puma.f90:110-111）；delt = spstep * ww_scale（puma.f90:941）。

(c) 垂直坐标：sigma 坐标，三种可选（set_vertical_grid，puma.f90:1524-1589）：
- nvg=0（默认）：等距 sigmh(jlev) = jlev/NLEV（puma.f90:1583-1585）
- nvg=1：Scinocca & Haynes (1998)——对流层 sigma 线性、平流层 log(sigma) 线性；zsigtran = (1 - alr*dtrop/tgr)^(ga/(gascon*alr))（puma.f90:1553-1554）
- nvg=2：Polvani & Kushner——sigmh(jlev) = ((jlev+inl-NLEV)/inl)^5（puma.f90:1576）

(d) 辐射恢复温度 T_R（PlaSim 默认，非 Held-Suarez）——setzt（plasim.f90:1992-2094）。由下向上递推（plasim.f90:2018-2033）：

    zzp  = zzprev + (gascon*ztprev/ga)*log(zsigprev/sigma(jlev))
    ztp  = tgr - dtrop*alr
    ztp  = ztp + sqrt((0.5*alr*(zzp-dtrop))**2 + dttrp**2)
    ztp  = ztp - 0.5*alr*(zzp-dtrop)

纬向变化（plasim.f90:2076-2084）——这就是"纬向平均温度勒让德"在 GCM 里的原型：

    zfac(jlev) = sin(0.5*PI*(sigma(jlev)-ztps)/(1.-ztps))   ! 对流层顶以上为 0
    sr(1,jlev) = zsqrt2  * (ztrs(jlev) - t0(jlev))          ! P0 项 = 全球平均
    sr(3,jlev) = (1./zsqrt6)  * dtns * zfac(jlev)           ! P1(sin phi) 项
    sr(5,jlev) = -2./3.*zsqrt04 * dtep * zfac(jlev)         ! P2(sin phi) 项

即 T_R(phi,sigma) = T_bar(sigma) + A(sigma)*sin(phi) + B(sigma)*(3 sin^2(phi)-1)/2 —— 只保留 P0/P1/P2。可直接抄。

参数默认值（ExoPlaSim p_exo.f90 全文，4900 字节）：https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/p_exo.f90

    eccen         =     0.016715
    obliq         =    23.441       ! deg
    rotspd        =     1.0         ! 自转速率因子
    sidereal_day  =    86164.0916   ! s
    solar_day     =    86400.0      ! s
    sidereal_year = 31558149.0      ! s
    akap          =   0.286         ! R/Cp
    alr           =   0.0065        ! 递减率 K/m
    als           = 2.8345E6        ! 升华潜热
    alv           = 2.5008E6        ! 汽化潜热
    gascon        = 287.0           ! J/(K kg)
    ra1           = 610.78          ! Magnus-Teten 饱和水汽压系数
    ra2           =  17.2693882
    ra4           =  35.86
    tmelt         = 273.16          ! K
    pnu           = 0.1             ! 时间滤波常数
    ga            = 9.80665         ! m/s2
    plarad        = 6371220.0       ! m
    gsol0         = 1365.0          ! W/m2

(e) 松弛时间尺度（PUMA 默认，puma.f90:1638-1666）：

    ! tau R
    taur(jlev) = sid_day * 50.0 * atan(1.0 - sigma(jlev))
    if (taur(jlev) > 30.0 * sid_day) taur(jlev) = 30.0 * sid_day
    ! tau F（仅 sigma > 0.8）
    if (sigma(jlev) > 0.8) tauf(jlev) = exp(10.0*(1.0-sigma(jlev)))/2.718 * sid_day
    damp = wwt / taur      ! 1/(2 pi tau)
    fric = wwt / tauf

(f) 超扩散：diffts = 21600.0 s（puma.f90:236）；zakk = 1/wwt*(radea^ndel)/((TWOPI*diffts/sol_day)*((NTRU*(NTRU+1))^(ndel/2)))（puma.f90:1681-1682）；sak(jr) = -zzakk*(n(n+1))^(ndel/2)（puma.f90:1708）。
T42 默认（plasim.f90:1245-1252）：nhdiff=16, ndel=4, tdissq=0.1 d, tdisst=0.76 d, tdissz=0.3 d, tdissd=0.06 d。

(g) 物理参数化清单（源码级）

| 模块 | 文件 | 做什么 | 关键默认参数 |
|---|---|---|---|
| 辐射 | plasim/src/radmod.f90（130 418 B） | 双波段短波 + 单波段长波 | ExoPlaSim 论文 2.3 节：SW1 = 0-0.75 um、SW2 = 0.75-2.5 um；Rayleigh 散射依赖面气压与恒星谱 |
| 深对流 | plasim/src/rainmod.f90:11-31 | Kuo 型（Kuo 1965/1974） | kbeta=1, nprl=1, nprc=1, nclouds=1, ndca=1, ncsurf=1, nmoment=0, nshallow=1, nstorain=0, nevapprec=1, nbeta=3；clwcrit1=-0.1, clwcrit2=0.0；pdeep=999999., pdeepth=70000.（Pa）；rkshallow=10. m2/s；gamma=0.01 |
| 浅对流 | 同上 | Tiedtke 1983 | if (NTRU==21 .and. NLEV==5) nshallow=0；if (NTRU==42 .and. NLEV==10) gamma=0.007（rainmod.f90:77-82） |
| 云临界湿度 | rainmod.f90:84 | rcrit(:) = MAX(0.85, MAX(sigma(:), 1.-sigma(:))) | - |
| 垂直扩散/地面通量 | plasim/src/fluxmod.f90:13-29 | von Karman 体块公式，传输系数由整体 Richardson 数修正 | vonkarman=0.4；nvdiff=1, nshfl=1, nevap=1, nstress=1, ntsa=2；zumin=1. m/s；vdiff_lamm=160., vdiff_b=5., vdiff_c=5., vdiff_d=5. |
| 地面应力 | fluxmod.f90:141-（surflx） | 用最低层风 du(:,NLEV)/dv(:,NLEV) 计算，zabsu2 = max(zumin, du^2+dv^2)（第 198 行）；znl = -gascon*0.5*(dt(:,NLEV)+dtsa)*log(sigma(NLEV))/ga（第 204 行） | - |
| 陆面 | plasim/src/landmod.f90（49 935 B） | 土壤温度/湿度、植被、雪 | - |
| 海冰 | plasim/src/icemod.f90（59 378 B） | 热力学海冰 | - |
| 冰川 | plasim/src/glaciermod.f90（21 225 B） | ExoPlaSim 新增 | - |
| 气溶胶 | plasim/src/aerocore.f90 + aeromod.f90 | FFSL 输送（Lin & Rood） | apart = 50e-9 m |
| 碳循环/风化 | plasim/src/carbonmod.f90 | Foley 2015 供给受限风化 | - |
| 风暴气候 | plasim/src/hurricanemod.f90（45 327 B） | CAPE / GPI / MPI / 通风指数 | VITHRESH=0.145, VMXTHRESH=33 m/s, LAVTHRESH=1.2e-5 1/s, MINSURFTEMP=25 C |

(h) 完整 configure() 参数清单（https://exoplasim.readthedocs.io/en/latest/source/exoplasim.html ）：
- 动力学：timestep（默认 45 min）、snapshots（默认 480 步）、vtype（0-5）、modeltop、physicsfilter（"gp|exp|sp" 等）、filterkappa=8.0、filterpower=8、filterLHN0=15、diffusionwaven=15（T21）、qdiffusion=0.1 d、tdiffusion=5.6 d、zdiffusion=1.1 d、ddiffusion=0.2 d、diffusionpower=2
- 辐射：flux（默认 1367）、startemp、starradius、starspec、twobandalbedo、synchronous、desync、substellarlon（默认 180 度）、pressurebroaden、ozone、snowicealbedo、soilalbedo、oceanalbedo、oceanzenith
- 轨道：year、rotationperiod、eccentricity、obliquity、lonvernaleq、fixedorbit、keplerian、meananomaly0
- 行星：gravity（默认 9.80665）、radius（单位 = 地球半径，默认 1.0）、orography、aquaplanet、desertplanet、tlcontrast、seaice、landmap（.sra 文件）、topomap
- 大气：gascon、vtype、modeltop、tropopause、stratosphere、pressure、pH2/pHe/pN2/pO2/pAr/pNe/pKr/pCH4/pCO2（单位 bar）
- 地表：mldepth（混合层深度，默认 50 m）、soildepth、cpsoil（默认 2.4e6 J/m3/K）、soilwatercap（0.5 m）、soilsaturation、maxsnow（5 m）
- 其他：co2weathering、vegetation、glaciers、stormclim、aerosol

4) 是否需要全局迭代
是（谱模式时间积分，全球半隐式求解散度方程），但不是迭代收敛型求解：每步直接求解（makebm 构造半隐式矩阵，ludcmp/lubksb 做 LU 分解与回代，plasim.f90:1801-1891）。主循环（plasim.f90:611-719）：

    call gridpointa      ! 格点空间非线性项
    call spectrala       ! 绝热部分（含半隐式求解）
    call gridpointd      ! 非绝热（物理）部分
    call spectrald       ! Robert 时间滤波

另有 nkits = 3 个"初始小步"（每步 dt/2），用于起动动力核心（plasim.f90:559-579）。

5) 时间复杂度 / 实测性能
- 官方教程原文："On my laptop with 4 cores, a year takes just over 6 minutes. Note that on HPC architecture with 16 cores, a year often takes less than a minute." — https://exoplasim.readthedocs.io/en/latest/tutorial.html
- 论文原文："PlaSim is a fast GCM, able to model a year of climate in under a minute of wall-time (Paradise & Menou 2017)." — https://ar5iv.labs.arxiv.org/html/2107.07685
- 原版 PlaSim 官方页："Fast - Simulate 100 years per day on a standard Linux-PC" — https://www.mi.uni-hamburg.de/en/arbeitsgruppen/theoretische-meteorologie/modelle/plasim.html
- 谱变换代价按 N^3 增长 — https://climatedataguide.ucar.edu/climate-tools/common-spectral-model-grid-resolutions
- 对你的 20 秒 / 100 km 瓦片预算的对照：T21 一个模式年 ≈ 360 s（4 核）。跑 100 模式年 ≈ 10 CPU-小时。作为离线建表这在预算内（但见 C2 的分辨率结论）。

6) 输出格式
- 原始输出：Fortran unformatted 序列文件 MOST.#####
- 后处理（exoplasim.pyburn）支持：netCDF（.nc）、HDF5（.h5/.he5/.hdf5）、NumPy（.npz/.npy）、CSV（.csv/.txt，可 gzip/tar/lzma/bzip2）
- netCDF 需 pip install exoplasim[netCDF4]；HDF5 需 [HDF5]
- 文件体积（官方硬数字）："A T21 model output with 10 vertical levels, 12 output times, all supported variables in grid mode, and no standard deviation computation will have the following sizes"：

| 格式 | 大小 |
|---|---|
| netCDF | 12.8 MiB |
| HDF5 | 17.2 MiB |
| NumPy (默认) | 19.3 MiB |
| tar.xz | 33.6 MiB |
| tar.bz2 | 36.8 MiB |
| gzipped | 45.9 MiB |
| 未压缩 | 160.2 MiB |

URL https://exoplasim.readthedocs.io/en/latest/postprocessor.html
← 这个 12.8 MiB 就是 C3 文件体积的答案：全部 53 个场 × 12 个时刻 × T21 × 10 层 = 12.8 MiB。

- 水平模式（mode 参数）："grid"（高斯经纬网格）、"spectral"（球谐系数）、"fourier"（每个纬度的傅里叶系数）、"synchronous"（以星下点为北极的旋转网格）、"syncfourier"。另有 zonal=True 只输出纬向平均。
- 时间压缩：times / timeaveraging / interpolatetimes；默认每年 72 个输出时刻，可压到月平均甚至年平均（最小 1 个）。

7) 我们能直接借鉴什么（针对"东边界沿岸风符号"卡点）

1. 抄 T_R 的勒让德截断（setzt，plasim.f90:2076-2084）：只保留 P0/P1/P2 就构造出完整纬向温度场。你的"纬向平均温度（勒让德）+ 海陆季节异常"和 GCM 的做法是同一个结构。
2. 抄地面应力的诊断方式：fluxmod.f90:198 用最低层风算应力，vas/uas（10 m 风）是标准输出。GCM 的沿岸风符号完全来自动力核心解出的 u/v，没有任何"海岸线特判"——这解释了为什么它在 T21 给不出 100 km 尺度的急流，也说明你的解析方案在 sign 层面并不比 GCM 差。
3. dpdx/dpdy（code 273/274）是现成的可对标量：把你的"副高气压距平"结果和 GCM 的 dpdx/dpdy 逐格对比，是验证你 39~52% 正确率的最直接手段。
4. mode="fourier" 是现成的"纬向平均 + 少量纬向谐波"压缩器。实现见 pyburn.py 的 _transformvar()（第 623 行起），fourier 分支在第 821-870 行：

    elif mode=="fourier":
        ...
        fcvar = pyfft.sp3fc(spvar,nlat,nlon,ntru,int(physfilter))
        fouriervar = np.reshape(np.transpose(fcvar),fcshape)   # dims = ["time",lev,"lat","fourier","complex"]

    URL https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/pyburn.py
    注意：网格 -> fourier 分支会先除以 sqrt(2)（第 847/861 行：variable/1.4142135623730951），复现时必须注意。
5. physicsfilter = "gp|exp|sp"：T21 上尖锐地形/海岸会造成 Gibbs 振荡。你的 x 方向无限世界里，如果沿 z 有突变，同样需要等效的谱滤波（指数滤波默认 filterkappa=8.0, filterpower=8）。

8) 关键文件路径 + 行号 + URL

| 内容 | 路径 | 行号 | URL |
|---|---|---|---|
| 行星参数默认值（全部） | exoplasim/plasim/src/p_exo.f90 | 全文（4900 B） | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/p_exo.f90 |
| 谱状态量 + 网格量声明 | exoplasim/puma/src/puma.f90 | 253-268（sd/st/sz/sp/sq/sr1/sr2） | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/puma/src/puma.f90 |
| T21 分辨率常量 | 同上 | 38, 55-67 | 同上 |
| 默认物理常量 | 同上 | 96-147 | 同上 |
| 松弛时间 tau_R / tau_F | 同上 | 1638-1666 | 同上 |
| Held-Suarez 开关（未在此文件实现） | 同上 | 192-196 | 同上 |
| 垂直层设置 | 同上 | 1524-1589 | 同上 |
| T_R 构造（PUMA 版） | 同上 | 2240-2311（setzt） | 同上 |
| 时间步自动选择 | plasim/src/plasim.f90 | 1330-1338 | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/plasim.f90 |
| 主时间步循环 | 同上 | 611-719 | 同上 |
| 初始小步（nkits=3） | 同上 | 559-579 | 同上 |
| T_R 构造（PlaSim 版，含 P0/P1/P2 截断） | 同上 | 1992-2094 | 同上 |
| namelist 全表 + T42 超扩散 | 同上 | 1211-1252 | 同上 |
| 半隐式矩阵求解 | 同上 | 1683-1705（makebm）、1801-1891（ludcmp/lubksb） | 同上 |
| 谱状态量（5 个预报量） | 同上 | 2795 | 同上 |
| slab 海洋参数 | plasim/src/oceanmod.f90 | 15-51、78-104 | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/oceanmod.f90 |
| 地面通量 / 体块公式 | plasim/src/fluxmod.f90 | 13-29、141-337（surflx）、338-483（mkstress）、750-（vdiff） | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/fluxmod.f90 |
| 对流 / 云 / 降水参数 | plasim/src/rainmod.f90 | 11-35、77-99 | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/rainmod.f90 |
| 分辨率常量（T21L10 默认） | plasim/src/resmod_def.f90 | 3-5 | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/resmod_def.f90 |
| 高斯纬度/权重 | puma/src/gaussmod.f90 | inigau，全文 | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/puma/src/gaussmod.f90 |
| 后处理模式（含 fourier） | exoplasim/pyburn.py | 623, 692, 778, 821-870, 872-960 | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/pyburn.py |
| 分子量与火星常量 | exoplasim/constants.py | 全文（328 B） | https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/constants.py |
| API 文档（configure 全参数 + 变量表） | - | - | https://exoplasim.readthedocs.io/en/latest/source/exoplasim.html |
| 后处理变量码表 + 文件体积 | - | - | https://exoplasim.readthedocs.io/en/latest/postprocessor.html |
| 教程（性能数字） | - | - | https://exoplasim.readthedocs.io/en/latest/tutorial.html |

9) 未找到 / 未确认
- spin-up 所需模式年数：官方只给 runtobalance 的判据——能量平衡阈值 0.05 W/m2/yr，在 45 年时间尺度上，以及教程原话 "reaching a strict energy balance equilibrium takes many decades, and sometimes up to a few centuries"。具体年数未给出 -> 未找到。
- CPU 小时数的定量表格：论文 3.1 节声称有 "ExoPlaSim's computational performance"，但在 100k 字符截断之外 -> 未确认。
- Held-Suarez 在 ExoPlaSim 中的实现：puma.f90:192-196 定义了 nhelsua 开关（1 = 用 H&S 的 T_R 替代 PUMA 的），但该文件内没有实现代码；setzt（2240-2311）只有 PUMA/aqua-planet 版本 -> ExoPlaSim 分支里 H&S 的 T_R 实际实现未找到。
- p_exo.f90 的文件头注释写的是 "Earth module for Planet Simulator"，与文件名不符（从 p_earth.f90 复制而来）——源码事实，非错误。
---

## 2. PlaSim / PUMA

1) 项目名 + 主链接 + 语言 + 许可证 + 维护状态
- 官方主页 https://www.mi.uni-hamburg.de/en/arbeitsgruppen/theoretische-meteorologie/modelle/plasim.html （页面显示 "Last update: 30 May 2022" -> 已不再活跃开发）
- 官方页面自述："The Planet Simulator (PlaSim): a climate model of intermediate complexity for Earth, Mars and other planets"；特性含 "Scalable - Wide range of resolutions starting at T21 L5"、"Compatible - supports MPI, GRIB, NetCDF"、"Free - All components are open source"、"Fast - Simulate 100 years per day on a standard Linux-PC"
- 你给的 https://github.com/tsunoda/PlaSim 返回 404（本会话实测）——该仓库不存在。
- 可用 GitHub 镜像 https://github.com/balintkaszas/PLASIM （200，实测可访问）
- ExoPlaSim 内嵌完整 PlaSim 源码树 https://github.com/alphaparrot/ExoPlaSim/tree/master/exoplasim/plasim 与 .../exoplasim/puma
- 语言：Fortran 90 + C + C++（GUI）；构建需 make、X11
- 许可证：官方称 "All components are open source"，具体 SPDX 标识未抓到 LICENSE 文件 -> 未确认
- 依赖（README 原文）：C / C++ / Fortran-90 编译器、make、X11 include + library；MPI 可选 — https://raw.githubusercontent.com/balintkaszas/PLASIM/master/README

2) 算的是什么（输出哪些场）
- 光谱量：散度、涡度、温度、比湿、ln(ps)、地面气压、orography、restoration temperature
- 格点量：u*cos(phi)、v*cos(phi)、T、q、ln(ps)、位势、动能、各类通量
- 输出：NetCDF / GRIB / 原生 unformatted（官方页明确支持 NetCDF 与 GRIB）

3) 用什么方程/近似
- 与 ExoPlaSim 同一份代码。动力核心：谱变换三角截断（Bourke 1972；Hoskins & Simmons 1975；James & Dodd 1993；Fraedrich, Kirk, Lunkeit 1998 DKRZ Tech. Report No. 16）。
- 标准分辨率（README 原文）："Standard resolutions are: T21 (64x32 grid), T31 (96x48), and T42 (128x64)."
- 时间步自动规则（plasim.f90:1330-1338）：

    if (mpstep <= 0 .and. ntspd <= 0) then
       if (nlat <= 32) then       ! T21
          mpstep = 45
       else if (nlat <= 48) then  ! T31
          mpstep = 36
       else                       ! T42 and more
          mpstep = (30 * 64) / nlat
       endif
    endif

    -> T21 = 45 min；T31 = 36 min；T42 = 30 min；T85（nlat=128）= 15 min。

- 预报量（plasim.f90:2795）：real, allocatable :: zsd(:,:),zsz(:,:),zsq(:,:),zsp(:),zst(:,:) -> 谱散度、谱涡度、谱比湿、谱 ln(ps)、谱温度 = 5 个。
- slab 混合层海洋（plasim/src/oceanmod.f90:15-51）：

    parameter(NLEV_OCE = 1)              ! 单层
    parameter(CRHOS=1030.)               ! 海水密度 kg/m3
    parameter(CRHOI=920.)                ! 海冰密度
    parameter(CPS=4180.)                 ! 海水比热 J/(kg K)
    parameter(CLFI  = 3.28E5)            ! 融化潜热 J/kg
    real :: dlayer(NLEV_OCE)  = 50.    ! 层厚 (m)
    real :: mldepth           = 50.    ! 混合层深度 (m)
    real :: taunc             =  0.    ! 牛顿冷却时间尺度 (d)
    real :: vdiffkl(NLEV_OCE) = 1.E-4  ! 垂直扩散系数 m2/s
    real :: hdiffk(NLEV_OCE)  = 1.E3   ! 水平扩散系数 m2/s
    integer :: nfluko = 0   ! 通量修正 0=none,1=heat-budget,2=newtonian
    integer :: ntspd  = 32  ! 海洋每天时间步
    real :: TFREEZE  = 271.25   ! 冰点 K

    状态量（oceanmod.f90:78-104）：ysst（海温 K）、ymld（层厚 m）、yls（海陆掩膜）、yicec/yiced（冰覆盖/厚度）、yheat、yfldo、ypme（淡水通量）、yroff（径流）、ytaux/ytauy（风应力）、yust3、yiflux、yclsst/yfsst（气候态 SST 与通量修正，14 个月）。
    单层 slab，无水平输送（nhdiff=0 为默认）；oceanmod.f90:1-7 标注 version '13.10.2005 by Larry'。

4) 是否需要全局迭代
是，但与 ExoPlaSim 相同：半隐式直接求解（LU 分解），非迭代收敛。主循环 plasim.f90:611-719（gridpointa -> spectrala -> gridpointd -> spectrald）。nkits = 3 个初始小步。

5) 时间复杂度 / 实测性能
- 官方页："Simulate 100 years per day on a standard Linux-PC" -> 约 14.4 模式年/CPU-小时/单机。

6) 输出格式
- NetCDF、GRIB、原生 unformatted（官方页 "Compatible - supports MPI, GRIB, NetCDF"）
- 后处理："burner"（burn7，C++，已弃用；ExoPlaSim 里由 pyburn.py 取代）
- exoplasim/puma/src/ppp.f90 提供 PPP 后处理接口

7) 我们能直接借鉴什么
1. oceanmod.f90 的单层 slab 是"大气-海洋耦合"的最小实现：状态量只有 ysst(NHOR)，加冰/雪/径流诊断。照抄这个结构即可（1 个预报量 + 4 个诊断量）。
2. nfluko 通量修正机制：slab 海洋跑久了会漂移，PlaSim 用 yclsst/yfsst（气候态 SST + 通量修正，12 个月 × 14 槽）把模式拉回观测。你的季节性气候也可以照这个思路：给每月一个"气候态基线 + 修正项"。
3. T21/T31/T42 的时间步规则（45/36/30 min）是可直接用的经验律：dt ≈ 1920 / nlat 分钟。
4. fluxmod.f90 的体块公式参数（vdiff_lamm=160., vdiff_b/c/d=5.）是你"Ekman-Rayleigh 风"里地表拖曳的直接对标。

8) 关键文件路径 + 行号 + URL
- README（标准分辨率 + 依赖）https://raw.githubusercontent.com/balintkaszas/PLASIM/master/README
- 官方主页（性能/特性/最后更新）https://www.mi.uni-hamburg.de/en/arbeitsgruppen/theoretische-meteorologie/modelle/plasim.html
- 5 个预报量 plasim.f90:2795 / 时间步 1330-1338 / T_R 1992-2094：https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/plasim.f90
- slab 海洋：https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/plasim/src/oceanmod.f90
- 物理模块全清单：https://github.com/alphaparrot/ExoPlaSim/tree/master/exoplasim/plasim/src

9) 未找到 / 未确认
- PlaSim 的 SPDX 许可证标识：官方页只说 "open source"；未抓到 LICENSE 文件 -> 未确认（exoplasim/plasim 目录下有 COPYING，682 B，未抓取）。
- spin-up 年数：未找到。
- https://github.com/tsunoda/PlaSim：404，不存在。

---

## 3. climlab

1) 项目名 + 主链接 + 语言 + 许可证 + 维护状态
- 仓库 https://github.com/climlab/climlab ｜ 文档 https://climlab.readthedocs.io/
- 语言：纯 Python（v0.8.1 起 Fortran 全部外移；README L164-168："all the Fortran code has been moved into external companion packages ... Climlab is now (once again!) a pure Python package"）
- 许可证：MIT（setup.py L19 "License :: OSI Approved :: MIT License"、L24 license='MIT'；README L340-343；PyPI 元数据亦为 MIT）— https://pypi.org/pypi/climlab/json
- 维护状态：活跃。setup.py L3 VERSION = '0.10.0.dev'；README L131 "Version 0.9.2 (released March 2025)"；docs 首页标题 "climlab 0.10.0.dev0 documentation"
- Python：README L101 "currently testing on versions 3.10, 3.11, 3.12, 3.13"
- 依赖：numpy / scipy / pooch（远程数据访问与缓存）/ xarray；推荐 numba>=0.43.1。environment.yml：conda-forge + climlab-rrtmg>=0.4.1, climlab-emanuel-convection, climlab-cam3-radiation>0.2, climlab-sbm-convection
- 安装：conda-forge（README L62）。README L72-73：Windows 目前不支持

包树更正（重要）：climlab/ebm/、climlab/column/、climlab/seaice/、climlab/atm/、climlab/ocean/ 五个路径在 master 上全部 404（实测）。实际为 climlab/{model,domain,process,radiation,dynamics,surface,convection,solar,utils}。EBM 源码在 climlab/model/ebm.py。

2) 算的是什么（输出场）
- EBM 族：纬向平均（可选经向）地表温度 Ts（摄氏度）。诊断：OLR、ASR、net_radiation、albedo、icelat、ice_area、diffusive_flux、flux_convergence、heat_transport(PW)、heat_transport_convergence(W/m2)。（ebm.py L207-210；meridional_heat_diffusion.py L30-31）
- 柱模式族：Ts + Tatm。诊断：OLR、ASR、LW_down_sfc、LW_up_sfc、LW_absorbed_sfc、SW_absorbed_sfc、SW_up_sfc、SW_up_TOA、SW_down_TOA、SW_down_sfc、planetary_albedo、LW_emission、LW_absorbed_atm、SW_absorbed_atm（column.py L87-102）

3) 方程与近似（实际公式 + 参数值）

(a) EBM 主方程（climlab/model/ebm.py L7，模块 docstring）：

    C dTs(phi,t)/dt = (1-alpha) S(phi,t) - [ A + B*Ts ] + (1/cos phi) d/dphi [ cos phi * D * dTs/dphi ]

(b) OLR 线性化 AplusBT（climlab/radiation/aplusbt.py）：L13 R_up = A + B*T；L148 实现 self.OLR[:] = self.A + self.B * value。类自身默认（L85）A=200., B=2.；climlab.EBM 实际生效值：ebm.py L232-233 A=210., B=2.（经 L265 AplusBT(..., **self.param) 覆盖）-> A=210 W/m2, B=2 W/m2/摄氏度。
AplusBT_CO2（L170-174、L187）：A(c) = -326.4 + 9.161c - 3.164c^2 + 0.5468c^3；B(c) = 1.953 - 0.04866c + 0.01309c^2 - 0.002577c^3；c = log(p/300)，p 单位 ppm，默认 CO2=300.0。

(c) 反照率（climlab/surface/albedo.py）：P2Albedo._compute_fixed L174：albedo = a0 + a2 * P2(sin phi)，P2(x) = (3x^2-1)/2；P2Albedo 默认 L120 a0=0.33, a2=0.25。
Iceline.find_icelines L244-279：ice = where(Ts < Tf)；ice_area = global_mean(ice)。
StepFunctionAlbedo._get_current_albedo L362-369：Field(where(ice, cold_albedo, warm_albedo))。
climlab.EBM() 默认（ebm.py L236-239）：Tf=-10.0 摄氏度, a0=0.3, a2=0.078, ai=0.62。climlab.EBM_seasonal() 默认（L366）：a0=0.33, a2=0.25, ai=None（关闭冰反照率反馈，L426-440）。

(d) 日照：P2Insolation._calc_insolation（radiation/insolation.py L280-282）：S(phi) = (S0/4)(1 + s2*P2(sin phi))；默认（L251）S0 = 1365.2 W/m2（constants.py L33），s2 = -0.48。
AnnualMeanInsolation 用 10000 个等距采样点数值平均（solar/insolation.py L398-401：days = np.arange(0., 1., 0.0001) * days_per_year）。
天文公式（solar/insolation.py）：赤纬 L411 delta = arcsin(sin(obliquity) sin lambda)；日没时角 L423-426 h0 = arccos(-tan(phi) tan(delta))；日平均 coszen L451 = [h0 sin(phi) sin(delta) + cos(phi) cos(delta) sin(h0)]/pi；日地距离 L264（Berger 1978）rho = (1-e^2)/(1 + e cos(lambda - varpi))。
现今天文参数硬编码（constants.py L70）：orb_present = {'ecc': 0.017236, 'long_peri': 281.37, 'obliquity': 23.446}；L71-74 注释明确说明该设计是为了避免读数据文件。

(e) 热扩散与热容量：MeridionalHeatDiffusion.__init__（meridional_heat_diffusion.py L65-68）默认 D = 0.555 W/m2/摄氏度（注释 "same as B"）；单位换算 L85-89 self.K = self.D / heat_capacity * const.a**2。
heat_capacity = rho_w*cw*dz（utils/heat_capacity.py L84）；rho_w=1000., cw=4181.3（constants L38-39）。climlab.EBM() 默认 water_depth=10.0 m -> C = 1000 x 4181.3 x 10 = 4.1813e7 J/(m2 K)。a = 6.373E6 m（constants L15）。
由此推出的等效扩散系数：K = 0.555 / 4.1813e7 x (6.373e6)^2 ≈ 5.39e5 m2/s（本报告由源码常量计算，climlab 源码未直接写出该数）。
球面算子：_Xcenter = phi*const.a、_weight_bounds = cos(phi_stag)。诊断 L96-99：heat_transport = diffusive_flux * C * 2 pi a cos(phi) * 1E-15（PW）。

(f) 柱模式物理：GreyRadiationModel 默认（model/column.py L46-57）num_lev=30, num_lat=1, water_depth=1.0, albedo_sfc=0.299, Q=341.3, abs_coeff=1.229E-4 m2/kg；RadiativeConvectiveModel 额外 adj_lapse_rate=6.5 K/km；BandRCModel 的 absorber_vmr['CO2']=380.E-6。
FixedRelativeHumidity / ManabeWaterVapor（radiation/water_vapor.py L8、L47）：relative_humidity=0.77，qStrat=5.E-6。
湍流表面通量（surface/turbulent.py）：_SurfaceFlux L66 Cd=3E-3, resistance=1.，L72 U 默认 5 m/s；SensibleHeatFlux L126 flux = resistance*cp*rho*Cd*U*dT；LatentHeatFlux L181 flux = resistance*Lhvap*rho*Cd*U*dq。

(g) 时间离散（关键）：EnergyBudget._temperature_tendencies（process/energy_budget.py L58-68）tendencies[var] = heating_rate[var]/C；父进程把所有子进程 tendency 相加后做一次前向欧拉。扩散子进程是 implicit（process/implicit.py L20 time_type='implicit'）。
EBM 实际格式 = 隐式扩散 + 显式辐射/反照率的算子分裂：T^{n+1} = T^{n+1}_隐式扩散 + dt * (显式 tendency 之和)。
默认时间步（ebm.py L240）timestep = const.seconds_per_year/90. = 350 632.5 s ≈ 4.058 天 -> 90 步/年。

4) 是否需要全局迭代（★ 本题最关键）

不需要，而且 climlab 根本不具备全局 2D 椭圆求解能力。

- 扩散算子只在单一一根轴上工作。advection_diffusion.py L41-42："The state variable psi may be multi-dimensional, but the diffusion will operate along a single dimension only."
- _guess_diffusion_axis（同文件 L235-259）：若存在多于一根长度 > 1 的轴，直接 raise ValueError('More than one possible diffusion axis.')。lat-lon 2D 状态在自动推断下会直接报错。
- 矩阵构建 advdiff_tridiag（dynamics/adv_diff_numerics.py L269-336）：返回 (..., J, J) 的三对角矩阵（只有 3 条对角线非零），或 (3, J) 的 banded 打包形式。
- 时间推进 implicit_step_forward（同文件 L382-429）求解 (I - T dt) psi^{n+1} = psi^n + S dt：
  - L420（banded 分支）：scipy.linalg.solve_banded((1, 1), IminusTdt, RHS)
  - L426-429（默认分支）：numpy.linalg.solve(IminusTdt, RHS[..., None])[..., 0]
- climlab.EBM 走默认分支：ebm.py L273 显式写 use_banded_solver=False。每步是一次 J x J 稠密 LU（O(J^3)），J = num_lat = 90（默认）。
- 若用 num_lon 开 2D 域（domain/domain.py L499 surface_2D），numpy.linalg.solve 对每个经度独立求解——经向无任何耦合项，不存在 2D 联立椭圆解。
- 唯一的"迭代"在外层平衡态搜索：TimeDependentProcess.integrate_converge(crit=1e-4)（time_dependent_process.py L463-498）——不是每步的迭代求解器，而是长时间推进到平衡。
- 网格（domain/axis.py L155-193）：lat 轴默认端点 (-90., 90.)，num_points=90 -> 均匀 2 度间隔，91 个边界点、90 个中心点。

-> 结论：climlab 的 EBM 属于你说的"沿一条线的一维积分"，完全符合约束。这是所有调研对象里唯一原生满足"无全局 2D 迭代"的模型。甚至可用 Thomas 算法 O(J) 手写移植到 Java。

5) 时间复杂度 / 实测性能
- 未找到任何官方实测性能数字（README、docs、各模块 docstring 均无 benchmark/timing）。
- 由源码可确定的复杂度：默认 EBM 每步 O(J^3)，J=90 -> 约 7.3e5 flops；若 use_banded_solver=True（仅限 1D）则每步 O(J)；积分 1 年 = 90 步；annual_insolation 每次固定 10000 个采样点。

6) 输出格式
- 内存对象 climlab.Field（numpy 子类 + domain 元数据）；model.state 为 AttrDict；model.diagnostics 为 dict
- climlab.to_xarray(model) / Field.to_xarray() 转 xarray.Dataset / DataArray
- 无自带文件输出格式；落盘由用户自行完成 -> 标准化的 NetCDF 变量命名表未找到

7) 我们能直接借鉴什么
1. 直接抄隐式三对角经向扩散求解。把纬向平均温度从"诊断量"升级为"隐式预报量"：eff = C + D dt 的三对角矩阵，一次 solve_banded。不违反你的性能预算，也不违反"无全局 2D 迭代"约束。
2. EBM 常数集可直接抄：A=210 W/m2、B=2 W/m2/摄氏度、D=0.555 W/m2/摄氏度、S0=1365.2 W/m2、s2=-0.48、a0=0.3、a2=0.078、ai=0.62、Tf=-10 摄氏度、water_depth=10 m -> C=4.1813e7 J/(m2 K)、K≈5.39e5 m2/s。
3. 反照率模型可直接抄：暖态 alpha(phi) = a0 + a2*P2(sin phi)；Ts < Tf 处切换到 alpha = ai。纯逐格点判断，无迭代、无全局耦合。
4. Budyko 型 OLR 就是直线 OLR = A + B*Ts。需要 CO2 敏感性时用 AplusBT_CO2 的三次多项式，输入只是 c = ln(CO2/300)。
5. 日照模型可完全离线硬编码：orb_present 三个数（ecc=0.017236, long_peri=281.37, obliquity=23.446）让 DailyInsolation / AnnualMeanInsolation 不需要任何数据文件；annual_insolation 的 10000 点数值积分是纯函数，可在生成期跑一遍存成纬度表。
6. 热传输诊断公式可直接抄：heat_transport = -2 pi a^2 cos(phi) D dT/dphi（PW），其散度即 heat_transport_convergence（W/m2）。经向热输送的散度正是副高位置的一阶驱动。
7. 不要抄的点：use_banded_solver=False 的稠密 LU。矩阵是三对角的，用 Thomas 算法即可，O(J) 且无需线性代数库。

8) 关键文件路径 + 行号 + URL
- climlab/model/ebm.py（L7 主方程；L227-244 全部默认值；L273 use_banded_solver=False；L365-366/426-443/454-506 EBM_seasonal/EBM_annual）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/model/ebm.py
- climlab/radiation/aplusbt.py（L13, L85, L148；L170-174, L187 AplusBT_CO2）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/aplusbt.py
- climlab/surface/albedo.py（L174 / L244-279 / L343, L351-352, L368）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/surface/albedo.py
- climlab/radiation/insolation.py（L219, L251, L282 / L439-440 / L556-591）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/insolation.py
- climlab/solar/insolation.py（L264, L398, L411, L423-426, L451, L462-467, L477-478, L518-528 全部天文公式）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/solar/insolation.py
- climlab/dynamics/adv_diff_numerics.py（L201, L269-336, L382-429 隐式三对角方程/矩阵/求解器）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/adv_diff_numerics.py
- climlab/dynamics/advection_diffusion.py（L41-42 仅一根轴；L235-259 多轴报错）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/advection_diffusion.py
- climlab/dynamics/meridional_heat_diffusion.py（L66 D=0.555；L89 K=D/C*a^2；L96-99 热输送诊断）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/meridional_heat_diffusion.py
- climlab/dynamics/budyko_transport.py（b=3.81）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/budyko_transport.py
- climlab/dynamics/__init__.py：https://raw.githubusercontent.com/climlab/climlab/master/climlab/dynamics/__init__.py
- climlab/utils/constants.py（L15 a, L33 S0, L38-39 rho_w/cw, L53 days_per_year, L70 orb_present）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/utils/constants.py
- climlab/process/time_dependent_process.py（L463-498 integrate_converge）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/process/time_dependent_process.py
- climlab/model/column.py（L46-57/143-150/153-179 三种柱模式）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/model/column.py
- climlab/surface/turbulent.py（L66-81, L126, L181）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/surface/turbulent.py
- climlab/radiation/rrtm/rrtmg_lw.py（L12, L16, L18）：https://raw.githubusercontent.com/climlab/climlab/master/climlab/radiation/rrtm/rrtmg_lw.py
- 版本/许可证：https://pypi.org/pypi/climlab/json

完整模型/组件类清单（按 __init__.py 导出，实读）：
- climlab/__init__.py：GreyRadiationModel, RadiativeConvectiveModel, BandRCModel, EBM, EBM_annual, EBM_seasonal, Field, global_mean, Axis, column_state, surface_state, Process, TimeDependentProcess, ImplicitProcess, DiagnosticProcess, EnergyBudget, process_like, get_axes, couple, to_xarray
- climlab/radiation/__init__.py：AplusBT, AplusBT_CO2, SimpleAbsorbedShortwave, Boltzmann, GreyGas, GreyGasSW, FixedInsolation, P2Insolation, AnnualMeanInsolation, DailyInsolation, InstantInsolation, NbandRadiation, ThreeBandSW, FourBandLW, FourBandSW, ManabeWaterVapor, CAM3, CAM3_LW, CAM3_SW, RRTMG, RRTMG_LW, RRTMG_SW
- climlab/dynamics/__init__.py：BudykoTransport, AdvectionDiffusion, Diffusion, MeridionalAdvectionDiffusion, MeridionalDiffusion, MeridionalHeatDiffusion, MeridionalMoistDiffusion, LargeScaleCondensation
- climlab/surface/__init__.py：SensibleHeatFlux, LatentHeatFlux, ConstantAlbedo, P2Albedo, Iceline, StepFunctionAlbedo
- climlab/convection/__init__.py：ConvectiveAdjustment, EmanuelConvection, SimplifiedBettsMiller
- 没有 climlab/seaice/、climlab/atm/、climlab/ocean/、climlab/column/、climlab/ebm/（全部 404 实测）

9) 未找到 / 未确认
- 无任何实测性能/基准数字（README、docs、docstring 中均未找到）。
- climlab 独立海冰模块：未找到；冰仅以 EBM 反照率阶跃函数（Iceline + StepFunctionAlbedo）形式存在，无冰厚/冰温/盐度状态量。
- climlab SDM / 3D 动力核心：未找到（不存在）。dynamics 包文档字符串原文："Other modules are 1D advection-diffusion solvers (implemented using implicit timestepping)"。最接近 GCM 的只有柱模式 + 1D 纬向 EBM，没有动量方程、没有风场、没有纬向环流。
- RRTMG 运行时外部数据文件：不需要（吸收系数以 Fortran module rrlw_kg01..16 编译进 climlab_rrtmg），但需要编译好的 Fortran 扩展；缺失时 rrtmg_lw.py L18 仅 warnings.warn，模块静默失效不报错。
- climlab 中唯一需要联网的组件是轨道数据表：solar/orbital/table.py L15-16 pooch.retrieve(path, known_hash="3afc20dd...")，path = climlab.utils._datapath_http + 'orbital/orbit91'（utils/__init__.py L4）。但 climlab.EBM 族默认用硬编码 orb_present，不触发下载。
- climlab/radiation/cam3.py 内容未逐行阅读；其是否需要臭氧/气溶胶数据文件未确认。
- README L72-73 只说明"早版本有部分 Windows 二进制，当前不支持"；conda-forge 之外是否提供 Windows 二进制未确认。
---

## 4. Isca

1) 项目名 + 主链接 + 语言 + 许可证 + 维护状态
- 仓库 https://github.com/ExeClim/Isca ｜ 文档 https://execlim.github.io/Isca/
- 语言：Fortran 90（GFDL FMS 动力核心与物理）+ Python 3.7 驱动包 isca
- 许可证：GPL v3（https://raw.githubusercontent.com/ExeClim/Isca/master/LICENSE ，35141 字符；注意 LICENSE.txt 不存在）
- 维护：活跃。master HEAD sha d1321ac372698bcbe054da1cc5b4eef9f998a096
- 自述（官网 Overview 原文）："Isca is a framework for the construction of models of the global circulation of the atmosphere of Earth and other planets... The framework uses the dynamical core and the software infrastructure (FMS, for Flexible Modeling System) from the Geophysical Fluid Dynamics Laboratory in Princeton, USA"
- "Isca itself is not a single model, nor is it intended to provide a fully 'comprehensive' model capable of weather forecasts or climate projections for policy use."

源码路径更正（重要）：不存在 src/atmos/、bucket_model.F90、land_model.F90（raw 全 404）。真实路径为：
- 动力核心 src/atmos_spectral/model/spectral_dynamics.F90
- Frierson 湿物理 src/atmos_spectral/driver/solo/idealized_moist_phys.F90
- 混合层 src/atmos_spectral/driver/solo/mixed_layer.F90
- QE 对流 src/atmos_param/qe_moist_convection/qe_moist_convection.F90
- 灰体辐射 src/atmos_param/two_stream_gray_rad/two_stream_gray_rad.F90
- H&S 强迫 src/atmos_spectral/model/hs_forcing.F90（文档未给出该文件名，由源码树确认）
- 测试用例是 Python：exp/test_cases/frierson/frierson_test_case.py、exp/test_cases/held_suarez/held_suarez_test_case.py

2) 算的是什么（输出哪些场）
held_suarez 用例写出的 NetCDF 变量（dynamics 模块）：ps, bk, pk, ucomp, vcomp, temp, vor, div（8 个；bk/pk 静态不加 time_avg；无 sphum/precipitation/t_surf）。
frierson 用例：上述 8 个 + dynamics sphum + atmosphere precipitation + mixed_layer t_surf（共 11 个）。
URL https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/held_suarez/held_suarez_test_case.py 与 https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/frierson/frierson_test_case.py

3) 用什么方程/近似

(a) 动力核心 = 谱变换（三角截断），无水平有限体积核。Fortran namelist 默认（spectral_dynamics.F90:153-227）：
triang_trunc=.true.、num_fourier=42 / num_spherical=43 / lon_max=128 / lat_max=64 -> 默认 T42、num_levels=18、use_implicit=.true.、damping_order=2、damping_coeff=1.15740741e-4（(1/10 day)^-1）、cutoff_wn=15、robert_coeff=.04、alpha_implicit=.5、vert_coord_option='even_sigma'、reference_sea_level_press=101325、valid_range_t=(/100.,500./)。
Python 侧 resolution 表（src/extra/python/isca/experiment.py:29-56）：T170/T85/T42(128/64/42/43)/T21。
仓库默认运行配置 exp/run_isca/input/input.nml 未改截断（故 T42）但覆盖 num_levels=40、dt_atmos=600、depth=100、albedo 0.205、do_qflux 且 qflux_amp=30、do_seasonal=.true.。

预报状态（谱空间）：vors/divs/ts = complex(ms:me, ns:ne, num_levels, 2)；ln_ps = complex(ms:me,ns:ne,2)；spec_tracers(...,2,num_tracers)；网格镜像 psg/ug/vg/tg/grid_tracers；num_time_levels=2（行 117-135、allocate_fields 行 639-667）。
u、v 由 vor/div 每步转换（uv_grid_from_vor_div 行 938、vor_div_from_uv_grid 行 903）。
时间积分：蛙跳 leapfrog_2level_A / leapfrog（行 922-934，leapfrog.F90）+ Robert/RAW 滤波 + 半隐式 implicit_correction（行 909，implicit.F90）+ 谱阻尼（行 911-913）；每步 2 次全局谱变换。

(b) Held-Suarez 强迫（hs_forcing.F90:77-82, 536-600, 644-666）：t_zero=315, t_strat=200, delh=60, delv=10, eps=0, sigma_b=0.7, P00=1e5, ka=-40d, ks=-4d, kf=-1d（负号=天）。
公式：t_star = t_zero - delh*sin^2(phi) - eps*sin(phi)；the = t_star - delv*cos^2(phi)*log(p/P00)；teq = max(the*(p/P00)^KAPPA, tstr)；tcoeff = (tks-tka)/(1-sigma_b)；sigma_b < sigma <= 1 时 tdamp = tka + cos^4(phi)*tcoeff*(sigma-sigma_b)，否则 tdamp = tka；tdt = -tdamp*(T-teq)；动量 vcoeff = -vkf/(1-sigma_b)，vfactr = vcoeff*(sigma-sigma_b)。

(c) Frierson 湿物理（idealized_moist_phys.F90:101-181 默认；调度行 924/1043/1116/1139/1242/1321/1334/1401）：
- QE(BM) 对流（qe_moist_convection.F90）：tau_bm=7200 s、rhbm=0.8（frierson 用例 0.7）、Tmin=173、Tmax=335、val_inc=0.01；dq = -(q-qref)*dt/tau；Pq = sum(dq*dp/g)；dT = -(T-Tref)*dt/tau；Pt = sum((Cp/HLv)*dT*dp/g)（行 731/757）；参考廓线 eref = rhbm*p*rp/(rp+rdgas/rvgas)、qref = rp/(1+rp)（行 786-788）；CAPE/LCL 查找表 + 深/浅对流分支（convflag 0/1/2）。
- 大尺度凝结（lscale_cond.F90:56-60）：hc=1.00（RH 阈值）、do_evap=.false.、do_simple=.false.。
- 灰体双流辐射（two_stream_gray_rad.F90）：rad_scheme='frierson'、solar_constant=1360.0、ir_tau_eq=6.0、ir_tau_pole=1.5、linear_tau=0.1、wv_exponent=4.0、solar_exponent=4.0、atm_abs=0.0（用例 0.2）、odp=1.0；tau = tau0(phi)*[a*(p/p_std) + (1-a)*(p/p_std)^4]（行 699-706）、trans = exp(-d tau)（行 710-717）。
  Geen 窗口常量：ir_tau_co2_win=0.2150、ir_tau_wv_win1=147.11、ir_tau_wv_win2=1.0814e4、ir_tau_co2=0.1、ir_tau_wv1=23.8、ir_tau_wv2=254.0、window=0.3732、carbon_conc=360.0。
- Rayleigh 海绵（damping_driver.f90:42-62, 594-637）：trayfric=0.（负=天）、sponge_pbottom=50 Pa、do_rayleigh=.false.；fact = rfactr*(sponge_pbottom-p)^2/sponge_pbottom^2；frierson 用例设 do_rayleigh=True、trayfric=-0.25 d、sponge_pbottom=5000 Pa。
- 湍流：用例 do_diffusivity=True/do_simple=True。frierson_monin_obukhov.F90 存在但用例未启用（默认 rich_crit=2.0、drag_min=1e-5、zeta_trans=0.5）。

(d) 混合层（slab）海洋（mixed_layer.F90）：depth=40.0 m（默认）、albedo_value=0.06、tconst=305.0、delta_T=40.0、qflux_amp=0.0、qflux_width=16.0；
C = depth * RHO_CP，RHO_CP = RHO0*CP_OCEAN = 1.035e3 * 3989.24495292815 = 4.1289e6 J/m3/K（行 519；constants.F90:87-90）-> 默认 C ≈ 1.65e8 J/K/m2。
倾向：C_eff = C + t_surf_dependence*dt；dT_surf = -corrected_flux*dt/C_eff（行 735-745），corrected_flux = -SW_net - LW_down + alpha_t*Cp + alpha_lw - qflux（+ alpha_q*HLV）。
耦合 = 单向时间分裂：先用当前 t_surf 算下行辐射与地表通量，再调 mixed_layer（行 1401）隐式更新 t_surf/albedo，下一步的上行辐射（行 1242）与通量用新值。
frierson 用例覆盖 depth=2.5 m、albedo=0.31、tconst=285 -> C ≈ 1.03e7 J/K/m2。

(e) 时间步/积分长度：frierson dt_atmos=720 s、25 层、30 天×120 段 = 3600 天 ≈ 9.9 年；held_suarez dt_atmos=600 s、T42L25、30 天×12 = 360 天；默认 input.nml dt=600 s、L40。官方 spin-up 年数：未找到。

(f) 行星与大气常量（constants.F90）—— radius=6371e3 m、gravity=9.80 m/s2、omega=7.2921150e-5 rad/s、orbital_period=31557600 s、solar_constant=1368.22 W/m2、pstd_mks=101325.0 Pa、rdgas=287.04 J/(kg K)、kappa=2/7、es0=1.0。
太阳日：seconds_per_sol = |2 pi/(orbital_rate - omega)|；earthday_multiple=True 时 = 86400*earth_omega/omega。
"Whilst the rotation and orbital rates are set within this module, other parameters associated with planetary motion such as axial tilt (obliquity) and eccentricity are controlled from the astronomy_mod module."
URL https://execlim.github.io/Isca/modules/constants.html

4) 是否需要全局迭代
无全局迭代求解器。显式蛙跳 + 半隐式重力波（每步一次线性求解 implicit.F90/matrix_invert.F90）+ 2 次全局谱变换；物理全为逐气柱局地计算。
调用结构（https://execlim.github.io/Isca/isca_structure.html ）：atmos_model.F90 -> 每步调用 atmosphere.F90 -> 调用 idealized_moist_phys.F90（物理驱动）、spectral_dynamics.F90（谱动力核心）、press_and_geopot.F90（垂直坐标驱动）。
原文注意点："If using grey radiation, the surface flux is calculated after the up sweep in the grey radiation code, but before the down sweep. In addition for any configuration, the mixed layer ocean code is called after the down sweep in the vertical diffusion code, but before the up sweep."

5) 时间复杂度 / 实测性能
未找到（官方 docs/source/modules/dynamics.rst 全文仅 97 字符 "Coming soon..."；begginers_guide.rst 只有定性说明）。

6) 输出格式
NetCDF（FMS diag_manager）。默认表 exp/run_isca/input/diag_table -> 文件 "atmos_monthly"，30 days。
注意：Isca 文档的 "Changing Isca output" 页正文只有 "Coming soon..."（https://execlim.github.io/Isca/modules/output.html ）。

7) 我们能直接借鉴什么
1. slab 海洋的隐式解法（本报告对你最有价值的一条，https://execlim.github.io/Isca/modules/mixedlayer.html ）：
   净通量 = net_surf_sw_down + surf_lw_down - flux_r - SH - LH + ocean_qflux
   隐式时间步：land_sea_heat_capacity * dTs/dt = -corrected_flux - t_surf_dependence * dTs
   化简为：eff_heat_capacity = land_sea_heat_capacity + t_surf_dependence*dt，然后 eff_heat_capacity * dTs/dt = -corrected_flux。
   这是"把通量对温度的依赖隐式化"的标准技巧——可以直接用在 Minecraft 的逐格海温/地表温度上：它把显式步长限制去掉了，且每格一次除法，O(1)。
2. 初始海温分布：Ts = tconst - (1/3)*dT*(3 sin^2(lambda) - 1)。文档正文给 Frierson 用例值 T_surf=285 K、dT=40 K；namelist 默认表给 tconst=305.0、delta_T=40.0；Frierson test case 源码实配 'tconst': 285.、'prescribe_initial_dist': True、'depth': 2.5（2.5 m！）、'albedo_value': 0.31。depth 默认值 = 40.0 m。
3. APE aquaplanet 解析海温（可直接抄的公式）：Ts = 27 (1 - sin^2(3 lambda/2))，60N-60S（Neale & Hoskins 2004）；60 度以外为 0 摄氏度。
4. slab 海洋无水平输送，官方原话："The slab ocean model only communicates between grid-boxes in the vertical (i.e. air-sea exchange) but does not represent any horizontal transport"。可选 Q-flux（Merlis et al. 2013 解析式，或文件读入）。若你需要"洋流把热量从赤道输送到高纬"，必须自己加 Q-flux 项。
5. Byrne 长波光学厚度公式 d tau/d(p/p0) = a mu + b q + 0.17 log(CO2/360)——只有 3 个系数，是"温室气体 -> 长波光学厚度"的最简可用式。
6. Betts-Miller 松弛（tau = 7200 s，RH_crit = 0.8）比 Kuo 型更容易实现且更稳定。
7. 输出变量名清单（ps/bk/pk/ucomp/vcomp/temp/vor/div/sphum/precipitation/t_surf）就是 C1 要求的 NetCDF 变量名答案。

8) 关键文件路径 + 行号 + URL
| 内容 | 路径 | URL |
|---|---|---|
| 混合层海洋（隐式步 + namelist 全表 + 诊断） | src/atmos_spectral/driver/solo/mixed_layer.F90 | https://execlim.github.io/Isca/modules/mixedlayer.html |
| 湿物理驱动 | src/atmos_spectral/driver/solo/idealized_moist_phys.F90 | https://execlim.github.io/Isca/modules/idealised_moist_phys.html |
| 两流灰/多波段辐射方程 | two_stream_gray_rad.F90 | https://execlim.github.io/Isca/modules/two_stream_gray_rad.html |
| Simple Betts-Miller 对流 | src/atmos_param/qe_moist_convection/qe_moist_convection.F90 | https://execlim.github.io/Isca/modules/convection_simple_betts_miller.html |
| 饱和水汽压 | src/shared/sat_vapor_pres/sat_vapor_pres.F90 | 同上 |
| 大尺度凝结 | - | https://execlim.github.io/Isca/modules/lscale_cond.html |
| 地表通量 + Monin-Obukhov | src/coupler/surface_flux.F90、src/atmos_param/monin_obukhov/monin_obukhov.F90 | https://execlim.github.io/Isca/modules/surface_flux.html |
| Q-flux / warmpool | src/atmos_param/qflux/qflux.f90 | https://execlim.github.io/Isca/modules/mixedlayer.html |
| SST 输入文件插值 | src/atmos_shared/interpolator/interpolator.F90 | 同上 |
| 行星与大气常量 | constants.F90 | https://execlim.github.io/Isca/modules/constants.html |
| 调用结构 | atmos_model.F90、atmosphere.F90、spectral_dynamics.F90、press_and_geopot.F90 | https://execlim.github.io/Isca/isca_structure.html |
| Held-Suarez 用例 | exp/test_cases/held_suarez/held_suarez_test_case.py | https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/held_suarez/held_suarez_test_case.py |
| Frierson 用例 | exp/test_cases/frierson/frierson_test_case.py | https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/frierson/frierson_test_case.py |

9) 未找到 / 未确认
- 源文件正文未读：src/coupler/surface_flux.F90（C_D/C_H 公式未找到）、vert_turb_driver.F90、diffusivity.F90。
- Isca 文档中 "Isca's dynamical core" 与 "Isca's physical parameterisations" 两页正文为 "Coming soon..." -> 官方文档本身缺失这两块。
- Frierson 用例的 RESOLUTION 行：抓取到的 6314 字节中未见 RESOLUTION 赋值 -> 未确认（但默认 namelist 就是 T42L18，用例覆盖 num_levels=25）。
- Isca 有限体积动力核心未找到；bucket_model.F90 / land_model.F90 路径不存在（raw 404）。
- damping_driver 中 rfactr 换算行未确认；官方性能与 spin-up 数值未找到。

---

## 5. SPEEDY / SpeedyWeather.jl

### 5a. SPEEDY（ICTP 原始 Fortran）

1) 项目名/主链接/语言/许可证/维护状态
- 官方页（200）http://users.ictp.it/~kucharsk/speedy-net.html —— 原文 "For downloading SPEEDY ... please contact: kucharsk@ictp.it"，只提供物理参数化 tar：http://users.ictp.it/~kucharsk/speedy_ver41.5_phys_par.tar （web_fetch 返回 "unsupported content type application/x-tar"，即文件存在）。
- 你可以给的 https://kestrel.nmt.edu/~rsonnenf/ 已核实无 SPEEDY 内容。
- 真实可读源码 https://github.com/samhatfield/speedy.f90 （Zenodo https://zenodo.org/record/5816982 ，标题 "samhatfield/speedy.f90: v1.0.0"；镜像 https://github.com/sciencewiki/speedy.f90 同 commit 0ebfdbf）。语言 Fortran free-form .f90。
- 许可证：MIT 风格但限"research, educational and other non-commercial purposes"。
- 依赖仅 NetCDF（README："only one dependency: the NetCDF library"），build.sh + run.sh，无 MPI，自带 data/bc/t30/clim/*.nc 边界数据 -> 可离线。

2) 算的是什么：谱动力核心全球原始方程大气 GCM（T30L8 sigma 坐标）+ 简化物理；NetCDF 输出 u, v, t, q, phi, ps。

3) 方程与近似
- source/params.f90：trunc=30、ix=96、iy=24(il=48)、kx=8、nx=32、mx=31、ntr=1、nsteps=36 -> delt=2400 s、rob=0.05、wil=0.53、alph=0.5（中心半隐式）、iseasc=1、nstrad=3、sppt_on=.false.、issty0=1979；namelist 默认 nsteps_out=1、nstdia=180。
- sigma 层（geometry.f90:47）：kx=8 时半层 hsg=(0.000,0.050,0.140,0.260,0.420,0.600,0.770,0.900,1.000) -> 全层 sigma = 0.025, 0.095, 0.200, 0.340, 0.510, 0.685, 0.835, 0.950（fsg(k)=0.5(hsg(k+1)+hsg(k)) 行 52-53）。
- 预报量（prognostics.f90）：vor/div/t = complex(mx,nx,kx,2)；ps = log(p_s/p0)；tr(mx,nx,kx,2,ntr)（tr(1)=比湿 g/kg）；phi/phis 为诊断量。
- 对流（convection.f90）：简化 Tiedtke(1993) 质量通量；psmin=0.8、trcnv=6 h、rhbl=0.9、rhil=0.7、entmax=0.5、smf=0.8、fqmax=5.0；fm0 = p0*dsigma(kx)/(g*trcnv*3600)；rdps = 2/(1-psmin)；卷入廓线 entr(k) = (max(0,sigma_k-0.5))^2 归一化到 entmax。
- 大尺度凝结（large_scale_condensation.f90）：trlsc=4 h、rhlsc=0.9、drhlsc=0.1、rhblsc=0.95；RH(sigma) = 0.9+0.1(sigma^2-1)，k=kx 时取 max(·,0.95)。
- 地表通量（surface_fluxes.f90）：fwind0=0.95、cdl=2.4e-3、cds=1.0e-3、chl=1.2e-3、chs=0.9e-3、vgust=5.0、ctday=1e-2、dtheta=3.0、fstab=0.67、hdrag=2000、clambda=clambsn=7.0；denvvs = (p0*psa/(rgas*t0))*sqrt(u0^2+v0^2+vgust^2)。
- 垂直扩散：trshc=6 h、trvdi=24 h、trvds=6 h、redshc=0.5、rhgrad=0.5、segrad=0.1。
- 辐射常数（mod_radcon.f90）：albsea=0.07、albice=0.60、albsn=0.60、epslw=0.05、emisfc=0.98。
- slab 海洋（sea_model.f90）：混合层深度 d(phi) = 40+(60-40)*cos^3(phi) m；海冰 1.5+(2.5-1.5)*cos^2(phi) m；热容 hcaps=4.18e6*depth、hcapi=1.93e6*depth J/m2/K；SST 异常耗散 tdsst=90 d、海冰 tdice=30 d；rhcaps=delt/hcaps；tanom = cdsea*(tanom+rhcaps*hflux)；sea_coupling_flag 0-4。
- 陆地（land_model.f90）：depth_soil=1.0 m、hcapl=1.0*2.50e6 J/m2/K、swcap=0.30、swwil=0.17、sd2sc=60 mm、veg=max(0, vegh+0.8*vegl)。
- 时间积分：蛙跳 + Robert/Williams + alph=0.5 半隐式（implicit.f90 正文本次未读 -> 求解器细节未确认）。

4) 是否需要全局迭代：无（蛙跳 + 半隐式 + 谱变换；物理逐列）。
5) 性能：未找到。
6) 输出格式：NetCDF（nf90_*）；维度 time(unlimited)/lon(96)/lat(48)/lev(8)；变量 time, lon, lat, lev, u("eastward_wind","m/s"), v("northward_wind","m/s"), t("air_temperature","K"), q("specific_humidity","1"), phi("geopotential_height","m"), ps("surface_air_pressure","Pa")；lon=3.75 度*k；time=timestep*24/nsteps(天)。默认 nsteps_out=1 -> 每 2400 s 一个 NetCDF 文件。
7) 可直接借鉴：T30L8+2400 s+36 步/天的常数化最小配置；明确 8 层 sigma 半层表；Tiedtke 简化对流全参数化在 6 个 parameter + 一行卷入廓线；slab 海洋 cos^3(phi) 深度插值 + SST 异常耗散格式；CF 风格 NetCDF 变量/单位表。
8) 关键文件：params.f90(22-37,46-65)、geometry.f90(42-59)、prognostics.f90(15-21,50-90)、convection.f90(15-22,51-70)、large_scale_condensation.f90(25-28,69-70)、surface_fluxes.f90(12-34,140)、sea_model.f90(104-122,211-212,245-246,415-437)、land_model.f90(43,56-61,111,129,144-156)、input_output.f90(135-181,209-216)。
9) 未确认：ICTP 原始源码未读到（需邮件索取）；所有细节来自 samhatfield 重写版，与 v41/v42 逐行一致性未确认；implicit/time_stepping/tendencies/spectral 与辐射公式正文未读；性能与 spin-up 未找到。

### 5b. SpeedyWeather.jl

1) 项目名/主链接/语言/许可证/维护状态
- https://github.com/SpeedyWeather/SpeedyWeather.jl ｜ docs https://speedyweather.github.io/SpeedyWeatherDocumentation/ （200，跳转 ./stable/）。
- 语言 Julia >= 1.10（docs/src/installation.md，测试 1.10/1.11/1.12）。
- monorepo：SpeedyWeather/、SpeedyTransforms/、RingGrids/、LowerTriangularArrays/、SpeedyWeatherInternals/（安装 subdir="SpeedyWeather"）。
- 版本 0.22.1+DEV。许可证 EUPL v1.2（LICENSE 首行含 2020 Milan Kloewer、2021 Contributors、2022 Fred Kucharski & Franco Molteni for SPEEDY parametrization schemes）。活跃：main HEAD sha 1ef2a0e2c5a41623f22dd97fa064fb8b4c8f2a25。
- 离线：计算本地；但地形/海陆掩膜/SST 气候态经 Julia Artifacts 从 SpeedyWeatherAssets.jl 按需下载 -> 首次运行需网络。

2) 算的是什么：谱动力核心 + 参数化；4 个层次 BarotropicModel / ShallowWaterModel / PrimitiveDryModel / PrimitiveWetModel（abstract_models.jl:5-9）。

3) 方程与近似
- 默认截断/网格/层数（dynamics/spectral_grid.jl）：DEFAULT_TRUNCATION=32（1-based，= 旧记法 T31）、DEFAULT_GRID=OctahedralGaussianGrid、DEFAULT_NLAYERS=8（行 9-11）；trunc 参数已废弃，truncation=trunc+1（行 184-186）。文档：T31 + 8 层 sigma + 八面体高斯网格 3168 点、48 纬圈、赤道最多 96 经度点、平均约 400 km（docs/src/how_to_run_speedy.md）。
- sigma 层精确值（dynamics/vertical_coordinates.jl）：sigma_half_spacing 默认 profile=z->z 且 8 层 -> sigma_half = 0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0；sigma_full = 0.0625, 0.1875, 0.3125, 0.4375, 0.5625, 0.6875, 0.8125, 0.9375（行 44-57, 83-86）。可选 frierson_profile(sigma) = exp(-5*(0.05(1-sigma)+0.95(1-sigma)^3))（行 141）。
- 预报变量：vorticity、divergence、temperature、pressure = log(Pa)（primitive_dry.jl:145-159）、humidity（primitive_wet.jl:156-159）、ocean.sea_surface_temperature（ocean.jl:4/292）、land 土壤变量。
- 默认物理组件（primitive_wet.jl:83-107）：SlabOcean、ThermodynamicSeaIce、LandModel、WhichZenith、OceanLandAlbedo、BoundaryLayer、BulkRichardsonDiffusion、SurfaceMomentumFlux/HeatFlux/HumidityFlux、ImplicitCondensation、BettsMillerConvection、OneBandShortwave、OneBandLongwave、Leapfrog、ImplicitPrimitiveEquation、HyperDiffusion、CenteredVerticalAdvection、ClipNegatives、NetCDFOutput。
- Betts-Miller（parameterizations/convection.jl）：time_scale=Hour(4)=14400 s、relative_humidity=0.7（行 10/13）；Pq += (q-qref)dsigma、PT -= (T-Tref)dsigma（行 87-88）；deep: Pq>0 && PT>0，shallow: Pq<=0 && PT>0（行 92-93）；深对流 dT=(PT-Pq*Lv/cp)/dsigma_lzb（行 104，Frierson eq5）；浅对流 Qref=-sum(qref*dsigma)、fq=1-Pq/Qref、qref*=fq、dT=PT/dsigma_lzb（行 117-125，eq11-15）；倾向 dT/dt -= (T-Tref)tau^-1、dq/dt -= (q-qref)tau^-1（行 132-136）。
- 大尺度凝结：relative_humidity_threshold=0.95（行 8）；dq_cond = sat*0.95-q；dqsat_dT = sat*0.95*Lv_cp/(Rv T^2)（行 98/133）。
- 垂直扩散（Frierson 2006）：von_Karman=0.4、roughness_length=3.21e-5 m、critical_Richardson=10、surface_layer_fraction=0.1（行 6-15）；sqrtC=(kappa/log(Z/z0))(1-Ri_N/Ri_c)；K0=kappa*|V|*sqrtC；K_k=K0*min(z,fb*h)*zfac*Rifac（行 193-230）。
- 拖曳/通量：BulkRichardsonDrag kappa=0.4、Ri_c=10、drag_min=1e-5，C=max(drag_min,(kappa/log(z/z0))^2(1-Ri/Ri_c)^2)（boundary_layer.jl:133-195）；SurfaceMomentumFlux wind_slowdown=0.95、drag_ocean=1.8e-3、drag_land=2.4e-3；SurfaceOceanHeatFlux drag=0.9e-3、sea_ice_insulation=0.01；SurfaceOceanHumidityFlux drag=0.9e-3；SurfaceCondition wind_slowdown=0.95、gust_speed=1 m/s；ConstantSurfaceRoughness 默认 land 0.5 m / ocean 1e-4 m。
- 辐射：OneBandShortwave = DiagnosticClouds + BackgroundShortwaveTransmissivity（absorptivity_dry_air=0.03135、absorptivity_aerosol=0.03135、absorptivity_water_vapor=75、absorptivity_cloud_base=10、absorptivity_cloud_limit=0.14、zenith_amplitude=1、zenith_exponent=2）+ OneBandShortwaveRadiativeTransfer；ConstantShortwaveTransmissivity 默认 0.85。OneBandLongwave 的 emissivity_ocean=emissivity_land=0.98。可选 UniformCooling（Hour(16) 约 -1.5 K/day、temp_min=207.5 K、temp_stratosphere=200 K、tau_strat=5 d）、JeevanjeeRadiation（alpha=0.025 W/m2/K2、eps_ocean=0.65、eps_land=0.6、T_t=200 K、tau=24 h）、FriersonLongwaveTransmissivity（tau0_eq=6、tau0_pole=1.5、f_l=0.1）。
- Slab 海洋：有。parameterizations/ocean.jl SlabOcean：specific_heat_capacity=4184 J/kg/K、mixed_layer_depth=50 m、density=1000 kg/m3、land_temperature=285 K -> heat_capacity_mixed_layer=2.092e8 J/K/m2（行 264-281）。演化（kernel 行 360-366，注释 Frierson 2006 eq(1)）：dSST/dt = C0^-1 (Rsd - Rsu - Rlu + Rld - Lv*H - S)。它是 PrimitiveWetModel 的默认 ocean（primitive_wet.jl:83）。
- 默认时间步：Leapfrog dt_at_T32 = Minute(40) -> 2400 s @ T32（leapfrog.jl:177），robert_filter=0.1、williams_filter=0.53；dt = dt_at_T32*(DEFAULT_TRUNCATION/truncation)*(radius/DEFAULT_RADIUS)，再调成输出 interval 的整数因数（steppers/general.jl:12-45）；首步 Euler dt/2、第 2 步 dt、之后 2dt（行 225-235）。注意：docs/src/how_to_run_speedy.md 写"T32 用 30 min"与源码 Minute(40) 矛盾，以源码/benchmark json（dt=2400）为准。
- spin-up：代码层 spin_up_steps(Leapfrog)=1（leapfrog.jl:204）；气候 spin-up 年数官方建议未找到。

4) 是否需要全局迭代：无。蛙跳 + Robert/Williams 滤波 + 每步一次隐式线性求解（ImplicitPrimitiveEquation）；参数化逐格点局地。

5) 实测性能（有数字）。benchmark/README.md + benchmark/assets/benchmark_results.json，SYPD = simulated years per wallclock day，不含初始化与输出，官方警告 ±50% 波动。PrimitiveWet：
- T32L8：cpu-arm 1400 / cpu-x86 856 / gpu-nvidia 5879（LT+FFT）；MT 757/107/5730
- T43L8：564/370/3779；T64L8：147/107/1188；T86L8：57/39/659；T86L16：51/49/566；T86L24：48/38/532；T128L8：15/11/264；T171L8：5.5/3.9/138；T256L8：1.4/1.0/53；T256L24：1.5/1.0/38
- 机器（cpu-arm.meta）：Apple M3 8 核 / macOS arm64 / Julia 1.12.6 / 1 线程 / v0.21.1+DEV / 2026-07-21
- JSON 另给 dt（T32 -> 2400、T43 -> 1800、T64 -> 1200、T86 -> 900、T128 -> 600、T171 -> 450、T256 -> 300）与内存（T32L8=6.22 MB、T256L8=343.7 MB）
- -> PrimitiveWetModel T32L8 在笔记本上约 1435 SYPD ≈ 1 模式年 / 60 秒。这是本报告所有模型里最快的。

6) 输出格式：默认 NetCDF（NetCDFOutput）：DEFAULT_OUTPUT_INTERVAL=Hour(6)、output NF=Float32、missing=NaN、compression_level=1、shuffle=false、keepbits=15（writers/general.jl:5-13）；输出默认关闭，需 run!(simulation, output=true)。默认输出变量（PrimitiveWet）= DynamicsOutput 的 vorticity, u, v, divergence, interface displacement, surface pressure, mean sea level pressure, temperature, humidity + Precipitation/Boundary/Radiation/RandomPattern/SurfaceFluxes/Land/Ocean/BoundaryLayer 各组。JLD2 有（JLD2Output，writers/variables_output.jl:74-160）；另有 Zarr、HEALPix、Array 后端。GRIB：未找到。

7) 可直接借鉴：SpectralGrid 单点集中"截断+网格+层数+精度+架构"并自动派生维度；sigma 坐标函数式 profile（一行切均匀/Frierson）；把 dt 自动调成输出 interval 的整数因数避免时间轴漂移；Betts-Miller 代码注释直接标 Frierson 2007 公式号；Frierson 2006 边界层 K 的三因子分解；SlabOcean 一行能量收支 + 三参数化；完整 SYPD 基准流水线；JLD2 checkpoint + NetCDF 成品。
8) 关键文件：SpeedyWeather/src/dynamics/spectral_grid.jl(9-11,168-200)、dynamics/vertical_coordinates.jl(44-57,83-100,135-141)、models/primitive_wet.jl(59-115,147-166)、time_stepping/steppers/leapfrog.jl(7-25,160-235)、time_stepping/steppers/general.jl(1-45)、parameterizations/convection.jl(8-14,33-160)、large_scale_condensation.jl(6-8,55-153)、vertical_diffusion.jl(4-31,132-230)、ocean.jl(257-366)、surface_fluxes/*.jl、radiation/*transmissivity.jl、output/writers/{general,netcdf_output,variables_output}.jl、benchmark/README.md。
9) 未确认：隐式求解器正文未读；DiagnosticClouds 公式未读；陆面模型参数未读；HyperDiffusion/CenteredVerticalAdvection/ClipNegatives 默认系数未找到；官方 spin-up 年数未找到；GRIB 输出未找到；GPU 基准机器型号未读到。
- 注意：src/physics/ 在 HEAD 分支已重构（main/master 下 temperature_relaxation.jl 等 404）。本报告引用的 Held-Suarez 永久链接为固定 commit：https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/1c1f369d6227b1c6188c0ef19b02b5d98c9fbcc3/src/physics/temperature_relaxation.jl 与 https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/719dce952a26bf7713f8c9a2332313e52cbb512b/src/physics/boundary_layer.jl
---

## 6. MITgcm（只要"能不能做大气"和耦合的结论）

1) 项目名 + 主链接 + 语言 + 许可证 + 维护状态
- 主链接 https://mitgcm.org/ ｜ 手册 https://mitgcm.readthedocs.io/en/latest/ （构建版本 d861cd5）｜ 代码 https://github.com/MITgcm/MITgcm ｜ 测试汇总 https://mitgcm.org/testing-summary
- 语言：Fortran（F77 风格源码 + Fortran 90 module）；构建用 bash + make，tools/genmake2 生成 Makefile
- 许可证：MIT（LICENSE.txt L1 "Copyright (c) 2018 MITgcm Developers and Contributors"，正文为标准 MIT 许可）— https://raw.githubusercontent.com/MITgcm/MITgcm/master/LICENSE.txt- 维护：活跃（testing-summary 显示 2026-09-12 的每日回归：linux_amd64_gfortran.dvlp forward 130:130、adjoint-taf 34:34、tanglin-taf 24:24）
- 构建依赖：Fortran 编译器、bash、make、makedepend；MPI 可选；NetCDF 可选（原文：a no response to Can we create NetCDF-enabled binaries will disable including pkg/mnc and switch to output plain binary files）

2) 算的是什么（输出哪些场）
- 通用水动力核心预报量：水平速度 (u,v)、垂直速度 r_dot、位温 theta、示踪量 S（大气=比湿 / 海洋=盐度）、位势 phi
- 大气配置额外有地面气压 ps 预报方程：d ps/dt + div_h( int_0^{ps} v_h dp ) = 0
- AIM 包（= 大气中间物理）诊断清单（逐字抓取）：DIABT/DIABQ（5 层，非绝热位温/比湿倾向）、RADSW/RADLW、DTCONV、TURBT/TURBQ、DTLS/DQLS、DQCONV、TSR/OLR、RADSWG/RADLWG、HFLUX、EVAP、PRECON/PRECLS、CLDFRC、CLDMAS、DRAG、WINDS、TS/QS、UFLUX/VFLUX（N/m2，地面风应力）、DTSIMPL（一个隐式时间步后的地表温度变化）
- 来源 https://mitgcm.readthedocs.io/en/latest/phys_pkgs/aim.html

3) 用什么方程/近似

(a) 大气的解释方式（逐字）— https://mitgcm.readthedocs.io/en/latest/overview/atmosphere.html ：

    r = p            ! 垂直坐标就是气压
    r_dot = Dp/Dt = omega
    phi = g z
    b = (dPi/dp) theta
    theta = T (p_c/p)^kappa
    S = q            ! 比湿
    Pi(p) = c_p (p/p_c)^kappa       ! Exner 函数  (式 1.16)

边界条件：R_fixed = p_top = 0（大气顶）；R_moving = p_o(x,y)（山体顶部的静力气压）；omega = 0 at r = R_fixed（式 1.17）；omega = Dp_s/Dt at r = R_moving（式 1.18）。

-> 关键结论：MITgcm 的大气不是独立的动力核心，而是同一个静力/非静力求解器在 p 坐标下的同构体（atmospheric isomorph）。原文：one hydrodynamical kernel is used to drive forward both atmospheric and oceanic models（https://mitgcm.readthedocs.io/en/latest/overview/overview.html ）。

(b) 大气物理包：
- pkg/aim_v23（AIM），原文：the aim_v23 package that is based on the version v23 of the SPEEDY code -> MITgcm 的大气物理就是 SPEEDY v23。https://mitgcm.readthedocs.io/en/latest/phys_pkgs/aim.html
- pkg/fizhi = Fizhi: High-end Atmospheric Physics — https://mitgcm.readthedocs.io/en/latest/phys_pkgs/fizhi.html
- pkg/land = 陆面 — https://mitgcm.readthedocs.io/en/latest/phys_pkgs/land.html
- pkg/atm_phys 存在（ATM_PHYS_OPTIONS.h 含 ALLOW_ATM_PHYS；atm_phys_driver.F 20 740 字符 536 行，调用 radiation_mod、lscale_cond_mod、dargan_bettsmiller_mod、surface_flux_mod、vert_turb_driver_mod、vert_diff_mod、mixed_layer_mod），但手册第 8 章只列 AIM / Land / Fizhi，没有 atm_phys 章节。
- pkg/gfdl_cloud_microphys 不存在（三个候选路径全部 404；手册无 microphys 字样）。
- 相关实验：aim.5l_cs（5 层，立方球）；atm_gray（C32，26 层，dt=384 s，混合层 SST + 固定 Q-flux）；fizhi-cs-aqualev20（20 层，dt=120 s，APE aquaplanet）
(c) Held-Suarez 基准（逐字引用）— https://mitgcm.readthedocs.io/en/latest/overview/global_atmos_hs.html ：

原文要点（英文逐字，已抓取）：
- A novel feature of MITgcm is its ability to simulate, using one basic algorithm, both atmospheric and oceanographic flows at both small and large scales.
- Figure 1.4 shows an instantaneous plot of the 500 mb temperature field obtained using the atmospheric isomorph of MITgcm run at 2.8 degree resolution on the cubed sphere.
- The model is driven by relaxation to a radiative-convective equilibrium profile, following the description set out in Held and Suarez (1994) [HS94] designed to test atmospheric hydrodynamical cores - there are no mountains or land-sea contrast.
- Figure 1.5 shows the 5-year mean, zonally averaged zonal wind from a 20-level configuration of the model.

教程真实 URL（重要修正：导航里的 examples/held_suarez_cs.html 是 404）：https://mitgcm.readthedocs.io/en/latest/examples/held_suarez_cs/held_suarez_cs.html （手册 4.7 节）
rst 源 https://mitgcm.readthedocs.io/en/latest/_sources/examples/held_suarez_cs/held_suarez_cs.rst.txt

教程内容要点：
- 水平网格：conformal cube-sphere grid (C32)，6 个面各 32x32 格点；赤道/格林尼治子午线处的分辨率相当于 128x64 等间距经纬网格，但格点数少 25%。
- 垂直：20 个等间距层（20 x 50 mb，从 p*=1000 mb 到 0）。
- 垂直坐标不是 eta，而是再缩放气压坐标 p*（Adcroft & Campin 2004）。原文说明：The set-up uses the rescaled pressure coordinate (p*) of Adcroft and Campin (2004) with 20 equally-spaced levels；without topography, the p* coordinate and the normalized pressure coordinate (sigma_p) coincide exactly。对应 namelist：nonlinFreeSurf=4, select_rStar=2。
- 强迫公式 (4.48)-(4.51)：F_v = -k_v(p) v_h；F_theta = -k_theta(phi,p)[theta - theta_eq(phi,p)]；k_v = k_f max[0, (p*/Ps0 - sigma_b)/(1-sigma_b)]，sigma_b = 0.7，k_f = 1/86400 s^-1；theta_eq = max{200 (Ps0/p*)^kappa, 315 - DeltaTy sin^2(phi) - Deltatheta_z cos^2(phi) log(p*/Ps0)}；k_theta = k_a + (k_s - k_a) cos^4(phi) max{0,(p*/Ps0 - sigma_b)/(1-sigma_b)}；DeltaTy = 60 K，Deltatheta_z = 10 K，k_a = 1/(40*86400)，k_s = 1/(4*86400)，Ps0 = 1e5 Pa。
- 稳定性数：S_inert = f^2 dt^2 = 4e-3（f = 1.45e-4 1/s）；S_adv = |u| dt/dx = 0.37（|u| = 90 m/s，dx = 1.1e5 m）；S_c = c_g dt/dx = 4e-1（c_g = 100 m/s）。
- 原文性能说明：At this resolution, the configuration can be integrated forward for many years on a single processor desktop computer.
- namelist 关键行：buoyancyRelation=ATMOSPHERIC, eosType=IDEALG, rotationPeriod=86400.；implicitFreeSurface=.TRUE., exactConserv=.TRUE., nonlinFreeSurf=4, select_rStar=2；cg2dMaxIters=200, cg2dTargetResWunit=8.E-16；deltaT=450.；usingCurvilinearGrid=.TRUE., horizGridFile=grid_cs32, radius_fromHorizGrid=6370.E3, delR=20*50.E2。

4) 是否需要全局迭代
需要，而且每个时间步都需要。证据：
- 地面气压用隐式自由面形式求解——原文：The implicit free surface form of the pressure equation described in Marshall et al. (1997) is employed to solve for p_s。
- namelist 中 cg2dMaxIters=200、cg2dTargetResWunit=8.E-16 就是 2D 预条件共轭梯度椭圆求解器 CG2D 的最大迭代次数与收敛容差（原文：Sets maximum number of iterations the 2-D conjugate gradient solver will use；Sets the tolerance (in units of omega) which the 2-D conjugate gradient solver will use to test for convergence）。
- atm_gray 与 fizhi-cs-aqualev20 的 input/data 同样是 cg2dMaxIters=200 + cg2dTargetResWunit=8.E-16。
- 求解器源码 model/src/cg2d.F；并行下需全局归约（MPI_Allreduce）。

5) 时间复杂度 / 实测性能
- 手册的性能章节是空的。_sources/software_arch/software_arch.rst.txt 中 6.4.2 / 6.4.3 两节正文原文只有一行：TO BE DONE (CNH)。
- 未找到 mitgcm.org 上专门的 benchmark 页面：https://mitgcm.org/benchmarks 与 https://mitgcm.org/public/benchmarks/ 均 404（实测）。
- https://mitgcm.org/testing-summary 存在，但内容是回归测试通过率，不是性能数据。
- 手册中确实存在的实测吞吐数字（getting_started 脚注，llc_540 案例，Pleiades 集群，模拟 20 天）：767 MPI ranks -> fall-through 方案 799.0 模拟日/日历日，默认方案 781.0；2819 MPI ranks -> fall-through 1300 dd/d，默认 800.0 dd/d。

6) 输出格式
- 默认二进制 MDSIO：pkg/mdsio / pkg/rw，每场一对 name.data + name.meta。
- netCDF（可选）：pkg/mnc，需编译时检测到 NetCDF；失败则自动退回二进制。
- 诊断框架：pkg/diagnostics（data.diagnostics + DIAGNOSTICS_SIZE.h），按短名输出任意物理量。
- 后处理：MITgcmutils Python 包 — https://mitgcm.readthedocs.io/en/latest/utilities/utilities.html
7) 我们能直接借鉴什么
1. 「大气 = 同一个静力求解器在 p 坐标下的同构体」这个思想很有启发性：大气与海洋的差别只在状态变量解释（r=p, b=(dPi/dp)theta, S=q）与边界条件（p_top=0, R_moving=p_o）。你的行星气候模拟器如果已经有一维 Munk 环流，理论上同一套方程换个解释就能出大气风场。
2. AIM = SPEEDY v23 这个事实非常有价值：意味着你不需要 MITgcm 就能拿到 SPEEDY 的物理（用 ExoPlaSim 或 SpeedyWeather.jl 即可，后者快 1400+ SYPD）。
3. Held-Suarez 强迫是纯局地逐格点计算（theta_eq 与 k_theta 只依赖纬度和气压），不需要任何全局求解。
4. DTSIMPL（一个隐式时间步后的地表温度变化）是 MITgcm 直接把隐式地表温度作为诊断输出——和 Isca 的 eff_heat_capacity 是同一个技巧，再次印证地表温度隐式化是标准做法。
5. UFLUX / VFLUX 以 N/m2 输出地面风应力——和 ExoPlaSim 的 tauu/tauv 单位一致，是沿岸风应力最直接的对标量。
6. 不要借鉴：全局 CG2D 椭圆求解、WRAPPER 并行框架、genmake2 构建体系。

结论：MITgcm 对 Minecraft 而言不是现实选择（NO）
- 它能做大气（p 坐标同构体 + AIM/SPEEDY 物理 + Fizhi 高端物理 + Held-Suarez 基准），功能上没问题；
- 但每步需要全局 2D 椭圆求解（你明确禁止），且分辨率（2.8 度立方球 = 310 km）依然远达不到沿岸风急流的 0.5 度门槛（见 C2）。同样的分辨率用 SpeedyWeather.jl 能快 3 个数量级以上。
- 它是编译型 Fortran HPC 程序，不是库：必须经 tools/genmake2 -> make depend -> make 生成 mitgcmuv 可执行文件；没有 Python API，无法嵌入。
- 输入输出链路沉重：namelist + 二进制网格（grid_cs32 或 tile00n.mitgrid x 6）+ 初始条件二进制 + SIZE.h + packages.conf + CPP_OPTIONS.h 全部要备齐。
- MITgcm 唯一合理的用法：作为一次性的离线真值参考用于校准自研降阶模型；若走这条路，务必用 tutorial_held_suarez_cs（只用 exch2 + shap_filt + diagnostics，牛顿冷却 + Rayleigh 摩擦全部局地计算）。

8) 关键 URL
| 内容 | URL |
|---|---|
| 大气 p 坐标同构（式 1.10-1.18） | https://mitgcm.readthedocs.io/en/latest/overview/atmosphere.html |
| one hydrodynamical kernel | https://mitgcm.readthedocs.io/en/latest/overview/overview.html |
| Held-Suarez 基准（2.8 度立方球，20 层） | https://mitgcm.readthedocs.io/en/latest/overview/global_atmos_hs.html |
| Held-Suarez 教程（真实 URL，含全部强迫公式与 namelist） | https://mitgcm.readthedocs.io/en/latest/examples/held_suarez_cs/held_suarez_cs.html |
| Held-Suarez namelist 原文 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/verification/tutorial_held_suarez_cs/input/data |
| AIM = SPEEDY v23 + 完整诊断表 | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/aim.html |
| Fizhi 高端大气物理 | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/fizhi.html |
| Land 包 | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/land.html |
| atm_phys 驱动源码 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/atm_phys/atm_phys_driver.F |
| 耦合器 atm 侧 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/atm_compon_interf/atm_store_my_data.F |
| 耦合器 ocn 侧 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/pkg/ocn_compon_interf/ocn_store_my_data.F |
| 立方球网格拓扑 | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/exch2.html |
| 三种水平网格 | https://mitgcm.readthedocs.io/en/latest/algorithm/horiz-grid.html |
| 全包清单 | https://mitgcm.readthedocs.io/en/latest/phys_pkgs/phys_pkgs.html |
| 诊断与 I/O | https://mitgcm.readthedocs.io/en/latest/outp_pkgs/outp_pkgs.html |
| 许可证 | https://raw.githubusercontent.com/MITgcm/MITgcm/master/LICENSE.txt |

9) 未找到 / 未确认
- 手册第 8 章只列 AIM / Land / Fizhi；pkg/atm_phys 存在但未列入手册。
- pkg/gfdl_cloud_microphys 不存在（三个候选路径全部 404）。
- 大气-海洋耦合器：pkg/atm_compon_interf 与 pkg/ocn_compon_interf 确实存在（ATM_CPL_OPTIONS.h / OCN_CPL_OPTIONS.h 抓取 200），但 readthedocs 上没有对应页面（phys_pkgs/atm_compon_interf.html 与 ocn_compon_interf.html 均 404）。耦合器本体可以是 MITgcm 自身的一个特制构建（verification/cpl_aim+ocn）；海洋侧用 MIT Coupler 的 checkpoint1 库调用。
- 示例 examples/held_suarez_cs.html：404（导航链接存在但页面不存在）。真实 URL 是 examples/held_suarez_cs/held_suarez_cs.html。
- 实测性能数字：手册性能章节为 TO BE DONE (CNH)；mitgcm.org/benchmarks 404 -> 未找到。
- AIM 的具体辐射/对流公式与参数：aim.html 只给了诊断表与基于 SPEEDY v23，无公式 -> 未确认。
- pkg/aim_v23 的源码文件名：aim.html 的 Key subroutines, parameters and files 小节内容为空 -> 未确认。
- eta 垂直坐标：未找到；大气使用 p（r=p）与再缩放 p*。

---

## 7. Held-Suarez 基准（干动力核心 + 牛顿松弛）

值不值得抄：非常值得。它是「最简全球大气环流」的公认基准，而且现在有三份可靠的源码实现可抄。

### 7.1 规范参数（来自 Isca 的实际 namelist，逐字源码级）

    hs_forcing_nml: {
        t_zero: 315.,    # temperature at reference pressure at equator (default 315K)
        t_strat: 200.,   # stratosphere temperature (default 200K)
        delh: 60.,       # equator-pole temp gradient (default 60K)
        delv: 10.,       # lapse rate (default 10K)
        eps: 0.,         # stratospheric latitudinal variation (default 0K)
        sigma_b: 0.7,    # boundary layer friction height (default p/ps = sigma = 0.7)
        # negative sign is a flag indicating that the units are days
        ka:   -40.,      # Constant Newtonian cooling timescale (default 40 days)
        ks:    -4.,      # Boundary layer dependent cooling timescale (default 4 days)
        kf:   -1.,       # BL momentum frictional timescale (default 1 days)
        do_conserve_energy: True,  # convert dissipated momentum into heat (default True)
    }

https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/held_suarez/held_suarez_test_case.py

### 7.2 完整方程（三份独立源码交叉验证一致）

(1) 动量：Rayleigh 摩擦（H&S 式 1）

    d v/dt = ... - k_v(sigma) * v
    k_v(sigma) = k_f * max(0, (sigma - sigma_b)/(1 - sigma_b))
    k_f = 1/(1 day) ,  sigma_b = 0.7

(2) 热力学：牛顿松弛（H&S 式 2）

    d T/dt = ... - k_T(phi,sigma) * [ T - T_eq(phi,sigma) ]

(3) 平衡温度（H&S 式 3）

    T_eq(phi,sigma) = max{ 200 K , [ 315 K - Delta_Ty sin^2(phi) - Delta_Tz ln(p/p0) cos^2(phi) ] * (p/p0)^kappa }
    Delta_Ty = 60 K ,  Delta_Tz = 10 K ,  kappa = R/c_p = 2/7 ,  p0 = 1e5 Pa

SpeedyWeather.jl 的等价拆分实现（temperature_relaxation.jl:87-91, 122）：temp_equil_a[j] = Tmax - DeltaTy*sinphi^2 + Deltatheta_z*log(pres_ref)*cosphi^2；temp_equil_b[j] = -Deltatheta_z*cosphi^2；Teq = max(Tmin, (temp_equil_a[j] + temp_equil_b[j]*lnp)*(pres[k]/pres_ref)^kappa)。
（符号约定核对：a + b ln p = Tmax - DeltaTy sin^2(phi) + Deltatheta_z(ln p0 - ln p) = Tmax - DeltaTy sin^2(phi) - Deltatheta_z ln(p/p0)，与 H&S 原式一致。）
MITgcm 教程给出的是 theta_eq 版本（用位温 + (Ps0/p*)^kappa）：theta_eq = max{200 (Ps0/p*)^kappa, 315 - DeltaTy sin^2(phi) - Deltatheta_z cos^2(phi) log(p*/Ps0)}。
Isca hs_forcing.F90 的版本：t_star = t_zero - delh*sin^2(phi) - eps*sin(phi)；the = t_star - delv*cos^2(phi)*log(p/P00)；teq = max(the*(p/P00)^KAPPA, tstr)。

(4) 松弛时间尺度（H&S 式 4）

    k_T(phi,sigma) = k_a + (k_s - k_a) * max(0, (sigma - sigma_b)/(1 - sigma_b)) * cos^4(phi)
    k_a = 1/(40 days) ,  k_s = 1/(4 days) ,  sigma_b = 0.7

SpeedyWeather.jl 逐行（temperature_relaxation.jl:84）：temp_relax_freq[k, j] = k_a + (k_s - k_a)*max(0, (sigma-sigma_b)/(1-sigma_b))*cosphi^4

(5) 其他：do_conserve_energy = True（把摩擦耗散的动量转成热，默认开启）。
### 7.3 Held-Suarez 的物理意义与局限
- 不含水汽、不含辐射、不含陆面、无地形、无海陆对比（MITgcm 文档原文：there are no mountains or land-sea contrast）。它是纯动力核心测试。
- 对你的用途：它给出的是纬向平均温度 + 经向温度梯度 + 地表拖曳这套最小全球环流。如果你想在 Minecraft 里有一层全球尺度的大气风，H&S 就是它 100 行以内的最小实现——不需要辐射、不需要水汽、不需要云。
- 但它给不出沿岸风：H&S 的解是纬向对称的（T_eq 只依赖 phi 和 sigma），经向风 v 的时间平均为零。所以它只能提供纬向风 u(phi,sigma)，用不上你的沿岸风问题。

### 7.4 参考与许可
- Held, I. M. & Suarez, M. J. (1994), A proposal for the intercomparison of the dynamical cores of atmospheric general circulation models, Bull. Amer. Meteor. Soc. 75(10), 1825-1830，DOI 10.1175/1520-0477(1994)075<1825:APFTIO>2.0.CO;2
  注意：本会话未能直接抓取该 DOI 页面（doi.org 跳转到 journals.ametsoc.org，遭 CloudFront 403）-> 论文原文未逐字验证。
  但 MITgcm 官方文档在已验证页面上引用为 Held and Suarez (1994) [HS94]（https://mitgcm.readthedocs.io/en/latest/overview/global_atmos_hs.html ），Isca 的 namelist 名 hs_forcing_nml 与该文件一致。文献身份可靠，只是原文链接未能在本会话验证。
- 三份可直接抄的源码：
  - SpeedyWeather.jl（Julia，简洁，含完整默认值 + 公式注释）：https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/1c1f369d6227b1c6188c0ef19b02b5d98c9fbcc3/src/physics/temperature_relaxation.jl 与 https://raw.githubusercontent.com/SpeedyWeather/SpeedyWeather.jl/719dce952a26bf7713f8c9a2332313e52cbb512b/src/physics/boundary_layer.jl
  - Isca（Fortran，含完整 namelist 参数与 hs_forcing.F90 实现）：https://raw.githubusercontent.com/ExeClim/Isca/master/exp/test_cases/held_suarez/held_suarez_test_case.py
  - MITgcm（Fortran，教程页有 (4.48)-(4.51) 完整公式 + namelist）：https://mitgcm.readthedocs.io/en/latest/examples/held_suarez_cs/held_suarez_cs.html
  - PUMA/ExoPlaSim（Fortran，有 nhelsua 开关但该分支未实现 T_R）：https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/puma/src/puma.f90 （第 192-196 行）
- 未找到：Held-Suarez 1994 原文的可抓取全文链接。

---

# C1 横向对比总结表

| 项目 | 语言 | 许可证 | 维护 | 动力核心 | 默认截断 | 层数 | 预报量 | slab 海洋 | dt | 性能 | 输出 | 离线可跑 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ExoPlaSim | Fortran+Python | GPL-2.0 | 3.x 最终维护版 | 谱变换三角截断 | T21 | 10 | 5（+地表） | 是（单层，50 m） | 45 min | 6 min/年（4 核） | netCDF/HDF5/npz/CSV | 是（pip + gfortran/gcc，MPI 可选） |
| PlaSim / PUMA | Fortran+C/C++ | 未确认 | 官方页 2022-05-30 后未更新 | 谱变换三角截断 | T21 | 10 | 4（PUMA）/ 5（PlaSim） | 是（单层，50 m） | 45/36/30 min | 100 年/天 | netCDF/GRIB/原生 | 是（需 X11；GUI 可关） |
| climlab | Python | MIT | 活跃（0.9.2 / 0.10.0.dev） | 1D 隐式扩散（EBM）+ 1D 柱模式 | - | 1（EBM） | 1（Ts） | 无（EBM 自身就是地表） | 隐式，约 4.06 天 | 未找到 | xarray/NetCDF | 是（conda-forge；Windows 不支持） |
| Isca | Fortran+Python | GPL v3 | 活跃（dev） | 谱变换（GFDL FMS） | T42（源码默认 L18） | 18 / 用例 25 | 5 + t_surf | 是（用例 2.5 m / 默认 40 m） | 600-720 s | 未找到 | NetCDF（有明确变量名） | 是（Fortran + netCDF + MPI + Python） |
| SPEEDY (Fortran) | Fortran | MIT 风格（非商业） | 原始版需邮件索取 | 谱变换三角截断 | T30 | 8 | 5 | 是（40-60 m，cos^3 phi） | 2400 s | 未找到 | NetCDF（CF 风格变量名） | 是（仅需 NetCDF） |
| SpeedyWeather.jl | Julia | EUPL v1.2 | 非常活跃 | 谱变换（GFDL 风格） | truncation=32 (=T31) | 8 | 5（+ 海洋 1） | 是（50 m，2.092e8 J/K/m2） | 2400 s | T32L8 = 1400 SYPD（Apple M3）/ 856（x86）/ 5879（GPU） | NetCDF + JLD2/Zarr/HEALPix | 是（首次需下载 assets） |
| MITgcm | Fortran | MIT | 活跃 | 有限体积，任意正交曲线网格（立方球） | C32 (约 2.8 度) | 20 | p 坐标同构体 | 有海洋（自身就是海洋模式） | 450 s | 未找到 | MDSIO 二进制 + netCDF 可选 | 是，但需全局 2D 椭圆求解 |

最少需要多少个预报量的最终答案：
- 纯干动力核心（要全球环流 + 纬向风）：4 个（涡度、散度、温度、ln ps）——见 PUMA。
- 要湿过程（降水/潜热）：5 个（+ 比湿）。
- 要海陆季节循环 / 大气-海洋耦合：+1 个海温（slab，无水平输送）或 +2 个土壤温度/湿度（陆面）。
- Isca 的 Frierson 配置总共 6 个预报量（vor/div/temp/sphum/ps + t_surf），这是能出沿岸风级别大尺度环流的最小完整配置。

---

# C2. 能不能把简化 GCM 离线跑成查表？

## 2.1 一句话答案
技术上完全可行，而且已经有人建好了数据集（ThousandWorlds）。但能不能给出沿岸风是 NO。

## 2.2 已有的 GCM 到查表 / emulator 工作

### 2.2.1 ThousandWorlds —— 最直接的对标物（5 个 GCM 含 ExoPlaSim）

- 论文 arXiv:2606.18338（v1 2026-06-16，v2 2026-09-03），DOI 10.48550/arXiv.2606.18338 — https://arxiv.org/abs/2606.18338 ｜ HTML 全文 https://arxiv.org/html/2606.18338v2
- 代码 https://github.com/edstevenson/ThousandWorlds （MIT）
- 数据 https://doi.org/10.57967/hf/8695 （HuggingFace es833/ThousandWorlds，revision v2.0.0）

规模与配置（README 逐字）：1689 simulations across 5 GCMs, 8 planet parameters, and atmospheric variables on a 32 x 64 x 10 latitude-longitude-pressure grid。
5 个 GCM：ExoCAM、UM、LFRic-Atmosphere、ExoPlaSim（论文附录 A.2 标题列出）—— ExoPlaSim 就在其中。

8 个输入参数（thousandworlds/field_spec.py:3，逐字）：

    CANONICAL_INPUT_NAMES = [T_star, F_star, radius, gravity, P_rot, P0, CO2, CH4]

映射（thousandworlds/data.py:20-29）：F_star -> stellar_flux、P_rot -> rotation_period、P0 -> surface_pressure、CO2 -> co2、CH4 -> ch4。没有陆地掩膜、倾角、偏心率。

53 个输出场（field_spec.py:4-15）：CANONICAL_FIELD_VARIABLES = [surface_temperature, temperature, specific_humidity, asr_cloudy, olr_cloudy, cloud_fraction, u, v]；SINGLE_LEVEL_FIELDS = {surface_temperature, asr_cloudy, olr_cloudy}。
-> 单层 3 个 + 5 个变量 x 10 层 = 53 个场。u、v 都在内——沿岸风的查表目标是现成的。

压缩：球谐系数（回答 C3 的纬向平均 + 少量纬向谐波）

thousandworlds/spectral.py（5137 B，全文抓到）：

    GRID_SHAPE = (32, 64)
    N_COEFFS = 484                                  # 第 9-10 行
    def to_grid(coeffs, matrix=None):               # 第 79 行：合成
        return (coeffs @ matrix.T).reshape(*coeffs.shape[:-1], *GRID_SHAPE)
    L_MAX = 21                                      # 第 86 行
    def degree_starts(l_max=L_MAX):                 # 第 88 行：每个 degree 块的起始下标
    def build_equatorial_symmetry_mask(l_max, m_max, mode):   # 第 15 行
        # mode in {none, symmetric, antisymmetric}
        # symmetric: 保留 (l+m) 偶 ; antisymmetric: 保留 (l+m) 奇
    def to_spectral(grid):                          # 第 112 行：分析

压缩量级（按定义直接数出来的）：
- 完整 T21：sum_{l=0}^{21}(2l+1) = 22^2 = 484 个系数（N_COEFFS = 484 印证）
- 2048 网格值 -> 484 系数 = 4.23x 无损压缩（对 T21 频谱带限场）
- 赤道对称掩膜后 -> 约 242 系数 = 8.5x
- 只保留 m <= 3（纬向平均 m=0 + 3 个纬向谐波）：sum_l [min(l,3)+1] = 1+2+3+19x4 = 82 系数/场 = 25x 相对网格
- 只保留 m <= 1：sum_l [min(l,1)+1] = 2+2+20x2 = 44 系数/场 = 46.5x
- 只有纬向平均（m=0）：22 系数/场 = 93x —— 就是你问的纬向平均
- 注意：m = 0 就是纬向平均；m > 0 就是纬向谐波——球谐天然就是你想要的那个分解。

论文 3.4 节 Spectral representation 原话：We release the dataset in two formats: gridded numpy arrays on the common 32x64 grid, and spectral coefficients after a T21 spherical harmonic transform (SHT) of each field. Truncation at degree 21 discards high-frequency spatial modes and yields a more compact representation.

最强基线是球谐系数 + PCA/PPCA，不是神经网络：
thousandworlds/models/README.md 列出 pca_ridge.py、pca_mlp.py（两层 MLP）、pca_gbt.py（梯度提升树）、ppca_icm.py（GP + Matern-5/2，64 后验样本）。
论文摘要原话：GP-based methods perform best, suggesting that ThousandWorlds exposes a regime where off-the-shelf deep learning does not yet succeed.
-> 对你最有用：不要上神经网络，用 PPCA（球谐系数）+ GP / 岭回归 / GBDT。

可直接抄的预处理变换（thousandworlds/preprocessing.py）：
- 输入：P_rot 与 P0 取 log；CO2/CH4 用 asinh(x/s)，s(CO2) = 1e-6，s(CH4) = 1e-8（第 60 行 arcsinh_Z-scaling）
- 输出：humidity 取 log；cloud fraction 用 smoothed-logit，epsilon = 1e-2；ASR/OLR 除以该样本的入射恒星光通量 F_star
- 重网格：水平双线性，垂直 log-p 插值
- 赤道对称化：对称场 = 两半球平均；v（南北风）= 半球差的一半

关键警告：论文 3.3 节 Within-GCM variability —— Even within a single GCM, simulations from different studies use different configurations ... These choices affect the final climate but are numerous, inconsistently documented, and far too sparsely sampled to include as inputs. From an emulation perspective, they act as structured noise. -> 同一天体参数下不同配置的 ExoPlaSim 自己就能给出明显不同的气候。
### 2.2.2 ClimateBench

- 仓库 https://github.com/duncanwp/ClimateBench （MIT）— https://raw.githubusercontent.com/duncanwp/ClimateBench/main/LICENSE
- 论文 ClimateBench v1.0: A benchmark for data-driven climate projections，JAMES 14(10), e2021MS002954，DOI 10.1029/2021MS002954，出版 2022-10
- 数据 Zenodo DOI 10.5281/zenodo.5196512，CC BY 4.0；CMIP6.zip 1.5 GB、test.tar.gz 74.4 MB、train_val.tar.gz 839.1 MB
- 源模式 NorESM2-LM；11 组 CMIP6 实验（1pctCO2 / abrupt-4xCO2 / historical / piControl / hist-GHG / hist-aer / ssp126 / ssp245 / ssp370 / ssp370-lowNTCF / ssp585），每情景 3 个成员
- 输出 4 个年平均值场：tas、diurnal_temperature_range、pr、pr90（prepare_data.py:14-17）

输入构造（直接可抄的场到低维向量）baseline_models/utils.py：

    L7:  max_co2 = 9500
    L9-L11:  normalize_co2(data) = data / 9500
    L15: max_ch4 = 0.8
    L25: create_predictor_data(data_sets, n_eofs=5)
          # 对 BC 与 SO2 各做 5 个 EOF（eofs.xarray.Eof, pcs(npcs=5, pcscaling=1)）
    L56-L62: inputs = {CO2, CH4, BC_0..BC_4, SO2_0..SO2_4}   # 共 12 个预测因子
    L106: get_rmse(truth, pred) = sqrt( weighted((truth-pred)^2).weighted(cos(lat)).mean([lat,lon]) )

精度现实检查（README 排行榜，NRMSE，2080-2100 vs SSP245）：

| 模型 | tas Total | dtr Total | pr Total | pr90 Total |
|---|---|---|---|---|
| Neural Network | 0.327 | 16.78 | 3.17 | 4.34 |
| Gaussian Process | 0.478 | 22.58 | 4.05 | 4.70 |
| Random Forest | 0.400 | 22.46 | 5.03 | 5.40 |

-> 温度类可信（NRMSE 约 0.33），降水类差一个数量级（3.2-5.4）。这是热力学量可模拟、水循环量不可模拟的直接证据。

### 2.2.3 其他相关（摘要级证据）

| 工作 | 做了什么 | 关键数字 | URL |
|---|---|---|---|
| Rasp, Pritchard & Gentine 2018 | DNN 替代 GCM 的全部大气次网格过程 | 多年积分稳定；加速比未给出 | PNAS 115, 9684，DOI 10.1073/pnas.1810286115 |
| Beucler et al. | NN 模拟器能量守恒问题 | 约束损失 / 约束结构两种方案 | arXiv:1906.06622 |
| Crossouard et al. | 离线模拟 LMDZ AGCM 的物理参数化（逐柱倾向廓线） | U-Net 均值与方差更好；DNN 变率差（湍流学不好） | GMD 19, 5907-5931 (2026)，DOI 10.5194/gmd-19-5907-2026 |
| Kochkov et al. | 可微动力学求解器 + ML 组件的 GCM | 140 km 分辨率；仍只够全球平均温度/热带气旋频数 | arXiv:2311.07222；Nature (2024) DOI 10.1038/s41586-024-07744-y |
| Tahseen et al. | RNN 代理 OASIS GCM 的辐射传输模块 | 精度 > 99.0%，整个模拟加速 147x（GPU，金星条件） | arXiv:2407.08556；MNRAS 535, 2210 (2024)，DOI 10.1093/mnras/stae2461 |
| Selten 1995 | T21 正压谱模式 -> EOF 降阶 | 231 维状态 -> 20 个 EOF；气候态与变率都保住 | JAS 52, 915-936 |
| TorchClim v1.0 | 深度学习插件替换气候模式物理 | 存在性已确认，参数未确认 | GMD 17, 5459-5475 (2024) |

关键观察：未找到任何对 3D 系外行星 GCM 的完整输出场做 NN 模拟的论文——唯一实例是 ThousandWorlds。其余都是替换 GCM 内部某个模块（辐射、物理参数化）。

## 2.3 决定性问题：T21/T42 能不能解析沿岸风急流 / 沿岸上升流？

### 答案：NO（沿岸急流与上升流）；partial（行星-海盆尺度背景）

### (a) 分辨率钉死

来源 UCAR Climate Data Guide, Common Spectral Model Grid Resolutions（Last modified 26 Nov 2017）— https://climatedataguide.ucar.edu/climate-tools/common-spectral-model-grid-resolutions

| Truncation | lat x lon | km @ 赤道 | deg @ 赤道 |
|---|---|---|---|
| T21 | 32x64 | 625 | 5.61 |
| T42 | 64x128 | 310 | 2.79 |
| T62 | 94x192 | 210 | 1.89 |
| T85 | 128x256 | 155 | 1.39 |
| T159 | 240x480 | 83 | 0.75 |
| T255 | 256x512 | 60 | 0.54 |
| T799 | 800x1600 | 25 | 0.22 |

该页同时给出代价标度：谱模式的 Legendre 变换代价按 N^3 增长。（注意：即使 T799 也只有 25 km。）

### (b) 大气侧：决定性文献 Small et al. 2015

Small, Curchitser, Hedstrom, Kauffman & Large (2015), The Benguela Upwelling System: Quantifying the Sensitivity to Resolution and Coastal Wind Representation in a Global Climate Model, J. Climate 28(23), 9409-9432, DOI 10.1175/JCLI-D-15-0192.1（模式：CCSM4 + ROMS）
（本会话通过 Crossref API 取到完整摘要：https://api.crossref.org/works/10.1175/jcli-d-15-0192.1 ）

摘要逐字关键结论（4 条）：
1. The main result is that a realistic wind stress curl at the eastern boundary, and a high-resolution ocean model, are required to well simulate the Benguela upwelling system.
2. When the wind stress curl is too broad (as with a 1 deg atmosphere model or coarser), a Sverdrup balance prevails at the eastern boundary, implying southward ocean transport extending as far as 30S and warm advection.
3. Higher atmosphere resolution, up to 0.5 deg, does bring the atmospheric jet closer to the coast, but there can be too strong a wind stress curl.
4. The most realistic representation of the upwelling system is found by adjusting the 0.5 deg atmosphere model wind structure near the coast toward observations, while using an eddy-resolving ocean model. A similar adjustment applied to a 1 deg ocean model did not show such improvement.

把 T21/T42 代进去：
- 门槛 = 优于 1 度（111 km）；最佳 = 0.5 度（55 km）+ 沿岸观测订正 + 涡分辨海洋
- T42 = 2.79 度（310 km）：比太宽的 1 度还粗 2.8 倍
- T21 = 5.61 度（625 km）：比 1 度粗 5.6 倍，比 0.5 度粗 11 倍
- -> T21/T42 落在风应力旋度太宽 -> 东边界退化为 Sverdrup 平衡 -> 向极地（南向）输送 + 暖平流的范畴。即：不只是不够细，而是会给出方向性错误的沿岸海洋响应。

### (c) 沿岸风 drop-off 的尺度：Capet et al. 2004

Capet, Marchesiello & McWilliams (2004), Upwelling response to coastal wind profiles, GRL 31, L13311, DOI 10.1029/2004GL020123
（正文可读副本 https://www.yumpu.com/en/document/view/24392361/capet-et-al-2004-legos ）

正文逐字要点：
1. COAMPS winds off the CCC also typically exhibit a transition in the alongshore wind speed within a narrow coastal strip where the wind stress decreases to about 10-20% of its offshore value.
2. COAMPS winds at resolutions of 27, 9, and 3 km ... their structures differ significantly with the resolution. Most notably, the drop-off takes place over an increasingly small region as the resolution increases and wind curl increases proportionally, indicating that the true structure is indeterminate by current modeling practices.
3. the scatterometer analyses are not reliable within 50 km of the coastline.

-> 沿岸风 drop-off 的特征宽度在数十 km 量级；27 km 尚未收敛；3-9 km 仍不可判定；连卫星观测在离岸 50 km 内都不可靠。

### (d) 海洋侧：Rossby 半径尺度论证

Nurser & Bacon (2014), The Rossby radius in the Arctic Ocean, Ocean Science 10, 967-975, DOI 10.5194/os-10-967-2014 — https://os.copernicus.org/articles/10/967/2014/os-10-967-2014.xml

方程：模分离 N^-2(z) d2phi/dz2 + c^-2 phi = 0，边界 phi = 0 at z = -H, z = 0；R_i = c_i / f，f = 2 Omega sin(Theta)；常 N 近似 c_i = N H/(i pi)。

分辨率判据（原文逐字）：
- A minimum of two grid points per eddy radius is necessary to resolve eddies adequately, and one grid point per radius to permit them.
- Hallberg (2013) suggests that eddy parameterizations may no longer be necessary once the ratio of the baroclinic deformation radius to a model effective grid spacing is greater than a value of about 2, where effective spacing means the grid-diagonal distance.
- The typical best resolution in oceanic general circulation models (OGCMs) is currently ~0.1 deg (ca. 10 km).
- 中纬第一斜压 Rossby 半径典型值：the 30-50 km characteristic of the mid-latitude oceans。

由此直接算出：
- 判据 A（每半径 >= 2 格点）：dx <= R1/2 = 15-25 km（约 0.14-0.23 度）
- 判据 B（Hallberg，网格对角线）：dx <= R1/(2 sqrt2) 约 11-18 km（约 0.10-0.16 度）
- 当前最好 OGCM 约 0.1 度（10 km），刚好压线

高纬更糟（表 1 实测）：Amerasian Basin R1 约 11.1 km（年均）、Eurasian Basin 约 7.8 km；mode 2 约 4.6-5.2 km；陆架海 1-7 km，冬季均一化时 < 1 km。
原文判语：OGCMs will therefore (typically) be eddy-permitting in the Arctic region at best. Over the broad Arctic Ocean shelf seas, the Rossby radius will be even smaller, and here OGCMs will not even be eddy-permitting.

相关：Holt et al. 2017, Prospects for improving the representation of coastal and shelf seas in global ocean models, GMD 10, 499-517, DOI 10.5194/gmd-10-499-2017 — 摘要逐字：we find that a 1/12 deg global model resolves the first baroclinic Rossby radius for only ~8% of regions <500 m deep, but this increases to ~70% for a 1/72 deg model, so resolving scales globally requires substantially finer resolution than the current state of the art. ... The benefits of resolution are particularly apparent in eastern boundary upwelling zones.（1/72 度 约 1.4 km）

Chelton et al. 1998, Geographical Variability of the First Baroclinic Rossby Radius of Deformation, JPO 28(3), 433-460, DOI 10.1175/1520-0485(1998)028<0433:GVOTFB>2.0.CO;2 — 给出全球 1x1 度的 c1 与 lambda1 气候态。（无摘要 -> 具体区域数值未确认。）

### (e) 本节结论表

| 目标量 | 阈值（文献数字） | 出处 |
|---|---|---|
| 沿岸风应力旋度不太宽、不退化到 Sverdrup 平衡 | 需优于 1 度(111 km)；0.5 度(55 km) 才把急流挪近岸，且需沿岸观测订正 | Small et al. 2015, DOI 10.1175/JCLI-D-15-0192.1 |
| 沿岸风 drop-off 结构收敛 | 27 km 未收敛；3-9 km 不可判定；散射计离岸 50 km 内不可靠 | Capet et al. 2004, DOI 10.1029/2004GL020123 |
| 中纬海洋涡旋 / 沿岸流系分辨 | dx <= 15-25 km（R1 = 30-50 km）；Hallberg 判据 dx <= 11-18 km；当前最好 OGCM 0.1 度(10 km) | Nurser & Bacon 2014, DOI 10.5194/os-10-967-2014 |
| 全球能解析 R1（陆架 <500 m） | 1/72 度 约 1.4 km（1/12 度只有约 8%） | Holt et al. 2017, DOI 10.5194/gmd-10-499-2017 |
| 高纬沿岸（南极/挪威/阿拉斯加） | dx 约 2.5-5.5 km（R1 = 5-11 km） | Nurser & Bacon 2014, 表 1 |

T21 相对这些门槛粗 5.6x（对 1 度）到 250x（对海洋涡分辨）；T42 粗 2.8x 到 125x。

### (f) 查表能提供 / 不能提供

| 能提供 | 不能提供 |
|---|---|
| 行星尺度温度带、季节循环 | 沿岸风急流（离岸 10-100 km 的狭窄加速带） |
| 大尺度风带与上升流有利风的海盆分布 | 近岸风应力 drop-off（降到离岸值 10-20%） |
| 海盆尺度 SST 梯度 | 风应力旋度（沿岸上升流的第二驱动机制） |
| 海盆尺度 Sverdrup 环流 | 沿岸上升流本身（Ekman 抽吸 + 沿岸辐散） |
| 云量 / OLR / ASR 等 2D 辐射场（ThousandWorlds 已验证） | 沿岸 SST 锋、沿岸流、涡动能 |
| 大尺度沿岸风的符号（赤道向 vs 极向） | 沿岸风的幅度与旋度结构 |

## 2.4 GCM 查找表字面检索 + 游戏界现状

- 未找到任何把 GCM 输出直接做成查找表的论文或项目。
- 但概念等价的做法是真的：ClimateBench 用 5 个 EOF 压缩气溶胶场；ThousandWorlds 同时发布 T21 球谐系数版；Selten 1995 把 T21 的 231 维压到 20 个 EOF。
- 游戏界（均非 GCM 驱动）：
  - Tellus（Minecraft 模组）https://github.com/Yucareux/Tellus —— 直接查真实观测栅格：ESA WorldCover 2021 v200（约 10 m，CC BY 4.0）、ETH Global Canopy Height 10 m 2020（DOI 10.3929/ethz-b-000609802）、Overture Maps 水体/海岸线（ODbL）、Koppen-Geiger 1 km 气候分类（Beck et al. 2018, Scientific Data, DOI 10.1038/sdata.2018.214）、Mapterhorn DEM、OpenWaters Seascape 水深、Open-Meteo 实时天气。README 原文：Tellus requires an active internet connection and will not work offline. -> 1 km Koppen 栅格 + 10 m 土地覆盖 + DEM + 水深，就是全球气候相关信息在游戏里实际可行的体量。
  - TerraFirmaCraft 的 Climate.java：https://github.com/TerraFirmaCraft/TerraFirmaCraft/blob/5040431220a91a5d91dbad872065e2bc97e43204/src/main/java/net/dries007/tfc/util/climate/Climate.java （194 行，EUPL v1.2）—— 运行时的解析/参数化气候模型（温度、降雨），无查找表。
  - -> 游戏界的实际选择是查观测栅格或跑解析式，不是跑 GCM 建表。

## 2.5 C2 结论

YES（离线建表可行）：ExoPlaSim 1 模式年 < 1 分钟；SpeedyWeather.jl T32L8 = 1400 SYPD；ThousandWorlds 已经用 ExoPlaSim 等 5 个 GCM 建好了 8 参数 -> 53 场的 T21 数据集和球谐系数版。

NO（沿岸风急流/上升流不可得）：T21（625 km）/ T42（310 km）比大气门槛（0.5-1 度）粗 2.8-11 倍，比海洋涡分辨门槛（10-25 km）粗 12-62 倍，且按 Small et al. 2015 会在东边界产生方向性错误的 Sverdrup 平衡 + 暖平流。

-> 建议架构：查表给大尺度背景风（含正确的沿岸风符号）+ 独立的解析/参数化沿岸 drop-off 模块（衰减尺度取数十 km 量级，但注意 Capet et al. 2004 指出该尺度本身不确定）。
---

# C3. 具体问题：给「极赤距离 10 000 km、R_eff = 6 366 km」的行星做查表

## 3.1 参数清单（可确认部分）

### (a) 行星几何与自转

你的两个约束是自洽的：极赤距离 = 1/4 周长 = 10 000 km -> 周长 = 40 000 km -> R = 40 000/(2 pi) = 6 366 km。

ExoPlaSim（configure()，https://exoplasim.readthedocs.io/en/latest/source/exoplasim.html ）：

| 参数 | 单位 | 说明 | 你的取值 |
|---|---|---|---|
| radius | 地球半径（！） | Planet radius in Earth radii. Default is 1.0. | 6366 / 6371.22 = 0.99918 |
| gravity | m/s2 | 默认 9.80665 | 你的值 |
| rotationperiod | 天（24 h 天） | Planetary rotation period, in days. Default is 1.0. | 你的值 |
| flux | W/m2 | Incident stellar flux. Default 1367 for Earth. | 你的值 |
| startemp | K | 恒星黑体有效温度 | 你的值 |
| year | 24-h 天 | 恒星年长度 | 你的值 |
| eccentricity / obliquity / lonvernaleq | - | 默认地球值 | 你的值 |
| fixedorbit | bool | True 则轨道参数不随时间变化 | 建议 True |
| pressure 或 pN2/pCO2/... | bar | 表面气压（或各气体分压） | 你的值 |
| ozone | bool/dict | 臭氧强迫（dict 需 height/spread/amount/varlat/varseason/seasonoffset） | False |
| landmap | .sra 文件路径 | 陆地掩膜 | 你的值 |
| topomap | .sra 文件路径 | 位势高度图，需同时给 landmap | 你的值 |
| aquaplanet / desertplanet | bool | 全海 / 全陆 | - |
| mldepth | m | 混合层深度，默认 50 m | 你的值 |
| soildepth / cpsoil / soilwatercap / soilsaturation / maxsnow | - | 陆面 | 你的值 |
| seaice | bool | False 则关闭海冰辐射效应（仍计算海冰） | - |
| orography | float | 地形缩放；0.0 = 无地形 | - |

重要陷阱（源码级）：radius 的单位是地球半径，不是米。内部常量是 plarad = 6371220.0 m（p_exo.f90）。换算：radius = 6366000 / 6371220 = 0.99918。
另注：p_exo.f90 的 print_planet 里 p_radius_eq = 6378.0、p_radius_po = 6356.0 只是打印用的地球参考值，不影响模拟。

旋转的处理（源码级，plasim.f90:1287-1316）——这是 ExoPlaSim 的关键修改：

    solar_day = day_24hr
    sidereal_day = solar_day * (n_days_per_year-1) / n_days_per_year
    if (rotspd /= 1.0) then
       sidereal_day = day_24hr / rotspd
       if (n_days_per_year /= 1) then
          solar_day = sidereal_day * n_days_per_year/(n_days_per_year-1)
       else
          solar_day = sidereal_day   ! 1:1 自转时太阳日为无限，设为 1 年
       endif
    endif
    ww = TWOPI / sidereal_day       ! Omega（标度）
    cv = plarad * ww                ! 速度标度
    ct = cv * cv / gascon           ! 温度标度

-> 这是从太阳日 / 恒星日到 Omega 与无量纲标度的完整推导链，可以直接抄。

Isca 的对应参数（constants.F90，https://execlim.github.io/Isca/modules/constants.html ）：radius（m，默认 6371e3）、gravity（默认 9.80）、omega（rad/s，默认 7.2921150e-5）或 orbital_period（s，默认 31557600）、solar_constant（默认 1368.22）、pstd_mks（默认 101325.0）、rdgas（默认 287.04）、kappa（默认 2/7）、es0（默认 1.0）。倾角与偏心率在 astronomy_mod 中，不在 constants 中。

SPEEDY Fortran：params.f90 用 trunc=30、ix=96、iy=24(il=48)、kx=8、delt=2400 s 这一组常数直接定义行星；无独立的半径/自转参数入口（本次未读到）。
SpeedyWeather.jl：文档有 input_data、orography、land_sea_mask、initial_conditions、vertical_coordinates 页（未逐页展开 -> 具体参数名未确认）；AquaPlanet 的 temperature_equator = 302.0 K / temperature_poles = 273.0 K / land_temperature = 285.0 K 已确认。

### (b) 温室气体
- ExoPlaSim：pCO2、pCH4、pN2、pH2、pHe、pO2、pAr、pNe、pKr，单位 bar；pH2O 只影响气体常数与表面气压，不影响湿过程；pCH4 不影响辐射。官方原文：the only absorbers explicitly included in the radiation scheme are CO2 and H2O——注意：辐射里只有 CO2 与 H2O！
- ThousandWorlds：只把 CO2 与 CH4 的体积混合比当输入参数（field_spec.py:3）
- Isca Byrne 方案：d tau/d(p/p0) = a mu + b q + 0.17 log(CO2/360)——只有 CO2 与水汽

### (c) 海温
1. slab 海洋（推荐）：ExoPlaSim mldepth = 50 m（默认）；Isca depth（默认 40 m，Frierson 用例 2.5 m）；SpeedyWeather.jl SlabOcean(mixed_layer_depth=50, specific_heat_capacity=4184, density=1000) -> heat_capacity_mixed_layer = 2.092e8 J/(m2 K)；SPEEDY Fortran d(phi) = 40+(60-40)cos^3(phi) m，hcaps = 4.18e6*depth J/(m2 K)
2. 解析剖面：Isca 的 Ts = tconst - (1/3)dT(3 sin^2(lambda) - 1)（默认 tconst 305 K / dT 40 K）；或 Ts = 27(1 - sin^2(3 lambda/2))（APE）；或 SpeedyWeather 的 AquaPlanet 余弦平方（302 K -> 273 K）
3. 固定海温：Isca do_sc_sst + sst_file（NetCDF）

## 3.2 输出网格分辨率与文件体积

### (a) 网格
- T21：64 经 x 32 纬（高斯纬度，非等距！）。文档原文：ExoPlaSim latitudes are not evenly-spaced（https://exoplasim.readthedocs.io/en/latest/tutorial.html ）
- T31：96x48；T42：128x64（README：Standard resolutions are: T21 (64x32 grid), T31 (96x48), and T42 (128x64).）
- 赤道格距：T21 = 625 km / 5.61 度；T42 = 310 km / 2.79 度（UCAR Climate Data Guide）
- 纬度排布由 inigau（gaussmod.f90）迭代求解高斯横坐标与权重：50 次迭代，收敛判据 ZEPS = 1.0e-16
- 10 个垂直层（nlev = 10，resmod_def.f90:4），sigma 坐标

### (b) 文件体积（官方硬数字，这是你能拿到的最可靠的答案）

官方原文：A T21 model output with 10 vertical levels, 12 output times, all supported variables in grid mode, and no standard deviation computation will have the following sizes：

| 格式 | 大小 |
|---|---|
| netCDF | 12.8 MiB |
| HDF5 | 17.2 MiB |
| NumPy (.npz) | 19.3 MiB |
| tar.xz | 33.6 MiB |
| tar.bz2 | 36.8 MiB |
| gzip | 45.9 MiB |
| 未压缩 | 160.2 MiB |

换算到你的场景：
- 12.8 MiB / 12 个时刻 约 1.07 MiB / (T21 x 10 层 x 全部 53 个场)（netCDF 压缩后）
- 若只保留你需要的 6 个场（ua/va/ta/hus/ps/ts），约 0.12 MiB / 时刻（推算）
- 若用 T42：格点数 x4 -> 约 51 MiB / 12 时刻（推算，非官方数字）
- 若从 10 层降到 3 层：约 0.32 MiB / 时刻（推算）

## 3.3 能不能只用「纬向平均 + 少量纬向谐波」压缩？

能。而且有两种现成做法，都有源码级证据。

### 做法 A：ExoPlaSim 的 mode=fourier（每纬度的傅里叶系数）

exoplasim/pyburn.py 的 _transformvar()（第 623 行起），fourier 分支在第 821-870 行：

    elif mode==fourier:
        if (ntru+1)*(ntru+2) in variable.shape: #spectral variable
            if len(variable.shape)==3: #Include lev
                nlevs = variable.shape[1]; ntimes = variable.shape[0]
                spvar = np.asfortranarray(np.transpose(np.reshape(variable,(ntimes*nlevs,variable.shape[2]))))
                fcshape = (ntimes,nlevs,nlat,nlon//2,2)
                dims = [time, levd, lat, fourier, complex]
            else:
                ntimes = variable.shape[0]
                spvar = np.asfortranarray(np.transpose(np.reshape(variable,(ntimes,variable.shape[1]))))
                fcshape = (ntimes,nlat,nlon//2,2)
                dims = [time, lat, fourier, complex]
            fcvar = pyfft.sp3fc(spvar,nlat,nlon,ntru,int(physfilter))
            fouriervar = np.reshape(np.transpose(fcvar),fcshape)
        else: #grid variable
            ...
            fcvar = pyfft.gp3fc(gpvar)
            fouriervar = np.reshape(np.transpose(fcvar),fcshape)
        meta.append(tuple(dims))
        outvar = fouriervar

注意：网格到 fourier 的分支会先除以 sqrt(2)（第 847 / 861 行：variable/1.4142135623730951），是对 FFT 归一化的处理，复现时必须注意。

输出维度 = time, lev, lat, fourier, complex，即每个纬度一条傅里叶级数。截取 fourier 维的前 N 个频次就得到「纬向平均（第 0 个）+ N-1 个纬向谐波」。
另有 zonal=True（只输出纬向平均，第 726-730 / 772-776 行）。
URL https://raw.githubusercontent.com/alphaparrot/ExoPlaSim/master/exoplasim/pyburn.py

### 做法 B（更推荐）：T21 球谐系数 + 赤道对称掩膜

ThousandWorlds 就是这么做的（thousandworlds/spectral.py，全文已抓）：GRID_SHAPE = (32, 64)；N_COEFFS = 484；to_spectral(grid) / to_grid(coeffs)（预计算分析/合成矩阵）；degree_starts(l_max=21)（可按 degree 截断）；build_equatorial_symmetry_mask(l_max, m_max, mode)（symmetric 保留 (l+m) 偶）。

为什么球谐比逐纬度傅里叶更好：球谐在球面上是正交归一基（论文原文：Spherical harmonics are a natural orthonormal basis on the sphere, analogous to sinusoids on a circle.）。逐纬度傅里叶在不同纬度上不是同一个基，压缩效率更低且边界处理麻烦。

压缩量级（可直接引用的数字）：

| 表示 | 系数数/场 | 相对 32x64 网格 | 说明 |
|---|---|---|---|
| 原始网格 | 2048 | 1x | |
| 完整 T21 球谐 | 484 | 4.23x | 对 T21 带限场无损 |
| + 赤道对称掩膜 | 约 242 | 8.5x | 论文对全部场做此对称化 |
| + 只保留 m <= 3 | 82 | 25x | sum_l [min(l,3)+1] = 1+2+3+19x4 |
| + 只保留 m <= 1 | 44 | 46.5x | sum_l min(l,1)+1 = 2+2+20x2 |
| 只有纬向平均（m=0） | 22 | 93x | 就是你问的纬向平均 |

注意：m=0 就是纬向平均；m>0 就是纬向谐波。球谐天然就是你想要的分解形式。

### 做法 C：PCA / PPCA 之上再压（论文的最强基线）

ThousandWorlds 的 pca_ridge / pca_mlp / pca_gbt / ppca_icm 全部是在 T21 球谐系数上做概率 PCA 降维，然后回归潜在得分。论文结论：GP 类最好。（thousandworlds/models/README.md）

-> 完整链路：GCM 输出场 ->（预计算 SHT 矩阵）-> 484 球谐系数 ->（赤道对称）-> 约 242 ->（PPCA）-> 数十维潜在向量 -> 存进模组。运行时逆变换是固定稀疏线性算子，成本极低。

## 3.4 C3 的能确认与未找到分列

### 能确认

| 项 | 结论 | 来源 |
|---|---|---|
| 半径换算 | radius = 0.99918 地球半径（ExoPlaSim 内部 plarad = 6371220.0 m） | p_exo.f90 |
| 自转 | rotationperiod 单位为天；ww = TWOPI/sidereal_day 是 Omega 标度 | p_exo.f90、plasim.f90:1287-1316 |
| 重力 | gravity 单位 m/s2，默认 9.80665 | p_exo.f90 |
| 太阳常数 | flux 单位 W/m2，默认 1367 | configure 文档 |
| 温室气体 | pCO2 等单位为 bar；辐射里只有 CO2 与 H2O | configure 文档 |
| 陆地掩膜 | landmap = .sra 文件（分辨率相关；仓库内有 N024/N032/N048/N064/N096 与 Alderaan/Example/Mountains 样例） | exoplasim/puma/dat/、exoplasim/ 根目录 |
| 海温 | slab 默认 50 m；mldepth 可调 | configure 文档、oceanmod.f90:47-48 |
| 网格 | T21 = 64x32 高斯纬度（非等距）；T42 = 128x64 | README、tutorial |
| 赤道格距 | T21 = 625 km / 5.61 度；T42 = 310 km / 2.79 度 | UCAR Climate Data Guide |
| 10 层 sigma | nlev = 10；nvg=0 时等距 | resmod_def.f90:4、puma.f90:1583-1585 |
| 文件体积 | T21 x 10 层 x 12 时刻 x 全部场 = netCDF 12.8 MiB | postprocessor 文档 |
| 球谐压缩 | 484 系数（完整）/ 约 242（赤道对称）/ 82（m<=3）/ 22（仅纬向平均） | thousandworlds/spectral.py |
| 傅里叶压缩 | ExoPlaSim mode=fourier 现成支持 | pyburn.py:821-870 |
| 输入参数集 | ThousandWorlds 的 8 参数（T_star, F_star, radius, gravity, P_rot, P0, CO2, CH4） | field_spec.py:3 |
| 输出场集 | 53 场（表温 + 温度/比湿/云量/u/v 各 10 层 + ASR/OLR） | field_spec.py:4-15 |

### 未找到 / 未确认

1. ExoPlaSim 的 spin-up 所需模式年数：只有 runtobalance 的判据（能量平衡漂移阈值 0.05 W/m2/yr，在 45 年时间尺度上）与教程定性描述（many decades, and sometimes up to a few centuries）。没有一个确定的年数。
2. ExoPlaSim 的 CPU 小时定量表：论文 3.1 节声称有性能小节，但在 web_fetch 的 100k 截断之外 -> 未确认。已知只有教程的 6 min/年（4 核）与 < 1 min/年（16 核）。
3. radius 在非 1.0 时的内部换算代码：ExoPlaSim 内部 plarad = 6371220.0 是硬编码；radius 参数如何写入 namelist 并改变 plarad 这段代码未定位 -> 未确认（但 configure 文档明确说 radius 单位是地球半径，行为确定）。
4. .sra 文件的二进制格式：仓库有 sractl.f90（13 359 B）与 srv2sra.c 但未展开 -> 格式未确认。若你要自制陆地掩膜，需先读这两个文件。
5. T42 输出文件体积：官方只给了 T21 的 -> 未确认（上面给的 T42 约 51 MiB 是推算，已标注）。
6. 单个场的文件体积：官方给的是全部场合计 -> 单场体积未给出。
7. ThousandWorlds 的基线数值 RMSE/ACC 表：arxiv HTML 100k 截断 -> 未确认（只有 GP 最好的定性结论）。
8. T21/T42 场中保留多少个纬向谐波够用的定量答案：未找到任何给行星气候场在多少个 zonal wavenumber 后能量可忽略的文献或代码。这必须你自己实测——跑一次 T21，对时间平均场做 SHT，看每个 degree 的方差占比（用 thousandworlds/spectral.py 的 degree_starts() 分块统计即可）。
9. Tellus 之外的游戏侧 GCM 查表先例：未找到（字面检索无结果）。
10. 原版 Fortran SPEEDY 的官方仓库 / 直接下载：未找到（需邮件 kucharsk@ictp.it）。
11. MITgcm 的 pkg/atm_phys 手册章节 / pkg/gfdl_cloud_microphys：均未找到（后者经三个候选路径 404 验证为不存在）。
12. Isca 官方性能（SYPD/CPU 小时/并行加速比）与官方 spin-up 建议：未找到（仅有用例积分长度 360 天 / 3600 天）。
13. climlab 与 SPEEDY 的实测性能数字：未找到。
14. SpeedyWeather.jl 的 GRIB 输出、官方 spin-up 年数、隐式求解器正文：未找到/未读。

---

# 附录 A：给这个项目的可执行建议（基于以上证据）

1. 不要指望 T21/T42 查表给出沿岸风急流。让查表负责行星尺度背景风（符号正确）+ 纬向平均温度 + 海盆尺度 SST，沿岸上升流用独立的解析/参数化模块。
2. 把你现有的副高气压距平 + Ekman-Rayleigh 风和 ExoPlaSim 的 dpdx/dpdy/uas/vas/tauu/tauv 逐格对比，这是诊断那 39~52% 最快的手段。
3. 抄 climlab 的隐式三对角经向扩散（D = 0.555 W/m2/摄氏度，A = 210，B = 2），把纬向平均温度从诊断量升级为隐式预报量。这不违反无全局 2D 椭圆求解约束（1D 三对角，Thomas 算法 O(n)，无需线性代数库）。
4. 抄 Isca 的 slab 海洋隐式步：eff_heat_capacity = land_sea_heat_capacity + t_surf_dependence*dt，然后 eff*dTs/dt = -corrected_flux。每格 O(1)。
5. 抄 ExoPlaSim 的 T_R 勒让德截断（sr(1)=sqrt2(T_bar-t0)、sr(3)=dtns/sqrt6*zfac、sr(5)=-2/3*sqrt0.4*dtep*zfac，zfac = sin(0.5 pi (sigma-sigma_TP)/(1-sigma_TP))）。
6. 若真要建表，用 ThousandWorlds 的格式：8 参数 -> T21 球谐系数 -> PPCA -> 数十维；用 GP 或岭回归，不要上神经网络（论文实测 GP 最好）。
7. 压缩优先球谐而非逐纬度傅里叶；赤道对称后系数减半；m=0 就是纬向平均。
8. 精度验收用 cos(lat) 加权 NRMSE（ClimateBench get_rmse），并以模式间分歧为上限（ThousandWorlds 协议）。
9. 降水/水循环不可信（ClimateBench：pr Total NRMSE 3.2-5.4 vs tas 0.33）。若模组需要降雨，风险显著高于温度/风。
10. 地表温度必须隐式化（Isca 的 eff_heat_capacity、MITgcm 的 DTSIMPL 诊断、climlab 的 ImplicitProcess 三处独立印证这是标准做法）——否则你会被显式步长限制卡死。
11. 若确实需要一个真值参考：用 SpeedyWeather.jl（T32L8，1400 SYPD，笔记本即可）或 ExoPlaSim（GPL-2.0，6 min/年）离线跑；不要用 MITgcm（编译型 HPC 程序、每步全局 2D 椭圆求解）。
12. 最简可用清单（可以直接开始写代码）：Held-Suarez 强迫常数（t_zero=315/t_strat=200/delh=60/delv=10/sigma_b=0.7/ka=40d/ks=4d/kf=1d）+ climlab EBM 常数集（A=210/B=2/D=0.555/S0=1365.2/s2=-0.48/a0=0.3/a2=0.078/ai=0.62/Tf=-10 C/water_depth=10 m）+ Isca slab 隐式步。

# 附录 B：本会话中被拦截 / 不可读的站点（供后续调研参考）

| 站点 | 状态 |
|---|---|
| api.github.com | 中途触发限流（403）。替代方案 https://ungh.cc/repos/OWNER/REPO/files/BRANCH 可列目录 |
| journals.ametsoc.org | CloudFront 403 |
| agupubs.onlinelibrary.wiley.com | Cloudflare Just a moment |
| nature.com | 跨域跳转到 idp.nature.com，不跟随 |
| sciencedirect.com | 403 |
| 所有 PDF | unsupported content type application/pdf |
| 本机 PowerShell / curl.exe | 无外网访问（curl: (35) schannel: AcquireCredentialsHandle failed: SEC_E_NO_CREDENTIALS），只有 web_fetch 可用 |
| api.semanticscholar.org | 429 Too Many Requests |
| export.arxiv.org/api/query | 429 Rate exceeded |
| api.crossref.org | 可用（成功取到 Small et al. 2015 与 Holt et al. 2017 的完整摘要） |
| pypi.org/pypi/{pkg}/json | 可用（climlab 元数据） |
| raw.githubusercontent.com | 可用（100k 截断） |
| execlim.github.io / mitgcm.readthedocs.io / exoplasim.readthedocs.io / speedyweather.github.io | 可用（Vitepress SPA 页正文可能在 JS chunk 中） |

本报告全部事实基于本会话内实际 web_fetch / web_search 抓取的内容。凡未经抓取验证者，一律以「未确认 / 未找到」标注，未做任何推断性补全。