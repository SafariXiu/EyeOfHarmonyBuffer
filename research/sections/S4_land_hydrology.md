# S4 — Land Surface Hydrology & Surface Energy Balance / Temperature

Basis for a **steady-state (diagnostic)** atmospheric circulation model.
**Evidence markers:** **[V]** = read in the primary full text here · **[M]** = Crossref-verified metadata, full text not obtained · **[S]** = equation form verified via an open secondary source, primary not obtained.

---
# 4. LAND SURFACE HYDROLOGY
## 4.1 The Manabe (1969) "bucket" — exact equations [V]

**Symbols.** `W` soil moisture in the surface-to-1-m layer [cm water]; `W_FC` field capacity [cm]; `W_K` critical moisture [cm]; `E` actual evaporation; `E_0` "evaporability" (= potential evaporation); `R_A` rainfall rate; `r_s` runoff rate; `rho(h)` air density at height `h`; `C_D(h)` drag coefficient; `V(h)` wind vector; `r_ws` saturation mixing ratio at surface temperature `T*`; `r(h)` mixing ratio at `h`.

**Evaporability** — Manabe (1969) Eq. (19); Eq. (15) uses one drag coefficient for momentum and moisture:

    E_0 = rho(h) . C_D(h) . |V(h)| . ( r_ws(T*) - r(h) )

**Evaporation efficiency (the "beta factor")** — Manabe (1969) Eq. (18):

    if W >= W_K :  E = E_0
    if W <  W_K :  E = E_0 . (W / W_K)      =>   beta = E/E_0 = min(1, W/W_K)

**Critical moisture** — Manabe (1969) Eq. (20), after Alpatev (1954):

    W_K = 0.75 . W_FC

> **Correct the common paraphrase.** "beta = W/W_FC, = 1 at saturation" is **not** what Manabe (1969) wrote: his threshold is `W_K = 0.75 W_FC`, so beta reaches 1 at 75 % of field capacity and has slope `1/W_K`. He also did not use the symbol beta — that is later terminology.

**Prognostic soil-moisture equation** — Manabe (1969) Eq. (21):

    if W = W_FC and R_A > E_0 :  dW/dt = 0 ,  r_s = R_A - E_0
    if W <  W_FC              :  dW/dt = R_A - E

**Field capacity:** one global value **W_FC = 15 cm** everywhere, after Palmer (1966); Manabe notes it is "somewhat smaller than the median value of field capacity" [V]. **Snow** — Eqs. (22)–(25) [V]: `dS/dt = S_F - E - M_e`, with `M_e = E_z/L_f` if `E_z > 0` else `M_e = 0`, `E_z` the net surface energy at `T* = 273.2 K`; snow liquid-water-holding capacity set to **zero**; under snow `dW/dt = M_e + R_A` or `W = W_FC`.

**Follow-ups.** Manabe & Holloway (1975) [M] adds the seasonal cycle. Milly (1994) [V abstract]: the long-term water balance is set *only* by the local interaction of fluctuating supply and demand, mediated by soil storage. Koster & Suarez (1996) [V abstract]: an explicit retention timescale that damps high-frequency evaporation variance. Robock et al. (1995) [V abstract]: validates exactly this **15-cm bucket** against Soviet soil-moisture records — good seasonal/interannual skill at 3 of 6 stations.

**=> W is PROGNOSTIC.** Eq. (21) is a first-order ODE with an inequality-switched RHS. **It has no steady-state solution a diagnostic model can simply look up.** Manabe reports the symptom: with no seasonal cycle, high-latitude `W` sits pinned at `W_FC` — "somewhat unrealistic… probably caused by the lack of seasonal variation in the model" [V].
## 4.2 Bucket steady-state / time-mean limit

Set `dW/dt = 0`; Eq. (21) becomes a **partition of rainfall**, not a determinant of `W`:

    E = R_A - r_s ,     0 <= r_s  _|_  (W_FC - W) >= 0          (complementarity)

With `beta(W) = min(1, W/W_K)`, `E = beta(W) E_0`, the steady state solves

    find W in [0, W_FC] :  R_A - beta(W) E_0(W,T*) - r_s(W) = 0 ,
    r_s = max(0, R_A - E_0) if W = W_FC ;   r_s = 0 if W < W_FC .

Three regimes, all diagnostic once `R_A = P` and `E_0` are known:
1. **Energy-limited (wet):** `R_A >= E_0` => `W = W_FC`, `E = E_0`, `r_s = R_A - E_0`.
2. **Water-limited (dry):** `R_A < E_0` and soil never fills => `r_s = 0`, `E = R_A`, and **W cancels — the steady balance does not determine it.** `W` is whatever the history left behind.
3. **Switched (interior):** `W = W_K R_A / E_0`, i.e. the fixed point `W* = W_K R_A / E_0(W*, T*)`, since `E_0` depends on `T*` which depends on `E` (§5).

**Recommended solver — fixed point / active set (my own construction, not a citation):**

    init W0 in [0, W_FC]                              # e.g. 0.5 W_FC
    repeat:
        E_0 <- f(T*, q_a)                                   # §5
        if R_A >= E_0 : W <- W_FC ; r_s <- R_A-E_0 ; E <- E_0     # active: upper bound
        else          : E <- R_A  ; r_s <- 0 ; W <- clip(W_K E/E_0)
        recompute T*, q_sat(T*) from the surface energy balance    # §5
    until |dW| < tol and |dT*| < tol

