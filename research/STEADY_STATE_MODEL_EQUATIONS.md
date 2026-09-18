# Steady-state (diagnostic) atmospheric circulation model — equations, numerics, BCs
Grid: coarse lon x lat, periodic in lon, solved ONCE per world seed. Must emerge: (a) summer monsoon over a large
heated continent beside a warm ocean, (b) dry subtropical desert over a continent beside a cooler sea.
`[JUDGEMENT]` = modelling choice, not a fact. Symbols: `phi` lat, `p` press, `a=r` radius, `g` gravity,
`f=2 Omega sin phi`, `beta=df/dy`, `[X]` zonal mean, `X*` deviation, `theta` pot. temp, `omega=Dp/Dt`, `psi` streamfunction.

## 0. Verdict table
| # | Equation set | One-shot steady solve? | Why |
|---|---|---|---|
| 1 | Kuo–Eliassen `psi(phi,p)` | **NO, not as a closure** | balance condition on the *evolving* flow, not a steady-state equation |
| 2 | Linear steady stationary wave / Gill shallow water, land–sea heating | **YES** | genuinely elliptic/steady once basic state + damping + heating are prescribed |
| 3 | Vertically integrated `P-E=-div(qV)` | **YES** if `E` is closed diagnostically | needs diagnostic E from 4/5 |
| 4 | Manabe bucket | **NO** generally (prognostic); **YES** in the `dW/dt=0` limit as an active-set problem | bucket + runoff threshold = complementarity |
| 5 | Surface energy balance | **YES for land** (zero heat capacity); **NO for a deep ocean slab** | mixed layer has a relaxation timescale |
| 6 | Precedent models | mixed | §6 |
**Recommended architecture.** Solver = (2)+(3)+(4-steady)+(5-land), iterated to a fixed point. Use (1) **only as a
post-hoc diagnostic**. Nothing below is fully closed; §7 lists what stays prescribed.

