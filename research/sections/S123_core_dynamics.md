# Steady-state (diagnostic) atmospheric circulation model — equations, numerics, boundary conditions

Scope: one-shot solve per world seed on a coarse 2-D lon x lat grid (periodic in lon) that must produce,
**emergently**, (a) a summer monsoon over a large heated continent next to a warm ocean and (b) a dry
subtropical desert over a continent next to a cooler sea. Everything below is traceable to a reference;
where sources disagree or a choice is a modelling judgement it is flagged **[JUDGEMENT]**.
Citations are markdown links (DOI or open full text).

Conventions: `phi` latitude, `p` pressure, `a`/`r` Earth radius, `g` gravity, `f = 2 Omega sin(phi)`,
`beta = df/dy`, `[X]` zonal mean, `X*` deviation from zonal mean, `theta` potential temperature,
`T` temperature, `q` specific humidity, `omega = Dp/Dt`, `psi` streamfunction.

---

## 0. Executive summary (read this first)

| # | Equation set | Steady-state solvable in one shot? | Why |
|---|---|---|---|
| 1 | Kuo–Eliassen for psi(phi,p) | **NO, not as a closure** | It is a *balance* condition on the evolving flow, not a steady-state equation |
| 2 | Linear steady stationary-wave / shallow-water (Gill) with land-sea heating | **YES** | Genuinely elliptic/steady once you prescribe the basic state, damping and heating |
| 3 | Vertically integrated moisture budget P-E = -div(qV) | **YES if E is closed diagnostically**; NO if E depends on soil moisture memory | Needs a diagnostic E (see 4/5) |
| 4 | Manabe bucket soil moisture | **NO in general** (prognostic); YES in a steady limit with an active-set/inequality solve | dW/dt=0 + runoff threshold = complementarity problem |
| 5 | Surface energy balance | **YES for land** (zero heat capacity -> instantaneous equilibrium); **NO for ocean mixed layer** unless you prescribe the equilibrium | Ocean slab has a relaxation timescale |
| 6 | Precedent models | mixed | see section 6 |

**Central design recommendation.** Close the model with (2)+(3)+(4-steady)+(5-land) as the *solver*, and use (1)
only as a **post-hoc diagnostic** on the converged state. Section 1 explains why (1) must not be the closure.

---

## 1. Mean meridional circulation: the Kuo–Eliassen equation

### 1.1 Streamfunction definition (mass streamfunction, pressure coordinates)

    psi(phi,p) = (2 pi a cos(phi) / g) * Integral_0^p [v] dp'                       (KE-1)

Inverted:

    [v]   = ( g / (2 pi a cos(phi)) ) * dpsi/dp                                    (KE-2)
    [omega] = -( g / (2 pi a^2 cos(phi)) ) * dpsi/dphi                             (KE-3)