The `min`/`max` switches make this non-smooth: use under-relaxation, or solve the two active sets separately and keep the feasible one. Relaxation is advisable because `E_0` rises with `T*` while `E` lowers `T*` — a negative but weak feedback.

**When is the diagnostic closure legitimate?**
* **Legitimate** at **annual / multi-year means** where storage change is negligible against the fluxes — the assumption behind every Budyko closure; made explicit by Milly (1994) [V] and stated in the open Budyko derivation as "positive and negative short-term changes in catchment storage average to negligibly small values, **Delta-Sbar ~ 0**" [V].
* **Not legitimate** when: (a) the seasonal cycle is resolved (spring recharge / summer depletion dominates — Manabe & Holloway 1975 [M]); (b) `R_A < E_0` persistently, so `W` has no equilibrium and the steady problem is under-determined; (c) soil-moisture memory matters, i.e. retention time ~ forcing timescale (Koster & Suarez 1996 [V]); (d) the initial `W` is what selects the desert vs vegetated branch (§4.4).
## 4.3 Budyko / Milankovitch aridity index

All overbars are long-term means.

    water balance                :  Pbar = Ebar + Qbar                    (Delta-Sbar = 0)
    radiational index of dryness :  phi = Rbar_n / (L . Pbar) = Ebar_0/Pbar    (Budyko 1974)
    aridity index                :  phi = Ebar_0 / Pbar                        (Budyko 1974)
    humidity index               :  1/phi = Pbar / Ebar_0                      (Hulme et al. 1992)
    evaporative index            :  Ebar/Pbar        R-index : Ebar/Ebar_0     (Yao 1974)

`Rbar_n` net radiation; `L` latent heat of vaporisation; `Pbar` precipitation; `Ebar_0` potential evaporation. **Conventions differ** — some authors define the aridity index as `P/E_0`. Below, `phi = E_0/P` as in the open HESS derivation [V].

**Budyko (1974)** original curve [V, transcribed from that derivation]:

    Ebar/Pbar = sqrt[ phi . tanh(1/phi) . (1 - exp(-phi)) ]

**Choudhury (1999) = Turc (1953) = Mezentsev (1955); analytically derived by Yang et al. (2008):**

    Ebar/Pbar = phi / (1 + phi^n)^(1/n) = 1 / (1 + phi^(-n))^(1/n)
              = 1 / (1 + (Pbar/Ebar_0)^n)^(1/n)

**Fu (1981) = Zhang et al. (2004):**

    Ebar/Pbar = 1 + phi - (1 + phi^w)^(1/w)

`n` and `w` are catchment-specific shape parameters, **not** universal constants (limits `n -> 0`, `w -> 1`). Larger `n, w` => more of `Pbar` becomes `Ebar`. The open HESS analysis warns the parameter "has no a priori physical meaning" and is not transferable [V]. Fitted values around 1.5–3 are common but **no canonical value was verified from a primary source — treat it as a calibration choice.**

**How the aridity index makes a desert.** The Budyko curve is a *partition rule*, not a desert criterion:
* `phi << 1` (humid): energy-limited, `Ebar -> Ebar_0`, runoff large.
* `phi ~ 1`: crossover, `Ebar ~ 0.7-0.8 Pbar`.
* `phi >> 1` (arid): water-limited, `Ebar -> Pbar`, runoff -> 0 — **essentially all rainfall returns to the atmosphere; no runoff, no through-flow.** A desert is where large `phi` coincides with small **absolute** `Pbar`. The index alone, being a ratio, cannot separate a true desert from a seasonally dry but wet-annual-mean catchment.
## 4.4 The self-consistent desert feedback loop

Manabe (1969) states the mechanism verbatim as the **"self-amplification mechanism"** [V]:

> "When the rate of rainfall is smaller on the land surface, then the soil moisture is less and accordingly the rate of evaporation is less. In turn, this small evaporation rate is responsible for the decrease in relative humidity in the lower troposphere and then for the further decrease in rainfall and soil moisture. On the other hand, moisture is always abundant in the ocean area."

Closure chain `P -> W -> E -> q -> P`:

    (1) land      :  dW/dt = P - E - r_s                          [PROGNOSTIC]
    (2) land flux :  E = beta(W) . E_0 ,  beta = min(1, W/W_K)    [DIAGNOSTIC given W]
    (3) moisture  :  dq/dt + div(u q) = E - P + (phase change)    [PROGNOSTIC]
    (4) precip    :  P = P(moisture convergence, q_sat, stability) [diagnostic given q]

**Needs TIME STEPPING (verified):**
* **(1) W** — the bucket ODE, i.e. soil-moisture *memory*; timescale = the retention time of Koster & Suarez (1996) [V] and the storage term of Milly (1994) [V].
* **(3) q** — days-scale atmospheric moisture memory; the moisture-convergence feedback runs through it. The classical box models explicitly assume it away ("neglect the change in time of the precipitable water", valid "only for monthly, seasonal or yearly timescales" [V]). **Under that assumption the loop collapses to diagnostic.**
* **Bistability** of the coupled loop (dry vs wet branch at identical external forcing): Charney (1975) [M]; explicit feedback theory Eltahir (1998) [M]; the stochastic formulation in which soil moisture has a **steady-state probability density** rather than a single steady value is Rodriguez-Iturbe et al. (1991) [M]. **Key result for us: a steady-state distribution exists; a single steady-state value generally does not.**

