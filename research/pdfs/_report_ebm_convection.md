# Literature report: moist energy balance models and convection / moisture closures

**Prepared for:** a physically-grounded, STEADY-STATE 2-D (lon x lat) atmospheric circulation model.
**Working dir:** `K:\moder\EyeOfHarmonyBuffer`

## 0. How to read this report, and what "verified" means here

Retrieval of every blocked publisher (AMS/Cloudflare, Wiley, r.jina.ai, direct PDF fetch) failed as expected.
The following routes DID work and are the basis of this report:

| Route | What it gave |
|---|---|
| Wayback Machine of AMS *view* pages + the per-equation GIFs on those pages | Full prose of Siler et al. (2018) and all 20 of its display equations |
| Author / institutional self-archiving | Emanuel (1991) full scan; Betts (2004) lecture notes; Rose & Ferreira (2013) via MIT DSpace |
| Open textbooks / lecture notes (open web) | Budyko-Sellers EBM equations; Manabe-Wetherald RH profile |
| arXiv / Copernicus (open access) | Manabe-Wetherald RH profile (2nd source); Neelin-Held (1987) budget reproduction |
| Crossref + OpenAlex + Unpaywall + Semantic Scholar APIs | Bibliographic verification (volume / issue / pages / DOI / OA status) |
| PyMuPDF page rendering + DeepEye vision OCR | Equations inside *image-only* (scanned) PDFs and inside *GIF* equation images |

**Labelling convention used below**

* `[RETRIEVED]` – I downloaded and text-extracted the cited source itself.
* `[SECONDARY]` – I could NOT get the cited source, but I retrieved a *different real* source that reproduces its
  content, and I say which one.
* `[UNVERIFIED]` – I could not verify it. I do not guess.

**Machine-extraction caveat.** Several equations below were obtained by OCR of low-resolution GIFs or of scanned
pages. Where OCR symbol shapes are ambiguous I say so explicitly. Every OCR output used is saved verbatim
(section 7), so any claim can be re-checked. Absolute rule applied: no equation appears below unless it came
out of a real retrieved artefact.

---

## PART A — MOIST ENERGY BALANCE MODELS

### A0. Bibliographic verification summary (all confirmed against Crossref **and** OpenAlex)

