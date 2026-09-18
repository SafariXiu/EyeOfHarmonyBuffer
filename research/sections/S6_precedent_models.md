# S6 — Precedent models (intermediate complexity, documented enough to copy)

**Target:** a *steady-state, diagnostic* 2-D (lon x lat) atmospheric model that must reproduce, **as emergent results**, (a) a summer monsoon over a heated continent and (b) a dry subtropical desert over a continent next to a cooler sea.

**"Steady-state solvable?" key:** **(a)** published as a steady problem — solvable directly, no spin-up; **(b)** initial-value problem — must time-step to a statistical/quasi-steady state; **(c)** both routes available.

> **WARNING — several citations in the task brief are wrong, and one coefficient does not exist.** Verified against Crossref, OpenAlex and the retrieved primary PDFs. See §0 before copying any DOI.

## Summary table

| Model | Year | PRESCRIBED | SOLVED | Grid | Cost | Steady-state? |
|---|---|---|---|---|---|---|
| [Held & Suarez 1994](#1-held--suarez-1994--bams-not-mwr) | 1994 | Newtonian relaxation to $T_{eq}(\sigma,\phi)$; Rayleigh drag $k_v(\sigma)$; **zonally symmetric only**; no topography, moisture or surface fluxes | dry primitive equations, **prognostic** | none prescribed (T63 spectral, G72 grid used) | benchmark-scale | **(b)** (paper targets *statistically* steady states) |
| [Frierson, Held & Zurita-Gotor 2006/07](#2-frierson-held--zurita-gotor-20062007) | 2006/07 | gray optical depth $\tau(\sigma,\phi)$; surface-only solar $R_S(\phi)$; slab $C_O$; MOS drag | PE + **prognostic** $q$, $T$, slab $T_s$ | spectral triangular, T42/T85/T170, 25 $\sigma$-levels | 1080 days/run | **(b)** |
| [Zebiak & Cane 1987](#3-zebiak--cane-1987) | 1987 | SST to heating; ocean params | **atmosphere DIAGNOSED**: linear **steady** shallow water. Ocean + SST **prognostic** | ocean basin 124E-80W, 29S-29N; **atmos. grid NOT stated** | not stated | **(c) mixed** |
| [Neelin & Zeng 2000 / QTCM1](#4-neelin--zeng-2000--qtcm1) | 2000 | Galerkin vertical structure functions; radiation, land, convection | **prognostic** $T_1,q_1,v_1,\zeta_0,T_s$; $\omega$, precip. diagnosed; barotropic divergence specified | Arakawa C-grid, **64 x 42, 5.625 deg x 3.75 deg** | **~1.5-5 min CPU per model-year** | **(b)** released; **(a)** explicitly *"well posed"* and multigrid-tested |
| [PlaSim / PUMA](#5-plasim-and-puma) | 2005 | PUMA: Rayleigh friction + Newtonian cooling (Held-Suarez style); PlaSim: moist physics | dry (PUMA) / moist (PlaSim) PE, **prognostic** | PUMA T21/T31/T42/T85, any levels; PlaSim T21L5-T42L10 | PUMA **~2 min/sim-year**; PlaSim **~10 min/sim-year** (3 GHz P4) | **(b)** |
| [Moist EBMs](#6-moist-energy-balance-models) | 1984-2018 | OLR, insolation, diffusivity $D$, BCs | **diagnostic/steady** MSE diffusion | 1-D in latitude | seconds | **(a)** |
| **QUADM** | — | — | — | — | — | **NOT FOUND (§7)** |
| **LDMZ** (= LMDZ?) | 2006/2020 | full physics | **prognostic** 3-D PE | finite-difference zoom grid | full GCM | **(b)** |
| [**LBM / SWM** (Watanabe & Kimoto)](#8-linear-stationary-wave--linear-baroclinic-models) | 2000 | basic state; diabatic + transient-eddy forcing; damping | **linear** perturbation PE, solved by **direct matrix inversion** | T21, L5/L8/L11/L20; SWM mode block-diagonal by zonal wavenumber | full T21L20 = 29643 squared (>7.5 GB); SWM blocks 2562 squared; T21L11m10 ~15 min (SR8000) | **(a)** exact steady solve |
| [**Huang & Gambo 1983**](#huang--gambo-1983--a-steady-linear-qg-model-forced-by-summer-heat-sources) | 1983 | topography; **summer stationary heat sources**; basic zonal wind | **steady-state, linear, quasi-geostrophic**, 34-level, spherical | hemispheric, 34 layers to 92 km | small (steady solve) | **(a)** |
| [Chen 2001](#chen-2001--analytic-linear-qg-stationary-waves) | 2001 | analytic heating; uniform $\bar u$ | **linear steady QG**, analytic | beta-plane channel, analytic | closed form | **(a)** |
| [Rodwell & Hoskins 1996/2001](#9-the-monsoon-desert-mechanism) monsoon-desert | 1996/01 | monsoon heating (25N 90E, 400 hPa, 5 K/day); mountains; Newtonian relaxation | **nonlinear, time-dependent** hydrostatic PE | T31, 15 $\sigma$-levels | quasi-steady at days 15-16 | **(b)** |
| [Gill 1980](#gill-1980) | 1980 | heating $Q$; Rayleigh friction = Newtonian cooling $\epsilon$ | **linear steady** shallow water, analytic | analytic beta-plane | closed form | **(a)** |

## 0. Citation corrections (verified)

| Brief said | Verified reality |
|---|---|
| Held & Suarez (1994) **MWR 122**, 1829-; doi 1520-0493(1994)122<1829:APFITC> | **Bull. Amer. Meteor. Soc. 75(10), 1825-1830**; doi 10.1175/1520-0477(1994)075<1825:APFTIO>2.0.CO;2. The brief's DOI returns **404** from Crossref *and* OpenAlex. Real PDF retrieved via Wayback (snapshot 20220425015644). |
| "older Held & Suarez (1983) **book chapter**" | **No such chapter verified.** HS94's own reference list has **no** Held & Suarez 1983. The nearby 1983 items: **(i)** Arakawa & Suarez (1983), *Vertical differencing of the primitive equations in sigma coordinates*, MWR **111**, 34-45; **(ii)** Held (1983), *Stationary and quasi-stationary eddies in the extratropical troposphere: theory*, in Hoskins & Pearce (eds), *Large-Scale Dynamical Processes in the Atmosphere*, Academic Press, 127-168 — **single-author**. |
| "the **60-day drag**" | **Does not exist in HS94.** Grepped the PDF: the only "60" is $(\Delta T)_y = 60$ K. Damping is $k_a = 1/40$ per day, $k_s = 1/4$ per day, $k_f = 1$ per day. |
| FHZ **Part II** = doi 10.1175/JAS3935.1 | Part II is **doi 10.1175/JAS3913.1**, JAS **64**, 1680-1693 (2007). doi JAS3935.1 is a **different single-author** paper: **Frierson (2007)**, JAS **64**, 1959-1976 — **and that is where the convection schemes live**; Part I has none. |
| Neelin & Zeng (2000) JAS 57, **2955-2972** | **JAS 57(11), 1741-1766**, doi 10.1175/1520-0469(2000)057<1741:AQETCM>2.0.CO;2. Companion **Zeng, Neelin & Chou (2000)**, JAS **57**, 1767-1796, doi ...057<1767:AQETCM>2.0.CO;2. Both verified from the PDFs' own running heads. |
| PlaSim doi 10.1007/s00382-005-0034-8 | **Does not resolve.** Correct: Meteorol. Z. **14**, 299-304, doi 10.1127/0941-2948/2005/0043; companion "Green planet and desert world" **14**, 305-314, doi 10.1127/0941-2948/2005/0044. |
| Ting & Held (1990), JAS 47, 495-515, *On the Stationary Flow of a Nonlinear Stationary Wave Model* | **DOI 404s; the title could not be found anywhere.** JAS 47(4), 495-500 is **Held & Ting (1990)**, *Orographic versus Thermal Forcing of Stationary Waves: The Importance of the Mean Low-Level Wind* (suffix OVTFOS, not OVTFOA). Actual **Ting & Held (1990)** = JAS **47**(21), 2546-2566, doi ...047<2546:TSWRTA>2.0.CO;2. NSWM = **Ting, Wang & Yu (2001)**, JAS **58**(16), 2331-2354. |
| Watanabe & Kimoto (2000) **JAS** | **Q. J. R. Meteorol. Soc. 126(570), 3343-3369**, doi 10.1002/qj.49712657017; Corrigendum QJRMS **127**, 733-734. JAS 57, 2621-2639 are ship-track aerosol papers. |
| "Dry Linear Baroclinic Model (DLBM)" | **Does not exist.** The LBM *is* dry; the named variant is the **moist LBM (mLBM)**, Watanabe & Jin (2003), J. Climate **16**(8), 1121-1139. |
| "Linear Atmospheric Model intercomparison" | **Not found.** No evidence such a project exists. |

## 1. Held & Suarez (1994) — BAMS, not MWR

**Citation:** Held, I. M., and M. J. Suarez, 1994: *A proposal for the intercomparison of the dynamical cores of atmospheric general circulation models.* **Bull. Amer. Meteor. Soc. 75(10), 1825-1830.** [doi:10.1175/1520-0477(1994)075<1825:APFTIO>2.0.CO;2](https://doi.org/10.1175/1520-0477(1994)075%3C1825:APFTIO%3E2.0.CO;2)

**PRESCRIBED** (verbatim from the paper's p. 1826 parameter box; independently corroborated by the Basilisk implementation citing "Held & Suarez p. 1826 and figure 1.b"):

    dv/dt = -(f k x v) - grad(Phi) - k_v(sigma) v
    dT/dt = ...                   - k_T(sigma) [ T - T_eq(sigma,phi) ]

    T_eq = max{ 200 K ,
        [ 315 K - (dT)_y sin^2(phi) - (dtheta)_z log(p/p_0) cos^2(phi) ] (p/p_0)^kappa }

    k_T = k_a + (k_s - k_a) max[ 0 , (sigma - sigma_b)/(1 - sigma_b) ] cos^4(phi)
    k_v = k_f               max[ 0 , (sigma - sigma_b)/(1 - sigma_b) ]

    sigma_b = 0.7      k_a = 1/40 day^-1    k_s = 1/4 day^-1    k_f = 1 day^-1
    (dT)_y  = 60 K     (dtheta)_z = 10 K    p_0 = 1000 mb
    kappa = R/c_p = 2/7                     c_p = 1004 J kg^-1 K^-1
    Omega = 7.292e-5 s^-1   g = 9.8 m s^-2  a = 6.371e6 m

$\sigma = p/p_s$ uses the **instantaneous** surface pressure "so that this 'boundary layer' will follow the topography in future calculations in this series". $k_v = 0$ for $\sigma$ at or below 0.7.

**SOLVED:** dry primitive equations, **prognostically**. The paper's own framing: *"benchmark calculations for the evaluation of **statistically steady states**"*. No steady solver exists. **Grid:** none prescribed — "Nothing is said as to whether the flow is or is not hydrostatic... The choice of upper boundary condition is also left open."

**CRITICAL FOR OUR TASK (verbatim, HS94 + Frierson's course notes):** no topography ("the surface is at constant geopotential"), no moisture, no surface fluxes, and $T_{eq}$, $k_T$, $k_v$ are **functions of latitude only**. Frierson's lecture notes: *"Weaknesses: No surface fluxes, **no possibility of land-sea contrast**... Says nothing about precipitation... Tropics are very quiet."* **HS94 can never produce a monsoon or a desert**; it is only a relaxation-forcing template.

Related older work: Held & Suarez (1978), *A two-level primitive equation atmospheric model designed for climatic sensitivity experiments*, JAS **35**, 206-229.

## 2. Frierson, Held & Zurita-Gotor (2006/2007)

**Part I:** JAS **63**, 2548-2566 (2006), [doi:10.1175/JAS3753.1](https://doi.org/10.1175/JAS3753.1) · **Part II:** JAS **64**, 1680-1693 (2007), [doi:10.1175/JAS3913.1](https://doi.org/10.1175/JAS3913.1) · **Convection schemes:** Frierson (2007), JAS **64**, 1959-1976, [doi:10.1175/JAS3935.1](https://doi.org/10.1175/JAS3935.1)

Most copyable documented moist model. Equations **verbatim from Part I** (printed numbering).

    C_O dT_s/dt = R_S - R_Lu + R_Ld - L_v E - S                                (1)  slab mixed layer
    R_S = R_S0 [ 1 + Delta_s P_2(sin phi) ]                                    (2)
    P_2(phi) = (1/4)[ 1 - 3 sin^2(phi) ]                                       (3)
    tau_0(phi) = tau_e + ( tau_p - tau_e ) sin^2(phi)                          (4)  gray optical depth
    tau = tau_0 [ f_l (p/p_s) + (1 - f_l) (p/p_s)^4 ]                          (5)
    dU/dtau = U - B ,  dD/dtau = B - D ,  B = sigma_SB T^4                     (6),(7)
    Q_R = -(1/(c_p rho)) d(U - D)/dz                                           (8)
      BCs: U(tau=0) = sigma_SB T_s^4 ; D(tau=0) = 0
    tau_vec = rho_a C |v_a| v_a ; S = rho_a c_p C |v_a| (theta_a - theta_s)
    E = rho_a C |v_a| (q_a - q*_s)                                             (9)-(11)
    C = kappa^2/[ln(z_a/z_0)]^2 ;  times (1 - Ri_a/Ri_c) for 0<Ri_a<Ri_c ;  0 for Ri_a>Ri_c   (12)-(14)
    Ri_a = g z [theta_v(z_a) - theta_v(0)] / ( theta_v(0) |v(z_a)|^2 )         (15)
    h = height where Ri(z) exceeds Ri_c                                        (16)
    K(z) = K_b(z)                                            for z < f_b h     (17)
    K(z) = K_b(f_b h)(z/(f_b h))[1 - (z-f_b h)/((1-f_b)h)]^2 for f_b h<=z<=h    (18)
    K_b(z) = kappa u_a/(C z)                                 for Ri_a < 0      (19)
    K_b(z) = kappa u_a/(C z) {1 + (Ri/Ri_c)ln(z/z_0)/(1 - Ri/Ri_c)}^-1  for Ri_a>0  (20)
    delta_q = (q* - q) / ( 1 + (L_v/c_p) dq*/dT )                              (21)
    e*(T) = e*_0 exp[ -(L_v/R_v)(1/T - 1/T_0) ] ;  q* = epsilon e*/p           (22)

**Table 1 control values:** $R_{S0}=938.4$, $\Delta_s=1.4$, $\tau_e=6$, $\tau_p=1.5$, $f_l=0.1$, $C_O=10^7$, $f_b=0.1$, $\kappa=0.4$, $z_0=3.21\times10^{-5}$ m, $Ri_c=1$, $c_p=1004.64$, $R_d=287.04$, $L_v=2.5\times10^6$, $R_v=461.5$, $e^*_0=610.78$ Pa at 273.16 K, $p_{s0}=10^5$ Pa.

**Convection:** *"the model can also be run with large-scale condensation only"* — **Part I uses no convection scheme**; schemes (incl. a simplified Betts-Miller) are in Frierson (2007). Re-evaporation is extreme: the column must be saturated all the way down for rain to reach the ground. $e^*_0$ (the $\alpha e^*_0$ family, $\alpha=0..10$) is the moisture knob.

**Core:** Eulerian spectral; **25 $\sigma$-levels**, $\sigma_c=\exp[-5(0.05 z\tilde{} + 0.95 z\tilde{}^3)]$; PPM vertical $q$ advection. T42 departs significantly from T85/T170; 1080-day runs, 360-day spin-up. **(b) requires time stepping.**

## 3. Zebiak & Cane (1987)

**Citation:** Mon. Wea. Rev. **115**, 2262-2278, [doi:10.1175/1520-0493(1987)115<2262:AMENO>2.0.CO;2](https://doi.org/10.1175/1520-0493(1987)115%3C2262:AMENO%3E2.0.CO;2). Full 17-pp scan (incl. Appendix) retrieved from the Columbia Academic Commons deposit via Wayback. *ZC87 is a scan; equations were re-read from rendered page images and cross-checked against the OCR layer.*

**Architecture (verbatim, section 2a):** *"The dynamics follow **Gill (1980)**, i.e., **steady-state, linear shallow-water equations on an equatorial beta plane**. Linear dissipation in the form of Rayleigh friction and Newtonian cooling is used. The circulation is forced by a heating anomaly that depends partly on local heating associated with SST anomalies and partly on the low-level moisture convergence."*

**ATMOSPHERE — Appendix (A1)-(A3), p. 2277. Linear, steady, reduced-gravity shallow water; ONE coefficient epsilon on all three equations; no time derivative anywhere:**

    (A1)  epsilon*u_a^n - beta_0*y*v_a^n = -(p^n/rho_0)_x
    (A2)  epsilon*v_a^n + beta_0*y*u_a^n = -(p^n/rho_0)_y
    (A3)  epsilon*(p^n/rho_0) + c_a^2[(u_a^n)_x + (v_a^n)_y] = - Q_s - Q_I^(n-1)

    (A3a) Q_s   = (alpha*T) * exp[ (Tbar - 30 degC) / 16.7 degC ]     <-- SST to heating
    (A3b) Q_I^n = beta * [ M(cbar + c^n) - M(cbar) ]                  <-- convergence feedback
    (A3c) M(x)  = 0 if x <= 0 ;  = x if x > 0                         <-- ramp (nonlinearity)
    (A3d) c^n   = -(u_a^n)_x - (v_a^n)_y

Tbar = **prescribed monthly-mean SST** (in the exponent; sets where the atmosphere is most responsive); T = **anomalous SST** (prefactor). The index n is an **iteration index, not time**: Q_I uses the previous iterate, so the system is solved as a **steady fixed point**. The Appendix attributes the heating to Zebiak (1986), MWR **114**, 1263-1271 (not retrieved, so **UNVERIFIED** in its own words).

**Parameters (p. 2278, verbatim):** epsilon = 1/(2 days), $c_a = 60$ m/s, alpha = 0.031 m^2 s^-3 per degC, beta = 1.6e4 m^2 s^-2, r = 1/(2.5 years), $c=(g'H)^{1/2}=2.9$ m/s, $H=150$ m, $H_1=50$ m, $r_s=1/(2$ days$)$, $\alpha_s=1/(125$ days$)$, gamma = 0.75, $T_1=28$ degC, $T_2=-40$ degC, $b_1=1/(80$ m$)$, $b_2=1/(33$ m$)$.

**OCEAN (prognostic, (A4)-(A6)):** $u_t - \beta_0 y v = -g'h_x + \tau^x/(\rho H) - ru$ ; $\beta_0 y u = -g'h_y + \tau^y/(\rho H) - rv$ ; $h_t + H(u_x+v_y) = -rh$. Plus a 50 m frictional surface layer (A8)-(A9) and entrainment (A10). SST (A11)-(A13): advection by anomalous + mean currents, ramp-gated upwelling acting on the mean vertical gradient, entrainment $T_e=\gamma T_{sub}+(1-\gamma)T$, and $-\alpha_s T$ damping.

**Coupling/cost (verbatim):** *"**The ocean dynamics time step is 10 days.**"*; the atmosphere is re-solved steadily about once per month; *"time dependence only in the moisture convergence component of the heating"*; *"a maximum of three feedback iterations is performed at each time step."* Explicit atmospheric time dependence was rejected because *"a time step of order 2 h would be required for inertial gravity waves."* **Neither the grid nor any cost figure is stated in ZC87 (UNVERIFIED).** Only the basin is given: 124E-80W, 29N-29S. **(c) MIXED: atmosphere (a), ocean/SST (b).**

## 4. Neelin & Zeng (2000) / QTCM1

**Citations:** Neelin & Zeng, JAS **57**(11), 1741-1766 (2000), [doi:10.1175/1520-0469(2000)057<1741:AQETCM>2.0.CO;2](https://doi.org/10.1175/1520-0469(2000)057%3C1741:AQETCM%3E2.0.CO;2) · Zeng, Neelin & Chou, JAS **57**, 1767-1796 (2000), [doi:10.1175/1520-0469(2000)057<1767:AQETCM>2.0.CO;2](https://doi.org/10.1175/1520-0469(2000)057%3C1767:AQETCM%3E2.0.CO;2) · **QTCM1 v2.3 manual** (86 pp, 2002): <https://csdms.colorado.edu/csdms_wiki/images/Qtcm_manv2.3.pdf> · Lin, GMD **2**, 1-11 (2009), [doi:10.5194/gmd-2-1-2009](https://doi.org/10.5194/gmd-2-1-2009)

**Vertical structure (Galerkin), NZ2000 (3.6)-(3.11):**

    T = T_r(p) + SUM_k a_k(p) T_k      q = q_r(p) + SUM_k b_k(p) q_k      v = SUM_k V_k(p) v_k
    V_0(p) = 1                                    (barotropic)
    V_1(p) = a_1^+(p) - abar_1^+ ,  a_1^+(p) = INT_p^{p_rs} a_1(p') d ln p'   (baroclinic)
    a_1(p) = A_1(p, T_r^c)   <-- the Betts-Miller QE moist-adiabat perturbation shape
    phi_s1 = -kappa * ahat_1^+ * T_1 ,  kappa = R/c_p                          (4.10)-(4.11)

$p_T = p_{rs}-p_{rt}$ is the reference tropospheric pressure depth (NZ2000 Table 1: about 8e4 Pa; QTCM1 v2.3 integrates 1000 to 150 mb, so $p_T=850$ mb). QTCM1 v2.3 code values: a1hat=0.45934841, a1phat=0.24520899, a1s=0.30203986, V1s=-0.24520899, b1hat=0.31574178, b1s=1.0, bb1hat (= B_1) = 0.37340307, Trefhat=267.77045 K.

**Moisture equation and QE closure:**

    d_t qhat + Dtilde_q q - M_q1 * div(v_1) = <Q_q> + (g/p_T) E              (4.20)
    M_q1 = p_T^-1 INT Omega_q1 d_p q  dp         (gross moisture stratification)  (4.21)
    M_1  = M_S1 - M_q1                           (gross moist stability)      (5.9)
    Q_q  = (q_c - q)/tau_c ;  q_c = alpha_sub(p) q_sat(T_c) ;  q_c = q_r^c + B_1 T_1^c   (2.21)-(2.23)
    -<Q_q> = <Q_c> = epsilon_c^* (q_1 - T_1)                                  (5.5)
    epsilon_c = H(C_1)/tau_c ;  epsilon_c^* = ahat_1 bhat_1 (ahat_1+bhat_1)^-1 epsilon_c   (5.7),(5.8)
    C_1 = (Ahat_1 T_1^c - ahat_1 T_1) + (That_r^c - That_r)      (CAPE proxy)   (4.28)

**"beta_q" DOES NOT EXIST** in NZ2000, ZNC2000 or the QTCM1 manual. The relevant symbols are $b_1$, $B_1$, $\alpha_{sub}$, $M_{q1}$, $\epsilon_c$. Do not cite a "betaq closure" for QTCM.

**Prognostic:** $T_1$ (5.3), $q_1$ (5.4), $v_1$ (5.1), $\zeta_0/\psi_0$ (5.2), surface $T_s$, cloud fraction. **Diagnostic:** $\omega(p)$ from continuity (4.1)-(4.3); div($v_0$) is **specified** (zero without topography), so the barotropic mode is prognostic only in its rotational part; $\phi_{s0}$, precipitation, CAPE and $dh_b$ are diagnosed postsolution. The baroclinic divergence — hence $\omega$ and precipitation — **is obtained from the column moist static energy equation (5.6) = (5.3)+(5.4)**, with $M_1$ as the effective static stability. Verbatim (section 5b): *"Within convective regions, to the extent that QE ties q1 to T1, (5.6) contains all thermodynamic information necessary to solve for large scales."*

**Grid/time step/cost (manual, verbatim):** 64 x 42, **5.625 deg lon x 3.75 deg lat**, 78.75S-78.75N, periodic in lon, walls in lat, Arakawa C-grid; barotropic in vorticity/streamfunction (Adams-Bashforth), baroclinic forward-backward; *"The numerical CFL instability criterion limits the model time-step at about 20 minutes"*, standard dt = 1200 s. Cost: *"approximately 5 minutes of CPU time to run one year of model simulation on a Sun Ultra 80 workstation (1.5 minutes on a Pentium-4/Linux workstation)."*

**Steady-state? (b) as released, but (a) is explicitly endorsed** — NZ2000 section 7d(1), verbatim: *"**The steady solution to these equations is well posed** and can be a reasonable approximation to the full solution in the Tropics under some circumstances... We have **tested steady solvers for versions of the model, using multigrid methods, and obtained reasonable solutions for idealized cases.** For the particular implementation tested, we did not obtain sufficient increase of solution speed compared to time integration to justify the added code complexity."* Section 7d also blesses dropping all advection terms, turning off midlatitude eddies, and neglecting the $\epsilon_{01}v_0$ term to decouple the baroclinic mode.

## 5. PlaSim and PUMA

- Fraedrich, Jansen, Kirk, Luksch & Lunkeit (2005), Meteorol. Z. **14**, 299-304, [doi:10.1127/0941-2948/2005/0043](https://doi.org/10.1127/0941-2948/2005/0043) · *Green planet and desert world*, **14**, 305-314, [doi:10.1127/0941-2948/2005/0044](https://doi.org/10.1127/0941-2948/2005/0044) · Fraedrich, Kirk, Luksch & Lunkeit (2005), **14**, 735-745, [doi:10.1127/0941-2948/2005/0074](https://doi.org/10.1127/0941-2948/2005/0074) · Fraedrich, Kirk & Lunkeit (1998), DKRZ Report **16**.

**Verbatim model descriptions (distributed with the packages):**

> **PUMA** — *"a very fast dynamical core... a spectral model with triangular truncation. It solves the Primitive Equations on sigma-coordinates... Possible horizontal resolutions are T21, T31, T42, and T85. The vertical resolution is an arbitrary number of levels. **The parameterisations are Rayleigh friction and Newtonian cooling.** ... usually run without orography as an 'Aqua Planet'... **It simulates the dry atmosphere... There are no moist processes and no boundary layer.** ... **one year of simulation in 2 minutes**"* (single 3 GHz Pentium-IV). Fewer than 4000 Fortran lines.

> **PlaSim** — the atmospheric module is **PUMA-2**, which *"solves the **moist** Primitive Equations on sigma-coordinates... **Resolutions range from T21 and five levels ... to T42 and ten levels** ... Included are **boundary layer, precipitation, interactive clouds and radiation.**"* Plus a **mixed layer ocean**, thermodynamic sea ice and the **SimBA** biosphere. *"**one year of simulation in 10 minutes**"*.

**PUMA's forcing is explicitly the Held-Suarez scheme** (User's Guide, Puma_UG_17/introduction.tex, verbatim): *"The PUMA code is the dynamical core of a GCM forced by **Newtonian cooling and Rayleigh friction, such as that proposed by Held & Suarez (1994)** to evaluate the dynamical cores of GCMs."* Damping namelist (Puma_UG_17/namelist.tex): **tfrc = Rayleigh friction timescale per level in days (default 0,0,...,1)**; **restim = Newtonian restoration timescale per level (default 15.0)**; t0k = 250.0 K reference profile; nlev = 10. Extra top-level Newtonian cooling with a default 10-day timescale.

PlaSim horizontal diffusion is spectral and scale-selective: $\partial X_n/\partial t = -k_X L_n X_n$, $L_n = (n-n_\star)^\alpha$ for $n>n_\star$, defaults $n_\star=15$, $\alpha=2$, with smallest-wave damping times $\tau_D=0.2$ d, $\tau_\xi=1.1$ d, $\tau_T=15.6$ d.

**(b) requires time stepping** for both — but PUMA is the closest thing to a ready-made *"prescribe T_eq and drag, get a circulation"* configuration, and at ~2 min/sim-year it is a cheap **oracle for validating our steady solutions**.

## 6. Moist energy balance models

Siler, Roe & Armour (2018), J. Climate **31**, 7481-7493, [doi:10.1175/JCLI-D-18-0081.1](https://doi.org/10.1175/JCLI-D-18-0081.1) · Flannery (1984), JAS **41**, 414-421, [doi:10.1175/1520-0469(1984)041<0414:EBMITO>2.0.CO;2](https://doi.org/10.1175/1520-0469(1984)041%3C0414:EBMITO%3E2.0.CO;2) · Rose & Ferreira (2013), J. Climate **26**, 2117-2136, [doi:10.1175/JCLI-D-11-00547.1](https://doi.org/10.1175/JCLI-D-11-00547.1) · dry baseline: Budyko (1969), *Tellus* **21**, 611-619; Sellers (1969), *J. Appl. Meteor.* **8**, 392-400.

**Siler, Roe & Armour (2018) — equations retrieved verbatim from the PDF.** It is an **elliptic (diffusive) boundary-value problem in latitude**, fully diagnostic: there is **no $dT/dt$ anywhere**, so it is a steady BVP by construction. With $x = \sin\phi$, $h = c_pT + Lq$ (near-surface MSE) and a **constant** diffusivity $D$:

    Q_net(x) = (1/(2 pi a^2)) dF/dx                                            (1)
    F(x)     = -(2 pi p_s/g) D (1 - x^2) dh/dx                                 (2)
    Q_net(x) = -(p_s/(g a^2)) D d/dx [ (1 - x^2) dh/dx ]                       (3)  [= (1)+(2)]
    F_HC(x)   = w(x) F(x) ;   F_eddy(x) = [1 - w(x)] F(x)                      (4),(5)
    w(x) = exp( -x^2 / sigma_x^2 ) ,   sigma_x = 0.3                           (6)
    F_HC(x)   = psi(x) g(x) ;  g(x) ~= h_T - h(x)                              (7),(8)
    F_HC,q(x) = -psi(x) L q(x)                                                 (9)
    R_f(x) - G'(x) + lambda(x) T'(x) = (1/(2 pi a^2)) dF'/dx                   (12)
    F'(x) = -(2 pi p_s/g) D (1 - x^2) dh'/dx ,   h' = c_p T' + L q'            (13)

**Prescribed:** $Q_{net}(x)$ directly from ERA-Interim / CMIP5 (there is **no explicit OLR parameterisation at all** — all radiative physics is absorbed into the imposed net column heating); $D = 1.16\times10^6\ \mathrm{m^2\,s^{-1}}$ uniform and fixed; **RH fixed at 80%**, so $q$ is a *diagnostic single-valued function of $T$*; $h_T = 1.06\,h(0)$; and in the perturbation runs $R_f$, $G'$, $\lambda$ (idealised: $R_f = 8\ \mathrm{W\,m^{-2}}$, $\lambda = -1.5\ \mathrm{W\,m^{-2}\,K^{-1}}$, $G' = 0$). **Solved:** $h(x)$ hence $T(x)$; then $F$, $F_{HC}$, $F_{eddy}$, $\psi$; then $F_q$; then $E-P = -(1/(2\pi a^2)) dF_q/dx$. The $(1-x^2)$ factor enforces vanishing flux at the poles automatically (an explicit BC statement is UNVERIFIED). Grid/cost are **not stated in the paper** — but the cost is trivially that of a 1-D elliptic solve. **Classification: (a) steady-state solvable, unambiguously.**

**The "moist" ingredient to copy:** $h = c_pT + Lq$ with **RH fixed at 80%**, so moisture is *slaved diagnostically to T* — no prognostic moisture equation and no separate moisture transport. That is the crucial simplification for a coarse 2-D model.

**Budyko/Sellers dry baseline** (originals not retrieved; form double-sourced from Stocker, *Introduction to Climate Modelling*, Eqs. (4.9)-(4.10), and the UCL/ELIC EBM page):

    h rho c dT/dt = (h/(R cos phi)) d/dphi[ (rho c K(phi)/R) (dT/dphi) cos phi ]
                    + (1 - alpha(phi))/4 S(phi) - eps(phi) sigma T^4           (4.9)
    BC:  dT/dphi = 0  at phi = +-pi/2                                          (4.10)
    S(phi) = S_0 (0.5294 + 0.706 cos^2 phi) ;  Sellers: alpha(T) = 0.3 - 0.009(T - 283 K)/K

Setting $dT/dt = 0$ makes it a steady elliptic BVP with Neumann BCs; the only nonlinearity is $\alpha(T)$, so a steady solve needs a Picard/Newton iteration (the classic ice-albedo multiple-equilibria problem). Sellers' original was, per Siler et al. (2018), *"a primitive 10-box EBM [that] included the advection of latent and sensible heat by the mean meridional wind, which he fit to observations."*

**Rose & Ferreira (2013) two-box EBM** (retrieved from MIT DSpace) — the cleanest explicit **thermal vs latent** transport split I found (their Eq. (3), attributed by them to Langen & Alexeev 2007 and Caballero & Langen 2005, **not** to Flannery):

    delta_b_e - F'_a = OLR'_e ;  delta_b_p + F'_a = OLR'_p                     (2)
    F'_a = gamma_d DeltaT' + gamma_lh T'_e ,   DeltaT' = T'_e - T'_p           (3)
    OLR'_e = B T'_e ;  OLR'_p = B T'_p - alpha delta_b_p                       (4)
    (gamma_d, gamma_lh) = (1.6, 0.8) W m^-2 K^-1 ;  Gamma = 2 gamma_d + gamma_lh + B

i.e. **dry static energy fluxes down the temperature gradient, latent heat scales with the absolute subtropical source temperature**. A 2x2 linear algebraic solve — **steady, analytic.** **Flannery (1984)** keeps **separate** thermal and latent energy transports — *the original is bronze-OA and free but Cloudflare-blocked here, so its equations are UNVERIFIED (see section 12)*. **Rose & Ferreira (2013)** is a two-box EBM solved by 2x2 linear algebra (steady, analytic).

**All of these carry NO momentum dynamics**, so none can produce a monsoon or a desert circulation on its own. Their value to us is the **moisture closure** ($h = c_pT + Lq$, fixed RH) and the confirmation that a **diagnostic, time-derivative-free** formulation of a moist transport problem is standard, publishable practice.

## 7. QUADM and LDMZ — honest negative result

**QUADM: NOT FOUND.** Searches for "QUADM model atmosphere", "QUADM quasi atmospheric model", "QUADM atmospheric general circulation model acronym" and "QUADM climatology OR meteorology model grid" returned **no atmospheric-science result of any kind** (the only hits are an unrelated healthcare company, quadmshops.com). **There is no authoritative definition of a "QUADM" atmospheric model that I could find, and I will not guess one.**

**LDMZ: almost certainly a typo for LMDZ** (Laboratoire de Meteorologie Dynamique **Zoom**), the atmospheric GCM of the IPSL Earth-system model: Hourdin et al. (2006), Clim. Dyn. **27**, 787-813, [doi:10.1007/s00382-006-0158-0](https://doi.org/10.1007/s00382-006-0158-0); Hourdin et al. (2020), JAMES **12**, e2019MS001892, [doi:10.1029/2019MS001892](https://doi.org/10.1029/2019MS001892). It is a **full 3-D finite-difference GCM** with a stretched "zoom" grid — **(b) requires time stepping**, not intermediate complexity, and **not a useful precedent**. Recommend dropping it from the design space.

## 8. Linear stationary wave / linear baroclinic models

### Huang & Gambo 1983 — a steady linear QG model forced by *summer* heat sources

Huang, R.-H., and K. Gambo, 1983: *The Response of a Hemispheric Multi-Level Model Atmosphere to Forcing by Topography and Stationary Heat Sources in Summer.* **J. Meteor. Soc. Japan 61(4), 495-509.** [J-STAGE, open access](https://www.jstage.jst.go.jp/article/jmsj1965/61/4/61_4_495/_pdf) — **retrieved and read**.

**This is the closest published precedent to our target.** Verbatim: *"The stationary planetary waves responding to forcing by topography and stationary heat sources in summer are investigated by means of a **steady-state, linear, quasi-geostrophic, 34-level model, with Rayleigh friction, the effect of Newtonian cooling and the horizontal kinematic thermal diffusivity** included in a spherical coordinate system."* The governing equations are *"the steady state, linear, quasi-geostrophic vorticity and thermodynamic equations"* in (lambda, phi, p).

Verified parameters: Ekman friction coefficient $F = 4\times10^{-6}$ per s; $p_s = 1000$ mb; model top $z = 92$ km ($p_t = 8.459\times10^{-4}$ mb) divided into **34 layers**; the static stability parameter from the mean July temperature and density at **45N** (U.S. Standard Atmosphere 1966), assumed latitude-independent; basic zonal wind from Murgatroyd et al. (1969), smoothed; $H_0 = 7$ km, $N = 2\times10^{-2}$ per s.

**Result (verbatim):** *"The amplitude of stationary planetary waves responding to forcing by heat sources is **larger than** that responding to forcing by topography."* And: the summer response *"is mainly confined to the troposphere over the subtropics"*, with a maximum in the upper troposphere near **30N** for $k=1,2$; the refractive-index square is **negative** in the summer stratosphere, so stationary waves **cannot propagate vertically** in summer. Only $k=1..3$ were computed; the authors flag $k=4..6$ as important for summer resonance. **The explicit algebra of eqs. (1)-(2) is rendered as images in the PDF, so it is UNVERIFIED.** The solution method (direct inversion vs. iteration) is **not stated**.

### Chen 2001 — analytic linear QG stationary waves

Chen, P., 2001: *Thermally Forced Stationary Waves in a Quasigeostrophic System.* **J. Atmos. Sci. 58(12), 1585-1594.** [doi:10.1175/1520-0469(2001)058<1585:TFSWIA>2.0.CO;2](https://doi.org/10.1175/1520-0469(2001)058%3C1585:TFSWIA%3E2.0.CO;2)

Verbatim: *"The model used in this paper is the **quasigeostrophic system on a beta plane**. The dynamics of the thermally forced **linear stationary waves** on a zonally symmetric basic state is governed by [Eq. 2.1]"*, *"where Q' denotes diabatic heating, delta and gamma coefficients of **Newtonian cooling and Ekman drag**, -(W/2) and +(W/2) the south and north ends of the beta-plane channel. Other symbols are defined as in **Holton (1992)**."* Uniform zonal flow; radiation condition at infinity; solutions of the form $\exp[i(kx+ly) + z/(2H)]$ times a vertical structure function. Findings: on a resting state the inviscid solution is the **Sverdrup** solution confined to the heating; on westerly flow the response splits into a local part and a vertically propagating part, the latter conceptualisable as a response to an **"equivalent topography"** whose height is proportional to heating intensity and zonal scale and **inversely proportional to the zonal flow**; for weak summer westerlies the equivalent topography is **larger than the real topography**, i.e. **heating dominates topography in the summer subtropics**. A 5-day Ekman drag / 15-day Newtonian cooling case makes the response asymmetric about the heating. **Eq. (2.1)'s algebra is an image, so it is UNVERIFIED.**

### The GFDL stationary wave model (SWM/NSWM) — full specification

Held, Ting & Wang (2002), *Northern Winter Stationary Waves: Theory and Modeling*, J. Climate **15**, 2125-2144, [doi:10.1175/1520-0442(2002)015<2125:NWSWTA>2.0.CO;2](https://doi.org/10.1175/1520-0442(2002)015%3C2125:NWSWTA%3E2.0.CO;2). **Its Appendix "Description of Model" was retrieved in full via Wayback — verbatim:**

> *"The nonlinear stationary wave model is based on the **three-dimensional primitive equations in sigma coordinates**. All the basic variables are deviations from a prescribed zonal flow. The basic prognostic equations are those for **perturbation vorticity, divergence, temperature, and log(surface pressure)**. Perturbation geopotential height and vertical velocity are calculated from the diagnostic hydrostatic balance and mass continuity equations. A semi-implicit time integration scheme is employed with a time step of 30 min. **The stationary wave solution in this model is obtained by integrating the model to a quasi-steady state** after a short period of time... The model has **rhomboidal wavenumber-30 truncation** in the horizontal and **14 unevenly spaced sigma levels**... During model integration, the zonal mean of the basic variables is relaxed very strongly, with a timescale of **3 days**, to the observed zonal mean... **Linear simulations are simply obtained by reducing the strength of the forcing by a factor of 100.** More details about the model equations can be found in **Ting and Yu (1998)**."*

> *"The damping used in the nonlinear model includes **Rayleigh friction, Newtonian cooling, and biharmonic diffusion**. The Rayleigh friction damping times for both the vorticity and divergence equations are **0.3, 0.5, 1.0, and 8.0 days for the lowest four sigma levels (0.997, 0.979, 0.935, and 0.866)**, and **25 days** throughout the rest of the model. The timescale of the **Newtonian cooling is 15 days at all levels**. The biharmonic diffusion coefficient ... is **1e17 m^4 s^-1** ... significantly stronger than values typically used in GCMs of this resolution."*

> *"...the nonlinear model reaches either a **true steady state or a quasi-steady state after being integrated for around 20 days**."*

Diabatic heating is computed as a residual of the thermodynamic equation in pressure coordinates from reanalysis. **Note for our design:** the GFDL SWM is a **primitive-equation** model, **not QG**, and its "steady" answer comes from **time integration**, not matrix inversion. The beta-plane QG PV form sketched in our brief is the textbook/analytic idealisation (Chen 2001; Holton 1992), not what the GFDL SWM integrates.

### The linear baroclinic model (LBM) — the exact steady solver

Watanabe & Kimoto (2000), **Q. J. R. Meteorol. Soc. 126(570), 3343-3369**, [doi:10.1002/qj.49712657017](https://doi.org/10.1002/qj.49712657017); Corrigendum QJRMS **127**, 733-734 · Watanabe & Jin (2003), J. Climate **16**(8), 1121-1139 (moist LBM) · Watanabe, Jin & Pan (2006), JAS **63**(12), 3366-3382, [doi:10.1175/JAS3807.1](https://doi.org/10.1175/JAS3807.1) · Hoskins & Karoly (1981), JAS **38**, 1179-1196, [doi:10.1175/1520-0469(1981)038<1179:TSLROA>2.0.CO;2](https://doi.org/10.1175/1520-0469(1981)038%3C1179:TSLROA%3E2.0.CO;2) · Branstator (1990), JAS **47**, 629-649 · Wu, Battisti & Sarachik (2000), JAS **57**(12), 1937-1957, [doi:10.1175/1520-0469(2000)057<1937:RFNCAT>2.0.CO;2](https://doi.org/10.1175/1520-0469(2000)057%3C1937:RFNCAT%3E2.0.CO;2) · Wu, Sarachik & Battisti (2001), JAS **58**(7), 724-741 · Ting & Yu (1998), JAS **55**, 3565-3582, [doi:10.1175/1520-0469(1998)055<3565:SRTTHI>2.0.CO;2](https://doi.org/10.1175/1520-0469(1998)055%3C3565:SRTTHI%3E2.0.CO;2).

**From the LBM Users' Guide v2.2** (Watanabe, 31 Aug 2005, Hokkaido Univ.; <https://ccsr.aori.u-tokyo.ac.jp/~hiro/lbm/doc2.2.pdf>), verbatim:

    X = ( zeta , D , T , Pi )  with  Pi = ln p_s
    dX/dt + (L + NL) X = F         (1)
    dX'/dt + L X' = F'             (2)   [linearized]
    L X = F                        (3)   [steady]
    X = L^{-1} F                   (4)

The spectral primitive equations *"with a vertical sigma coordinate has been explicitly linearized about a basic state... The form of linear equations are given by **Watanabe and Kimoto (2000, 2001)** and also briefly described in the Appendix of this manual."* — **the manual does not print the PE system and those references are closed-access Wiley, so the LBM's actual primitive equations are UNVERIFIED.**

Four documented solution routes with costs: **(1) time integration** — *"only a good approximation to a steady solution... **If you need to obtain the exact steady solution to a forcing, choose matrix inversion methods.**"*; **(2) direct matrix inversion, 3-D basic state** — N = 483 x (3 x 20 + 1) = 29643, **more than 7.5 GB**; a T21L11m10 solve takes about **15 min** on a HITACHI SR8000 (matrix build about 1 h); **(3) SWM mode** (zonal-mean basic state) — the operator is **block-diagonal by zonal wavenumber**, L = direct sum of L_k: *"T21L20 SWM is now separated into 21 different matrices and a size of one matrix ... will be N_1 = 21 x 2 x (3 x 20 + 1) = 2562"* — **the cheapest exact steady route**; **(4) AIM** (Watanabe, Jin & Pan 2006). Resolutions: T21 recommended, L5/L8/L11/L20.

**Damping defaults (namelist, verbatim):** ddragv / ddragd / ddragt = 1,1,1,5,15,30,...,30,1,1 with tunit='DAY', i.e. *"1dy for the lowest three levels and the topmost two levels, 5 and 15dy for the fourth and fifth levels, and 30dy elsewhere"*, **separately for vorticity, divergence and temperature**; vertical diffusion is 1/(1000 days); biharmonic (nabla^4) horizontal diffusion with a 6 h e-folding at the largest wavenumber.

**CRITICAL WARNING FOR A STEADY SOLVER** — from the LBM author's own note (note-on-lbm.062805.pdf), verbatim: *"the AIM (and probably matrix inversion as well) does not allow such a propagation, so that the response may only amplify at particular area (i.e. the most baroclinically unstable region) without phase propagation, resulting in an **artificially steady response** near Siberia as has sometimes been found in the matrix inversion."*

**(a) steady-state solvable** — by matrix inversion; the guide's own position is that **time integration gives only an approximate steady state**.

### Gill 1980

Gill, A. E., 1980: *Some simple solutions for heat-induced tropical circulation.* **Q. J. R. Meteorol. Soc. 106(449), 447-462.** [doi:10.1002/qj.49710644905](https://doi.org/10.1002/qj.49710644905)

Described by Shamir, Garfinkel, Gerber & Paldor (2023), *J. Fluid Mech.* **964**, A32 as *"system (30) of Matsuno (1966), and equations (2.6)-(2.8) of Gill (1980)"*, with *"the dissipative terms in the momentum equations and the continuity equation tak[ing] the forms of **Rayleigh friction and Newtonian cooling**, respectively, and assume[d] ... characterized by the same time scale"*. Their steady beta-plane reduction (spectral in x, wavenumber k):

    -v_k y + i k Phi_k = -alpha u_k
    u_k y + dPhi_k/dy  = -alpha v_k
    i k u_k + dv_k/dy  = -alpha Phi_k + Qtilde_k        (alpha = epsilon = 1/tau)

Gill then imposes the **long-wave approximation**, *"tantamount to neglecting -gamma*v on the right-hand side of (2.3b)"*. The dimensional beta-plane form, as presented in secondary teaching sources (MPI-M wiki, Hawaii OCN666, LMD/IPSL notes — **SECONDARY**):

    eps*u - (1/2) y v = -dp/dx ;  (1/2) y u = -dp/dy  [long-wave: -eps*v dropped] ;
    eps*p + du/dx + dv/dy = -Q ,     c = 70 m/s ,  eps^-1 ~ 1.5 days

**Solution structure:** a forced **Kelvin** component decaying **east** of the forcing on scale 1/eps (Gaussian meridional structure) and a first symmetric **Rossby** component decaying **west** on scale 1/(3 eps) (D_1 structure). Gill's abstract notes a solution *"with the heating displaced north of the equator provides a flow similar to the monsoon circulation of July"*. **(a) steady-state solvable — analytically. But Gill produces no desert**: there is no midlatitude westerly basic state for the Rossby response to interact with.

### Held & Ting (1990) — orographic vs thermal forcing: the conclusion

Held, I. M., and M. Ting, 1990: *Orographic versus Thermal Forcing of Stationary Waves: The Importance of the Mean Low-Level Wind.* **J. Atmos. Sci. 47(4), 495-500.** [doi:10.1175/1520-0469(1990)047<0495:OVTFOS>2.0.CO;2](https://doi.org/10.1175/1520-0469(1990)047%3C0495:OVTFOS%3E2.0.CO;2) — **abstract retrieved verbatim:**

> *"The amplitude of the **linear, stationary response to low-level extratropical heating decreases as the magnitude of the low-level mean flow increases**, while the amplitude of the **orographically forced waves increases**. As a result, linear theory predicts that the **relative importance of thermal and orographic forcing for the extratropical stationary wave field is very sensitive to the magnitude of the zonal mean low-level winds**. In the process of illustrating this sensitivity, we also show how the dependence of the orographic response on the low level winds can be distorted by a numerical sigma-coordinate model."*

**Direct answer:** **thermal forcing dominates when the low-level mean flow is weak; orographic forcing dominates when it is strong.** There is no single "dominant level" — it is a wind-speed-dependent crossover, and for **summer subtropical** weak westerlies the thermal (heating) response wins. This agrees with Huang & Gambo (1983) and Chen (2001), both of which find heating stronger than topography in summer. **Nothing is said about the vertical level of dominance beyond "low-level".**

**"Linear Atmospheric Model (LAM) intercomparison": NOT FOUND.** No evidence such a project exists. AMIP/CMIP/CFMIP/APE compare *GCMs*.

## 9. The monsoon-desert mechanism

Rodwell, M. J., and B. J. Hoskins, 1996: *Monsoons and the dynamics of deserts.* **Q. J. R. Meteorol. Soc. 122(534), 1385-1404**, [doi:10.1002/qj.49712253408](https://doi.org/10.1002/qj.49712253408) · — 2001: *Subtropical Anticyclones and Summer Monsoons.* **J. Climate 14(15), 3192-3211**, [doi:10.1175/1520-0442(2001)014<3192:SAASM>2.0.CO;2](https://doi.org/10.1175/1520-0442(2001)014%3C3192:SAASM%3E2.0.CO;2)

RH1996's abstract (verbatim, via Crossref): *"...suggests a **monsoon-desert mechanism** for desertification whereby remote diabatic heating in the Asian monsoon region can induce a **Rossby-wave pattern to the west**. Integral with the Rossby-wave solution is a **warm thermal structure that interacts with air on the southern flank of the mid-latitude westerlies causing it to descend**. This adiabatic descent is localized over the eastern Sahara and Mediterranean, and over the Kyzylkum desert to the south-east of the Aral Sea, by the mountains of north Africa and south-west Asia..."*

From the retrieved RH2001 text: the model is *"a time-dependent, global, hydrostatic, primitive equation model derived from Hoskins and Simmons (1975). It is **nonlinear**, spectral in the horizontal, and uses finite differences in sigma coordinates... **triangular truncation 31**, and there are **15 levels**."* Heating: *"idealized elliptical deep-convective monsoon heating centered at **25N, 90E and 400 hPa** and maximizing at **5 K per day**"*. Damping: *"linear drag in the lowest two levels, sigma = 0.967, 0.887, on timescales of 1 and 5 days, respectively, over the oceans, and 1/4 and 5/4 days, respectively, over land"*; *"Newtonian relaxation ... with a timescale of **25 days** ..., but decreasing to **5 days** in the boundary layer"*; zonal means of vorticity, divergence, temperature and surface pressure held constant. **Diabatic enhancement** (4-day relaxation) strengthened descent by **18%**, or **54%** when the heating was raised 33% to compensate.

**Mechanism (2001 abstract, verbatim):** the equatorward part of each subtropical anticyclone is the **Kelvin wave response** to monsoon heating to the west; a poleward low-level jet is required for **Sverdrup vorticity balance** (beta*v = f * divergence); *"The **Rossby wave response to the west** of subtropical monsoon heating, interacting with the midlatitude westerlies, produces a region of **adiabatic descent**."* *"...the Mediterranean-type climates of regions such as California and Chile may be **induced remotely by the monsoon to the east**."*

**RH2001's own warning against our linearization (verbatim):** *"The June-August zonal-mean winds between 10 and 30N throughout the troposphere generally lie in the range -5 to +5 m s-1. **The critical line at which it is zero will clearly play a central role in any linearized, steady-state model.**"*

**Other land-sea/monsoon models:** Rupp & Haynes (2021), *Weather and Climate Dynamics* **2**, 413-431 (OA; steady heat source, 3-D dry model, no mechanical friction above the BL) — **time-stepped** · Zhou & Xie (2018), J. Climate **31**(22), 9021-9036 (idealized land-sea geometry, intermediate GCM plus a linearized zonal-mean diagnostic) — **time-stepped** · Cherchi et al. (2016), Clim. Dyn. **47**, 2361-2371 — *closed, content UNVERIFIED* · Seager et al. (2003), J. Climate **16**, 1948-1966, [doi:10.1175/1520-0442(2003)016<1948:AIATSC>2.0.CO;2](https://doi.org/10.1175/1520-0442(2003)016%3C1948:AIATSC%3E2.0.CO;2) (mixed-layer SST feedback reinforcing the subtropical highs).

**DIRECT ANSWER:** the only genuinely **linear + steady** model that produces a monsoon-like response is **Gill (1980)** — and it produces **no desert**. The only **steady linear model forced by summer land-sea heating** that I could verify is **Huang & Gambo (1983)** (it produces stationary-wave amplitudes over the subtropics, not a named monsoon/desert). **Rodwell & Hoskins are nonlinear and time-stepped.** The monsoon-desert mechanism itself is, however, fundamentally an *adiabatic wave-mean-flow* mechanism and is therefore a natural target for a steady solver.

## 10. Ancillary closures (convection, moisture, MSE)

Betts (1986), *Part I*, QJRMS **112**, 677-691, [doi:10.1002/qj.49711247307](https://doi.org/10.1002/qj.49711247307) · **Betts & Miller (1986)**, *Part II*, QJRMS **112**, 693-709, [doi:10.1002/qj.49711247308](https://doi.org/10.1002/qj.49711247308) — **"Betts & Miller (1986)" is Part II; Part I is Betts alone** · Manabe & Wetherald (1967), JAS **24**, 241-259, [doi:10.1175/1520-0469(1967)024<0241:TEOTAW>2.0.CO;2](https://doi.org/10.1175/1520-0469(1967)024%3C0241:TEOTAW%3E2.0.CO;2) · Emanuel (1991), JAS **48**, **2313-2335**, [doi:10.1175/1520-0469(1991)048<2313:ASFRCC>2.0.CO;2](https://doi.org/10.1175/1520-0469(1991)048%3C2313:ASFRCC%3E2.0.CO;2) *(Crossref lists 2313-2329; the running heads of the author's own PDF run 2313 to 2335 over 23 pages. Treat 2313-2335 as correct and Crossref as wrong.)* · Emanuel & Zivkovic-Rothman (1999), JAS **56**, 1766-1782 · Neelin & Held (1987), MWR **115**, 3-12, [doi:10.1175/1520-0493(1987)115<0003:MTCBOT>2.0.CO;2](https://doi.org/10.1175/1520-0493(1987)115%3C0003:MTCBOT%3E2.0.CO;2).

**Betts "soft" adjustment — retrieved from the first author's own lecture notes** (Betts 2004, *The Parameterization of Deep Convection and the Betts-Miller scheme*, CPTEC, <http://alanbetts.com/workspace/uploads/bettsmiller2004-1282913116.pdf>; the 1986 originals are closed). The core is a **lagged relaxation**, not a snap:

    dS_bar/dt = -omega_bar dS_bar/dp + (R - S_bar)/tau                        (3)
    R - S_bar ~= omega_bar (dS_bar/dp) tau                                    (4)
    F = integral( (R - S_bar)/tau dp/g ) ~= integral( omega_bar (dR/dp) dp/g ) (6)

so **at steady state ($dS/dt = 0$) the scheme becomes purely diagnostic and $\tau$ cancels** — exactly what a steady model needs. Reference $\theta$ profile (their Eq. 7): a fixed fraction of the moist pseudo-adiabat slope through cloud base, $\theta_R = \bar\theta_B + 0.85\,\Gamma_w (p_B - p)$ for $p_B < p < p_F$; the notes state 0.9 is the wet-virtual adiabat and **0.85 is deliberately "more unstable"**; above the freezing level the profile returns to the moist adiabat **quadratically** (the notes flag this as a *change* from Betts & Miller 1986, which returned linearly and interpolated $\theta$ rather than $T$). Prescribed reference RH at the notes' levels: **RH $\approx$ (90, 70, 50)% at $(p_B,p_M,p_T) = (-25,-40,-20)$ mb**.

**Verified QTCM rendering of the same QE closure** (NZ2000 (2.21)-(2.23), (5.5), (5.7), (5.8) — see section 4): $Q_c = c_c(T_c-T)/\tau_c$ if $\langle T_c-T\rangle>0$; $T_c = T_r^c(p) + A_1(p)T_1^c$; $Q_q = (q_c-q)/\tau_c$ with $q_c=\alpha_{sub}(p)q_{sat}(T_c)$; $\tau_c = 2$ h; simplest coded form $-\langle Q_q\rangle = \langle Q_c\rangle = \epsilon_c^*(q_1-T_1)$.

**Neelin & Held (1987) MSE-budget closure — the most directly reusable closure for a steady model.** Full text NOT retrieved (closed), but the closure is verified as reproduced in a Copernicus OA preprint (Rao et al., *Clim. Past Discuss.* cp-2018-108), which states *"The time derivatives have been dropped in these equations because the climate is assumed to be in a steady state."*:

    <grad.(m U)> + <d(m omega)/dp> = Q_div                                   (1)
    <grad.(q U)> + <d(q omega)/dp> = E - P                                   (2)
    <A> = -(1/g) integral_{Pb}^{Pt} A dp ;   Q_div = LHF + SHF + Q_rad       (3),(5)
    P - E = Q_div / GMS                                                      (6)
    GMS = (m_1 - m_2) / ( L_v (q_2 - q_1) )                                  (7)
    m = c_p T + g Z + L_v q ;  m_1, m_2 = divergence-weighted MSE of the upper / lower troposphere

Given the steady circulation, **$P - E$ follows from $Q_{div}/GMS$ with no $\tau$ and no time stepping.** Note: the brief's "**b coefficient**" for Neelin-Held **could not be verified anywhere** — do not cite it.

**Emanuel (1991)** (retrieved): the mass flux is $M^i = \rho^i \sigma^i w^i$ with $w^i = \sqrt{2\,\mathrm{CAPE}^i}$ and $\mathrm{CAPE}^i = \sum_n R_d (T_{vp}^n - T_v^n) \Delta\ln p$ (Eqs. 17a,b), and the fractional areas are updated **in time** via $\delta\sigma^i = \alpha^i \delta w^i \pm \beta$ (Eqs. 18-20, *"adjusted with time towards quasi-equilibrium"*). Precipitation efficiency enters as $l_c^i = (1-\epsilon^i) l_a^i$ (Eq. 1). **Classification: needs time stepping** — the $\sigma$ update is explicit in time; the $\epsilon^i$ values are prescribed, not formulaic.

**Manabe & Wetherald (1967):** bibliographic data verified, but the paper itself was not retrieved; the fixed-RH profile is double-sourced only. **Emanuel (1991) closure values, the Betts-Miller numeric reference profiles and the Manabe-Wetherald RH profile remain UNVERIFIED (section 12).**

## 11. Bottom line for our model

**(i) No published precedent does exactly what we want.** Closest: **Huang & Gambo (1983)** (steady + linear + summer heating, but QG and hemispheric, no monsoon/desert named), **Gill (1980)** (steady + monsoon, no desert), **Rodwell & Hoskins 1996/2001** (monsoon + desert, but nonlinear and time-stepped).
**(ii) Target the Rodwell-Hoskins mechanism:** monsoon heating gives a Kelvin response east (subtropical high) plus a **Rossby response west interacting with the midlatitude westerlies to give adiabatic descent**, amplified by **diabatic enhancement** (descent suppresses convection and increases radiative cooling). It is adiabatic and steady-compatible.
**(iii) Steady solver of record = LBM in SWM mode:** linearize about a **zonally symmetric** basic state so the operator is **block-diagonal in zonal wavenumber**, then invert each block exactly (N_1 = 21 x 2 x (3L+1) at T21 — trivial on a coarse 2-D grid).
**(iv) Two hard constraints found in the literature.** (a) *Stability*: an unstable or marginally unstable linear operator gives an *"artificially steady response"* (LBM author's note) — we must demonstrate strict stability. (b) *Critical line*: RH2001 — the summer subtropical zonal-mean zonal wind is within +/-5 m/s, so the zero-wind critical line *"will clearly play a central role in any linearized, steady-state model."*
**(v) Templates to copy.** Relaxation forcing defaults from HS94 (sigma_b = 0.7, k_a = 1/40 per day, k_s = 1/4 per day, k_f = 1 per day, Delta T_y = 60 K, Delta theta_z = 10 K). Moist physics from Frierson et al. (2006), **but** its gray radiation decouples water vapour from the radiation, removing part of the water-vapour feedback that drives the desert — and the convection schemes are in doi 10.1175/JAS3935.1, not Part I. Full-physics benchmark with a *demonstrated* steady solver: **QTCM1** (section 7d(1), multigrid-tested) at 5.625 x 3.75 deg, ~1.5-5 min CPU per model-year.
**(vi) Cost is not a barrier.** LBM SWM blocks of a few thousand unknowns, and ~2 min per simulated year for dry PUMA, are both far above what a coarse lon-lat steady solve needs. A direct sparse solve of a linearized steady PE system on about 72 x 36 x 10 levels is entirely feasible today. **PlaSim/PUMA also give us a cheap oracle**, and prove that a publishable model can be built from *"Rayleigh friction and Newtonian cooling"* alone.

## 12. Consolidated UNVERIFIED items (do not cite as fact)

- **Do not cite**: QUADM (no definition found anywhere; not verified to exist); a "Held & Suarez (1983) book chapter" (not verified to exist); a "60-day drag" in HS94 (does not exist).
- **Explicit algebra not retrieved** (rendered as images or closed access): Huang & Gambo (1983) eqs. (1)-(2); Chen (2001) eq. (2.1); RH1996 body and RH2001 eqs. (1)-(2); Held & Ting (1990) full text; the LBM primitive-equation system (the Users' Guide points to closed-access Wiley refs).
- **Ting & Held (1990)** model details (equations, damping, basic state, resolution) — not retrieved; only its citation by the LBM manual as a **direct matrix-inversion** steady solve.
- **Zebiak & Cane (1987)** horizontal grid of either component, and any computational-cost figure — **not stated in the paper**. **Zebiak (1986)** full text not retrieved; its heating form is known only as reproduced in ZC87's Appendix.
- **QTCM "beta_q"** — no such symbol exists in NZ2000, ZNC2000 or the QTCM1 manual. Also unverified: NZ2000 eqs. (2.18)/(2.19) exact glyph order (PDF math-font scrambling); Table 1 units for M_Sr1 / M_qr1.
- **Equations not retrieved verbatim**: moist EBM governing equations (Siler/Roe/Armour 2018; Flannery 1984; Rose & Ferreira 2013 — section 6 gives the standard diffusive-EBM form); Betts-Miller (1986) reference profiles; Manabe & Wetherald (1967) RH profile; Emanuel (1991) closure; Neelin & Held (1987) MSE closure. Bibliographic data for all of these IS verified.
- **Gill (1980)'s own epsilon value and vertical structure** — original unobtainable here; the beta-plane form quoted is from **secondary** teaching sources.
- **Wu, Battisti & Sarachik (2000, 2001)** — bibliographic data verified, content not retrieved. **"Linear Atmospheric Model intercomparison"** — no evidence it exists.
- **Page-range conflicts between Crossref and the artefact itself**: Emanuel (1991) is 2313-2335 by the printed running heads (Crossref says 2313-2329); Betts (1986) Part I is 677-691 and Betts & Miller (1986) Part II is 693-709 (the authors' own notes cite 677-692 / 693-710). Where they disagree, **the artefact wins**.
- **Brief items that are simply not in the literature**: the Neelin-Held "**b coefficient**" (no retrievable source reproduces one); the "Rose, Armour, Battisti, Feldl & Koll (2017)" reference is really **Rose et al. (2014), GRL 41(3), 1071-1078, doi:10.1002/2013GL058955**, and it is **not** a moist-EBM paper (it is a GCM feedback / ocean-heat-uptake-pattern study). Cite **Siler, Roe & Armour (2018)** as *the* moist EBM.
- **Highest-value sources still missing** (all require a Cloudflare-clearing browser or library access): **Flannery (1984)**, JAS 41(3), 414-421 — bronze-OA and the paper the brief wants for the thermal-vs-latent transport split, **zero equations obtained**; **Manabe & Wetherald (1967)** — would settle surface RH (77% vs 80%) and the exact convective-adjustment algorithm; **Neelin & Held (1987)** — would settle the "b coefficient" question; **North, Mengel & Short (1983)**, JGR 88, 6576-6586, or **CLIMBER-2** (Petoukhov et al. 2000, Clim. Dyn. 16, 1-17) — for a genuine 2-D (lon-lat) **moist** EBM precedent (none was found).
- **A 2-D (lon-lat) moist EBM with a prescribed ocean could not be found.** Stocker's textbook lists 2-D EBMs as an EMIC category ("2: EBM (lat,lon) + diffusive ocean (z)") developed in the 1980s, but no equations for any *moist* 2-D EBM were retrieved.