**Diagnostic (2) + Budyko partition** — given `W, E_0, P`.

**Precipitation recycling ratio** (fraction of `P` that originated as evapotranspiration inside the region):

    Budyko-Drozdov (1-D streamline; beta = wbar/wbar_a = Pbar/Pbar_a):
        d(w u)/dx = E - P     =>    R = 1 - beta^-1 = eL / (eL + 2 Q_ox) ,   e = E.Dt/A
    Brubaker et al. (1993), regional:
        R = E.A / (E.A + 2.I) = 1 / (1 + 2I/(E A))
    Eltahir & Bras (1994, 1996), local per grid cell, solved iteratively:
        rho = (I_i + E.A) / (I_i + E.A + I_0) = (I_i + E.A) / (I + E.A)

`w` precipitable water; `u` vertically integrated moisture flux; `L` length along the streamline; `A` area; `I` influx; `O` outflux; subscript `i` internal origin, `o` external origin. **[S]** — transcribed from the open TU Delft review of these box models; the Brubaker and Eltahir–Bras originals are paywalled and were **not** read here. Brubaker et al.'s own abstract [V] reports `R ~ 0.10-0.30` monthly, up to 0.40.

**Structural point:** `R` is a ratio of the *same* steady-state fluxes as §4.2, so once `E, P, I` are known diagnostically, `R` is diagnostic too. What must be time-stepped is the *feedback of R onto P*.
## 4.5 Recommended pragmatic scheme

    E_land = beta(W) . E_0 ,  beta = min(1, W/W_K) ,  W_K = 0.75 W_FC ,  W_FC = 0.15 m
    W from   P - beta(W) E_0 - r_s = 0         (active-set fixed point, §4.2)
    bounds   0 <= W <= W_FC                    (positivity; upper bound via r_s)

with `E_0` from one of:
* **Penman (1948) combination** [V] — his Eq. (16): `E = (H.Delta + E_a.gamma)/(Delta + gamma)`, with `H` available energy, `E_a = f(u)(e_s - e_a)` the drying power, `Delta = de_s/dT`, `gamma` the psychrometric constant. Penman's budget `H = E + K + S + C` (Eq. 8) reduces over long periods to `H = E(1 + beta_B)` with Bowen ratio `beta_B` (Eq. 11).
* **Penman–Monteith** (Monteith 1965 [M]; book chapter, no DOI) in operational FAO-56 form [V]:

      ET_0 = [ 0.408 Delta (R_n - G) + gamma (900/(T+273)) u_2 (e_s - e_a) ]
             / [ Delta + gamma (1 + 0.34 u_2) ]

  `ET_0` mm/day; `R_n` net radiation MJ m^-2 d^-1; `G` soil heat flux; `Delta` saturation-curve slope kPa/degC; `gamma` psychrometric constant kPa/degC; `T` degC; `u_2` m/s at 2 m; `e_s - e_a` kPa. Traceable constants: `0.408 = 1/lambda` with `lambda = 2.45 MJ/kg` (verified as Eq. 20, FAO-56 Ch. 3 [V]); `(1 + r_s/r_a) = (1 + 0.34 u_2)` with grass `r_s = 70 s/m` and `r_a = 208/u_2 s/m` (verified, FAO-56 Box 6 [V]). **The 900 coefficient sits in a rendered image — treat it as FAO-56-specific and not independently re-derived here.**
* **Priestley–Taylor (1972)** [V abstract; constant via S]:

      L . E_0 = alpha . [ Delta/(Delta + gamma) ] . (R_n - G) ,      alpha ~ 1.26

  Their abstract [V] describes exactly this: one formula for saturated sites, multiplied by a factor for drying surfaces. The constant is confirmed in an open IAHS/WMO review — "alpha is an empirical factor with a mean value of 1.26" [S]. **Verified caveat:** alpha is not universal; it "increase[s] with climate aridity, from around 1.25 up to 1.75 (Shuttleworth 2012)" [V]. `alpha = 1.26` will therefore *underestimate* `E_0` in arid cells.
## 4.6 Boundary conditions / well-posedness for the land column

1. **State bounds** `0 <= W <= W_FC`. The lower bound is mandatory — otherwise `E = beta(W)E_0` goes negative. Impose `E <- max(0, beta(W)E_0)`.
2. **Upper bound** enforced by `r_s = max(0, R_A - E_0)` on the active set `W = W_FC`; without it `W` diverges.
3. **Degenerate case:** if `P < E_0` and `W` is not initialised, the steady balance does **not** determine `W` (regime 2 of §4.2). Options: (a) **prescribe W** (equivalently prescribe `beta`) as a boundary condition; (b) prescribe `P` and `E_0` independently and treat `W` purely as a diagnostic of the Budyko partition; (c) add a `W`-dependent `E_0` closure and solve the fixed point, accepting non-uniqueness.
4. **Non-uniqueness:** at fixed `P, T*`, two solutions can be feasible (dry branch `r_s = 0`; saturated branch `W = W_FC`) when `E_0 ~ P`. Selection needs a hysteresis rule or an initial guess — i.e. **path dependence: exactly the memory the steady model claims to have discarded.** Document as a limitation.
5. **Prescribed constants** `W_FC` and `W_K/W_FC` (15 cm; 0.75). Soil texture, rooting depth and vegetation are absent from the bucket; Robock et al. (1995) [V] show the resulting errors are large at 3 of 6 validation stations.