| # | Reference | Venue / vol(issue) / pages | DOI | OA status (Unpaywall) | Full text obtained? |
|---|---|---|---|---|---|
| A1 | Siler, N., G. H. Roe, K. C. Armour (2018) | J. Climate **31**(18), 7481–7493 | 10.1175/JCLI-D-18-0081.1 | closed | **YES** (Wayback, full text + all equation GIFs) |
| A2 | Flannery, B. P. (1984) | J. Atmos. Sci. **41**(3), 414–421 | 10.1175/1520-0469(1984)041<0414:EBMITO>2.0.CO;2 | bronze (AMS only) | **NO** |
| A3a | Rose, B. E. J., D. Ferreira (2013) | J. Climate **26**(6), 2117–2136 | 10.1175/JCLI-D-11-00547.1 | bronze | **YES** (MIT DSpace, full PDF) |
| A3b | Rose, B. E. J., K. C. Armour, D. S. Battisti, N. Feldl, D. D. B. Koll (2014) | Geophys. Res. Lett. **41**(3), 1071–1078 | 10.1002/2013GL058955 | closed | **NO** |
| A4 | Budyko (1969); Sellers (1969) | see A4 below | see A4 | — | **NO** (originals) |
| B1a | Betts, A. K. (1986) | Q. J. R. Meteorol. Soc. **112**(473), 677–691 | 10.1002/qj.49711247307 | closed | **NO** |
| B1b | Betts, A. K., M. J. Miller (1986) | Q. J. R. Meteorol. Soc. **112**(473), 693–709 | 10.1002/qj.49711247308 | closed | **NO** |
| B2 | Manabe, S., R. T. Wetherald (1967) | J. Atmos. Sci. **24**(3), 241–259 | 10.1175/1520-0469(1967)024<0241:TEOTAW>2.0.CO;2 | bronze (AMS only) | **NO** |
| B3 | Emanuel, K. A. (1991) | J. Atmos. Sci. **48**(21), **2313–2335** | 10.1175/1520-0469(1991)048<2313:ASFRCC>2.0.CO;2 | closed | **YES** (author's own PDF, scanned) |
| B4 | Neelin, J. D., I. M. Held (1987) | Mon. Wea. Rev. **115**(1), 3–12 | 10.1175/1520-0493(1987)115<0003:MTCBOT>2.0.CO;2 | closed | **NO** |

### Corrections to the brief (please propagate these)

1. **Betts & Miller page ranges are swapped in the brief.**
   Part I is **pp. 677–691** and is authored by **A. K. Betts alone** (not "Betts & Miller").
   Part II is **pp. 693–709** and is by **Betts & Miller**. Both in QJRMS **112**(473), 1986.
   (Crossref. Note: Betts's own 2004 notes cite Part I as 677–**692** and Part II as 693–**710** — a one-page
   discrepancy; Crossref and OpenAlex both give 677–691 / 693–709.)
   Part II's exact title (Crossref, note the spelling): "A new convective adjustment scheme. Part II: Single
   column tests using GATE wave, BOMEX, ATEX and arctic air-mass data sets".
2. **Rose, Armour, Battisti, Feldl & Koll is 2014, not 2017** — GRL **41**(3), 1071–1078,
   doi:10.1002/2013GL058955. And it is **not** a "moist EBM" paper: it is a GCM feedback/OHU-pattern study
   (abstract unavailable to me — full text not retrieved, so I make no claim about its equations).
3. **Emanuel (1991) pages are 2313–2335**, which matches the brief. Crossref's record says "2313-2329" and is
   **wrong**: I OCR'd the printed running heads of the author's own PDF and got p. 2313 on PDF page 1,
   p. 2334 on PDF page 22, p. 2335 on PDF page 23 (23 pages, 2313 + 22 = 2335). Use 2313–2335.
4. **Rose & Ferreira (2013)**: Crossref lists the year as **2012** (online-first); the printed issue is
   *Journal of Climate*, **VOLUME 26**, **15 MARCH 2013**, pp. 2117–2136. Cite as 2013.

---

### A1. Siler, Roe & Armour (2018) — the moist (diffusive) EBM `[RETRIEVED]`

**Citation.** Siler, N., G. H. Roe, and K. C. Armour, 2018: *Insights into the Zonal-Mean Response of the
Hydrologic Cycle to Global Warming from a Diffusive Energy Balance Model.* J. Climate, **31**(18), 7481–7493,
doi:10.1175/JCLI-D-18-0081.1.
**Obtained from:** Wayback capture `20210417033122` of
`https://journals.ametsoc.org/view/journals/clim/31/18/jcli-d-18-0081.1.xml` (full text, 102 004 chars) plus the
20 display-equation GIFs `jcli-d-18-0081.1-e1.gif … e17.gif, ea1.gif … ea3.gif` from the same directory.
(The live AMS PDF endpoint returns **0 bytes** — Cloudflare.)

#### A1.1 The governing equations (Section 1 and Section 2)

All equations below are OCR of the archived GIFs; the plain-text form is given. Numbering is the paper's own.

**Eq. (1)** — definition of the net heating, from the column energy budget:
```
Q_net(x) = 1/(2*pi*a^2) * dF/dx                                       (1)
```
> "where a is the radius of Earth, x is the sine of latitude, and Q_net is the difference between the net
> downward energy flux at the top of the atmosphere (TOA) and the surface (e.g., Pierrehumbert 2010)."

**Eq. (2)** — the moist (MSE) diffusion ("Fickian") closure:
```
F(x) = -(2*pi*p_s/g) * D * (1 - x^2) * dh/dx                          (2)
```
> "where p_s is surface air pressure (1000 hPa), g is the acceleration due to gravity, D is a (constant)
> diffusion coefficient (with units of m^2 s^-1), and (1 - x^2) accounts for the spherical geometry."

**Eq. (3)** — the MEBM proper (Eqs. 1 + 2 combined):
```
Q_net(x) = -(p_s/(g*a^2)) * D * d/dx[ (1 - x^2) * dh/dx ]             (3)
```

**The moist (MSE) variable.** The paper states:
> "more recent EBM formulations have replaced T with near-surface moist static energy h = c_p T + Lq, where
> c_p is the specific heat of air, L is the latent heat of vaporization, and q is the near-surface specific
> humidity (e.g., Flannery 1984; Frierson et al. 2007; Hwang and Frierson 2010; Rose et al. 2014; Roe et al.
> 2015)."

**The moisture closure (this is the "moist" part):**
> "Typically, relative humidity is assumed to be fixed at 80%, making h a single-valued function of T
> (Hwang and Frierson 2010; Rose et al. 2014; Roe et al. 2015). While unrealistic over land, this value is
> close to the observed near-surface relative humidity over the oceans, where most eddy heat transport occurs
> (Peixoto and Oort 1996)."

So: **RH ≡ 0.80 exactly, q = 0.80 · q_sat(T, p_s)**; there is no prognostic moisture equation and no separate
moisture transport equation — moisture is slaved diagnostically to T. This is the crucial simplification a
coarse 2-D model can copy.

**The diffusivity D.**
> "The best agreement is found by setting D = 1.16 x 10^6 m^2 s^-1 (Fig. 2b), which is within 10% of the value
> used in previous studies (Hwang and Frierson 2010; Rose et al. 2014; Roe et al. 2015). Like these studies, we
> assume that D is uniform with latitude and held fixed at this value for all following analyses."

**Eqs. (4)–(6)** — the Hadley-cell / eddy partition (the paper's novel addition):
```
F_HC(x)   = w(x) * F(x)        (4)
F_eddy(x) = [1 - w(x)] * F(x)  (5)
w(x) = exp( -x^2 / sigma_x^2 ) (6)
```
> "For w(x), we choose a Gaussian function [Eq. 6] and specify a characteristic width of sigma_x = 0.3 …
> Note that the original MEBM is recovered by setting w(x) = 0."
> "there are two obvious limitations to Eq. (6). First, because w(x) approaches zero outside the tropics, it
> does not account for heat transport by the Ferrel and polar cells … Second, by choosing a fixed value for
> sigma_x, we neglect possible changes in the partitioning between eddy and Hadley cell transport in response
> to global warming."

**Eqs. (7)–(9)** — the Hadley-cell circulation and its (upgradient!) latent transport:
```
F_HC(x)     = psi(x) * g(x)          (7)
g(x)  ~=  h_T - h(x)                 (8)
F_HC,q(x)   = -psi(x) * L * q(x)     (9)
```
> "Let psi(x) be the southward mass transport in the lower branch of the cell … where g(x) is the gross moist
> stability of the atmosphere, which represents the flow-weighted difference in moist static energy between the
> upper and lower branches at each latitude (Neelin and Held 1987)."
> "We set h_T = 1.06 x h(0), or 6% above the near-surface moist static energy at the equator."
> "Since g(0) > 0, the net transport of moist static energy is downgradient throughout the Hadley cell. However,
> because the upper branch of the cell is essentially dry, latent heat transport F_q is confined to the lower
> branch."

**Eqs. (10)–(11)** — the Held & Soden (2006) thermodynamic scaling they compare against:
```
(E' - P')/(E - P)  ~=  alpha * T'    (10)
alpha = L / (R_v * T^2)              (11)
```
> "where primes indicate the difference between the warmed and mean-state climates and [Eq. 11] is the
> Clausius–Clapeyron scaling factor, with R_v representing the gas constant of water vapor."

**Eqs. (12)–(13)** — the perturbation (global-warming) form, Section 3a:
```
R_f(x) - G'(x) + lambda(x)*T'(x) = 1/(2*pi*a^2) * dF'/dx              (12)
F'(x) = -(2*pi*p_s/g) * D * (1 - x^2) * dh'/dx                        (13)
```
> "Let R_f(x) be defined as the TOA radiative forcing resulting from an abrupt quadrupling of atmospheric CO2,
> and G'(x) be defined as the change in ocean heat uptake. Following the linear feedback framework of Roe et al.
> (2015), the perturbation form of the MEBM can then be expressed as [Eq. 12] where lambda(x) is the strength of
> the local climate feedback, and F'(x) is the change in atmospheric heat transport … where
> h'(x) = c_p T'(x) + L q'(x)."
> "Because q' ~= alpha T' q under constant relative humidity (according to a linearization of the
> Clausius–Clapeyron equation), the solution to Eqs. (12) and (13) will depend to some extent on the reference
> climate."

#### A1.2 What is prescribed vs solved; grid; cost; steady vs time-stepping

**PRESCRIBED (exogenous, from observations or GCMs):**
* `Q_net(x)` — taken from ERA-Interim 1981–2010 (Fig. 2a) or from the CMIP5 preindustrial ensemble mean
  (Fig. 3). *This is the model's substitute for an OLR parameterization* (see below).
* `D = 1.16e6 m^2 s^-1`, uniform in latitude and fixed for all experiments.
* RH = 80% (constant, spatially uniform) ⇒ `q(x)` is a diagnostic function of `T(x)`.
* `w(x)` with `sigma_x = 0.3`; `h_T = 1.06 h(0)` (i.e. 6% above equatorial near-surface MSE).
* In the perturbation runs: `R_f(x)`, `G'(x)`, `lambda(x)` (in the idealized case `G' = 0`,
  `R_f = 8 W m^-2`, `lambda = -1.5 W m^-2 K^-1`, both spatially uniform).

**SOLVED:**
* Mean state: `h(x)` [equivalently `T(x)`], then `F(x)`, `F_HC`, `F_eddy`, `psi(x)` from
  Eqs. (2),(4),(7),(8); then `F_q(x)` from (9); then `E - P = -(1/(2 pi a^2)) dF_q/dx`.
* Perturbation state: `T'(x)` from the single ODE obtained by combining (12) and (13).

**Prognostic vs diagnostic.** Fully **diagnostic/steady**. There is no `dT/dt` anywhere in the retrieved
equations. The model is a steady boundary-value problem.

**OLR parameterization — important, and probably not what the brief expects.** The mean-state MEBM contains
**no OLR parameterization at all**: outgoing longwave radiation is not written as `A + B T` nor as `sigma T^4`.
Instead the *net* column heating `Q_net` is imposed directly from reanalysis/GCM data (Eq. 1), so all radiative
physics is absorbed into the prescribed `Q_net(x)`. In the perturbation problem the radiative response enters
only through the **linear feedback parameter** `lambda(x)` in Eq. (12), i.e.
`OLR'` is implicitly `-lambda(x) T'(x)` relative to forcing. I did **not** find an explicit OLR formula in the
retrieved text.

**Boundary conditions.** The paper does not print an explicit BC statement in the text I retrieved. The
evidence available is: (i) the operator in Eqs. (2)/(3) contains the factor `(1 - x^2)`, which forces the
diffusive flux to vanish at `x = +-1` automatically; (ii) the paper states that the latent eddy and Hadley
contributions "both … vanish at the poles". **A formally stated BC is `[UNVERIFIED]`** — I could not find a
sentence in the retrieved full text reading e.g. "we impose F(+-1) = 0".

**Grid / resolution / computational cost.** `[UNVERIFIED]`. I grepped the entire retrieved article body for
`grid`, `resolution`, `discret`, `finite`, `numerically`, `time step` and `computational`; every hit was in
figure captions or unrelated. The paper never states the grid. All that is stated is
> "Combining Eqs. (12) and (13) yields a single differential equation that we solve numerically for T'(x)
> given R_f(x), G'(x), and lambda(x)."
By construction the cost is trivial (a 1-D steady elliptic BVP), but **that inference is mine, not the paper's**.

**Classification: (a) steady-state solvable.** Unambiguously. This is the single most directly reusable
template in this report for a 2-D steady model.

---

### A2. Flannery (1984) — thermal vs latent energy transport `[UNVERIFIED]`

**Citation (verified).** Flannery, B. P., 1984: *Energy Balance Models Incorporating Transport of Thermal and
Latent Energy.* J. Atmos. Sci., **41**(3), 414–421,
doi:10.1175/1520-0469(1984)041<0414:EBMITO>2.0.CO;2. (Title, author, volume, issue, pages all confirmed by
Crossref and OpenAlex; the brief's title was correct.)

**Full text: NOT RETRIEVED.** Unpaywall reports `is_oa: true, oa_status: bronze` with the *only* location being
`https://journals.ametsoc.org/downloadpdf/journals/atsc/41/3/1520-0469_1984_041_0414_ebmito_2_0_co_2.pdf`,
which returns 0 bytes to me (Cloudflare). The Wayback CDX index has **no captures** of that PDF and no captures
of the modern `/view/journals/...` article page. Semantic Scholar reports the same single AMS-only OA link.

**⇒ I cannot give you Flannery's equations. I will not reconstruct them. Every equation attributed to
Flannery (1984) below would be invention, so there are none.**

**What I CAN verify about its content, from a retrieved source.** Siler et al. (2018), Section 1
(`[RETRIEVED]`, full text) explicitly cites Flannery (1984) as one of the studies that replaced temperature by
moist static energy in the diffusive EBM:

> "However, recognizing that T represents only the sensible component of atmospheric heat content, more recent
> EBM formulations have replaced T with near-surface moist static energy h = c_p T + Lq, where c_p is the
> specific heat of air, L is the latent heat of vaporization, and q is the near-surface specific humidity
> (e.g., **Flannery 1984**; Frierson et al. 2007; Hwang and Frierson 2010; Rose et al. 2014; Roe et al. 2015)."

That is the full extent of what is verified: **Flannery (1984) is the earliest cited member of the
"MSE-diffusion EBM" lineage.** The specific way it "distinguishes thermal vs latent energy transport" —
whether two separate diffusivities, two separate flux terms, or a shared diffusivity on MSE — is
`[UNVERIFIED]`.

*Suggested next step for whoever can reach AMS:* the single highest-value missing artefact is this 8-page
paper; it is bronze-OA, so any browser that can clear Cloudflare gets it free.

---

### A3. Rose & Ferreira (2013) and Rose et al. (2014) — which one is the "moist EBM"?

**Answer: neither. The actual "moist EBM" reference is Siler et al. (2018) (A1), building on the lineage
Hwang & Frierson (2010) → Rose et al. (2014) → Roe et al. (2015) that Siler et al. cite.**

#### A3a. Rose & Ferreira (2013) `[RETRIEVED]` — a two-box EBM, not a moist EBM

**Citation.** Rose, B. E. J., and D. Ferreira, 2013: *Ocean Heat Transport and Water Vapor Greenhouse in a Warm
Equable Climate: A New Look at the Low Gradient Paradox.* J. Climate, **26**(6), 2117–2136,
doi:10.1175/JCLI-D-11-00547.1. Retrieved in full from MIT DSpace (`http://hdl.handle.net/1721.1/80782`,
REST bitstream endpoint), 20 pages.

**What the paper actually is.** From the abstract (p. 2117):
> "An ensemble of idealized aquaplanet GCM calculations is used to assess the equilibrium sensitivity of global
> mean surface temperature (T) and its equator-to-pole gradient (Delta T) to variations in OHT, **prescribed
> through a simple analytical formula** representing export out of the tropics and poleward convergence."

The energy-balance content is confined to **Section 4, "A two-box EBM interpretation"** (pp. 2128–2130), which
is a **2-box** (tropics / extratropics, boundary at 30 deg latitude) *linear perturbation* model — not a
latitudinally resolved diffusive model and not a moist EBM.

**Its actual equations (Section 4a).**

```
delta_b_e - F'_a = OLR'_e ,     delta_b_p + F'_a = OLR'_p              (2)
F'_a = gamma_d * DeltaT' + gamma_lh * T'_e ,   DeltaT' = T'_e - T'_p  (3)
OLR'_e = B * T'_e ,   OLR'_p = B * T'_p - alpha * delta_b_p            (4)
```
> "Following LA07, perturbations in the AHT are parameterized as [Eq. 3] … We thus account for the different
> physics setting DSE and LH transports (Caballero and Langen 2005): DSE fluxes heat diffusively down the
> temperature gradient, while LH scales with the absolute temperature of the subtropical source regions feeding
> moisture into storm tracks. The parameters gamma_d, gamma_lh set the relative efficiency of these processes.
> LA07 offer plausible values (gamma_d, gamma_lh) = (1.6, 0.8) W m^-2 degC^-1."
> "Typically in simple EBMs the OLR is assumed to be linear in temperature, e.g., OLR' = B T'. We have argued
> that an additional longwave feedback is operating in the mid- to high latitudes … A crude parameterization is
> simply [Eq. 4], with alpha a dimensionless coefficient."

**This is the clean, explicit statement of the thermal-vs-latent distinction that the brief asks about for
Flannery (1984)** — but it is Rose & Ferreira's (2013) formulation, attributed by them to Langen & Alexeev
(2007) and Caballero & Langen (2005), *not* to Flannery. Please do not attribute Eq. (3) to Flannery.

Solutions (Section 4b, Eqs. 5–6), verified by OCR of the printed page:
```
T'bar = (delta_b / B) * (1 + alpha/2)                                              (5)
DeltaT' = -(delta_b / Gamma) * [ (2*gamma_lh/B)*(1 + alpha/2) + alpha ]             (5)
DeltaT'/T'bar = -(2/Gamma) * [ gamma_lh + B*(1 + 2/alpha)^-1 ]                     (6)
where Gamma = 2*gamma_d + gamma_lh + B
```
and for an imposed OHT change `delta_b_p = F'_o = -delta_b_e` (Section 4c), Eqs. (7)–(11), of which the
transport-compensation result is
```
F'_t / F'_o = B / Gamma = 1 / (1 + (2*gamma_d + gamma_lh)/B) < 1                     (9)
```
> "One might be tempted to fit this result to DeltaT/Tbar = -2.6 as found in our GCM in Fig. 4. However this is
> the wrong scaling for experiments with altered OHT, since the forcings ought to be conservative (zero global
> mean)."

*OCR caveat:* the OCR rendered the latent-transport coefficient as `gamma_th`; the embedded-text extraction of
the same PDF renders it `glh` and the surrounding prose calls it "LH"/latent heat. I therefore read it as
**gamma_lh**. Flagging this because it is exactly the kind of symbol confusion that matters.

**Prescribed / solved / grid / cost / classification for the 2-box part:**
* *Prescribed:* `B` (linear OLR feedback), `gamma_d`, `gamma_lh` = (1.6, 0.8) W m^-2 degC^-1, `alpha`,
  and the box forcings `delta_b_e`, `delta_b_p`.
* *Solved:* the two algebraic temperature perturbations `T'_e`, `T'_p` — a 2x2 **linear algebraic system**.
* *Grid:* none (two boxes of equal area).
* *Cost:* analytic; negligible.
* *Classification:* **(a) steady-state solvable** — trivially; it is already the steady perturbation solution.
  The GCM half of the paper obviously requires time-stepping but is not an EBM.

#### A3b. Rose, Armour, Battisti, Feldl & Koll (2014) `[UNVERIFIED for content]`

**Citation (verified):** Geophys. Res. Lett., **41**(3), 1071–1078, doi:10.1002/2013GL058955. Year **2014**
(the brief says 2017 — wrong). Authors confirmed by Crossref: Rose; Armour; Battisti; Feldl; Koll.
Rose's own publication list (`http://www.atmos.albany.edu/facstaff/brose/publications.html`) shows the
identical author list and title.

**Full text NOT retrieved:** Unpaywall says `closed`. A copy on a Harvard site
(`climate.fas.harvard.edu/.../rosearmourbattisti13grl_spatialpatterns.pdf`) returns HTTP 403 even with a
Referer header. Wiley is blocked. **⇒ no equations reported; content marked `[UNVERIFIED]`.**

The only content statement I can support is indirect and comes from Siler et al. (2018) `[RETRIEVED]`:
> "More recent studies have also found that the MEBM realistically emulates the surface temperature response to
> different spatial patterns of ocean heat uptake (Rose et al. 2014) and climate feedbacks (Roe et al. 2015)."

i.e. Rose et al. (2014) is an **application/validation** of the moist EBM against GCMs, not the source of the
moist-EBM equations.

---

### A4. Budyko (1969) / Sellers (1969) — the dry baseline `[SECONDARY]`

**Originals: NOT RETRIEVED `[UNVERIFIED]`.** Two real retrieved sources reproduce the classic EBM form:

#### (i) UCL / ELIC open textbook, "Energy Balance Models" exercise page `[SECONDARY]`
URL: `https://www.elic.ucl.ac.be/textbook/EBM.html` (retrieved; text saved as `ucl_ebm.txt`).
It attributes the model to "(Budyko, 1969; Sellers, 1969)" and gives, verbatim:
```
S_i(1 - alpha(T_i)) = A_up(T_i) + Delta_F_transp(T_i)      (1)
A_up_i = A_up(T_i) = a + b*T_i                             (2)
F_i = F(T_i) = k_t * (T_i - Tbar)                          (3)
T_i = [ S_i(1 - alpha_i) + k_t*Tbar - a ] / (b + k_t)      (4)
alpha_i = alpha_ice  if T_i <= T_c ;  alpha_land if T_i > T_c   (5)
```
with `"The value for the total solar irradiance used here equals 1360 W m^-2"`, `alpha_ice = 0.6`,
`alpha_land = 0.3`. The linear OLR `a + b T` is attributed by that page to McGuffie & Henderson-Sellers
(2014), *A Climate Modelling Primer* (4th ed.), not to Budyko/Sellers themselves.
**Equation (4) is the analytic steady-state solution** — useful for a steady 2-D model: the dry EBM's steady
state is a *local, explicit* algebraic relation once `Tbar` is known.

*Note the transport form here is a relaxation to the global mean,* `F_i = k_t (T_i - Tbar)`.

#### (ii) Stocker, "Introduction to Climate Modelling", Univ. of Bern `[SECONDARY]`
URL: `https://climatehomes.unibe.ch/~stocker/stocker24icm.pdf` (retrieved; 211 pp.; text in `stocker_icm.txt`).
Its Eq. (4.9) is the diffusive Budyko-Sellers EBM, and it names the model explicitly:
> "The one-dimensional energy balance model presented in (4.9) is referred to as the **Budyko-Sellers EBM**.
> Budyko (1969) and Sellers (1969) were the first to propose such a simplified climate model …"

```
h*rho*c * dT/dt = (h/(R cos(phi))) * d/dphi[ (rho*c*K(phi)/R) * (dT/dphi) * cos(phi) ]
                  + (1 - alpha(phi))/4 * S(phi) - eps(phi)*sigma*T^4          (4.9)
S(phi) = S_0 * ( 0.5294 + 0.706 * cos^2(phi) )        [annual mean insolation fit]
Boundary conditions:  dT/dphi = 0  at  phi = -pi/2, +pi/2                     (4.10)
```
> "Since (4.9) is a differential equation of 2nd order (d^2/dphi^2) in space, two boundary conditions must be
> satisfied. The boundary conditions at the two poles require the heat flux to vanish, hence [4.10]."
> "where the eddy diffusivity K, the albedo alpha, and the emissivity eps may be functions of latitude."

Sellers' albedo parameterization, Eq. (2.35) of the same source, attributed to Sellers (1969):
```
alpha(T) = 0.3 - 0.009*(T - 283 K)/K ,   222 K <= T <= 283 K      (2.35)
```
> "with constant values beyond the upper and lower bounds of the temperature range."

**Sellers' grid, quoted by Siler et al. (2018) `[RETRIEVED]`:**
> "This idea originated with Sellers (1969), whose primitive **10-box EBM** included the advection of latent
> and sensible heat by the mean meridional wind, which he fit to observations."

**Classification:** (a) steady-state solvable — the diffusion form (4.9) with `dT/dt = 0` is a steady
elliptic BVP in `phi` with Neumann BCs (4.10); the relaxation form (1)–(3) is a steady linear algebraic
system (4). Note that `alpha(T)` makes it *nonlinear*, so a steady 2-D model needs an iterative (Picard/
Newton) solve — the classic Budyko ice-albedo multiple-equilibria problem.

**Original numeric constants of Budyko (1969) / Sellers (1969) — e.g. their `A`, `B`, `k_t`, `D` —
are `[UNVERIFIED]`.** I could not retrieve either original paper (Budyko 1969, *Tellus* **21**, 611–619,
doi:10.1111/j.2153-3490.1969.tb00466.x — Wiley/T&F blocked; Sellers 1969, *J. Appl. Meteor.* **8**, 392–400 —
AMS blocked).

---

### A5. Is there a 2-D (lon x lat) EBM with moisture and a prescribed ocean? `[PARTIALLY VERIFIED]`

**Short answer: I found that 2-D (lat, lon) EBMs exist and are used in EMICs, but I did NOT retrieve any paper
that gives a 2-D (lon x lat) EBM *with an explicit moisture closure* and a prescribed ocean. Those specific
equations are `[UNVERIFIED]`.**

What I *can* support, from a retrieved source — Stocker (2024), *Introduction to Climate Modelling*, §4.3
(`[SECONDARY]`, URL above):

> "The EBM in (4.9) can be further generalized to two dimensions by additionally considering the zonal
> direction. **Such models were developed in the 1980ies** for studying the temperature difference between
> glacial and interglacial periods based on the changes in the radiation balance (**North et al., 1983**).
> Still today, they are implemented in some models of reduced complexity (Table 2.1, dimensions 2/2 and 2/3,
> e.g. **Ritz et al. (2011)**)."

And Stocker's Table 2.1 ("Hierarchy of coupled models for the ocean and the atmosphere … ordered according to
the number of spatial dimensions considered") explicitly contains the category rows:
```
Dim 2 :   EBM (lat,lon)                       + diffusive ocean (z)
          ocean (lat,z) + stat.-dyn. atmosphere (lat,lon)
          OGCM          + EBM (lat,lon)
          Bern3D model
```
with the caption note "2.5D corresponds to several two-dimensional ocean basins linked in the Southern Ocean".
So: **2-D (lat, lon) EBMs are a recognised EMIC category**, and so are **statistical-dynamical atmospheres
resolved in (lat, lon)**. That is a strong pointer for where to look next, but it is not the same as
retrieving the equations.

**Leads I identified but did NOT retrieve (all `[UNVERIFIED]`):**
* North, G. R., J. G. Mengel, D. A. Short (1983), *J. Geophys. Res.* **88**, 6576–6586 — seasonal 2-D EBM.
* Hyde, W. T., K.-Y. Kim, T. J. Crowley, G. R. North (1990), *J. Geophys. Res.* — nonlinear seasonal 2-D EBM.
* Ritz, S. P., T. F. Stocker, J. P. Severinghaus (2011) — cited by Stocker for a 2/3-dimensional EMIC.
* CLIMBER-2 (Petoukhov et al. 2000, *Climate Dynamics* **16**, 1–17) and ECBilt / LOVECLIM — intermediate-
  complexity models with 2-D statistical-dynamical atmospheres plus moisture budgets. I found PIK and TECLIM
  web leads for these but did not obtain a text I could read; **do not treat the "with moisture" part of this
  bullet as verified.** (Note: a sibling agent is already working on a QTCM manual in this same workspace —
  check with the parent before duplicating that line of work.)

**Recommendation for the parent agent:** if a 2-D moist EBM is the target, the fastest defensible construction
is *A1's equations extended in longitude*: keep `Q_net` prescribed, keep `h = c_p T + 0.8 q_sat(T)`, and
replace the 1-D operator `d/dx[(1-x^2) d/dx]` by the 2-D spherical Laplacian
`(1/a^2){ d/dx[(1-x^2) d/dx] + (1/(1-x^2)) d^2/dlambda^2 }` with `D` possibly anisotropic. **This extension is
my proposal, not something found in the literature — it must be labelled as such in any writeup.**

---

## PART B — CONVECTION / MOISTURE CLOSURES

### B1. Betts (1986) / Betts & Miller (1986) — convective adjustment `[SECONDARY, author's own notes]`

**Citations (verified, see A0 for the page-range correction).**
Part I: Betts, A. K., 1986, QJRMS **112**(473), 677–691, doi:10.1002/qj.49711247307.
Part II: Betts, A. K., and M. J. Miller, 1986, QJRMS **112**(473), 693–709, doi:10.1002/qj.49711247308.

**Originals NOT retrieved:** both `closed` per Unpaywall; `onlinelibrary.wiley.com/doi/10.1002/qj.49711247307`
has **no Wayback captures**; Wiley is blocked.

**What I retrieved instead — and it is a good substitute:** the first author's own lecture notes,
Betts, A. K. (2004): *The Parameterization of Deep Convection and the Betts-Miller scheme*, CPTEC, May 2004,
41 pp., from `http://alanbetts.com/workspace/uploads/bettsmiller2004-1282913116.pdf` (author's own website).
The PDF's embedded text layer uses an unmappable Symbol font (Greek letters vanish), so the equations below
were recovered by OCR of 200-dpi page renders (saved in `betts_miller2004_ocr.txt`; page images
`img/bettshi_p0NN.png`). The notes themselves reference "Betts, A. K., 1986" and "Betts and Miller, 1986" as
the primary sources for the scheme.

#### B1.1 The lagged-adjustment ("soft adjustment") core — Section "Formal structure of a lagged adjustment scheme"

Let `S` be the thermodynamic state variable (temperature or moisture), `R` the reference profile,
`tau` the relaxation timescale, `omega` the large-scale vertical pressure velocity.

```
dS_bar/dt = -omega_bar * dS_bar/dp + (R - S_bar)/tau                      (3)
```
> "If the large-scale forcing is steady, on timescales longer than tau, then the atmosphere will reach a
> quasi-equilibrium with dS_bar/dt ~= 0. Then
```
R - S_bar  ~=  omega_bar * (dS_bar/dp) * tau                              (4)
```
> "If tau = 1hr, (T-106 global model) R - S_bar corresponds to one hour's forcing by the large-scale fields,
> including radiation. For deep convection the atmosphere will therefore remain slightly cooler and moister
> than the reference state R."
```
R - S_bar  ~=  omega * tau * dR/dp                                        (5)
F = integral( (R - S_bar)/tau dp/g )  ~=  integral( omega_bar * (dR/dp) dp/g )   (6)
```
> "Equation (6) shows that the structure of the convective fluxes is closely linked to the structure of the
> specified reference profile R. By adjusting towards an observationally realistic thermodynamic structure R,
> we simultaneously constrain the convective fluxes (including precipitation) to have a structure similar to
> those derived diagnostically from (1), or its simplified form (6), by the budget method (Yanai et al., 1973)."

**This is the "soft" adjustment: the model relaxes toward `R` with timescale `tau` rather than snapping to it.**

#### B1.2 The reference temperature profile (the "moist adiabat through cloud base")

Slide "Reference profiles for deep convection", Eq. (7):
```
theta^1_R(p) = theta_bar_B + 0.85 * Gamma_w * (p_B - p)      for p_B < p < p_F        (7)
   with  Gamma_w = (d theta / d p)_w   [the moist pseudo-adiabat slope in theta]
```
> "The reference profile for theta is computed up to the freezing level, as a fraction of the slope of the moist
> pseudo-adiabat."
> "A coefficient of **0.9** corresponds to the slope of the **wet virtual adiabat**: the coefficient of **0.85**
> is a **more unstable profile**: a 'compromise' value."
> "Hurricane core has coefficient of 0.9 (Gamma_wv); VIMHEX a value of 0.8."

Above the freezing level, Eq. (8) (note: this is the **revised 1993** form, which the notes explicitly say
differs from Betts & Miller 1986):
```
T^1_R(p) = T_c(p) + [ T_R(p_F) - T_c(p_F) ] * (1 - y^2)         (8)
   where  y = (p_F - p)/(p_F - p_T)
```
> "Above the freezing level, the profile returns to the moist pseudo-adiabat at cloud-top. The interpolation is
> done **quadratically** in terms of the temperature difference from the wet adiabat."
> "[This involves several small changes from Betts and Miller (1986), in which the reference profile returned
> **linearly** to the environmental temperature at cloud-top, and **theta rather than T** was used for the
> interpolation.]"

**⚠ Sign-convention warning on Eq. (7).** Two independent OCR passes both read the printed sign as
`+ 0.85 Gamma_w (p_B - p)` with `Gamma_w = (d theta/d p)_w`. On a moist adiabat `theta` *increases* with
height while `p` decreases, so `(d theta/d p)_w < 0`; combined with `(p_B - p) > 0` above cloud base this
literal reading gives a reference `theta` that *decreases* upward — which is backwards. The physically
consistent reading is either that the notes define `Gamma_w` as the (positive) magnitude of the moist-adiabatic
theta-slope, or that the intended grouping is `+0.85 Gamma_w (p - p_B)` with `Gamma_w<0`. The *magnitudes*
(0.85 vs 0.9, "0.85 is more unstable") are unambiguous and consistent either way. **Treat the sign as
`[UNVERIFIED]` and re-read slide 16 of the original PDF before coding it.**

#### B1.3 The reference moisture profile (this is the bit the brief asked for)

Slide "Moisture Reference profile q_R":
> "Computed from temperature reference profile by specifying **subsaturation** `P = (p* - p)` at three levels,
> cloud-base (P_B), the freezing level (P_F) and cloud-top (P_T) with linear gradients between."
> "In the present version of the model, the values chosen are **(P_B, P_M, P_T) = (-25, -40, -20 mb)**. Again
> this is just a compromise to keep the atmosphere from saturating in the presence of forcing."
> "Note that
> ```
> P/p = (1 - RH) / [ A + (A - 1) RH ]        where  A = R*L / (R_v * 2 * C_p * T)
> ```
> so we are simply constraining RH at different levels."
> "(P_B, P_M, P_T) = (-25, -40, -20 mb) correspond to **(RH_B, RH_M, RH_T) ≈ (90, 70, 50%) at
> p = (950, 550, 200 hPa)**."

**Note the brief's guess `q_ref = q_s(T_ref) * ...` is essentially right in spirit**: the scheme does *not*
prescribe `q_R` directly; it prescribes a **saturation deficit** (equivalently an RH) at cloud base, freezing
level and cloud top, interpolates linearly in pressure, and then sets `q_R = RH(p) * q_sat(T_R(p), p)`.

*Consistency check I performed (mine, not the source's):* inserting the three stated `(P, RH, p)` pairs into
the printed relation gives `A ≈ 2.5, 2.8, 3.7` at 950/550/200 hPa, whereas
`A = R_d L/(R_v 2 C_p T)` gives ≈ 2.7 at 288 K and ≈ 3.5 at 220 K. The printed formula and the printed
numbers are therefore mutually consistent, which raises my confidence in the transcription of the formula.
The OCR of the `A` definition is the least certain part; **the (`P` → RH) mapping is the part I would trust.**

#### B1.4 Enthalpy correction, tendencies and precipitation

```
integral_{p_O}^{p_T} (H_R - H_bar) dp = 0        where H = c_p*T + L*q              (11)
Delta_H = (1/Delta_p_c) * integral_{p_O}^{p_T} (H_R - H_bar) dp                      (12)
```
> "The first guess profiles of (T^1_R, q^1_R) are then modified until they satisfy the total enthalpy
> constraint [11] … T_R is then corrected at each level, **at constant P**, so as to change H_R by Delta_H,
> independent of pressure. This energy correction is iterated once."
> "In Betts and Miller (1986), this correction was applied at all levels except cloud-top and a shallow
> surface layer."

Convective tendencies — applied to T and q **separately**:
```
(dT/dt)_Cu = ( T_R - T_bar ) / tau                                                   (13a)
(dq/dt)_Cu = ( q_R - q_bar ) / tau                                                   (13b)
PR = integral_{p_O}^{p_T} ( (q_R - q_bar)/tau ) dp/g
   = -(c_p/L) * integral_{p_O}^{p_T} ( (T_R - T_bar)/tau ) dp/g                      (14)
```
> "No liquid water is stored in the present scheme, and the deep convective adjustment is suppressed if it ever
> gives PR < 0."
> "If PR < 0, the shallow cloud scheme is called. Since a shallow convective cloud top has not previously been
> found from a buoyancy criterion, we specified a shallow cloud-top [700 hPa]."

#### B1.5 The relaxation timescale tau

> "With P_R ~= 40 mb in the middle troposphere, we have found this suggests an upper limit on tau, which is
> **two hours for the ECMWF T-63 spectral model and one hour for the T-106 model**, with smaller values at
> higher resolutions. We recommend that tau should be set so that the model atmosphere **nearly saturates on
> the grid-scale in major convective disturbances**. In a numerical model a **lagged adjustment**, rather than
> a sudden adjustment at a single time-step, has the advantage of smoothness, with less of a tendency to
> 'blink' on and off at grid-points in a physically unrealistic way."

Betts (1997)'s gravity-wave argument, Table 1 of the same notes:

| Wave mode | speed C (m/s) | tau at 60 km | tau at 120 km | tau at 400 km |
|---|---|---|---|---|
| Mode 1 (deep troposphere) | 50 | 20 min | 40 min | 2.2 h |
| Mode 2 (inflow at freezing level) | 25 | 40 min | 80 min | 4.4 h |

Also, a constraint from not over-saturating: `tau < P_M / omega_max` (from Eq. 10 and the slide after it).

#### B1.6 Prescribed / solved / grid / cost / steady vs time-stepping — and what a coarse 2-D steady model can copy

* **PRESCRIBED:** `R` (the reference T and q profiles, via 0.85, `theta_B`, `p_B`, `p_F`, `p_T`, and
  `(P_B, P_M, P_T)`); **`tau`**; the shallow cloud-top level (700 hPa).
* **SOLVED:** the convective tendencies `(dT/dt)_Cu`, `(dq/dt)_Cu` and the precipitation rate `PR`;
  the enthalpy correction `Delta_H` is a *diagnostic* integral.
* **Grid/resolution:** none specified; the scheme is column-local and grid-resolution-dependent only through
  `tau` ("smaller values at higher resolutions").
* **Computational cost:** cheap — the notes describe it as a relaxation with a single energy-correction
  iteration; no iterative plume/entrainment solve. (The notes do not give a cost figure.)
* **Classification:**
  * As published: **(b) requires time-stepping** — it is explicitly a *lagged* adjustment, `dS/dt = (R-S)/tau`,
    and `tau` is what makes it smooth.
  * **But it collapses trivially to a steady/diagnostic scheme for your purposes:** in the steady limit
    `dS_bar/dt = 0`, Eq. (3) gives `R = S_bar` (the scheme is the identity at equilibrium), and Eq. (6) gives
    the convective flux directly from the large-scale `omega` and the **analytic** reference profile `dR/dp`.
    So for a steady 2-D model you can use Betts-Miller as a **diagnostic closure**: given `omega` (from the
    steady circulation you solve) and the reference profile `R`, compute the convective heating/moistening and
    `PR` directly, with no `tau` at all. Equations (5), (6), (11), (13), (14) are all you need; `tau` drops
    out. **That reduction is my inference from the retrieved equations** — the notes do not state it, and
    `tau < P_M/omega_max` tells you the regime in which it is valid.
* **Copy-list for a coarse 2-D model:** Eq. (7) (0.85 × moist-adiabat slope up to freezing level), Eq. (8)
  (quadratic return to cloud top), the three-level `(P_B, P_M, P_T) = (-25, -40, -20 mb)` moisture reference ↔
  RH ≈ (90, 70, 50)%, Eq. (11) (enthalpy-conserving correction), Eq. (14) (PR from the moisture relaxation).

---

### B2. Manabe & Wetherald (1967) — fixed relative humidity `[SECONDARY]`

**Citation (verified).** Manabe, S., and R. T. Wetherald, 1967: *Thermal Equilibrium of the Atmosphere with a
Given Distribution of Relative Humidity.* J. Atmos. Sci., **24**(3), 241–259,
doi:10.1175/1520-0469(1967)024<0241:TEOTAW>2.0.CO;2.
(Note: a search hit rendered the title as "…with a **fixed** distribution of relative humidity"; the Crossref
title is "**given** distribution".)

**Full text: NOT RETRIEVED.** Unpaywall: `bronze`, sole location the AMS PDF, which returns 0 bytes to me.
The Wayback capture of the AMS article page (`20201210032204`) contains only the abstract (8 891 chars).

#### B2.1 The fixed-RH closure — obtained from TWO independent retrieved sources that agree

**Source 1 `[SECONDARY]`:** van Delden, A. (Univ. Utrecht), *Atmospheric Dynamics*, Chapter 2, Eq. (2.29)–(2.30),
retrieved from `https://webspace.science.uu.nl/~delde102/AtmosphericDynamics%5b2020a%5dCh2.pdf`
(text in `utrecht_ch2.txt`, lines 1132–1162). Verbatim:
> "Moreover, Manabe and Wetherald assumed that the temperature profile adjusts to the moist adiabatic lapse
> rate (Box 1.8). … They followed Arrhenius' assumption of constant relative humidity. More specifically, they
> assumed that the global-, annual average relative humidity, RH, obeys the following equation:
```
RH = RH_g * ( ( sigma - 0.02 ) / ( 1 - 0.02 ) )        (2.29)
sigma = p / p_s                                       (2.30)
```
> "where RH_g is the relative humidity at the ground (Earth's surface) (**77 %**) and p_s [is] the pressure at
> the Earth's surface. Because the r.h.s. is negative when sigma < 0.02 (in the stratosphere), the mixing ratio
> of water vapour is set at a fixed very low value of **3 x 10^-6** if sigma <= 0.02."

**Source 2 `[SECONDARY]`:** von Paris, P., H. Rauer, J. L. Grenfell, B. Patzer, P. Hedelt, B. Stracke,
T. Trautmann, F. Schreier (2008), *Warming the early Earth — CO2 reconsidered*, arXiv:0804.4134v2, §2.3,
Eq. (22) (text in `arxiv_0804_4134.txt`, lines 483–502). Verbatim:
> "In the troposphere, water vapor concentrations C_H2O are calculated from a fixed relative humidity
> distribution RH:
```
C_H2O(T,z) = ( p_sat(T(z)) / p(z) ) * RH(z)            (21)
```
> "where p_sat is the saturation vapor pressure of water at the given temperature T and p the atmospheric
> pressure at level z. The default relative humidity profile RH follows the approach of **Manabe and Wetherald
> (1967)**, with a relative humidity R_s of **80%** at the surface.
```
RH(z) = R_s * ( ( p(z)/p_surface ) - 0.02 ) / 0.98     (22)
```
> "Above the cold trap, water vapor is treated as a non-condensable gas, and its concentration is fixed at the
> cold trap value."

**⇒ The closure form is verified by two independent sources and is unambiguous:**
```
RH(p) = RH_s * ( p/p_s - 0.02 ) / ( 1 - 0.02 )                [= / 0.98]
q(p)  = RH(p) * q_sat( T(p), p )
```
**One discrepancy remains: the surface relative humidity.** Utrecht says **77%**, von Paris et al. say **80%**.
The value MW67 actually used is therefore `[UNVERIFIED]`. (Both agree the profile is linear in `p/p_s`, hits
zero at `p/p_s = 0.02`, and that the stratospheric water vapour is pinned to a floor — 3×10⁻⁶ in Utrecht,
"the cold trap value" in von Paris et al.)

#### B2.2 The convective adjustment `[PARTIALLY VERIFIED]`

**Not retrieved for MW67 itself.** The nearest retrieved description of the *scheme class* is von Paris et al.
(2008), §2.2.4 "Convective adjustment" `[SECONDARY]`:
> "Convective adjustment to the lapse rate is performed whenever the calculated radiative lapse rate
> `grad_rad T` exceeds the adiabatic value `grad_ad T` (Schwarzschild criterion):
```
grad_rad T > grad_ad T                                 (20)
```
> The adiabatic lapse rate is calculated as a standard **dry adiabat in the stratosphere**. In the
> **troposphere, a wet H2O adiabatic lapse rate** is assumed."

and the Utrecht textbook's one-line summary of MW67 `[SECONDARY]`:
> "Manabe and Wetherald assumed that the temperature profile **adjusts to the moist adiabatic lapse rate**."

That is the standard "moist convective adjustment": adjust wherever the lapse rate would exceed the moist
adiabat, condensing the excess vapour and releasing latent heat. **The precise adjustment algorithm MW67 used
(Manabe & Strickler 1964 style), and its exact constants, are `[UNVERIFIED]`.** Related primary reference I
located but did not retrieve: Manabe, S., and R. F. Strickler (1964), *J. Atmos. Sci.* **21**(4), 361–…,
doi:10.1175/1520-0469(1964)021<0361:TEOTAW>2.0.CO;2 (title code TEOTAW = "Thermal Equilibrium of the
Atmosphere with a Convective Adjustment").

#### B2.3 Model character

* **PRESCRIBED:** the RH profile (above); solar constant / insolation; CO₂ and O₃ distributions; cloud
  distributions (the Utrecht text cites "TABLE 2.3. Cloud classification adopted by Manabe and Wetherald
  (1967)").
* **SOLVED:** the vertical temperature profile, by radiative-convective equilibrium; the convective adjustment
  is a **diagnostic** correction applied each iteration.
* **Grid:** a **1-D vertical column**; number of levels `[UNVERIFIED]` (not stated in either retrieved source).
* **Cost:** negligible by modern standards (1-D radiative-convective equilibrium, iterated to equilibrium).
* **Classification: (c) mixed, but effectively (a)** — it is an *equilibrium* calculation reached by
  relaxation; the convective adjustment is applied diagnostically at each iteration. A steady 2-D model can
  implement the fixed-RH closure as a purely **local algebraic** relation and the convective adjustment as a
  **local column-wise diagnostic** — no memory, no time-stepping required.

---

### B3. Emanuel (1991) — cumulus scheme `[RETRIEVED]`

**Citation (verified).** Emanuel, K. A., 1991: *A Scheme for Representing Cumulus Convection in Large-Scale
Models.* J. Atmos. Sci., **48**(21), **2313–2335**, doi:10.1175/1520-0469(1991)048<2313:ASFRCC>2.0.CO;2.
Page range confirmed from the printed running heads of the article itself (2313 at start, 2335 at end);
Crossref's "2313-2329" is a bad record.

**Obtained from:** the author's own website, `https://texmex.mit.edu/pub/emanuel/PAPERS/convect91.pdf`
(MIT). **The PDF is an image-only scan** — `pypdf` extracts 0 characters — so all equations below come from
DeepEye OCR of 200-dpi page renders, saved verbatim in `research/pdfs/emanuel1991_ocr.txt`
(page images `research/pdfs/img/emanhi_p0NN.png`). **OCR is not perfect; the OCR model emitted LaTeX that the
original does not contain.**

#### B3.1 The closure — and an important correction to the brief's premise

**The brief says "cloud work function / CAPE quasi-equilibrium".** The retrieved paper does **not** use a
cloud work function (that is Arakawa–Schubert). It uses **CAPE** and quasi-equilibrium *language*, but its
closure is a **mass-flux / fractional-area closure**:

> "It is important to note that, if the environmental forcing of convection (e.g., surface fluxes and radiative
> cooling) is known, the M^i must be such that the convective tendencies balance the large-scale tendencies
> over a sufficient length of time, as pointed out by Arakawa and Schubert (1974). **The M^i are not actually
> forced to meet this condition, but are adjusted with time towards quasi-equilibrium.**"
> "One may regard a convective flux averaged over a large-scale grid box as the product of a fractional area,
> sigma^i, and a vertical velocity, w^i. Thus **M^i = rho^i sigma^i w^i**, where rho^i is the air density at
> level i. The vertical velocity is estimated from the subcloud parcel's convective available potential energy
> (CAPE):
```
w^i = sqrt( 2 * CAPE^i )                                                          (17a)
CAPE^i = sum_{n=ICB}^{i} R_d * ( T_vp^n - T_v^n ) * Delta ln p                     (17b)
```
and, from Section 3c, the fractional areas are updated as
```
delta M^i  = rho^i sigma^i delta w^i                                              (18)
delta sigma^i = alpha^i * delta w^i                                               (19)
delta sigma^i = alpha^i * delta w^i + beta      if w^i > 0
delta sigma^i = alpha^i * delta w^i - beta      if w^i = 0                        (20)
```
> "with beta very small. In summary, the mass fluxes are calculated according to **M^i = rho^i sigma^i w^i**,
> with w^i given by (17) and sigma^i adjusted according to (20). At the initial time, and if there has been no
> convection for ten time steps, the sigma^i are set to zero."
> "If alpha^i is large, one can achieve large changes in the mass flux without appreciable changes in the
> sounding's CAPE^i. I choose alpha^i to give relatively smooth evolution of the mass flux; tests show that the
> equilibrium profiles are insensitive to alpha^i within a reasonable range."

Note the paper's own finding that the `sigma` specification is **secondary**:
> "One can argue convincingly that the determination of the sigma's is a secondary issue for quasi-equilibrium
> convection; they become more of an issue for 'stored-energy' convection of the kind experienced over
> continents under some conditions."

#### B3.2 Precipitation efficiency

```
l_c^i = (1 - epsilon^i) * l_a^i                                                   (1)
```
> "Suppose air is lifted without mixing from the subcloud layer to an arbitrary level i between cloud base and
> cloud top (ICB < i <= INB). We now allow a specified fraction, **epsilon^i, of the condensed water to be
> converted into precipitation**, leaving an amount of cloud water l_c^i given by [Eq. 1], where l_c^i is the
> cloud water mixing ratio at level i and l_a^i is the adiabatic water content at that level."

And, in the episodic-mixing tree, on further ascent:
```
l_new^{ij} = l^{ij} * (1 - epsilon^j)
```
> "The main closure parameters in this scheme are the parcel precipitation efficiencies, epsilon_i, which
> determine the fraction of condensed water in a parcel lifted to level i that is converted to precipitation"
> (abstract).

**⚠ The paper does not give a *formula* for `epsilon^i`** — it is a *prescribed* profile of closure
parameters ("With the specification of the precipitation efficiencies, epsilon^i, the fraction of precipitation
falling through the unsaturated environment, sigma_s^i, and the fractional area covered by precipitating
downdrafts, sigma_d, the tendencies of temperature and water vapor depend only on the fluxes M^i"). Whether the
1991 paper tabulates `epsilon^i` values I did not check — **the numeric values are `[UNVERIFIED]`.**

#### B3.3 The episodic-mixing / buoyancy-sorting machinery (Section 3a)

```
theta_lm^{ij} = sigma^{ij} * theta_l^{ij} + (1 - sigma^{ij}) * theta_lp^{ij}      (2)
theta_lp^{ij} = theta_p * exp[ (epsilon^i * L_v * l_a^i) / (C_p * T^i) ]          (3)
theta_lp^{ij} = theta_lp^i * exp[ (epsilon^j * L_v * l_p^{ij})/(C_p T^j) ]  , j > i
theta_lp^{ij} = theta_lp^i                                                  , j <= i   (4)
theta_l^{ij}  = theta^i * exp[ (epsilon^j * L_v * l^{ij}) / (C_p * T^j) ]         (5)
sigma^{ij} = ( theta^j - theta_lp^{ij} ) / ( theta_l^{ij} - theta_lp^{ij} )       (6)
MENT^{ij} = M^i ( |sigma^{ij+1} - sigma^{ij}| + |sigma^{ij} - sigma^{ij-1}| )
            / ( (1 - sigma^{ij}) * sum_{j=ICB-1}^{INB} [ |sigma^{ij+1}-sigma^{ij}| + |sigma^{ij}-sigma^{ij-1}| ] )   (7)
QENT^{ij} = sigma^{ij} r^i + (1 - sigma^{ij}) ( r^i - epsilon^i l_a^i )           (8)
```
> "Lacking information to the contrary, I assume an **equal probability distribution** of the mixing fraction,
> sigma^i, following Raymond and Blyth (1986). Each mixture then ascends or descends to its new level of
> neutral buoyancy."
> "it will be apparent in actual practice that little if any cloud water remains in mixed parcels."

#### B3.4 Precipitation and the unsaturated downdraft (Section 3b)

```
d/dp ( omega_T * l_p * sigma_d )^i = (g/Delta p) epsilon^i l_a^i M^i
    + (g/Delta p) epsilon^i * sum_{j=1}^{i-1} [ QENT^{ji} - r^{*i} ] MENT^{ji}
    - sigma_d * sigma_s^i * E^i                                                    (9)
P = ( g^{-1} * omega_T * l_p * sigma_d )^{i=0}                                    (10)
omega_T^i = 73.45 * (p^i/T_v^i)^{0.6346} * (l_p^i)^{0.1346}                       (11a)
E^i = (T_v^i/p^i)^{0.475} (1 - r_p^{*i}/r^{*i}) C (l_p^i)^{0.525}
      / ( 1.23e4 + 5.83e4/(p^i r^{*i}) )                                          (11b)
C = 1.6 + 24.55 * ( p^i l_p^i / T_v^i )^{0.2046}                                  (11c)
```
> "In deriving (9) I have neglected the vertical advection of precipitation in comparison to its fall velocity
> and have assumed that the downdraft is steady on the time scale of the large-scale flow."

**The cheap replacements the author himself offers — directly relevant to a coarse model:**
```
omega_T^i = 0.45 mb s^-1                                                          (12a)
E^i = ( 1 - (r_p^*)^i / r^{*i} ) * sqrt(l_p^i) / ( 2e3 + 1e4/(p^i r^{*i}) )       (12b)
```
> "in view of the potential use of this scheme in climate models, **where integration time is a serious
> concern**, (11) is replaced by the following approximations [12] … There was little difference noticed
> between integrations using (12) and those using (11)."

Downdraft thermodynamics:
```
(dM_p/dp)^i = -( rho^i sigma_d^2 / theta^i ) ( theta_p^i - theta^i )             (13a)
M_p^i (g/theta^i) (d theta_p/dp)^i = -( sigma_d sigma_s^i L_v E^i )/(C_p T^i) + E_theta   (13b)
E_theta = (g/theta^i)(theta^i - theta_p^i) dM_p/dp   if dM_p/dp > 0 ;  0 otherwise          (13c)
g M_p^i (d r_p/dp)^i = sigma_s^i sigma_d E^i + E_r                                (14a)
E_r = g (r^i - r_p^i) dM_p/dp   if dM_p/dp > 0 ;  0 otherwise                              (14b)
M_p^i = sigma_d sigma_s^i L_v E^i theta^i / ( C_p g T^i (-d theta^i/dp) )         (15)
| (M_p^{i+1})^2 - (M_p^i)^2 | < 0.1 rho^i sigma_d^2 (theta^i - theta^{i-1})/theta^i * Delta p   (16)
```
> "Once again, **the time involved in the numerical solution of (13) is a matter of concern**. To save time, we
> first approximate the solution by its **hydrostatic equivalent** [15], and then check a posteriori if the
> hydrostatic equation is violated … If (16) is violated, then Eqs. (13a–c) are solved simultaneously."

Convective heating (Section 3d):
```
(d theta/dt)_sd = (g/theta) (d/dp)( M (theta_c - theta) ) - g M (d theta_c/dp) - (L_v theta / C_p T) E_d   (21)
(d theta/dt)_sd = -g M (d theta/dp) - [ (L_v theta/C_p T) E_d - (g/theta)(theta_c - theta)(dM/dp) ]        (22)
```
> "In this form, the warming can be interpreted as due to forced subsidence in the environment (first term on
> the right), evaporation of detrained condensate, and detrainment of cloudy air with a temperature different
> from the environment."

#### B3.5 Prescribed / solved / grid / cost / classification

* **PRESCRIBED:** `epsilon^i` (parcel precipitation efficiencies), `sigma_s^i` (fraction of precipitation
  falling through the unsaturated environment; `sigma_s^i = 1` forced for `i < ICB`), `sigma_d` (fractional
  area of precipitating downdrafts), `alpha^i` and `beta` in the `sigma` update, `ICB`/`INB` from the
  sounding, and the fall-speed / evaporation law constants.
* **SOLVED:** `M^i` (updraft mass flux), `MENT^{ij}`, `QENT^{ij}`, `sigma^{ij}`, `M_p^i` and the downdraft
  `theta_p`, `r_p`; then the convective tendencies and `P`.
* **Grid/resolution:** **column-local**, on the model's discrete vertical levels. The paper states it assumes
  "the atmosphere as consisting of a finite number of discrete layers … anticipating that most implementations
  of a convective representation will be in the context of models whose vertical structure is phrased in
  finite-difference equations." The number of levels is the host model's.
* **Computational cost — the paper is unusually explicit, and this is the key transferable point:**
  * "in view of the potential use of this scheme in climate models, **where integration time is a serious
    concern**" → they substitute Eqs. (12) for (11);
  * "the **time involved in the numerical solution of (13) is a matter of concern**. To save time, we first
    approximate the solution by its hydrostatic equivalent [15]";
  * they explicitly avoid solving (9), (11), (13), (14) simultaneously — a provisional `(r_p^*)^i` is used
    instead.
  The abstract summarises: "One-dimensional radiative-convective equilibrium experiment with this scheme
  produce reasonable profiles of buoyancy and relative humidity." Cheaper than a full Arakawa-Schubert
  plume-spectrum solve, but still far heavier than Betts-Miller.
* **Classification: (b) requires time-stepping.** Eqs. (18)–(20) are explicit *time increments*, and the closure
  is "adjusted **with time** towards quasi-equilibrium". There is no algebraic steady-state closure in the 1991
  paper.
* **What a coarse steady 2-D model could copy:** the **CAPE-based mass-flux ansatz**
  `M = rho * sigma * sqrt(2 CAPE)` with a *prescribed, constant* `sigma`, and the cheap fall-speed /
  evaporation laws (12a,b). The paper itself reports (Section 3c) that with fixed `sigma` the equilibrium
  profiles are essentially unchanged whether `sigma` is large or small — so in a steady model you can
  plausibly *fix* `sigma` and treat `M` as diagnostic in CAPE. **That last sentence is the paper's own
  finding for the time-averaged state** ("the time-averaged M^i are very nearly identical in cases 1 and 2"),
  which is exactly the licence a steady model needs. Recommended over Betts-Miller only if you need buoyancy
  sorting or downdrafts; otherwise B1 is much cheaper.

---

### B4. Neelin & Held (1987) — MSE-budget convergence closure `[SECONDARY]`

**Citation (verified).** Neelin, J. D., and I. M. Held, 1987: *Modeling Tropical Convergence Based on the
Moist Static Energy Budget.* Mon. Wea. Rev., **115**(1), 3–12,
doi:10.1175/1520-0493(1987)115<0003:MTCBOT>2.0.CO;2.

**Full text: NOT RETRIEVED.** Unpaywall: `closed`. Wiley and AMS blocked; the Wayback captures of the AMS
article page contain the abstract only (the abstract text itself is truncated mid-word in the capture at
"A vertically i…"). Semantic Scholar: no OA PDF.

**What IS verified about the paper:**
1. Its own abstract (from the retrieved Wayback capture of the AMS article page) begins:
   > "The vertically integrated moist static energy equation provides a convenient starting point for the
   > construction of simple models of the time-mean low level convergence in the tropics. A vertically i[…truncated]"
   This confirms the MSE-budget formulation and that the target is a **time-mean (steady) low-level
   convergence** model — i.e. exactly the kind of closure a steady 2-D model wants.
2. Its use in the moist-EBM lineage, from Siler et al. (2018) `[RETRIEVED]`:
   > "g(x) is the **gross moist stability** of the atmosphere, which represents the flow-weighted difference in
   > moist static energy between the upper and lower branches at each latitude (**Neelin and Held 1987**)."
   So the *gross moist stability* concept that A1's Eq. (8) uses comes from this paper.

**A retrieved source that reproduces the NH87 budget** `[SECONDARY]`:
Rao, P. et al., *Climate of the Past Discussions* preprint `cp-2018-108`
(`https://cp.copernicus.org/preprints/cp-2018-108/cp-2018-108-AR2.pdf`, retrieved). Its Section 2 says:
> "In this section, we have discussed this simple model in detail. The Eqs. 1 and 2 correspond to the
> conservation of MSE and moisture in a vertical column of the atmosphere… Further details on the derivation of
> Eq. 1 can be found in Neelin and Held (1987). **The time derivatives have been dropped in these equations
> because the climate is assumed to be in a steady state.** The angle brackets (< >) indicate vertical integral."

```
< grad . (m U) > + < d(m omega)/dp > = Q_div                              (1)
< grad . (q U) > + < d(q omega)/dp > = E - P                              (2)
<A> = -(1/g) * integral_{Pb}^{Pt} A dp                                   (3)
Q_div = LHF + SHF + Q_rad                                                (5)
P - E = Q_div / GMS                                                      (6)
GMS = ( m_1 - m_2 ) / ( L_v ( q_2 - q_1 ) )                              (7)
m_1 = integral_{Pm}^{Pt} m grad.U dp/g  /  integral_{Pm}^{Pt} grad.U dp/g    (8)
m_2 = integral_{Pb}^{Pm} m grad.U dp/g  /  integral_{Pb}^{Pm} grad.U dp/g    (9)
with  m = c_p T + g Z + L_v q
```
> "Where, **GMS is the gross moist stability, as obtained by taking the ratio of the Eqs. 2.11 and 2.12 from
> (Neelin and Held, 1987)**. m_1 and m_2 are respectively, the total MSE in the upper (mid-troposphere to top)
> and lower troposphere (surface to mid-troposphere), normalized by the divergence of that layer. Thus, GMS is
> mainly a function of vertical profiles of MSE and it provides a measure of vertical stratification of the
> atmosphere… **This simple model attributes the changes in P-E to either the changes in total column energy or
> the vertical stability.**"

**⇒ The verified core of NH87's closure is: `P - E = Q_div / GMS`, with `GMS` defined by Eqs. (7)–(9).**
The MSE budget equation numbers cited in the preprint are NH87's own Eqs. (2.11) and (2.12) — I have their
*content* only indirectly.

**⚠ The "b coefficient" — `[UNVERIFIED]`.** I searched specifically for a "b" coefficient in the Neelin-Held
moisture-convergence closure and found **no retrievable source** reproducing one. I cannot confirm that such a
coefficient appears in Neelin & Held (1987), and I will not guess at its definition. Possibilities I could not
resolve: the brief may be thinking of a later Neelin-lineage paper, of the QTCM's parameters, or of the
`b`-coefficient used in some idealized "moisture-mode"/WISHE closures. **Flag this to the parent as an open
item** — it is the one item in the brief I could not nail down at all.

**Model character (from the verified statements):**
* **PRESCRIBED:** vertical profiles of MSE and moisture (or a sounding), the net column energy input `Q_div`,
  and the assumption of weak horizontal temperature/moisture gradients within the tropics.
* **SOLVED:** `P - E` (diagnostically), and by inversion the low-level convergence.
* **Grid/cost:** not a grid model — it is a **diagnostic closure** intended to be embedded in a larger model;
  the evaluation is a vertical integral, so the cost is that of a column integral.
* **Classification: (a) steady-state solvable** — explicitly so; the preprint states "the time derivatives have
  been dropped in these equations because the climate is assumed to be in a steady state". **This is the most
  directly reusable closure in Part B for a steady 2-D model**, since it needs no `tau` and no time stepping:
  given the steady circulation you solve, `P - E` follows from `Q_div/GMS`.

---

### B5. Frierson, Held & Zurita-Gotor (2006) — deliberately not covered

The brief says another agent covers it. Noting only that a copy already exists in this workspace
(`research/pdfs/frierson2006_gray_aquaplanet.pdf`) and that I did not analyse it, so as not to duplicate.

---

## PART C — SYNTHESIS FOR A STEADY-STATE 2-D (lon x lat) MODEL

### C1. Steady vs time-stepping, at a glance

| Model | Equation source | Steady-state solvable? | Needs time-stepping? | Where the time dependence lives |
|---|---|---|---|---|
| Siler/Roe/Armour 2018 MEBM (A1) | `[RETRIEVED]` Eqs. (1)–(13) | **Yes** | No | Nowhere — no `d/dt` term exists |
| Rose & Ferreira 2013 two-box (A3a) | `[RETRIEVED]` Eqs. (2)–(11) | **Yes** (analytic) | No (EBM part) | 2x2 linear algebra |
| Rose et al. 2014 GRL (A3b) | content `[UNVERIFIED]` | — | — | — |
| Flannery 1984 (A2) | `[UNVERIFIED]` | — | — | — |
| Budyko/Sellers dry EBM (A4) | `[SECONDARY]` (4.9),(4.10),(1)–(5) | **Yes** (nonlinear via `alpha(T)`) | No | `rho c h dT/dt` on the LHS; set to 0 |
| Betts/Betts-Miller adjustment (B1) | `[SECONDARY]` Eqs. (3)–(14) | **Yes, in the `dS/dt=0` limit** | As published, **yes** | `(R-S)/tau`; `tau` cancels at equilibrium |
| Manabe-Wetherald RH + conv. adj. (B2) | `[SECONDARY]` Eq. (2.29) | **Yes** (local, algebraic) | Only for the radiative-convective iteration | Relaxation to equilibrium; adjustment is diagnostic |
| Emanuel 1991 (B3) | `[RETRIEVED]` Eqs. (1)–(22) | Not algebraically | **Yes** | `delta sigma = alpha delta w +- beta`; "adjusted with time" |
| Neelin-Held 1987 (B4) | `[SECONDARY]` Eqs. (1)–(9) | **Yes** | No | Explicitly dropped ("climate assumed steady") |

### C2. A defensible minimal steady 2-D closure set (all traceable)

1. **Energy transport:** A1 Eqs. (1)–(3), with `h = c_p T + L q` — extended from `x` to `(x, lambda)`.
   *(The 2-D extension is my proposal, not a literature result.)*
2. **Moisture closure:** A1's "RH fixed at 80%, making h a single-valued function of T" — the cheapest
   defensible option; if you need a height-resolved humidity, B2 Eq. (2.29),
   `RH(p) = RH_s (p/p_s - 0.02)/0.98`.
3. **Convection, if you need a convective heating/moistening profile:** B1 Eqs. (5),(6),(11),(13),(14) in the
   `dS/dt = 0` limit — a purely diagnostic, `tau`-free closure with an analytic reference profile (B1 Eqs.
   7–8) and prescribed reference RH (B1: `(P_B,P_M,P_T) = (-25,-40,-20)` mb ↔ RH ≈ (90,70,50)%).
4. **Convergence/precipitation:** B4's `P - E = Q_div / GMS` with GMS from B4 Eqs. (7)–(9) — steady by
   construction.
5. **Boundary conditions:** A4 Eq. (4.10) / Stocker — vanishing meridional flux at the poles, i.e.
   `dT/dphi = 0` at `phi = +-pi/2`. A1's operator `(1-x^2)` enforces the same thing automatically.
6. **Ocean:** prescribed, exactly as in A3a ("OHT … prescribed through a simple analytical formula
   representing export out of the tropics and poleward convergence") and A1's `G'(x)` term in Eq. (12).

### C3. Honest gaps

* **Biggest gap: Flannery (1984)** (A2). It is the earliest "MSE EBM" reference and it is the one paper the
  brief asks to distinguish thermal vs latent transport in. It is bronze-OA and therefore *free* — it just
  needs a browser that can clear Cloudflare. **Zero equations reported.**
* **Manabe & Wetherald (1967)** itself (B2): the RH profile is double-sourced and solid; the surface RH value
  (77% vs 80%) and the exact convective-adjustment algorithm are not.
* **The "b coefficient"** in the Neelin-Held closure (B4): no source found at all.
* **Rose et al. (2014)** equations (A3b): not retrieved.
* **Any 2-D (lon x lat) EBM with moisture** (A5): only the EMIC *category* is verified; no equations.
* **Siler et al.'s grid/resolution and cost** (A1): not stated in the paper.
* **Numerical values of `epsilon^i`** in Emanuel (1991) (B3): not checked.
* **Budyko (1969)/Sellers (1969) original constants** (A4): not retrieved.

---

## 7. Files written (for verification)

All under `K:\moder\EyeOfHarmonyBuffer\research\`:

**This report**
* `pdfs/_report_ebm_convection.md`

**Raw extracted text of sources relied on**
* `pdfs/siler2018_body.txt` — Siler et al. (2018) full article body with equation placeholders (retrieved)
* `pdfs/siler2018_wb.txt` — Siler et al. (2018) full text, Wayback extraction
* `pdfs/siler2018_raw.html` — the archived AMS HTML (shows the `<div class="formula">` GIF markup)
* `pdfs/siler2018_equations_ocr.txt` — **OCR of Siler's Eqs. (1)–(13)** + parameter quotes
* `pdfs/rose_ferreira2013.txt` / `.pdf` — Rose & Ferreira (2013) full paper (MIT DSpace)
* `pdfs/emanuel1991_ocr.txt` — **OCR of Emanuel (1991) pages 2313–2320**
* `pdfs/emanuel1991jas.pdf` — the scanned original (author's website)
* `pdfs/emanuel_zivkovic1999.txt` — Emanuel & Zivkovic-Rothman (1999), downloaded, *not* analysed
* `pdfs/betts_miller2004.txt`, `pdfs/betts_miller2004_mu.txt` — Betts (2004) raw text (Symbol font lost)
* `pdfs/betts_miller2004_ocr.txt` — **OCR of Betts (2004) slides 14–24, Eqs. (3)–(15)**
* `pdfs/utrecht_ch2.txt` — Utrecht *Atmospheric Dynamics* Ch. 2 (Manabe-Wetherald RH profile, Eq. 2.29)
* `pdfs/arxiv_0804_4134.txt` — von Paris et al. (2008), arXiv:0804.4134v2 (MW67 RH profile, Eq. 22)
* `pdfs/cp2018_108.txt` — *Clim. Past Discuss.* preprint reproducing the Neelin-Held budget
* `pdfs/stocker_icm.txt` — Stocker, *Introduction to Climate Modelling* (Budyko-Sellers Eqs. 4.9/4.10/2.35)
* `pdfs/ucl_ebm.txt` — UCL/ELIC EBM textbook page (Budyko/Sellers EBM, linear OLR)
* `pdfs/wrf_bmj_F.bin` — WRF `module_cu_bmj.F` source (Betts-Miller-Janjic implementation; *not* the 1986 scheme)
* `pdfs/neelinheld1987_wb.txt`, `pdfs/emanuel1991_wb.txt`, `pdfs/mw67_wb.txt` — abstract-only AMS captures

**Equation images used for OCR**
* `pdfs/img/jcli-d-18-0081.1-e1.gif … e17.gif, ea1.gif … ea3.gif` (Siler, from Wayback)
* `pdfs/img/siler_eq_1_6.png`, `pdfs/img/siler_eq_7_13.png` (stitched, 3x upscaled)
* `pdfs/img/bettshi_p0NN.png`, `pdfs/img/emanhi_p0NN.png`, `pdfs/img/rf2013_p014.png`

**Reusable helper scripts I added**
* `research/wbimg.py`, `research/wbimg2.py` — Wayback CDX / availability image+PDF downloader with backoff
* `research/pdfrender.py` — PyMuPDF page -> PNG at chosen dpi
* `research/pdftext_mu.py` — PyMuPDF text extraction (better font handling than pypdf)
* `research/stitch_eq.py`, `research/cropbox.py` — upscale/stitch/crop for OCR
* `research/crossref.py`, `research/oa_search.py`, `research/oa_land.py`, `research/unpaywall.py`,
  `research/s2.py`, `research/fatcat.py` — bibliographic + OA-location lookups
* `research/dl_url.py`, `research/dl_ref.py`, `research/html2txt.py` — generic downloaders

**Techniques that did NOT work (don't waste time re-trying)**
* `api.fatcat.wiki` — SSL EOF / unreachable from here.
* `scholar.archive.org` — returns a bot-verification page.
* `web.archive.org/web/<YEAR>id_/<url>` without a CDX timestamp — HTTP 403.
* `archive.org/wayback/available` for the AMS equation GIFs — returns "no capture" even though CDX has them;
  you must use CDX to get the timestamp.
* Direct fetch of the AMS `downloadpdf` endpoint — HTTP 200 with a **0-byte** body (silent Cloudflare block).
* Harvard `climate.fas.harvard.edu` PDF (Rose et al. 2014) — 403 even with a Referer.
* `eprints.soton.ac.uk` (Goodwin & Williams 2023) — 403 even with a Referer.
* `apps.dtic.mil` — SSL EOF.
* Archive.org **rate-limits hard**: after a burst of CDX calls every request returns HTTP 429 for several
  minutes. Pace CDX/availability calls at >= 15 s and implement backoff (see `wbimg.py`).