## 1. Mean meridional circulation: the Kuo–Eliassen equation
### 1.1 Streamfunction (mass streamfunction, pressure coordinates)
```
psi(phi,p) = (2 pi a cos phi / g) * Integral_0^p [v] dp'                                 (1.1)
[v]     = ( g/(2 pi a cos phi) ) * dpsi/dp                                               (1.2)
[omega] = -( g/(2 pi a^2 cos phi) ) * dpsi/dphi                                          (1.3)
```
(1.2)-(1.3) satisfy `(1/(a cos phi)) d([v]cos phi)/dphi + d[omega]/dp = 0` identically. Normalisation differs
between papers (`2 pi r` vs `2 pi a`) — always check that (1.2)/(1.3) are consistent with (1.1). Verbatim from
[Knietzsch, Lucarini & Lunkeit 2014, ESD 5, 481 (OA)](https://esd.copernicus.org/articles/5/481/2014/).
### 1.2 Full elliptic operator + every forcing term
Exact form from [Knietzsch et al. 2014, ESD, Eq. 2](https://esd.copernicus.org/articles/5/481/2014/) (they cite
Peixoto & Oort 1992 §14.5.5):
```
 f^2 g/(2 pi r cos phi) * d2psi/dp2  -  g/(2 pi r^3 rho [theta]) * d/dphi( (d[theta]/dp) dpsi/dphi )
   =  1/(r rho [T]) d/dphi( [Q]/c_p )                                        <- DIABATIC HEATING
    -  f d[F]/dp                                                             <- FRICTION / ZONAL DRAG
    -  1/(r^2 rho [theta]) d/dphi( (1/cos phi) d([v*theta*] cos phi)/dphi )  <- EDDY HEAT FLUX
    +  f/(r cos^2 phi) d2( [u*v*] cos^2 phi )/(dp dphi)                      <- EDDY MOMENTUM FLUX   (1.4)
```
`Q` diabatic heating rate, `c_p` specific heat, `F` zonal-wind tendency from friction/sub-grid stress, `rho`
density. Dimensionally consistent (`[psi]=kg/s`; both sides `m^2 s^-1 kg^-1`).
**QG form** (constant f, constant N), verbatim from
[Held & Zurita-Gotor 2025, JAS 82, 1763-1766, Eq. 5](https://doi.org/10.1175/JAS-D-24-0246.1):
```
f^2 d2psi/dz2 + N^2 d2psi/dy2 = f dG/dz + dH/dy ,   [v,w] = [-dpsi/dz, dpsi/dy]           (1.5)
```
`G` zonal momentum forcing, `H` buoyancy forcing. **This is the cleanest implementable diagnostic**: a two-term
elliptic operator, one Poisson inversion per forcing term (linear ⇒ superposition).
**General non-QG vortex form** (arbitrary Rossby number, spatially varying stabilities), verbatim from
[Montgomery & Persing 2021, JAS 78, 75-95 §4](https://core.ac.uk/download/479441386.pdf) (open copy):
```
d/dr(A dpsi/dr + B dpsi/dz) + d/dz(C dpsi/dz + B dpsi/dr) = Q_f
u = -(1/(r rho)) dpsi/dz ,  w = (1/(r rho)) dpsi/dr
A = (g/theta)(1/(r rho))(dtheta/dz)/N^2      [static stability]
B = -(1/(r rho)) d(xi C)/dz                  [baroclinicity]
C = ( xi(zeta+f) chi + C dchi/dr )/(r rho)   [inertial stability]
Q_f = g d/dr(chi^2 udot) + d/dz(C chi^2 udot) + d/dz(xi chi Vdot)                        (1.6)
```
This is the tropical-cyclone form ([Eliassen 1951](https://doi.org/10.1175/1520-0469(1951)008<0001:OTPOTF>2.0.CO;2);
[Kuo 1956](https://doi.org/10.1175/1520-0469(1956)013<0561:FAFMCI>2.0.CO;2)); for a planetary model use (1.4)/(1.5).
### 1.3 Ellipticity condition
Discriminant, verbatim [Montgomery & Persing 2021, Eq. 11](https://core.ac.uk/download/479441386.pdf):
`D = 4 gamma^2 [ (2 gamma/chi)(dchi/dz)(xi zeta_a + (C/chi) dchi/dr) - (1/chi) d(C chi)/dz ]`,
`gamma = chi/(r rho)`, `zeta_a = zeta + f`. "The balance equation is elliptic when D is everywhere positive...
If D < 0 at isolated points, or extended regions..., the balance equation is locally hyperbolic and the flow there
satisfies the conditions for symmetric instability. Technically speaking, the balance equation loses solvability as
an elliptic (balance) problem."
For the zonal-mean form (1.4) state it operationally — elliptic iff
`f^2 > 0` everywhere (the grid must not contain the exact equator, where the operator degenerates) **(1.7a)** and
`d[theta]/dp < 0` everywhere (positive static stability) **(1.7b)**.
(1.7a) is a real problem for a coarse lon–lat grid with a row at `phi=0`. Remedies: use the equatorial beta-plane
`f=beta y` and the Gill balance there (§2.3); floor `f^2` at `f_min^2`; or use the non-QG `f(f - d[u]/dy)`.
[JUDGEMENT] flooring `f^2` is the least intrusive for a world grid. Non-elliptic regions need **regularization**:
[Möller & Shapiro 2002, MWR 130, 1866-1881](https://doi.org/10.1175/1520-0493(2002)130<1866:BCTTIO>2.0.CO;2), used
by Bui et al. (2009), Abarca & Montgomery (2014, 2015) and [Montgomery & Persing 2021](https://core.ac.uk/download/479441386.pdf).
### 1.4 Boundary conditions (well-posedness)
* `psi = 0` on **all four** boundaries: `p=p_s`, `p=0`, `phi=+-90`. Rationale: `omega=0` at `p=0` and `p=p_s`
  (mass conservation / rigid lid) and `[v]=0` at the poles. Stated verbatim in
  [Grotjahn ATM240 PS8](http://grotjahn.ucdavis.edu/course/atm240/2019/hwk2019-8_v2.pdf) ("The stream function, psi
  is zero along the boundaries of the domain") and in a 2025 GRL supplement ("At surface (p = 1000 hPa), omega is
  assumed to be zero").
* Channel variant: `psi=0` at the channel walls instead of the poles.
### 1.5 Numerical method (recommended)
* **Best for a coarse grid — double Fourier sine series.** [Grotjahn's PS8](http://grotjahn.ucdavis.edu/course/atm240/2019/hwk2019-8_v2.pdf)
  solves `A d2psi/dy2 + C d2psi/dp2 = -dH/dy` with `psi = sum F_nm sin(n pi (y+2)/4) sin(m pi (P-0.1)/0.9)`,
  which satisfies `psi=0` on all boundaries automatically, with closed-form `F_nm`. Microseconds at 64x32.
* Else **Gauss–Seidel/SOR** ([Knietzsch et al. 2014](https://esd.copernicus.org/articles/5/481/2014/): "applying an
  iterative method (Gauss-Seidel method) to its finite difference approximation") or **Jacobi**
  ([Nature Comms 2026](https://doi.org/10.1038/s41467-026-69990-0): "solved... using a Jacobi iteration method").
* Derivation/mathematical remarks: [Yano 2011, JAMES 3, M03001](https://doi.org/10.1029/2011MS000058) (gold OA).
### 1.6 What must be KNOWN before (1.4)/(1.5) can be solved
(1) `[theta]` or `[T]` (static stability, inside the operator); (2) `rho(phi,p)`, `f(phi)`; (3) `[Q]` diabatic
heating — in a moist model dominated by latent heating, hence dependent on precipitation, hence on the moisture
budget (§3), hence on the circulation (**circular**); (4) `[F]` friction/drag; (5) `[v*theta*]`, `[u*v*]` eddy
fluxes, produced by baroclinic eddies that respond to the circulation (**circular**). ⇒ Nothing is closed.
### 1.7 [FLAG] The Kuo–Eliassen equation CANNOT be your steady-state closure
Verbatim, [Held & Zurita-Gotor 2025, JAS 82, 1763-1766](https://doi.org/10.1175/JAS-D-24-0246.1) abstract:
> "The Kuo-Eliassen equation provides the mean meridional circulation that must be present for the axisymmetric
> component of a flow forced by heat and momentum sources to remain **balanced as it evolves**. It does **not**
> tell us whether or not the flow is steady."
Eliminating the ageostrophic flow instead of the time tendencies gives the PV equation
`dq/dt = -v dQ/dy + (f/N^2) dH/dz`; the true **steady-state condition is** `dG/dy = (f/N^2) dH/dz` **(1.8)** — a
PV-tendency condition, not the K-E operator. Since (1.5) is linear one may write `psi(G,H)=psi(0,H)+psi(G,0)`, but if
`[G,H]` satisfies (1.8) then `[G,0]` and `[0,H]` do **not** — so this is *not* a decomposition of the steady solution
into heating-forced and momentum-forced steady solutions. Splitting forcing into prescribed + reactive parts reproduces
the K-E operator only if the reactive damping is **spatially uniform and identical in the heat and momentum equations**;
with `lambda_M != lambda_T` the operator changes:
`f^2 (lambda_T/lambda_M) psi_zz + N^2 psi_yy = f(lambda_T/lambda_M) G_z + H_y`. They call uniform damping "very
suspect, especially for the momentum damping". Earlier critiques:
[Chang 1996, JAS 53, 113-125](https://doi.org/10.1175/1520-0469(1996)053<0113:MMCDBE>2.0.CO;2);
[Kim & Lee 2001, JAS 58, 2845-2858](https://doi.org/10.1175/1520-0469(2001)058<2845:HCDIAP>2.0.CO;2).
**Consequence.** Get the overturning from an explicitly steady closed closure — angular-momentum-conserving Hadley cell
([Held & Hou 1980](https://doi.org/10.1175/1520-0469(1980)037<0515:NASIA>2.0.CO;2);
[Lindzen & Hou 1988](https://doi.org/10.1175/1520-0469(1988)045<2416:HCFZAH>2.0.CO;2)), or MSE/WTG (§3), or a prescribed
relaxation. Compute the K-E decomposition once, after convergence, as a diagnostic if desired.

## 2. Stationary waves forced by land–sea diabatic heating
### 2.1 Hoskins & Karoly (1981): linear steady 3-D primitive equations
[Hoskins & Karoly 1981, JAS 38, 1179-1196](https://doi.org/10.1175/1520-0469(1981)038<1179:TSLROA>2.0.CO;2).
Background `u(z)`, `theta(y,z)`; steady, linear, geostrophic + hydrostatic + Boussinesq. Equations exactly as
re-derived in [Czaja's notes](http://www.sp.ph.ic.ac.uk/~aczaja/PG2013/Notes_HK81.pdf):
```
u d(zeta')/dx + beta v' = f d(w')/dz                                          (2.1)  [HK81 (3.1)]
f u d(v')/dz - f (du/dz) v' + N^2 w' = Q                                      (2.2)  [HK81 (3.2b)]
zeta' = dv'/dx - du'/dy ,  beta = 2 Omega cos(phi)/r ;  N^2 = g alpha theta_z ;
Q = (g alpha theta/(c_p T)) Qdot'   with rho = rho_0 (1 - alpha theta)        (2.3)
```
**The heating enters only through `Q` on the RHS of the thermodynamic equation** — precisely the structure needed
for land–sea contrast. **Scaling that separates monsoon from desert:** with
`gamma ~ f^2 u/(beta N^2 H_Q H_u)`: `gamma << 1` (deep tropics) ⇒ `w' N^2 ~ Q`; with `w'=0` at the surface this
gives **poleward low-level flow and a low to the WEST of the heating = the monsoon** (HK81 Fig. 2a).
`gamma >> 1` (midlatitudes) ⇒ `v' theta_y ~ Q`, giving **equatorward flow and descent, low to the EAST** = the
subtropical desert response (HK81 Fig. 2b). One heating therefore yields a monsoon in the tropics and subsidence in
the subtropics; `gamma` depends on `f^2/beta`, i.e. strongly on latitude. **This is the minimal monsoon/desert separator.**
### 2.2 Beta-plane channel form (what to code)
From [Held's GFDL Lecture 3](https://www.gfdl.noaa.gov/wp-content/uploads/files/user_files/io/lect_3_572.pdf):
```
q = lap(psi) + f + (f_0^2/rho_0) d/dz( (rho_0/N^2) dpsi/dz )                             (2.4)
dq/dt + U dq/dx + v dQ/dy = 0 ,  v = dpsi/dx ,  omega = U k - beta k/(k^2+l^2)            (2.5)
stationary: U = beta/(k^2+l^2) ;  C_gx = 2 U k^2/(k^2+l^2) > 0  for U > 0                (2.6)
```
Linearised about `U(y,z)`, `Q(y,z)`, Rayleigh friction `alpha`, Newtonian cooling `lambda`, buoyancy source `B'`:
```
U d/dx[ lap(psi') + (f_0^2/rho_0) d/dz((rho_0/N^2) dpsi'/dz) ] + (dQ/dy) dpsi'/dx
  = -alpha lap(psi') - (f_0^2/rho_0) d/dz( (rho_0/N^2) lambda dpsi'/dz ) + (f_0/N^2) dB'/dz   (2.7)
```
Last term derived from Boussinesq QG: with `b = f_0 dpsi/dz` and `D_g b/Dt + N^2 w = B`, eliminating `w` between the
buoyancy and vorticity equations gives `D_g q/Dt = (f_0/N^2) dB/dz` (check: `[f_0/N^2]=s`, `[dB/dz]=s^-3`, product
`s^-2 = [Dq/Dt]`). In pressure coordinates use `z = -H ln(p/p_s)`.
**[JUDGEMENT] Damping is the main fork.** Equal uniform `alpha = lambda` collapses (2.7) to
`(U d/dx + alpha) q' + (dQ/dy) psi'_x = forcing`, clean and invertible. Unequal damping rescales `f_0^2/N^2`
([Held & Zurita-Gotor 2025](https://doi.org/10.1175/JAS-D-24-0246.1) Eq. 11), and treating free-tropospheric eddy
momentum flux convergence as local damping cannot reproduce the non-local annular-mode-like response
(Chen & Zurita-Gotor 2008, cited there). Uniform equal damping is the pragmatic choice — **state it as an assumption**.
Damping is also what makes the steady problem well posed: at `U = beta/K^2` the undamped steady response is singular.
**Numerics:** periodic in x ⇒ FFT in x, then a 2-D `(y,z)` boundary-value problem per zonal wavenumber.
`U d/dx` makes the operator **hyperbolic in x** (stationary Rossby waves radiate downstream), so solve the *steady*
equation by relaxation, or use exact matrix inversion (§6). BCs: `psi'=0` at `p=p_s` and `p=0`; channel walls
`v'=0`; periodic in x; radiation condition in x or enough damping that the domain is absorbing. [JUDGEMENT] damping
is the practical substitute for a radiation condition.
### 2.3 Minimal model that separates a strong monsoon from a weak desert
[Gill 1980, QJRMS 106, 447-462](https://doi.org/10.1002/qj.49710644905): linear steady shallow water on the
equatorial beta-plane, Rayleigh friction = Newtonian cooling `eps`:
```
eps u - beta y v = -dp/dx ;  eps v + beta y u = -dp/dy ;  eps p + c^2(du/dx+dv/dy) = -Q    (2.8)
```
`Q` = mass source (deep convective heating projected on the first baroclinic mode), `c = N H/pi`,
`L_R = c/beta`. Solution (verbatim, [UH OCN666 notes](https://rcfftp.soest.hawaii.edu/niklas/OCN666/ForcedEquatorialMotion.pdf)):
with `q=p+u`, `r=p-u`, expand in parabolic cylinder functions `D_n(y)`:
```
dq_0/dx + q_0 = -Q_0   (forced KELVIN, decays EAST on scale eps^-1, Gaussian meridional structure)
3 dq_2/dx - q_2 = -Q_2 (forced ROSSBY, decays WEST on scale (3 eps)^-1, D_1 meridional structure)   (2.9)
```
"In response to heating there is poleward flow in the boundary layer due to vortex stretching."
**Asymmetric land–sea heating therefore gives a monsoon/Kelvin response to the east and descent/Rossby response to
the west.** Gill's abstract: a solution "with the heating displaced north of the equator provides a flow similar to
the monsoon circulation of July".
**Caveat:** Gill alone produces **no desert** — there is no midlatitude westerly basic state for the Rossby response
to interact with. The desert needs (2.1)-(2.2) or (2.7) with `dQ/dy != 0`: the
[Rodwell & Hoskins 1996, QJRMS 122, 1385-1404](https://doi.org/10.1002/qj.49712253408) mechanism. Verbatim from
[Rodwell & Hoskins 2001, J. Climate 14, 3192-3211](https://doi.org/10.1175/1520-0442(2001)014<3192:SAASM>2.0.CO;2):
> "the equatorward portion of each subtropical anticyclone may be viewed as the **Kelvin wave response to the
> monsoon heating over the continent to the west**. A poleward-flowing low-level jet into a monsoon ... is required
> for **Sverdrup vorticity balance**. ... The **Rossby wave response to the west of subtropical monsoon heating,
> interacting with the midlatitude westerlies, produces a region of adiabatic descent**. ... the Mediterranean-type
> climates of regions such as California and Chile may be **induced remotely by the monsoon to the east**."
Sverdrup balance `beta v = f d(w)/dz` is the diagnostic closure for the low-level flow. **RH2001's own warning
about linearising this:** "The June–August zonal-mean winds between 10N and 30N generally lie in the range -5 to +5
m/s. **The critical line at which it is zero will clearly play a central role in any linearized, steady-state
model.**" At that critical line the steady linear operator is singular — you must damp through it or go nonlinear.
### 2.4 Classic references
[Smagorinsky 1953, QJRMS 79, 342-366](https://doi.org/10.1002/qj.49707934103) (first linear steady heat-source model) ·
[Webster 1972, MWR 100, 518-541](https://doi.org/10.1175/1520-0493(1972)100<0518:ROTTAT>2.3.CO;2) (local *steady* forcing, first monsoon-as-heating model) ·
[Gill 1980](https://doi.org/10.1002/qj.49710644905) · [Hoskins & Karoly 1981](https://doi.org/10.1175/1520-0469(1981)038<1179:TSLROA>2.0.CO;2) (steady linear 3-D PE, ray theory) ·
Held (1983), "Stationary and quasi-stationary eddies in the extratropical troposphere: theory", in Hoskins & Pearce (eds), *Large-Scale Dynamical Processes in the Atmosphere*, Academic Press, 127-168 (book chapter, no DOI) ·
[Held & Ting 1990, JAS 47, 495-500](https://doi.org/10.1175/1520-0469(1990)047<0495:OVTFOS>2.0.CO;2) (orographic vs thermal forcing; role of low-level wind; thermal forcing dominates when low-level flow is weak, orographic when strong) · **HUANG & GAMBO 1983, J. Meteor. Soc. Japan 61, 495-509 — [OPEN ACCESS, text-extractable](https://www.jstage.jst.go.jp/article/jmsj1965/61/4/61_4_495/_pdf): a STEADY, LINEAR, QUASI-GEOSTROPHIC 34-LEVEL model with Rayleigh friction, Newtonian cooling and horizontal thermal diffusivity in spherical coordinates, forced by SUMMER stationary heat sources and topography. This is the closest published precedent to the target model.** Verbatim result: the amplitude of stationary planetary waves forced by heat sources is LARGER than that forced by topography; the summer response is mainly confined to the troposphere over the subtropics (max near 30N, upper troposphere, k=1,2), and the refractive index squared is negative in the summer stratosphere so stationary waves cannot propagate vertically in summer. Verified parameters: Ekman friction F = 4e-6 s^-1, p_s = 1000 mb, top ~92 km, 34 layers, N = 2e-2 s^-1, H0 = 7 km · [Chen 2001, JAS 58, 1585-1594](https://doi.org/10.1175/1520-0469(2001)058<1585:TFSWIA>2.0.CO;2) — thermally forced **linear stationary waves** on a beta plane, uniform zonal flow, Newtonian cooling `delta`, Ekman drag `gamma`, channel walls at `y = +-W/2`, radiation condition at infinity. Shows heating acts as an **equivalent topography** whose height is proportional to heating intensity and zonal scale and **inversely proportional to the zonal flow** — so for weak summer westerlies the equivalent topography exceeds the real topography (heating dominates orography in the summer subtropics, consistent with Huang & Gambo) ·
[Ting & Held 1990, JAS 47, 2546-2566](https://doi.org/10.1175/1520-0469(1990)047<2546:TSWRTA>2.0.CO;2) ·
[Held, Ting & Wang 2002, J. Climate 15, 2125-2144](https://doi.org/10.1175/1520-0442(2002)015<2125:NWSWTA>2.0.CO;2) (review) ·
[Chou, Neelin & Su 2001, QJRMS 127, 1869-1891](https://doi.org/10.1002/qj.49712757602) (ocean–atmosphere–land feedbacks in an *idealized monsoon*; closest precedent) ·
[Bretherton & Sobel 2003, JAS 60, 451-460](https://doi.org/10.1175/1520-0469(2003)060<0451:TGMATW>2.0.CO;2) (when Gill is the right minimal model).

## 3. Moisture budget closure
### 3.1 Vertically integrated moisture budget
```
P - E = -(1/(rho_w g)) div_2D( Integral_0^{p_s} q V dp )                                  (3.1)
      = -(1/(rho_w g)) [ div( qbar Vbar ) + div( Integral q' V' dp ) ]   (mean + eddy)    (3.2)
```
Standard per [Trenberth & Guillemot 1995, J. Climate 8, 2255-2272](https://doi.org/10.1175/1520-0442(1995)008<2255:EOTGAM>2.0.CO;2);
basis of [O'Gorman & Schneider 2008, J. Climate 21, 3815-3832](https://doi.org/10.1175/2007JCLI2065.1) ("In a steady
state, in which changes in atmospheric moisture storage are negligibly small, the difference between precipitation
and evaporation ... is balanced by the divergence of the vertically integrated atmospheric moisture flux").
[JUDGEMENT] A coarse 2-D grid resolves no transients: drop the eddy term in (3.2) (and say so) or parameterise it
as downgradient diffusion `-D_q lap(q)`. Dropping it removes the storm-track precipitation maximum but is standard.
### 3.2 How `q` is obtained
**(a) Advection–condensation.** `C = (q-q_sat)/tau_c` when `q > q_sat`, else 0; implicit adjustment and `q*`
verbatim from [Frierson, Held & Zurita-Gotor 2006, JAS 63, 2548-2566, Eqs. 21-22](https://doi.org/10.1175/JAS3753.1):
`delta_q = (q*-q)/(1 + (L_v/c_p) dq*/dT)`, `e*(T) = e*_0 exp[-(L_v/R_v)(1/T - 1/T_0)]`, `q* = eps e*/p`,
`e*_0 = 610.78 Pa` at `T_0 = 273.16 K`, `eps = R_d/R_v`. **Caveat from that paper:** with large-scale condensation
only, "each layer below the level of condensation must be saturated by reevaporation for the rain to fall below this
level, so the column must be saturated all the way down for precipitation to reach the ground" — bad for a monsoon,
which is why [Frierson 2007, JAS 64, 1959-1976](https://doi.org/10.1175/JAS3935.1) adds idealized convection schemes.
**(b) Diagnostic RH / moisture-convergence closure — recommended for a one-shot steady solve.** *(Caution: the widely used label `beta_q` does NOT appear in Neelin & Zeng 2000, Zeng et al. 2000, or the QTCM1 manual — the mechanism is real but the symbol is folklore. QTCM's own steady closure is the gross moist stability `M_1 = M_S1 - M_q1`.)* *Fixed-RH:*
`q(phi,p) = RH(p) q_sat(T(phi,p),p)`, per
[Manabe & Wetherald 1967, JAS 24, 241-259](https://doi.org/10.1175/1520-0469(1967)024<0241:TEOTAW>2.0.CO;2).
*`beta_q`/moisture-convergence:* column moisture sink proportional to mass convergence with a coefficient
interpolating moist/dry limits — [Neelin & Zeng 2000, JAS 57, 1741-1766](https://doi.org/10.1175/1520-0469(2000)057<1741:AQETCM>2.0.CO;2)
and [Zeng, Neelin & Chou 2000, JAS 57, 1767-1796](https://doi.org/10.1175/1520-0469(2000)057<1767:AQETCM>2.0.CO;2);
moisture-mode/WTG limit [Sobel, Nilsson & Polvani 2001, JAS 58, 3650-3665](https://doi.org/10.1175/1520-0469(2001)058<3650:TWTGAA>2.0.CO;2),
[Emanuel, Neelin & Bretherton 1994, QJRMS 120, 1111-1143](https://doi.org/10.1002/qj.49712051902). *MSE closure (most
parsimonious):* in steady state the column moist-static-energy budget `<V . grad(m)> = F_net` gives `P` from the
circulation and surface fluxes with no cloud scheme —
[Neelin & Held 1987, MWR 115, 3-12](https://doi.org/10.1175/1520-0493(1987)115<0003:MTCBOT>2.0.CO;2).
### 3.3 Diagnostic-precipitation references
[O'Gorman & Schneider 2008](https://doi.org/10.1175/2007JCLI2065.1) canonical `P-E` from the moisture budget across
a wide range of climates · [Muller & O'Gorman 2011, Nature Clim. Change 1, 266-271](https://doi.org/10.1038/nclimate1169)
energetic (MSE) perspective · [Bony et al. 2013, Nature Geosci. 6, 447-451](https://doi.org/10.1038/ngeo1799)
circulation vs thermodynamic control · [Byrne & O'Gorman 2015, J. Climate 28, 8078-8092](https://doi.org/10.1175/JCLI-D-15-0369.1)
`P-E` over **land**; why "wet-get-wetter" fails there · [Chou & Neelin 2004, J. Climate 17, 2688-2701](https://doi.org/10.1175/1520-0442(2004)017<2688:MOGWIO>2.0.CO;2)
"upped-ante" mechanism.

## 4. Land surface hydrology
### 4.1 The Manabe (1969) bucket
[Manabe 1969, MWR 97, 739-774 ("Climate and the Ocean Circulation: I")](https://doi.org/10.1175/1520-0493(1969)097<0739:CATOC>2.3.CO;2).
One soil-moisture reservoir `W` (equivalent water depth), with snow a separate reservoir. **Equations read verbatim
from the original MWR PDF** (Manabe 1969 eqs. 16, 18-21):
```
dW/dt = R_A - E ;  if W = W_FC and R_A > E_0 :  dW/dt = 0 and runoff r_s = R_A - E_0     (4.1) [his eq. 21]
E_0 = rho(h) C_D(h) |V(h)| ( r_ws(T*) - r(h) )        [evaporability; his eq. 19]        (4.2)
E   = E_0                 for W >= W_K                                                   (4.3a)[his eq. 18]
E   = E_0 (W / W_K)       for W <  W_K      =>   beta = min(1, W/W_K)                    (4.3b)[his eq. 18]
W_K = 0.75 W_FC ;   W_FC = 15 cm everywhere (after Palmer 1966)                           (4.4) [his eq. 20]
S* + (DLR)* = sigma T*^4 + (c_p H)* + (L E)*   [his eq. 16: T* solved DIAGNOSTICALLY, G = 0]  (4.5)
```
**CORRECTION to the common statement of the bucket: the `beta` threshold is `W_K = 0.75 W_FC`, NOT `W_FC` — Manabe's
`beta` reaches 1 at 75% of field capacity, not at saturation.** Many later codes (and most textbook summaries) use
`W_FC`; state which you use. **Note also that (4.5) shows Manabe himself already solved the land surface temperature
diagnostically** (`G = 0`), which is direct precedent for §5.3.
Follow-ups/validations: [Manabe & Holloway 1975, JGR 80, 1617-1649](https://doi.org/10.1029/JC080i012p01617) (seasonal hydrologic cycle in a GCM),
[Robock et al. 1995, J. Climate 8, 15-35](https://doi.org/10.1175/1520-0442(1995)008<0015:UOMSMA>2.0.CO;2),
[Koster & Suarez 1996, J. Climate 9, 2551-2567](https://doi.org/10.1175/1520-0442(1996)009<2551:TIOLSM>2.0.CO;2),
[Milly 1994, WRR 30, 2143-2156](https://doi.org/10.1029/94WR00586). [JUDGEMENT] the form of `beta(W)`, the threshold
(`W_K` vs `W_FC`) and `W_FC` itself (15 cm vs 150 mm vs 1 m) all differ between implementations — state your choice.
Manabe's own caveat, verified in the text: with no seasonal cycle the high-latitude `W` pins at `W_FC`, which he calls
"somewhat unrealistic".
### 4.2 [FLAG] The bucket is PROGNOSTIC and NOT steady-state solvable in general
`dW/dt` is soil-moisture *memory*: a dry anomaly persists and feeds back on `E`, hence on `q`, hence on `P`. That
memory is the physical content of the desert feedback and **cannot be recovered from a single steady solve**. In the
steady limit `dW/dt=0` ⇒ `E = P_r - R_runoff` **(4.4)**, which with (4.2)+(4.3) is a **complementarity /
free-boundary problem**: either `W < W_K` and `r_s = 0` ⇒ `E = R_A = (W/W_K) E_0` ⇒ `W = W_K R_A/E_0` **(4.6a)**;
or `W = W_FC` and `r_s > 0` ⇒ `E = E_0`, `r_s = R_A - E_0` **(4.6b)**. Equivalently `E = min(E_0, R_A)` and
`W = W_K min(1, R_A/E_0) = 0.75 W_FC min(1, R_A/E_0)` — note the `0.75`, from (4.4). Intermediate states with
`W_K <= W < W_FC` have `beta = 1` and `dW/dt = R_A - E_0`, so `W` drifts to `W_FC`; they are not steady.
**Solve by active-set / fixed-point iteration:** guess the wet/dry partition, solve `E`, re-evaluate, repeat (a few
iterations). Cheap, and genuinely diagnostic.
### 4.3 Budyko / Milankovitch aridity index
Radiational index of dryness (Budyko 1974, *Climate and Life*, D. H. Miller transl., Academic Press, 508 pp. — book, no DOI):
`DI = R_n/(L P)`. Modern aridity index `AI = E_p/P`. The Budyko framework states
`E/P = F(E_p/P)` **(4.6)**, bounded by the water limit `E <= P` and the energy limit `E <= E_p`. Closed forms:
Budyko (1974) `E/P = {(E_p/P) tanh(P/E_p)[1 - exp(-E_p/P)]}^{1/2}`;
[Choudhury 1999, J. Hydrol. 216, 99-110](https://doi.org/10.1016/S0022-1694(98)00293-5)
`E = E_p P/(P^n + E_p^n)^{1/n}`, re-derived analytically by
[Yang, Yang, Lei & Sun 2008, WRR 44, W03410](https://doi.org/10.1029/2007WR006135) — the Choudhury and Yang forms are identical, both written `E/P = phi/(1 + phi^n)^{1/n}` with `phi = E_p/P`; Fu (1981) = Zhang et al. (2004) `E/P = 1 + phi - (1 + phi^w)^{1/w}`. `n` / `w` are tunable shape parameters ([JUDGEMENT] their values are NOT verified here). [JUDGEMENT] `AI << 1` ⇒
energy-limited (wet); `AI >> 1` ⇒ water-limited (desert). A desert requires `AI >> 1`, i.e. large `E_p` and small
`P` — which is the *output* of §2, not an input.
### 4.4 The self-consistent desert loop
`heating contrast -> monsoon circulation (§2) -> remote subsidence to the west -> P down -> W down -> beta(W) down
-> E down -> q down -> P down further` **(4.7)**. The steps `W->beta->E->q` are diagnostic given `P` and `T_s`; the
`q->P` step is diagnostic via (3.1). What requires **time stepping** is the *transient* of this loop (soil-moisture
memory, moisture-convergence feedback); the *fixed point* is reachable by iteration without time stepping. **This
loop is what makes the desert emergent rather than prescribed.** Precipitation recycling, the local-origin fraction
`rho = P_local/P`, is scale-dependent — see
[Brubaker, Entekhabi & Eagleson 1993, J. Climate 6, 1077-1089](https://doi.org/10.1175/1520-0442(1993)006<1077:EOCPR>2.0.CO;2)
and [Eltahir & Bras 1996, Rev. Geophys. 34, 367-378](https://doi.org/10.1029/96RG01927). Verified closed forms: Budyko–Drozdov
`R = eL/(eL + 2 Q_ox)`; Brubaker et al. `R = EA/(EA + 2 I)`; Eltahir & Bras `rho = (I_i + EA)/(I + EA)` — where `E` is
 evaporation, `A` region area, `I` the incoming moisture flux and `L`/`Q_ox` the length/flux scales (verified in the S4
 appendix; the symbol definitions should be re-checked against the originals before use).
### 4.5 Potential evaporation and the recommended land scheme
*Penman–Monteith:* [Penman 1948, Proc. R. Soc. A 193, 120-145](https://doi.org/10.1098/rspa.1948.0037); Monteith
1965, *Symp. Soc. Exp. Biol.* 19, 205-234. *Priestley–Taylor* (simplest without a surface-resistance scheme):
`LE_p = alpha_PT (Delta/(Delta+gamma)) (R_n - G)`, `alpha_PT = 1.26`, `Delta = de*/dT`, `gamma = c_p p/(0.622 L_v)`
**(4.8)** — [Priestley & Taylor 1972, MWR 100, 81-92](https://doi.org/10.1175/1520-0493(1972)100<0081:OTAOSH>2.3.CO;2).
**Recommended land column (fully diagnostic, iterated):** (4.8) for `E_p`; (4.5) for `W,beta,E`; (3.1) for `P`; §5
for `T_s`; iterate to a fixed point. `alpha_PT = 1.26` is verified (IAHS/WMO). **Verified caveat: `alpha_PT` is NOT constant — it rises from ~1.25 toward
~1.75 as aridity increases**, so a single `alpha_PT` biases dry regions low. [JUDGEMENT] multiplying `alpha_PT` by
`beta(W)` is the conventional fix but double-counts part of that aridity dependence; a `beta(W)`-dependent `alpha_PT` is
more faithful. Either way, state the choice.

## 5. Surface energy balance and temperature
### 5.1 Balance and fluxes
Sign convention: all fluxes **positive downward into the surface** except `H`, `LE`, `G` (positive upward):
```
(1-alpha_s) S_down + eps L_down - eps sigma_SB T_s^4 - H - LE - G = 0                       (5.1)
H  = rho_a c_p C_H |V| (T_s - T_a)                                                          (5.2)
LE = rho_a L_v C_E |V| ( q_sat(T_s) - q_a )                                                 (5.3)
q_sat(T_s) = eps_s e*(T_s)/p_s ,  e*(T_s) = e*_0 exp[-(L_v/R_v)(1/T_s - 1/T_0)]              (5.4)
```
`alpha_s` albedo, `eps` emissivity (~1), `C_H = C_E ~ 1.0-1.5e-3`. These are verbatim the drag laws of
[Frierson et al. 2006, Eqs. 9-14](https://doi.org/10.1175/JAS3753.1) (equal coefficients for momentum, heat, water;
`C = kappa^2/[ln(z_a/z_0)]^2` for `Ri_a<0`, reduced by `(1 - Ri_a/Ri_c)` for stable conditions, `C=0` for
`Ri_a > Ri_c`; `z_0 = 3.21e-5 m`, `kappa=0.4`, `Ri_c=1`, giving `C ~ 0.001` at `z=10 m`). [JUDGEMENT] "unstable ⇒
neutral" is the standard cheap choice, but the same paper warns "we have observed the tropical precipitation
distributions to be sensitive to the formulation of the unstable side of the surface flux formulation". It damps the
land–sea contrast — if your monsoon is too weak, this is the first knob.
### 5.2 Linearized OLR
`F_OLR = A + B T_s` **(5.5)**, `[A]=W m^-2`, `[B]=W m^-2 K^-1`. Origin: [Budyko 1969, Tellus 21, 611-619](https://doi.org/10.3402/tellusa.v21i5.10109),
[Sellers 1969, J. Appl. Meteor. 8, 392-400](https://doi.org/10.1175/1520-0450(1969)008<0392:AGCMBO>2.0.CO;2),
[North 1975, JAS 32, 1301-1307](https://doi.org/10.1175/1520-0469(1975)032<1301:ASTASC>2.0.CO;2), reviewed in
[North, Cahalan & Coakley 1981, Rev. Geophys. 19, 91-121](https://doi.org/10.1029/RG019i001p00091); modern fits in
[Graves, Lee & North 1993, JGR 98, 5025-5036](https://doi.org/10.1029/92JD02666). **Sources disagree on A and B and
the values are reference-state dependent — and the disagreement has a physical cause: Budyko's original fit retains an
explicit cloudiness dependence.** His form (verified in the Tellus PDF) is `I = a + bT - (a1 + b1 T) n` with
`a = 14.0`, `b = 0.14`, `a1 = 3.0`, `b1 = 0.10` (kcal cm^-2 month^-1, `T` in degC, RMS < 5%), `n` = cloud fraction;
converted, clear-sky `A = 222.9`, `B = 2.23`, and at `n = 0.5` `A = 199.0`, `B = 1.43` W m^-2 K^-1. Compare
North & Coakley (1979) `A = 203.3`, `B = 2.09`. Present-day global-mean OLR is ~239 W m^-2 at `T_s~288 K`
([Trenberth, Fasullo & Kiehl 2009, BAMS 90, 311-324](https://doi.org/10.1175/2008BAMS2634.1)), so `A + 288B ~ 239`;
per-K slopes in the EBM literature cluster near `B ~ 2` W m^-2 K^-1 but differ by tens of percent between fits and
between clear-sky and all-sky. **Re-fit A and B to your own radiative code or your chosen observations.** A more
physical alternative is gray radiation solved in the vertical (§6, Frierson et al.).
### 5.3 Land vs ocean thermal response
* **Land: zero heat capacity ⇒ instantaneous equilibrium.** `C_land = 0`, so (5.1) is an **algebraic equation for `T_s`**
  given `S_down, L_down, T_a, q_a`; substituting (5.2)-(5.4) into (5.1) gives a scalar monotone nonlinear equation in
  `T_s` (Newton/bisection). **Legitimate and standard — and it is exactly what Manabe (1969) himself did**: his eq. 16
  `S* + (DLR)* = sigma T*^4 + (c_p H)* + (L E)*` solves the land skin temperature diagnostically with `G = 0`.
* **Ocean: mixed layer of depth `h`, `C_O = rho_w c_w h`:** `C_O dT_s/dt = (1-alpha_s)S + L_down - sigma_SB T_s^4 - H
  - LE + Q_oc` **(5.6)**, relaxation timescale `tau = C_O/B`. `h=50` m ⇒ `C_O ~ 2.1e8` J K^-1 m^-2, `tau ~ 3` yr;
  `h=60` m ([Knietzsch et al. 2014](https://esd.copernicus.org/articles/5/481/2014/)) ⇒ `tau ~ 4` yr; `h = 68` m with
`dT_m/dt = Q/(C_o H)` and `tau ~ 4.3` yr, the standard slab-ocean configuration of
[Manabe & Stouffer 1980, JGR 85, 5529-5554](https://doi.org/10.1029/JC085iC10p05529);
  `C_O = 1e7` J K^-1 m^-2 — a **deliberately shallow** slab, ~2.4 m of water, as in
  [Frierson et al. 2006](https://doi.org/10.1175/JAS3753.1) — ⇒ `tau ~ 2` months. **Not steady-state solvable in
  general**: a first-order ODE, one time constant per grid cell.
* **[JUDGEMENT] Is a diagnostic steady `T_s` legitimate?** Yes, with three stated caveats: (i) it removes the
  **seasonal cycle** — a "steady monsoon" is a solstice-mean monsoon, which is exactly why the land–sea contrast is
  *largest* (land equilibrates instantly, the ocean lags); (ii) `T_s` then depends on the imported `A,B` (or
  gray-radiation constants) more strongly than on dynamics — calibrate against a target present-day `T_s` field;
  (iii) ocean heat transport is neglected except as prescribed.
* **Practical ocean closure.** (a) Prescribe SST (defensible — the ocean is the world's boundary condition), or
  (b) diagnose `T_s` from the steady mixed-layer budget with a prescribed `q`-flux divergence `Q_oc = -d(OHT)/dy`;
  a verified analytic family is `OHT = OHT_0 sin(phi) cos^{2N}(phi)` (Rose & Ferreira 2013; peak at `phi~27` deg for
  `N=2`), as used in [Knietzsch et al. 2014, ESD, Eq. 1](https://esd.copernicus.org/articles/5/481/2014/).
  Option (b) makes the land–sea contrast genuinely emergent.

## 6. Precedent models (prescribes / solves / cost / steady?)
| Model | Prescribes | Solves | Cost | Steady? |
|---|---|---|---|---|
| [Held & Suarez 1994, BAMS 75, 1825-1830](https://doi.org/10.1175/1520-0477(1994)075<1825:APFTIO>2.0.CO;2) | `T_eq = max{200, [315 - 60 sin^2 phi - 10 log(p/p0) cos^2 phi](p/p0)^kappa}`; `k_T = k_a + (k_s-k_a) max[0,(sigma-sigma_b)/(1-sigma_b)] cos^4 phi`; `k_v = k_f max[0,(sigma-sigma_b)/(1-sigma_b)]`; `sigma_b=0.7`, `k_a=1/40`, `k_s=1/4`, `k_f=1` day^-1 | dry primitive equations, prognostic | benchmark-cheap | **(b)** |
| [Frierson, Held & Zurita-Gotor 2006](https://doi.org/10.1175/JAS3753.1) (+[Part II](https://doi.org/10.1175/JAS3913.1); convection schemes are in [Frierson 2007](https://doi.org/10.1175/JAS3935.1), *not* Part II) | gray LW `tau_0(phi)=tau_e+(tau_p-tau_e)sin^2 phi`, `tau=tau_0[f_l (p/p_s)+(1-f_l)(p/p_s)^4]`, `tau_e=6`, `tau_p=1.5`, `f_l=0.1`; `R_S=R_S0[1+Delta_s P_2(sin phi)]`, `R_S0=938.4` W m^-2, `Delta_s=1.4`; 2-stream `dU/dtau=U-B`, `dD/dtau=B-D`, `B=sigma_SB T^4`, `U(0)=sigma_SB T_s^4`, `D(0)=0`; slab `C_O=1e7`; MOS drag `z_0=3.21e-5` m | PE + prognostic `q,T,T_s` | T42/T85/T170, 25 sigma levels, 1080 d/run | **(b)** |
| [Zebiak & Cane 1987, MWR 115, 2262-2278](https://doi.org/10.1175/1520-0493(1987)115<2262:AMENO>2.0.CO;2) | SST->heating; ocean params | atmosphere **diagnosed**: linear steady shallow water re-solved per ocean step | ~5.6x2 deg, ~1 model yr/min (1980s HW) | **(c) mixed** |
| [Neelin & Zeng 2000, JAS 57, 1741-1766](https://doi.org/10.1175/1520-0469(2000)057<1741:AQETCM>2.0.CO;2) QTCM1 (+[Zeng et al. 2000](https://doi.org/10.1175/1520-0469(2000)057<1767:AQETCM>2.0.CO;2); open doc [Lin 2009, GMD 2, 1-11](https://doi.org/10.5194/gmd-2-1-2009)) | **truncated Galerkin vertical structure** (1 baroclinic + barotropic mode) from convective quasi-equilibrium | prognostic `T,q,T_s`; simple land soil moisture; no topography | Arakawa C-grid 5.625x3.75 deg; "fraction of a full GCM" | **(a) steady-solvable** — NZ2000 §7d(1) verbatim: "The steady solution to these equations is well posed and can be a reasonable approximation to the full solution in the Tropics under some circumstances... We have tested steady solvers for versions of the model, using multigrid methods, and obtained reasonable solutions for idealized cases." |
| [PlaSim / PUMA 2005](https://doi.org/10.1127/0941-2948/2005/0043) ([desert world](https://doi.org/10.1127/0941-2948/2005/0044), [PUMA](https://doi.org/10.1127/0941-2948/2005/0074)) | PUMA: Held-Suarez-style relaxation. PlaSim: full moist physics | spectral PE, prognostic | T21-T85, 5-10 levels; PUMA ~2 min/sim-yr, PlaSim ~10 min/sim-yr | **(b)** |
| [Linear Baroclinic Model, Watanabe & Kimoto 2000, QJRMS 126, 3343-3369](https://doi.org/10.1002/qj.49712657017) | basic state; diabatic + transient-eddy forcing; Rayleigh/Newtonian damping (T42L20: 0.5 day lowest 4 + topmost, 30 day between) | linear PE, **exactly by matrix inversion**; zonal-mean basic state ⇒ operator **block-diagonal by zonal wavenumber** (T21L20 = 21 blocks of 2562^2) | seconds-minutes per solve | **(a) exactly steady** |
| Moist EBMs (Flannery 1984; [Rose & Ferreira 2013](https://doi.org/10.1175/JCLI-D-11-00547.1); Siler, Roe & Armour 2018) | OLR parameterisation, insolation, diffusivity `D`, `q`-flux | diagnostic diffusion of MSE / `T`+latent energy | 1-D latitude, seconds | **(a) steady, elliptic** |
| [Rodwell & Hoskins 1996](https://doi.org/10.1002/qj.49712253408)/[2001](https://doi.org/10.1175/1520-0442(2001)014<3192:SAASM>2.0.CO;2) | elliptical monsoon heating at 25N/90E/400 hPa max 5 K/day; mountains; drag 1/4-5 d over land; Newtonian 5-25 d | nonlinear **time-dependent** hydrostatic PE | T31, 15 sigma levels; quasi-steady at days 15-16 | **(b)** |
| [GFDL stationary wave model, Held, Ting & Wang 2002, J. Climate 15, 2125-2144](https://doi.org/10.1175/1520-0442(2002)015<2125:NWSWTA>2.0.CO;2) | prescribed zonal-mean basic flow; orography; diabatic heating; transient vorticity and heat flux convergences; zonal mean relaxed back with a **3-day** timescale | **primitive equations** (NOT QG): perturbation vorticity, divergence, temperature, log(p_s); semi-implicit, dt = 30 min | R30 rhomboidal, 14 sigma levels | ~20 days to quasi-steady | **(b) time stepping** — "the stationary wave solution in this model is obtained by integrating the model to a quasi-steady state". Damping: Rayleigh 0.3/0.5/1/8 day at the lowest four sigma levels and 25 day elsewhere; Newtonian 15 day everywhere; biharmonic 1e17 m^4 s^-1 |
| [Huang & Gambo 1983, JMSJ 61, 495-509](https://www.jstage.jst.go.jp/article/jmsj1965/61/4/61_4_495/_pdf) | July mean zonal wind, static stability, Ekman friction F = 4e-6 s^-1, Newtonian cooling, thermal diffusivity; topography; summer heat sources | **steady, linear, quasi-geostrophic** vorticity + thermodynamic equations in (lambda, phi, p), 34 levels to ~92 km | spherical, k=1-3 | direct steady solve | **(a) steady** — the closest published precedent |
| [Gill 1980](https://doi.org/10.1002/qj.49710644905) | `Q`; Rayleigh = Newtonian `eps` (BOTH the same timescale, per Shamir et al. 2023 JFM 964 A32 citing Gill eqs. 2.6-2.8); `c = 70` m/s, `eps^-1 ~ 1.5` day | linear steady shallow water, closed form | analytic | **(a) steady, analytic** |
| QUADM / LDMZ | **NOT FOUND** as authoritative model definitions. "LDMZ" is almost certainly **LMDZ** (LMD Zoom GCM, IPSL), a full prognostic GCM. Do not cite QUADM without a source. | | | |

**Two warnings.** (i) **HS94 is a *relaxation-forcing template only*** — `T_eq,k_T,k_v` are latitude-only, with no
topography, no moisture and no surface fluxes; Frierson's course notes: "No surface fluxes, **no possibility of
land–sea contrast**". It therefore **cannot** produce a monsoon or a desert. (Correct the folklore: there is **no
"60-day drag"** in HS94 — the only 60 is `(Delta T)_y = 60` K — and **no verifiable "Held & Suarez 1983"**; that year
belongs to Arakawa & Suarez 1983, MWR 111, 34-45, and to Held 1983, the stationary-wave book chapter.)
(ii) **A steady solve of a marginally unstable operator is an artifact.** From the LBM author's own note: "the AIM
(and probably matrix inversion as well) does not allow such a propagation, so that the response may only amplify at
particular area ... resulting in an **artificially steady response**". Ensure enough damping — same failure mode as
RH2001's critical line (§2.3).
(iii) **Do not conflate the two "stationary wave models".** The GFDL SWM (Held, Ting & Wang 2002) is a **primitive-equation**
model whose steady answer comes from ~20 days of **time integration**; the LBM (Watanabe & Kimoto 2000) is the one you
solve **exactly by matrix inversion**. The QG beta-plane form (2.4)-(2.7) is the textbook idealisation underlying
Chen (2001) and Huang & Gambo (1983), not what the GFDL SWM integrates.

## 7. What stays PRESCRIBED, and what cannot be solved diagnostically
**Always prescribed in every approach above (nothing is fully closed):** the basic state `u(z)`/`U(y,z)`, `theta(y,z)`
the linear operators are built from; the damping rates `alpha`, `lambda` and their vertical profiles; `A` and `B` in
the linear OLR (or the gray-radiation constants); `C_H`, `C_E`, `z_0`, `Ri_c`; the RH profile (or the gross moist stability `M_1`); `W_fc` and `beta(W)`; the ocean `q`-flux / OHT or the slab depth `h`; the land/ocean mask and topography.
**Cannot be solved diagnostically without time stepping:**
1. **The mean meridional circulation via Kuo–Eliassen as a closure** — it is a balance condition on the evolving
   flow, not a steady-state equation ([Held & Zurita-Gotor 2025](https://doi.org/10.1175/JAS-D-24-0246.1)).
2. **Soil-moisture memory** — `dW/dt` in the bucket (§4.2). The steady limit is solvable by active-set iteration; the
   memory and the associated desert hysteresis are not.
3. **Ocean mixed-layer temperature** with realistic `h` (50-70 m, `tau` ~ 3-4 yr). Shrinking the slab to
   `C_O ~ 1e7` J K^-1 m^-2 (`tau` ~ 2 months) is the standard trick that makes it *effectively* diagnostic.
4. **Eddy heat/momentum fluxes** and any transient-driven precipitation: absent on a coarse grid; parameterise or drop.
5. **Anything through a critical line** — a steady linear stationary-wave model linearised about a zonal-mean flow
   that changes sign in the domain is singular there (RH2001's own warning, §2.3).
**Solvable in one shot:** the linear steady stationary-wave/shallow-water problem with land–sea heating (§2), the
diagnostic moisture budget (§3), the steady-limit bucket (§4.2), the land surface energy balance (§5.1), the GFDL-style
steady linear QG model of Huang & Gambo (1983)/Chen (2001), and the LBM by exact matrix inversion. Those, iterated to a
fixed point, are a closed diagnostic world model.
### 7.1 Concrete recommended steady closure set (every piece traceable)
1. **Energy transport / `T`:** moist energy balance in the Siler, Roe & Armour (2018) form — diffuse `h = c_p T + L_v q`
   with a fixed RH (their standard choice is RH = 80%), which makes `h` a single-valued function of `T` and removes any
   prognostic moisture equation; extend from `x = sin(phi)` to `(x, lambda)`. **The 2-D extension is a proposal, not a
   literature result — flag it.**
2. **Humidity:** fixed RH (above), or `RH(p) = RH_s (p/p_s - 0.02)/0.98` if height-resolved humidity is needed
   ([Manabe & Wetherald 1967](https://doi.org/10.1175/1520-0469(1967)024<0241:TEOTAW>2.0.CO;2)).
3. **Convection (if used):** Betts–Miller in the `dS/dt = 0` limit — purely diagnostic, tau-free, with
   `(P_B, P_M, P_T) = (-25, -40, -20)` mb corresponding to RH ~ (90, 70, 50)%.
4. **Precipitation closure:** `P - E = Q_div / GMS` with the gross moist stability `M_1 = M_S1 - M_q1` from
   [Neelin & Held 1987](https://doi.org/10.1175/1520-0493(1987)115<0003:MTCBOT>2.0.CO;2) — steady by construction.
5. **Damping defaults to copy:** HS94's `sigma_b = 0.7`, `k_a = 1/40`, `k_s = 1/4`, `k_f = 1` day^-1, and the LBM's
   per-level drag profile (0.5 day at the lowest four and topmost levels, 30 day in between) for anything taller.
   **You must be able to demonstrate strict stability of the linear operator** (see warning (ii) above).
6. **Boundary conditions:** vanishing meridional flux at the poles and `dT/dphi = 0` at `phi = +-90`; periodic in longitude.
7. **Cheap validation oracle:** PUMA (dry, Held–Suarez-type forcing, T21) runs ~1 simulated year in ~2 minutes on one
   3 GHz core; PlaSim (moist, T21L5-T42L10) ~10 min/sim-year. Use one to validate the steady solutions and to scan
   land–sea heating configurations.
8. **Reference surface energy numbers** to calibrate against ([Trenberth, Fasullo & Kiehl 2009](https://doi.org/10.1175/2008BAMS2634.1),
   verified from the PDF): OLR 238.5, ASR 341.3, net TOA +0.9; surface LW 396 up / 333 down, LH 80, SH 17 W m^-2.

## 8. Verification status — what is safe to cite and what is not
**Verified against the primary full text** (PDF read, not metadata): Manabe 1969 eqs. 16, 18-21 (bucket + diagnostic skin
temperature); Budyko 1969 OLR coefficients and their cloudiness dependence; Trenberth et al. 2009 energy budget numbers;
Frierson et al. 2006 gray radiation, two-stream, drag laws and `C_O = 1e7`; Manabe & Stouffer 1980 slab `H = 68` m;
Priestley-Taylor `alpha = 1.26` and its aridity drift; Penman 1948 eq. 16; Held & Zurita-Gotor 2025 eqs. 5, 7, 11;
Montgomery & Persing 2021 eqs. 3-11; Knietzsch et al. 2014 eqs. 1-2; Hoskins & Karoly 1981 eqs. (3.1)/(3.2b) via Czaja's
derivation; Gill 1980 eqs. (2.6)-(2.8) via Shamir et al. 2023; Huang & Gambo 1983 abstract and parameters; Chen 2001
abstract and steady thermodynamic balance; Rodwell & Hoskins 2001 abstract and critical-line warning.
**Metadata-only (citation correct, equations NOT read):** Sellers 1969, North 1975, North et al. 1981, Graves et al. 1993 —
their `A`/`B` values are secondary; Zebiak & Cane 1987 grid and cost; Ting & Held 1990 model details; Flannery 1984;
Emanuel 1991 (Crossref gives 2313-2329, the author's own running heads give 2313-2335).
**Explicitly UNVERIFIED — do not quote as fact:** the verbatim algebra of Huang & Gambo eqs. (1)-(2), Chen 2001 eq. (2.1),
RH1996/RH2001, Held & Ting 1990, and the LBM primitive equations (all publisher images or closed access); bulk transfer
coefficients `C_H`/`C_E` (only the Frierson et al. drag-law form is verified); Budyko shape parameters `n`/`w`; the
FAO-56 `900` constant and UNEP aridity thresholds; any 2-D moist EBM; the "LAM intercomparison" (no evidence it exists);
**QUADM (no authoritative definition found anywhere — do not cite)**. §2.2's heating term `(f0/N^2) dB'/dz` is derived
in-report from first principles and flagged as a derivation, not a quotation.