---
# 5. SURFACE ENERGY BALANCE & TEMPERATURE
## 5.1 The surface energy balance — sign convention

**Convention: every flux is positive INTO the surface.**

    S_abs + F_LW_down - sigma T_s^4 - H - L.E - G = 0

`S_abs = S(1 - alpha)` absorbed solar [W/m2]; `F_LW_down` downwelling longwave; `sigma T_s^4` emitted longwave (black surface, `sigma = 5.6704e-8 W m^-2 K^-4`); `H` sensible; `L.E` latent (`L = 2.5e6 J/kg`); `G` ground heat flux. Manabe (1969) writes the identical balance as his Eq. (16), `S* + (DLR)* = sigma T*^4 + (c_p H)* + (L E)*`, and states the closure assumption explicitly [V]:

> "If we assume that the heat capacity of the earth is zero (no heat conduction into soil), the equation of the requirement of the heat balance is [Eq. 16]… Since the diurnal variation of solar insolation is eliminated in the model, it may be justifiable to neglect the heat conduction into the soil."
## 5.2 Bulk formulae

Manabe (1969) Eqs. (11), (12), (15) [V; Eq. 12 is lost to OCR — the form below is the standard drag law], with `c_p = 1004 J kg^-1 K^-1`:

    tau = rho(h) C_D(h) |V(h)| V(h)
    H   = rho(h) c_p C_D(h) |V(h)| (T_s - T(h))
    E   = rho(h) C_D(h) |V(h)| (r_ws(T_s) - r(h))
    L.E = L . E

Frierson et al. (2006) Eqs. (9)–(11) [V] use the same laws with one coefficient for momentum, heat and water:

    tau = rho_a C |v_a| v_a ,   S = rho_a c_p C |v_a| (theta_a - theta_s) ,   E = rho_a C |v_a| (q_a - q*_s)

> **Sign trap.** Frierson's `S` and `E` are positive **upward** (their `E > 0` means dew). The task's suggested `H = rho c_p C_H U (T_s - T_a)` is the **opposite** convention. Pick one and state it.

Magnitudes: `C_D ~ 1.1-1.5e-3` over ocean (Manabe 1969 derives it from the lowest model level with `z_0 = 1 cm`); over land `C_H ~ C_E ~ 1-3e-3` and strongly stability-dependent. **No single canonical C_H was verified from a primary source here — treat it as a tunable with a documented range, not a constant.**
## 5.3 Linearized OLR: F = A + B . T

**Budyko (1969), the original** [V]. His Eq. (1), fitted to 260 stations' monthly means, **T in degC, n = cloudiness fraction, flux in kcal cm^-2 month^-1**:

    I = a + b.T - (a_1 + b_1.T) n ,    a = 14.0 , b = 0.14 , a_1 = 3.0 , b_1 = 0.10

"root-mean-square deviation of the results of calculation by this formula from the initial data accounts for less than 5 % of the radiation values". Converting (1 kcal cm^-2 month^-1 = 15.92 W/m2):

    clear sky :  A = 222.9 W/m2 , B = 2.23 W m^-2 K^-1
    n = 0.5   :  A = 199.0 W/m2 , B = 1.43 W m^-2 K^-1
    transport :  his Eq. (3): A = B(T - T_p) with B = 0.235 kcal cm^-2 month^-1 K^-1 = 3.74 W m^-2 K^-1

**North-school value** [S, from an open-source EBM implementation]: "North & Coakley 1979 utilized a linearization of the OLR with temperature `I = A + B T`, where, for Earth, A = 203.3 W m^-2 and B = 2.09 W m^-2 degC^-1, and T is the surface temperature in degC. This linearization is a good fit to the observations of Earth (Warren & Schneider 1979)." The same value family is standard in North et al. (1981) and Graves, Lee & North (1993) [M].

**Climlab / Rose (classic Budyko–Sellers EBM)** [V, code + notebook]: `A = 210 W/m2`, described as "emission at 0 degC", and `B = 2 W m^-2 degC^-1`.

**The disagreement is real and material.** Sanity check at `T = 15 degC` against the observed global OLR of **238.5 W/m2** (Trenberth et al. 2009 [V]; their budget table gives ASR = 341.3 W/m2 and a net imbalance of 0.9 W/m2):

| source | A [W/m2] | B [W m^-2 K^-1] | A + B.15 |
|---|---|---|---|
| Budyko (1969), n = 0.5 | 199.0 | 1.43 | **220.5** |
| North & Coakley (1979) | 203.3 | 2.09 | **234.7** |
| climlab / Rose | 210 | 2.0 | **240.0** |
| observed (Trenberth et al. 2009) | — | — | **238.5** |