(KE-2)-(KE-3) satisfy the zonal-mean mass continuity
`(1/(a cos phi)) d([v] cos phi)/dphi + d[omega]/dp = 0` identically.
Verified as written in [Knietzsch, Lucarini & Lunkeit 2014, ESD (OA)](https://esd.copernicus.org/articles/5/481/2014/)
and [Held & Zurita-Gotor 2025, JAS](https://doi.org/10.1175/JAS-D-24-0246.1).
Careful: normalisations differ between papers (some use `2 pi r`, some absorb `a^2`); the pair (KE-2)/(KE-3)
must always be mutually consistent with (KE-1).

### 1.2 The full elliptic operator and every forcing term

Written exactly as in [Knietzsch et al. 2014, ESD Eq. 2](https://esd.copernicus.org/articles/5/481/2014/)
(they credit Peixoto & Oort 1992, chapter 14.5.5):

    f^2 g /(2 pi r cos phi) * d2psi/dp2
      - g /(2 pi r^3 rho [theta]) * d/dphi( (d[theta]/dp) * dpsi/dphi )

        =  1/(r rho [T]) * d/dphi( [Q]/c_p )                        <- DIABATIC HEATING
         - f * d[F]/dp                                              <- FRICTION / ZONAL DRAG
         - 1/(r^2 rho [theta]) * d/dphi( (1/cos phi) d([v* theta*] cos phi)/dphi )   <- EDDY HEAT FLUX
         + f /(r cos^2 phi) * d2( [u* v*] cos^2 phi )/(dp dphi)      <- EDDY MOMENTUM FLUX   (KE-4)

Symbols: `Q` diabatic heating rate; `c_p` specific heat; `F` tendency of the zonal wind due to friction and
sub-grid stresses; `u* v*` eddy momentum flux; `v* theta*` eddy heat flux; `rho` density; `[theta]`, `[T]`
zonal-mean potential temperature and temperature. The four right-hand terms are respectively diabatic heating,
friction, meridional eddy heat transport and eddy momentum transport.

**Equivalent QG form** (constant f and N, Boussinesq), verbatim from
[Held & Zurita-Gotor 2025, Eq. 5](https://doi.org/10.1175/JAS-D-24-0246.1):

    f^2 * d2psi/dz2 + N^2 * d2psi/dy2 = f * dG/dz + dH/dy ,   [v,w] = [ -dpsi/dz , dpsi/dy ]   (KE-5)

with `G` the zonal momentum forcing and `H` the buoyancy forcing. They call this "our simplified K-E
equation ... simply the standard QG omega-equation specialised to the axisymmetric component of the flow".
This is the cleanest form to implement as a diagnostic: a **two-term elliptic operator**, solved by one
Poisson-like inversion per forcing term (the operator is linear, so contributions superpose).

**General (non-QG, arbitrary Rossby number) form.** Per [Held & Zurita-Gotor 2025](https://doi.org/10.1175/JAS-D-24-0246.1),
"f^2 on the left hand side [is] replaced by f(f - d[u]/dy), a measure of inertial instability", with nonlinear
rather than linear balance; the full vortex form with spatially varying static and inertial stability is

    d/dr( A dpsi/dr + B dpsi/dz ) + d/dz( C dpsi/dz + B dpsi/dr ) = Q_forcing                 (KE-6a)
    u = -(1/(r rho)) dpsi/dz ,   w = 1/(r rho) dpsi/dr                                        (KE-6b)
    A = (g/theta) (1/(r rho)) (dtheta/dz) / N^2  (static stability)
    B ~ -(1/(r rho)) d(xi C)/dz                 (baroclinicity)
    C = ( xi(zeta+f) chi + C dchi/dr ) /(r rho) (inertial stability)
    Q_forcing = g d/dr(chi^2 u_dot) + d/dz(C chi^2 u_dot) + d/dz(xi chi V_dot)                (KE-6c)

verbatim from [Montgomery & Persing 2021, JAS section 4](https://doi.org/10.1175/JAS-D-20-0043.1)
(open copy: [NPS Calhoun](https://core.ac.uk/download/479441386.pdf)); same structure as
[Eliassen 1951](https://doi.org/10.1175/1520-0469(1951)008<0001:OTPOTF>2.0.CO;2) and
[Kuo 1956](https://doi.org/10.1175/1520-0469(1956)013<0561:FAFMCI>2.0.CO;2).
For a *planetary* model you want (KE-4)/(KE-5), not (KE-6) — (KE-6) is the tropical-cyclone form.

### 1.3 Ellipticity condition

The discriminant of (KE-6a), verbatim from [Montgomery & Persing 2021, Eq. 11](https://core.ac.uk/download/479441386.pdf):

    D = 4 gamma^2 * [ (2 gamma / chi) * (dchi/dz) * (xi zeta_a + (C/chi) dchi/dr)
                      - (1/chi) d(C chi)/dz ] ,   gamma = chi/(r rho),  zeta_a = zeta + f   (KE-7)

"The balance equation is elliptic when D is everywhere positive ... If D < 0 at isolated points or extended
regions ... the balance equation is locally hyperbolic and the flow there satisfies the conditions for
symmetric instability. Technically speaking, the balance equation loses solvability as an elliptic problem."

For the zonal-mean form (KE-4) the condition is simpler and should be stated operationally: the operator is
elliptic iff

    f^2 > 0  (f != 0, i.e. the grid must not include the exact equator with f = 0)                     (KE-8a)
    d[theta]/dp < 0  everywhere  (static stability positive; [theta] increasing with height)          (KE-8b)

(KE-8a) is a **real practical problem for a coarse lon-lat grid**: `f -> 0` at the equator makes the operator
degenerate. Standard workarounds: use the equatorial beta-plane with `f = beta y` and the *equatorial*
balance (Gill 1980, section 2), or floor `f^2` at some `f_min`, or use the non-QG form with
`f(f - d[u]/dy)`.

### 1.4 Typical boundary conditions (well-posedness)

* `psi = 0` on **all** boundaries of the (phi, p) domain: `p = p_s` (surface), `p = 0` (top), and `phi = +-90 deg`.
  Rationale: `omega = 0` at p = 0 and p = p_s (mass conservation / rigid lid), and `[v] = 0` at the poles.
  Stated explicitly in [Grotjahn, ATM 240 problem set 8](http://grotjahn.ucdavis.edu/course/atm240/2019/hwk2019-8_v2.pdf)
  ("The stream function, psi is zero along the boundaries of the domain") and used in
  [GRL 2025 supplementary](https://agupubs.onlinelibrary.wiley.com/action/downloadSupplement?doi=10.1029%2F2025GL117726&file=2025GL117726-sup-0001-Supporting+Information+SI-S01.pdf)
  ("At surface (p = 1000 hPa), omega is assumed to be zero").
* If you solve only a channel, use `psi = 0` at the channel walls instead; the pole boundary condition is
  then the wall condition. **[JUDGEMENT]** for a coarse world grid: the pole condition is cleaner.
* If the problem is locally non-elliptic, you need **regularization**: the scheme of
  [Möller & Shapiro 2002](https://doi.org/10.1175/1520-0469(2002)059<1281:BIOTBO>2.0.CO;2) as used by
  Bui et al. (2009), Abarca & Montgomery (2014, 2015) and
  [Montgomery & Persing 2021](https://core.ac.uk/download/479441386.pdf).

### 1.5 Numerical method (recommended)

* Discretize (KE-4) or (KE-5) with 2nd-order central differences on the **staggered or unstaggered** (phi,p)
  grid; the equation is a standard 2-D variable-coefficient Poisson problem.
* **Gauss–Seidel / SOR** iteration on the finite-difference form is what the literature uses:
  [Knietzsch et al. 2014](https://esd.copernicus.org/articles/5/481/2014/) "solve the Kuo-Eliassen equation
  for psi by applying an iterative method (Gauss-Seidel method) to its finite difference approximation";
  [Nature Comms 2026](https://doi.org/10.1038/s41467-026-69990-0) "solved numerically for each ensemble member
  using a Jacobi iteration method". Both are trivial at coarse resolution (e.g. 64x32 -> microseconds).
* For a coarse world grid, use a **double Fourier sine series** in (y,p) when the coefficients are separable
  — this is the analytic solution route of
  [Grotjahn's problem set](http://grotjahn.ucdavis.edu/course/atm240/2019/hwk2019-8_v2.pdf):
  `A d2psi/dy2 + C d2psi/dp2 = -dH/dy`, with `psi = sum F_nm sin(n pi (y+2)/4) sin(m pi (P-0.1)/0.9)`,
  and it automatically satisfies `psi = 0` on all four boundaries. **This is the cheapest correct solver for
  a coarse grid.** Recommended.
* Alternatively a sparse direct solve (the operator is tridiagonal-block) or multigrid.

### 1.6 What must be KNOWN before the Kuo–Eliassen equation can be solved

This is the crux, and it is why (1) cannot close a steady-state model:

1. `[theta](phi,p)` or `[T](phi,p)` — needed for static stability in the operator (KE-4)/(KE-5).
2. `rho(phi,p)`, `f(phi)`.
3. `[Q](phi,p)` — the diabatic heating. In a moist model this includes latent heating from precipitation,
   so it depends on the moisture budget (section 3), which depends on the circulation. **Circular.**
4. `[F](phi,p)` — friction / sub-grid drag.
5. `[v* theta*]`, `[u* v*]` — the eddy heat and momentum fluxes. These are produced by baroclinic eddies,
   which respond to the circulation. **Circular.**

### 1.7 **FLAG: the Kuo–Eliassen equation is NOT a steady-state equation**

This is the single most important caveat and it is stated verbatim in
[Held & Zurita-Gotor 2025, JAS 82, 1763-1766](https://doi.org/10.1175/JAS-D-24-0246.1):

> "The Kuo-Eliassen equation provides the mean meridional circulation that must be present for the
> axisymmetric component of a flow forced by heat and momentum sources to remain **balanced as it evolves**.
> It does **not** tell us whether or not the flow is steady."

Their argument, in their own equations:
* Combining the z-derivative of the zonal momentum equation and the y-derivative of the buoyancy equation with
  thermal wind gives (KE-5). Eliminating the ageostrophic flow instead gives the PV equation
  `dq/dt = -v' dQ/dy + (f/N^2) dH/dz`; **the steady-state condition is**

      dG/dy = (f/N^2) * dH/dz                                                              (KE-9)

  i.e. a PV-tendency condition, **not** the K-E operator.
* Because K-E is linear you can always write `psi(G,H) = psi(0,H) + psi(G,0)`, but if `[G,H]` satisfy (KE-9)
  then `[G,0]` and `[0,H]` **do not** — so this is *not* a decomposition of the steady solution into
  heating-forced and momentum-forced steady solutions. Splitting forcing into *prescribed* + *reactive* parts
  only reproduces the K-E operator when the reactive damping is **spatially uniform and identical in the heat
  and momentum equations** (their Eq. 11); with `lambda_M != lambda_T` the operator itself changes:
  `f^2 (lambda_T/lambda_M) d2psi/dz2 + N^2 d2psi/dy2 = f (lambda_T/lambda_M) dG/dz + dH/dy`. They call the
  uniform-damping assumption "very suspect, especially for the momentum damping".
* Prior critiques: [Chang 1996, JAS 53, 113-125](https://doi.org/10.1175/1520-0469(1996)053<0113:MMCDBE>2.0.CO;2)
  and [Kim & Lee 2001, JAS 58, 2845-2858](https://doi.org/10.1175/1520-0469(2001)058<2845:HCDIAP>2.0.CO;2).
* Pedagogical derivation and mathematical remarks on the operator:
  [Yano 2011, JAMES 3, M03001](https://doi.org/10.1029/2011MS000058) (gold OA).

**Consequence for the model design.** Do not invert the K-E operator to obtain the Hadley/Ferrel cells.
Choose an explicitly steady, closed axisymmetric closure instead — e.g. angular-momentum-conserving Hadley
cell with Held–Hou/Lindzen–Hou scaling
([Held & Hou 1980](https://doi.org/10.1175/1520-0469(1980)037<0515:NASIA>2.0.CO;2),
[Lindzen & Hou 1988](https://doi.org/10.1175/1520-0469(1988)045<2416:HCFZAH>2.0.CO;2)), or a
moist-static-energy / WTG closure (section 3), or explicitly prescribe a relaxation. Then, if you want the
KE decomposition for flavour, compute it **once, after convergence**, as a diagnostic (section 1.5).

---

## 2. Stationary waves forced by land-sea diabatic heating

### 2.1 Hoskins & Karoly (1981): the linear steady primitive-equation form

[Hoskins & Karoly 1981, JAS 38, 1179-1196](https://doi.org/10.1175/1520-0469(1981)038<1179:TSLROA>2.0.CO;2)
derive the steady (d/dt = 0), linear, 3-D perturbations `(u',v',w')` on a zonal background `u(z)`, `theta(y,z)`
with geostrophic + hydrostatic + Boussinesq balance. Their two governing equations, exactly as re-derived in
[Czaja's lecture notes](http://www.sp.ph.ic.ac.uk/~aczaja/PG2013/Notes_HK81.pdf):

    u * d(zeta')/dx + beta * v' = f * d(w')/dz                                              (HK-1)  [their (3.1)]
    f u * d(v')/dz - f * (du/dz) * v' + N^2 * w' = Q                                        (HK-2)  [their (3.2b)]

with

    zeta' = dv'/dx - du'/dy ,   beta = df/dy = 2 Omega cos(phi)/R                            (HK-3)
    N^2 = g alpha theta_z  (buoyancy frequency),   Q = (g alpha theta/(c_p T)) * Qdot'        (HK-4)

where `Qdot'` is the prescribed diabatic heating rate and `alpha` comes from the linear equation of state
`rho = rho_0 (1 - alpha theta)`. **The heating enters only through `Q` on the RHS of the thermodynamic
equation (HK-2)** — exactly the structure you want for land-sea contrast.

Their scaling analysis (same notes) is the key to **monsoon vs desert**. Define `gamma ~ (f^2 u)/(beta N^2 H_Q H_u)`:

* `gamma << 1` (deep tropics): vertical advection dominates; heating is balanced by adiabatic cooling,
  `w' N^2 ~ Q`. With `w' = 0` at the surface this implies **poleward low-level flow and a low to the WEST
  of the heating** — this is the monsoon regime. (HK81 Fig. 2a.)
* `gamma >> 1` (midlatitudes): meridional advection dominates; heating is balanced by cold advection,
  `v' theta_y ~ Q`, giving **equatorward flow and descent to the west, low to the EAST of the heating**.
  (HK81 Fig. 2b.)

So the *same* heating produces a monsoon circulation in the tropics and a **descent/desert response**
in the subtropics. **This is the minimal mechanism that separates a strong monsoon from a weak desert.**

### 2.2 The beta-plane channel version (what to actually code)

QG potential vorticity on a beta-plane, as in [Held's GFDL lecture 3](https://www.gfdl.noaa.gov/wp-content/uploads/files/user_files/io/lect_3_572.pdf):

    q = lap(psi) + f + (f_0^2/rho_0) * d/dz( (rho_0/N^2) * dpsi/dz )                          (SW-1)
    dq/dt + U dq/dx + v dQ/dy = 0 ,   v = dpsi/dx                                            (SW-2)

Linearised about a zonally symmetric basic state `U(y,z)`, `Q(y,z)`, with **Rayleigh friction** (rate `alpha`)
and **Newtonian cooling** (rate `lambda`), and a diabatic buoyancy source `B'`:

    U * d/dx [ lap(psi') + (f_0^2/rho_0) d/dz( (rho_0/N^2) dpsi'/dz ) ] + (dQ/dy) * dpsi'/dx
        = -alpha * lap(psi') - (f_0^2/rho_0) d/dz( (rho_0/N^2) * lambda * dpsi'/dz )
          + (f_0/N^2) * dB'/dz                                                              (SW-3)

Derivation of the last term: for Boussinesq QG with `b = f_0 dpsi/dz` and `D_g b/Dt + N^2 w = B`,
eliminating `w` between the buoyancy and vorticity equations gives `D_g q/Dt = (f_0/N^2) dB/dz`
(dimensionally consistent: `[f_0/N^2] = s`, `[dB/dz] = s^-3`, product `s^-2` = `[Dq/Dt]`).
For pressure coordinates replace `z -> -H ln(p/p_s)` and use the standard `rho_0(z)` from (SW-1).

**[JUDGEMENT] The damping operator is the main fork in the road.** Uniform, equal `alpha = lambda` makes
(SW-3) collapse to `(U d/dx + alpha) q' + (dQ/dy) psi'_x = forcing`, which is clean and invertible. But
[Held & Zurita-Gotor 2025](https://doi.org/10.1175/JAS-D-24-0246.1) show that unequal heat/momentum damping
rescales `f_0^2` relative to `N^2` (their Eq. 11), and that treating free-tropospheric eddy momentum flux
convergence as local damping cannot reproduce the non-local annular-mode-like response seen in
Chen & Zurita-Gotor (2008). **[JUDGEMENT]** For a *steady diagnostic world-builder*, uniform equal damping is
the pragmatic and defensible choice, and it must be stated as an assumption.

Numerics: for (SW-1)-(SW-3) on a periodic-in-x channel, expand in zonal wavenumber `k` (FFT in x) and solve
the resulting 2-D (y,z) boundary-value problem per wavenumber by relaxation or a direct banded solve. For the
**steady** problem with `U > 0` there is no time stepping if you use a relaxation/iterative solve of the
steady equation, but note `U d/dx` makes the operator **non-elliptic (hyperbolic in x)** — a stationary
Rossby wave radiates energy downstream, so the numerical problem needs care:
[Held's notes](https://www.gfdl.noaa.gov/wp-content/uploads/files/user_files/io/lect_3_572.pdf) give the
stationary wavenumber condition `U = beta/(k^2 + l^2)` and the group velocity `C_gx = 2 U k^2/(k^2+l^2)`,
and "energy only flows downstream, always positive in the zonal direction" for `U > 0`.
Practical consequence: **damping is what makes the steady problem well posed** (it converts the resonance at
`U = beta/K^2` into a finite-amplitude response). Without damping the linear steady problem is singular at
resonance. **[JUDGEMENT]** set the damping rate so that the response is finite but the wave still propagates:
typical GCM linear-stationary-wave models use Rayleigh friction `~1/(0.5-30) day^-1` and Newtonian cooling
`~1/(10-30) day^-1`; the LBM configuration is Rayleigh/Newtonian `0.5 day^-1` at the lowest 4 and topmost
levels and `1/30 day^-1` in between (Watanabe & Kimoto 2000; see section 6).

### 2.3 Minimal version that separates a strong monsoon from a weak desert: Gill (1980)

[Gill 1980, QJRMS 106, 447-462](https://doi.org/10.1002/qj.49710644905) is the smallest model that already
contains the monsoon/desert asymmetry. Linear steady shallow water on the equatorial beta-plane with
Rayleigh friction `epsilon` and Newtonian cooling `epsilon`:

    epsilon*u - beta*y*v = -dp/dx
    epsilon*v + beta*y*u = -dp/dy
    epsilon*p + c^2 (du/dx + dv/dy) = -Q                                                  (GILL-1)

with `Q` the mass source (deep convective heating projected on the first baroclinic mode), `c` the gravity
wave speed (`c = NH/(pi)` for the first baroclinic mode; `L_R = c/beta` the equatorial Rossby radius).
Their solution, verbatim from the [UH OCN666 notes](https://rcfftp.soest.hawaii.edu/niklas/OCN666/ForcedEquatorialMotion.pdf):

    q = p + u ,  r = p - u ;  expand in parabolic cylinder functions D_n(y)
    dq_0/dx + q_0 = -Q_0                      (forced KELVIN wave)
    3 dq_2/dx - q_2 = -Q_2                    (forced ROSSBY wave)                     (GILL-2)
    "Kelvin wave: no response west of the forcing region, response decays with scale
     epsilon^-1 to the east, meridional structure is Gaussian. Rossby wave: no response east of the forcing
     region, response decays with (3 epsilon)^-1 to the west, meridional structure is D_1. In response to
     heating there is poleward flow in the boundary layer due to vortex stretching."

**Why this is the minimal monsoon/desert model.** `Q > 0` over the heated continent gives (i) a Kelvin
response *east* of the heating (equatorward-flowing, low-level convergence to the east — the "monsoon +
anticyclone to the east" picture) and (ii) a Rossby response *west* of the heating with **descent to the
west** — the desert. Note the crucial asymmetry: **the desert is not local to the dry continent; it is the
remote Rossby-wave response to the monsoon heating to its east.**

This is exactly the mechanism of [Rodwell & Hoskins 1996, QJRMS 122, 1385-1404](https://doi.org/10.1002/qj.49712253408)
and [Rodwell & Hoskins 2001, J. Climate 14, 3192-3211](https://doi.org/10.1175/1520-0442(2001)014<3192:SAASM>2.0.CO;2).
Verbatim from the RH2001 abstract:

> "By prescribing in the model the heatings associated with several of the world's monsoons, it is confirmed
> that the equatorward portion of each subtropical anticyclone may be viewed as the **Kelvin wave response to
> the monsoon heating over the continent to the west**. A poleward-flowing low-level jet into a monsoon ... is
> required for **Sverdrup vorticity balance**. ... The **Rossby wave response to the west of subtropical monsoon
> heating, interacting with the midlatitude westerlies, produces a region of adiabatic descent**. It is
> demonstrated here that a local 'diabatic enhancement' can lead to a strengthening of the descent. ...
> The conclusion is that the Mediterranean-type climates of regions such as California and Chile may be
> induced remotely by the monsoon to the east."

They also state: "In agreement with Sverdrup vorticity balance of the steady flow, ...". The Sverdrup balance
`beta v = f d(w)/dz` (the low-latitude limit of HK-1 with the vorticity-advection term dropped) is the
steady-state ingredient that closes the low-level flow, and it is **diagnostic** — no time stepping.

**Recommended minimal model for the "monsoon + desert" requirement**

1. Equatorial beta-plane / beta-channel shallow water (GILL-1) for the *low-level* flow, with `Q` from the
   land-sea surface-flux contrast (section 5) rather than prescribed by hand. This gets the monsoon and the
   subtropical descent.
2. Add the mid-latitude westerlies via (SW-1)-(SW-3) so that the Rossby response to the west of the heating
   interacts with `U` and gives descent at the right latitude.
3. Add moisture (section 3) so that the descent suppresses `P` and the desert becomes *self-consistent*
   (rather than being a prescribed dry patch).

### 2.4 Classic references for the stationary-wave/heating problem

| Reference | What it contributes |
|---|---|
| [Smagorinsky 1953, QJRMS 79, 342-366](https://doi.org/10.1002/qj.49707934103) | first linear steady model of the response to large-scale heat sources/sinks; origin of "stationary waves forced by land-sea heating" |
| [Webster 1972, MWR 100, 518-541](https://doi.org/10.1175/1520-0493(1972)100<0518:ROTTAT>2.3.CO;2) | response of the tropical atmosphere to *local, steady* forcing — the first monsoon-as-heating-response model |
| [Gill 1980, QJRMS 106, 447-462](https://doi.org/10.1002/qj.49710644905) | the shallow-water heating solution (section 2.3) |
| [Hoskins & Karoly 1981, JAS 38, 1179-1196](https://doi.org/10.1175/1520-0469(1981)038<1179:TSLROA>2.0.CO;2) | steady linear 3-D primitive-equation response to thermal + orographic forcing; ray theory |
| Held (1983), "Stationary and quasi-stationary eddies in the extratropical troposphere: theory", in Hoskins & Pearce (eds), *Large-Scale Dynamical Processes in the Atmosphere*, Academic Press, 127-168 | the standard review of linear stationary-wave theory (book chapter; cite via the reference list of [Held & Suarez 1994](https://doi.org/10.1175/1520-0477(1994)075<1825:APFTIO>2.0.CO;2)) |
| [Ting & Held 1990, JAS 47, 2546-2566](https://doi.org/10.1175/1520-0469(1990)047<2546:TSWRTA>2.0.CO;2) | GCM stationary-wave response to a tropical SST anomaly — the linear-model validation target |
| [Held & Ting 1990, JAS 47, 495-500](https://doi.org/10.1175/1520-0469(1990)047<0495:OVTFOA>2.0.CO;2) | orographic vs thermal forcing of stationary waves; importance of the mean low-level wind |
| [Held, Ting & Wang 2002, J. Climate 15, 2125-2144](https://doi.org/10.1175/1520-0442(2002)015<2125:NWSWTA>2.0.CO;2) | review: "Northern Winter Stationary Waves: Theory and Modeling" |
| [Rodwell & Hoskins 1996](https://doi.org/10.1002/qj.49712253408) / [2001](https://doi.org/10.1175/1520-0442(2001)014<3192:SAASM>2.0.CO;2) | monsoon-desert mechanism: monsoon heating -> Rossby descent to the west -> desert |
| [Chou, Neelin & Su 2001, QJRMS 127, 1869-1891](https://doi.org/10.1002/qj.49712757602) | ocean-atmosphere-land feedbacks in an *idealized monsoon* — the intermediate-complexity precedent |
| [Bretherton & Sobel 2003, JAS 60, 451-460](https://doi.org/10.1175/1520-0469(2003)060<0451:TGMATW>2.0.CO;2) | Gill model vs the weak-temperature-gradient approximation — tells you when the Gill model is the right minimal model |

---

## 3. Moisture budget closure

### 3.1 The vertically integrated moisture budget (the one equation you cannot avoid)

In steady state (no tendency, no storage change):

    P - E = -(1/(rho_w g)) * div_2D( Integral_0^{p_s} q V dp )        [kg m^-2 s^-1]        (M-1)
    P - E = -(1/(rho_w g)) * [ d/dx(Int q u dp) + d/dy(Int q v dp) ]

More usually written in flux form with the divergent part split out:

    P - E = -(1/(rho_w g)) * div( Int q V dp )
          = -(1/(rho_w g)) * [ div( q_bar * V_bar ) + div( Int q' V' dp ) ]                  (M-2)

i.e. **mean** (thermodynamic + dynamic) plus **transient eddy** moisture flux convergence. This is the
standard budget of e.g. [Trenberth & Guillemot 1995](https://doi.org/10.1175/1520-0442(1995)008<2255:EAOTGG>2.0.CO;2)
and is the quantity that the diagnostic-precipitation literature builds on:
[O'Gorman & Schneider 2008, J. Climate 21, 3815-3832](https://doi.org/10.1175/2007JCLI2065.1) —
"In a steady state, in which changes in atmospheric moisture storage are negligibly small, the difference
between precipitation and evaporation ... is balanced by the divergence of the vertically integrated
atmospheric moisture flux". Title: "The Hydrological Cycle over a Wide Range of Climates Simulated with an
Idealized GCM".

**[JUDGEMENT] On a coarse 2-D world grid there are no resolved transients**, so (M-2) reduces to

    P - E = -(1/(rho_w g)) * div( q_bar V_bar )                                               (M-3)

and the second term of (M-2) is *not closed* — you must either drop it (and say so) or parameterise it as a
diffusion `-D q * lap(q)` (which is what "eddy moisture diffusion" means in EBMs; cite the moist-EBM
literature in section 6). Dropping it is the standard minimal choice; on a coarse grid it is defensible but
it removes the midlatitude storm-track precipitation maximum.

### 3.2 Two ways to get q

**(a) Advection–condensation (prognostic-ish, "q is what survives saturation").** Carry `q` with the flow and
condense the excess over saturation:

    Dq/Dt = E - C ,   C = (q - q_sat(T,p)) / tau_c   if q > q_sat , else 0                      (M-4)

with `q_sat(T,p) = eps e*(T)/p`, `e*(T) = e*_0 exp[ -(L_v/R_v)(1/T - 1/T_0) ]` (Clausius–Clapeyron with
fixed `L_v`), `eps = R_d/R_v`. Verified verbatim from
[Frierson, Held & Zurita-Gotor 2006, JAS 63, 2548-2566, Eqs. 21-22](https://doi.org/10.1175/JAS3753.1):

    delta_q = (q* - q) / ( 1 + (L_v/c_p) dq*/dT )                                              (M-5)
    e*(T) = e*_0 exp[ -(L_v/R_v)(1/T - 1/T_0) ] ,   q* = eps e*/p ,  eps = R_d/R_v             (M-6)

`q*_0 = 610.78 Pa` at `T_0 = 273.16 K`; the model is run at `L_v = 2.5e6 J/kg`, `R_v = 461.5 J/(kg K)`,
`c_p = 1004.64 J/(kg K)`, `R_d = 287.04 J/(kg K)`. **Note the important caveat from that paper: with
large-scale condensation only, "the column must be saturated all the way down for precipitation to reach the
ground"** (their re-evaporation assumption) — a poor assumption for a monsoon. Frierson (2007) adds Betts–Miller
style convection schemes for exactly this reason
([Frierson 2007, JAS 64, 1959-1976](https://doi.org/10.1175/JAS3935.1)).

**(b) Diagnostic RH / "beta_q" closure (the one to use for a steady-state model).** Prescribe a vertical
profile of relative humidity (or of `q` relative to a reference profile) so that `q` becomes a *diagnostic*
function of `T` and `p`, and `P` is diagnosed from (M-3). Two standard variants:

* **Fixed-RH closure**: `RH(p) = RH_0`, so `q(phi,p) = RH_0 q_sat(T(phi,p),p)`. Origin:
  [Manabe & Wetherald 1967, JAS 24, 241-259](https://doi.org/10.1175/1520-0469(1967)024<0241:TEOTAW>2.0.CO;2)
  ("Thermal Equilibrium of the Atmosphere with a Given Distribution of Relative Humidity").
* **`beta_q` / quasi-equilibrium moisture closure**: in the QTCM the moisture equation is closed by writing
  the column moisture convergence as a term proportional to the *convergence* with a coefficient that
  interpolates between "moist" and "dry" limits — the "`beta_q`" or "moisture-convergence feedback" closure
  of [Neelin & Zeng 2000, JAS 57, 1741-1766](https://doi.org/10.1175/1520-0469(2000)057<1741:AQETCM>2.0.CO;2)
  and [Zeng, Neelin & Chou 2000, JAS 57, 1767-1796](https://doi.org/10.1175/1520-0469(2000)057<1767:AQETCM>2.0.CO;2).
  In the simplest form `P ~ M_q * (convergence)` with `M_q` a moisture-weighted vertical structure
  coefficient; the "moisture mode"/WTG limit is
  [Sobel, Nilsson & Polvani 2001, JAS 58, 3650-3665](https://doi.org/10.1175/1520-0469(2001)058<3650:TWTGAA>2.0.CO;2)
  and [Emanuel, Neelin & Bretherton 1994, QJRMS 120, 1111-1143](https://doi.org/10.1002/qj.49712051902).
* **Moist static energy / Neelin–Held closure**:
  [Neelin & Held 1987, MWR 115, 3-12](https://doi.org/10.1175/1520-0493(1987)115<0003:MTCBOT>2.0.CO;2) —
  "Modeling Tropical Convergence Based on the Moist Static Energy Budget". In steady state the column moist
  static energy budget is `<V . grad(m)> = F_net` — this gives you `P` from the *circulation* and the
  surface fluxes without needing an explicit cloud scheme. **This is the most parsimonious closure for a
  monsoon/desert model.**

### 3.3 Diagnostic precipitation: standard references

| Reference | Contribution |
|---|---|
| [O'Gorman & Schneider 2008, J. Climate 21, 3815-3832](https://doi.org/10.1175/2007JCLI2065.1) | the canonical "P - E from the moisture budget in an idealized GCM across a wide range of climates" |
| [Muller & O'Gorman 2011, Nature Clim. Change 1, 266-271](https://doi.org/10.1038/nclimate1169) | "An energetic perspective on the regional response of precipitation to climate change" — P from the *energetic* (MSE) budget |
| [Bony et al. 2013, Nature Geosci. 6, 447-451](https://doi.org/10.1038/ngeo1799) | "Robust direct effect of carbon dioxide on tropical circulation and regional precipitation" — circulation- vs thermodynamics-driven P change |
| [Byrne & O'Gorman 2015, J. Climate 28, 8078-8092](https://doi.org/10.1175/JCLI-D-15-0369.1) | P - E over **land** specifically; shows the "wet-get-wetter" scaling fails over land because of the land hydrology |
| [Chou & Neelin 2004, J. Climate 17, 2688-2701](https://doi.org/10.1175/1520-0442(2004)017<2688:MOGWIO>2.0.CO;2) | "Mechanisms of Global Warming Impacts on Regional Tropical Precipitation" — the "upped-ante" / moisture-budget mechanism |

### 3.4 **[JUDGEMENT]** Recommended closure for a one-shot steady solve

1. Diagnose `q(phi,p,T) = RH(p) * q_sat(T,p)` with `RH` prescribed (fixed-RH or a two-mode profile).
2. Get `V` from the momentum solve (section 2), `T` from the surface+radiative solve (section 5).
3. Compute `P - E = -(1/(rho_w g)) div(Int q V dp)` from (M-3).
4. Compute `E` from the surface energy balance / bulk formula (sections 4-5).
5. Solve for `P`, allowing `P = 0` where the column diverges moisture.
6. Iterate 1-5 to convergence (fixed point).

This is **fully diagnostic** and requires no time stepping. What it does **not** give you: precipitation
efficiency/temperature scaling beyond the Clausius–Clapeyron dependence of `q_sat`, and any memory of soil
moisture (section 4).

---