So the Budyko (1969) fit is ~18 W/m2 low with a ~1.5x smaller B; the North fit is within 4 W/m2. **B sets the model's entire temperature sensitivity — a factor-1.5 error in B is a factor-1.5 error in Delta-T.**

> **Unit trap.** A is meaningful only together with the temperature unit. `A = 203.3` pairs with `T` in **degC**; converting to Kelvin without shifting A (to `A_K = A - 273.15 B = -367.5 W/m2`) gives an OLR of order 840 W/m2.

**Gray-radiation alternative (no A + B fit).** Frierson et al. (2006) Eqs. (6)–(8) [V]: two-stream gray transfer `dU/dtau = (U - B)`, `dD/dtau = (B - D)`, `B = sigma T^4`; surface BC `U(tau=0) = sigma T_s^4`; TOA BC `D(tau=0) = 0`; diffusivity factor folded into `tau`; `tau(p)` specified (equatorial `tau_0 = 6`, polar `tau_0 = 1.5`, linear stratospheric term `f_l = 0.1`). **Here OLR is not a function of T_s alone** — it depends on the atmospheric temperature profile — so an A + BT fit is a genuine approximation whose B must be *fitted*, not quoted.
## 5.4 Absorbed solar and ice–albedo feedback

    S_abs = S_0 (1 - alpha) f_geometry ;   global mean  S_abs = S_0 (1 - alpha)/4
    S_0 = 1361 W/m2 (modern) ;  planetary alpha ~ 0.294
    (Trenberth et al. 2009 [V]: ASR 341.3 W/m2, reflected 101.9 W/m2)

Albedo (Budyko 1969 [V]): prescribed by latitude and surface type — 0.32 for 0–60 deg, 0.50 at 70 deg, 0.62 at 80 deg. Manabe (1969) [V] assigns **alpha = 0.70 to snow-covered land** and uses separate land/ocean profiles from Budyko (1956). Ice–albedo feedback in the Budyko–Sellers EBM class: North (1975) [M], Sellers (1969) [M]; threshold/bifurcation analysis: Eisenman & Wettlaufer (2009) [V abstract]. Practical form `alpha(T_s) = alpha_i` for `T_s < T_f`, else `alpha_w`, with the classic EBM threshold `T_f = -10 degC` — the open climlab notebook says "Empirically, we follow classic work by Budyko (1969) and set the threshold temperature…" [V]. **T_f is an empirical EBM choice, not a physical constant.**
## 5.5 Land vs ocean thermal response

**Land: zero heat capacity.** Manabe (1969) [V] solves `T*` from the instantaneous balance with `G = 0` => `T_s` is **diagnostic**.

**Ocean: slab mixed layer.** Manabe & Stouffer (1980) Eqs. (1)–(3) [V]:

    dT_m/dt = Q / (C_o H) ,      Q = f_RAD - f_SH - f_LH
    H = 68 m everywhere — the global mean "effective depth of the seasonal thermocline" D_f,
        defined by  D_f . Delta-T = Integral[ Delta-T(z) dz ]  on Levitus & Oort (1977) data

`C_o` heat capacity of water; `H` mixed-layer thickness; `Q` net heat gain; `f_RAD` net downward radiation; `f_SH, f_LH` upward turbulent fluxes; exchange with the deeper ocean neglected. Frierson et al. (2006) [V] use the same slab with `C_O = 1e7 J K^-1 m^-2` (equivalent to `h = C_O/(rho_w c_w) ~ 2.4 m`) and `C_O dT_s/dt = R_S - R_Lu + R_Ld - L.E - S`.

    heat capacity per unit area :  C_o H = rho_w c_w h = 1000 . 4186 . 68 ~ 2.85e8 J m^-2 K^-1
    relaxation timescale        :  tau = C_o H / B_eff

| h | C_o h [J m^-2 K^-1] | tau with B = 2.09 |
|---|---|---|
| 68 m (Manabe & Stouffer 1980) | 2.85e8 | **4.3 yr** |
| 50 m | 2.09e8 | 3.2 yr |
| 2.4 m (Frierson et al. 2006) | 1.0e7 | 55 d |

These are **pure-OLR-feedback** timescales; atmospheric coupling and a prescribed `Q_flux` shorten them, but the order of magnitude stands — and Manabe & Stouffer corroborate it operationally by advancing the *ocean* 16 years per 1 simulated *atmosphere* year (365/16 ~ 23 days per step at the start of the integration) [V]. **=> T_s over ocean is NOT steady-state solvable without time stepping, unless Q_flux is prescribed.**
## 5.6 Is a diagnostic steady-state surface temperature legitimate?

**Yes, with explicit caveats.** Manabe (1969) did exactly this and said so [V]:

> "This scheme for computing T* is applied to the ocean surface as well as to the land surface; therefore, the downward conduction of heat into the sea is neglected… in part I we are effectively considering a hypothetical ocean or wet surface that does not transport heat horizontally."

Limitations, each traceable:
1. **Diurnal and seasonal cycles are gone.** Manabe justifies `G = 0` *precisely* because "the diurnal variation of solar insolation is eliminated in the model" [V]. Nor can a steady model hold the seasonal storage term `C_o H dT/dt`, which is first-order at 68 m.
2. **Ocean heat transport is absent or prescribed.** Manabe & Stouffer (1980): the mixed-layer model "includes [heat capacity and the moisture source] but lacks the third one, the horizontal heat transport" [V]. A 2-D steady model must carry `Q_flux(x,y)` as a **prescribed** field, and its magnitude is not small: Trenberth et al. (2009) [V] report a net ocean-to-land energy transport of **2.2 PW**, of which 3.2 PW is latent.
3. **Land thermal inertia is neglected** — defensible for time means; the land memory lives in `W`, not `T_s`.
4. **The result depends on the assumed A, B and alpha.** §5.3 shows `A + B.15 degC` spanning 220–240 W/m2; `T_s` inherits that spread.
5. **Non-uniqueness** from ice–albedo feedback (North 1975 [M]; Sellers 1969 [M]; Eisenman & Wettlaufer 2009 [V]): the same forcing can admit multiple steady `T_s` fields, so a steady solver needs an initial guess and a branch-selection rule.

*Slab / mixed-layer sources:* Manabe & Stouffer (1980) [V]; Frierson et al. (2006) [V]; and the observational budget of Trenberth et al. (2009) [V] that any such model should reproduce.
## 5.7 Recommended closed diagnostic surface-temperature equation

Linearise about the air state (`T_a, q_a`); all quantities cell-local:

    sigma T_s^4 ~ sigma T_a^4 + 4 sigma T_a^3 Delta-T
    q_sat(T_s)  ~ q_sat(T_a) + Delta_q Delta-T ,
                  Delta_q = dq_sat/dT at T_a = eps e_s'(T_a)/p   (eps = 0.622)

with `Delta-T = T_s - T_a`. Substituting into §5.1 with `G = 0`:

    S(1-alpha) + F_LW_down - [sigma T_a^4 + 4 sigma T_a^3 Delta-T]
      - rho c_p C_H U Delta-T
      - rho L C_E U [ Delta_q Delta-T - (q_sat(T_a) - q_a) ] = 0

**LAND cell — exact closed algebra (zero heat capacity):**

    Delta-T = [ S(1-alpha) + F_LW_down - sigma T_a^4 + rho L C_E U (q_sat(T_a) - q_a) ]
              -------------------------------------------------------------------------
                     [ 4 sigma T_a^3 + rho c_p C_H U + rho L C_E U Delta_q ]

    T_s = T_a + Delta-T
    H   = rho c_p C_H U Delta-T
    L.E = rho L C_E U [ q_sat(T_s) - q_a ]      (clip at 0 for dew-free land)

No iteration is needed for the linearised form; iterate 2–3 times with `T_a <- T_s` if `Delta-T` exceeds ~5 K. This is a **direct, non-iterative closure for T_s given T_a, q_a, U, S(1-alpha), F_LW_down.**

**OCEAN cell — same algebra plus a prescribed transport term:**

    Delta-T = [ S(1-alpha) + F_LW_down - sigma T_a^4 + Q_flux
                + rho L C_E U (q_sat(T_a) - q_a) ]
              -------------------------------------------------------------------------
                     [ 4 sigma T_a^3 + rho c_p C_H U + rho L C_E U Delta_q ]

`Q_flux` is the prescribed convergence of horizontal ocean heat transport [W/m2, positive into the cell]. This is the *equilibrium* of the slab `C_o h dT_s/dt =` (same bracket). It is **valid only if Q_flux is prescribed**, or if the spin-up `tau ~ C_o h / B_eff` is short against the timescale of interest — which it is not for `h = 68 m`.

**Planetary (TOA / EBM) form**, as a one-line global consistency check rather than a surface budget:

    S_0 (1-alpha)/4 - (A + B T_s) + Q_transport = 0
    =>  T_s = [ S_0 (1-alpha)/4 + Q_transport - A ] / B

with A, B and A's temperature unit stated explicitly (§5.3).
## 5.8 WHAT REMAINS PRESCRIBED
* `W_FC = 0.15 m` and `W_K/W_FC = 0.75` — Manabe (1969) [V].
* `P` precipitation — prescribed, or from the separately solved atmospheric moisture budget; **not** closed by land hydrology alone.
* `r_s` runoff — from the active-set rule, but only once `P, E_0, W` are known.
* `alpha` surface albedo — latitude / surface-type table; `alpha = 0.70` for snow (Manabe 1969 [V]); ice–albedo threshold `T_f` is empirical.
* `A, B` and the temperature unit of the OLR linearisation; §B§ spans 1.43–2.09 W m^-2 K^-1 across sources.
* `C_H, C_E` bulk transfer coefficients — no single canonical value verified here.
* `C_o h` ocean mixed-layer heat capacity, `h = 68 m` (Manabe & Stouffer 1980 [V]).
* `Q_flux` ocean heat-transport convergence — required for a diagnostic ocean `T_s`.
* `F_LW_down, T_a, q_a, U` — supplied by the atmospheric side.
* The `E_0` closure choice (Penman / Penman–Monteith / Priestley–Taylor) and §alpha_PT§ (1.26 nominal; 1.25–1.75 with aridity [V]).
* Runoff routing, groundwater, soil texture and all vegetation physics — **absent from the bucket** and not restored here.
## 5.9 WHAT CANNOT BE SOLVED WITHOUT TIME STEPPING

1. **Bucket soil moisture W** when `P < E_0` — no equilibrium exists and `W` is under-determined (§4.2). Even when one exists, `dW/dt = R_A - E` (Manabe 1969 Eq. 21 [V]).
2. **Soil-moisture memory / retention time** — Koster & Suarez (1996) [V]; the storage-mediated annual balance of Milly (1994) [V].
3. **Atmospheric moisture q and the moisture-convergence feedback** — the box models must drop `dw/dt` to become diagnostic, valid "only for monthly, seasonal or yearly timescales" [V].
4. **The closed desert loop P -> W -> E -> q -> P as a self-amplifying feedback** — Manabe (1969) [V]; Charney (1975) [M]; Eltahir (1998) [M].
5. **Ocean slab temperature T_s** — `C_o h ~ 2.9e8 J m^-2 K^-1`, `tau ~ 4 yr` (Manabe & Stouffer 1980 [V]).
6. **Deep-ocean heat uptake, seasonal thermocline storage, and the seasonal cycle** — all outside `G = 0`.
7. **Ice–albedo bifurcation / hysteresis and branch selection** — North (1975) [M]; Eisenman & Wettlaufer (2009) [V].
8. **The diurnal cycle** — explicitly excluded by Manabe's own justification for `G = 0` [V].
9. **Anything whose steady state is non-unique** — the dry and saturated branches of §4.2 must be selected by history.

---
# REFERENCES

**Hydrology.** Manabe, S. (1969), *Mon. Wea. Rev.* **97**, 739–774 — https://doi.org/10.1175/1520-0493(1969)097%3C0739:CATOC%3E2.3.CO;2 **[V]** · Manabe, S. & Holloway, J. L. (1975), *J. Geophys. Res.* **80**, 1617–1649 — https://doi.org/10.1029/JC080i012p01617 **[M]** · Milly, P. C. D. (1994), *Water Resour. Res.* **30**, 2143–2156 — https://doi.org/10.1029/94WR00586 (USGS record https://pubs.usgs.gov/publication/70187191) **[V abstract]** · Koster, R. D. & Suarez, M. J. (1996), *J. Climate* **9**, 2551–2567 — https://doi.org/10.1175/1520-0442(1996)009%3C2551:TIOLSM%3E2.0.CO;2 **[V abstract]** · Robock, A. et al. (1995), *J. Climate* **8**, 15–35 — https://doi.org/10.1175/1520-0442(1995)008%3C0015:UOMSMA%3E2.0.CO;2 **[V abstract]**

**Aridity / Budyko.** Budyko, M. I. (1974), *Climate and Life*, Academic Press — book, **no DOI** · open Budyko-framework derivation, all curve forms transcribed from here — https://hess.copernicus.org/preprints/hess-2020-584/ **[V]** · Choudhury, B. J. (1999), *J. Hydrol.* **216**, 99–110 — https://doi.org/10.1016/S0022-1694(98)00293-5 **[M]** · Yang, H., Yang, D., Lei, Z. & Sun, F. (2008), *Water Resour. Res.* **44**, W03410 — https://doi.org/10.1029/2007WR006135 **[M]** · Yang, D. et al. (2007), *Water Resour. Res.* **43**, W04426 — https://doi.org/10.1029/2006WR005224 **[M]** · Zhang, L. et al. (2004), *Water Resour. Res.* **40**, W02502 — https://doi.org/10.1029/2003WR002710 **[M]** · Fu, B. P. (1981), *Sci. Atmos. Sin.* **5**, 23–31 — Chinese-language, **no DOI in Crossref**; equation verified only through Zhang et al. (2004) and the HESS derivation

**Recycling / feedbacks.** Brubaker, K. L., Entekhabi, D. & Eagleson, P. S. (1993), *J. Climate* **6**, 1077–1089 — https://doi.org/10.1175/1520-0442(1993)006%3C1077:EOCPR%3E2.0.CO;2 **[V abstract]** · Eltahir, E. A. B. & Bras, R. L. (1996), *Rev. Geophys.* **34**, 367–378 — https://doi.org/10.1029/96RG01927 **[M]** · Eltahir, E. A. B. & Bras, R. L. (1994), *Quart. J. Roy. Meteor. Soc.* **120**, 861–880 — https://doi.org/10.1002/qj.49712051806 **[M]** · box-model equations, open secondary source, transcribed not read in the originals — https://repository.tudelft.nl/file/File_f5164715-0136-4ab2-809f-d97a48362209 **[S]** · Charney, J. G. (1975), *Quart. J. Roy. Meteor. Soc.* **101**, 193–202 — https://doi.org/10.1002/qj.49710142802 **[M]** · Eltahir, E. A. B. (1998), *Water Resour. Res.* **34**, 765–776 — https://doi.org/10.1029/97WR03499 **[M]** · Rodriguez-Iturbe, I., Entekhabi, D. & Bras, R. L. (1991), *Water Resour. Res.* **27**, 1899–1906 — https://doi.org/10.1029/91WR01035 **[M]**

**Evaporation.** Penman, H. L. (1948), *Proc. Roy. Soc. A* **193**, 120–145 — https://doi.org/10.1098/rspa.1948.0037 **[V]** · Monteith, J. L. (1965), *Symp. Soc. Exp. Biol.* **19**, 205–234 — **no DOI** · Priestley, C. H. B. & Taylor, R. J. (1972), *Mon. Wea. Rev.* **100**, 81–92 — https://doi.org/10.1175/1520-0493(1972)100%3C0081:OTAOSH%3E2.3.CO;2 **[V abstract]** · Allen, R. G., Pereira, L. S., Raes, D. & Smith, M. (1998), *FAO Irrigation & Drainage Paper 56* — https://www.fao.org/3/x0490e/x0490e00.htm **[V]** · alpha = 1.26 — IAHS/WMO proceedings PDF https://www.ircwash.org/sites/default/files/71-IAHS83-5217.pdf **[S]** · alpha varies 1.25 -> 1.75 with aridity — https://doi.org/10.5194/hess-2016-220 **[V preprint]**

**Radiation.** Budyko, M. I. (1969), *Tellus* **21**, 611–619 — https://doi.org/10.3402/tellusa.v21i5.10109 **[V]** · Sellers, W. D. (1969), *J. Appl. Meteor.* **8**, 392–400 — https://doi.org/10.1175/1520-0450(1969)008%3C0392:AGCMBO%3E2.0.CO;2 **[V abstract only — coefficients not obtained]** · North, G. R. (1975), *J. Atmos. Sci.* **32**, 2033–2043 — https://doi.org/10.1175/1520-0469(1975)032%3C2033:TOEBCM%3E2.0.CO;2 **[V abstract only]** · North, G. R. & Coakley, J. A. (1979), *J. Atmos. Sci.* **36**, 1178–1188 — https://doi.org/10.1175/1520-0469(1979)036%3C1178:ASTFEB%3E2.0.CO;2 **[M]**, source of A = 203.3 / B = 2.09 · North, G. R., Cahalan, R. F. & Coakley, J. A. (1981), *Rev. Geophys.* **19**, 91–121 — https://doi.org/10.1029/RG019i001p00091 **[M]** · Graves, C. E., Lee, W.-H. & North, G. R. (1993), *J. Geophys. Res.* **98**, 5025–5036 — https://doi.org/10.1029/92JD02666 · https://www.osti.gov/biblio/6598291 **[M]** · A = 203.3, B = 2.09, I = A + BT with T in degC — open-source reproduction, Barnes et al. (2020) VPLanet https://arxiv.org/abs/1905.06367 **[S]** · A = 210, B = 2, T_f = -10 degC — Rose, *The Climate Laboratory* https://github.com/brian-rose/ClimateLaboratoryBook **[V]** · Trenberth, K. E., Fasullo, J. T. & Kiehl, J. (2009), *Bull. Amer. Meteor. Soc.* **90**, 311–324 — https://doi.org/10.1175/2008BAMS2634.1 **[V: OLR 238.5, ASR 341.3, net 0.9; surface LW 396 up / 333 down, LH 80, SH 17 W m^-2]** · Kiehl, J. T. & Trenberth, K. E. (1997), *Bull. Amer. Meteor. Soc.* **78**, 197–208 — https://doi.org/10.1175/1520-0477(1997)078%3C0197:EAGMEB%3E2.0.CO;2 **[M]** · Frierson, D. M. W., Held, I. M. & Zurita-Gotor, P. (2006), *J. Atmos. Sci.* **63**, 2548–2566 — https://doi.org/10.1175/JAS3753.1 **[V]** · Eisenman, I. & Wettlaufer, J. S. (2009), *PNAS* **106**, 28–32 — https://doi.org/10.1073/pnas.0806887106 **[V abstract]**

**Ocean mixed layer.** Manabe, S. & Stouffer, R. J. (1980), *J. Geophys. Res.* **85**, 5529–5554 — https://doi.org/10.1029/JC085iC10p05529 **[V]** · Manabe, S. & Wetherald, R. T. (1967), *J. Atmos. Sci.* **24**, 241–259 — https://doi.org/10.1175/1520-0469(1967)024%3C0241:TEOTAW%3E2.0.CO;2 **[M]**

---
## VERIFICATION GAPS (do not cite as fact)
* Sellers (1969), North (1975), North et al. (1981) and Graves et al. (1993) full texts were **blocked** (AMS/AGU Cloudflare, plus Wayback CDX rate-limiting); their A / B values come from an open-source reproduction and are flagged **[S]**.
* Bulk transfer coefficients C_H and C_E: no single canonical value verified here. Budyko shape parameters n and w: no canonical value verified; Fu (1981) has no DOI.
* FAO-56's 900 coefficient and Eq. (6) sit in rendered images; the derivative quantities 0.408, 0.34, r_s = 70 s/m and r_a = 208/u_2 **were** verified as text.
* Brubaker (1993) and Eltahir & Bras (1996) equations are transcribed from an open thesis, not read in the originals.
* UNEP/FAO aridity-index class thresholds were **not verified** here and are deliberately omitted.
