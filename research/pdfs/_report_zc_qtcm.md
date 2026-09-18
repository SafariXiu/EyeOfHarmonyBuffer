# Literature report - Two intermediate-complexity tropical models
## (A) Zebiak & Cane (1987)  and  (B) Neelin & Zeng (2000) / QTCM1

Prepared for: steady-state 2-D (lon x lat) atmospheric circulation model scoping.
Working dir: K:/moder/EyeOfHarmonyBuffer    Raw sources saved under: research/pdfs/

---

## 0. PROVENANCE, METHOD, AND HONESTY NOTES

### 0.1 What was actually retrieved (primary sources)

| Tag | Source | How retrieved | Local file |
|---|---|---|---|
| ZC87 | Zebiak, S. E., and M. A. Cane, 1987: A Model El Nino-Southern Oscillation. Mon. Wea. Rev., 115, 2262-2278. | Columbia Univ. Academic Commons deposit (academiccommons.columbia.edu/doi/10.7916/D87W6NR5/download, file ZebiakCane1987.pdf, 1.2 MB), fetched through the Wayback Machine because the live host is Cloudflare-protected. FULL 17-page article scan incl. the Appendix. | research/pdfs/zc87_wb2.pdf ; text zc87_flat.txt / zc87_lines.txt |
| NZ2000 | Neelin, J. D., and N. Zeng, 2000: A Quasi-Equilibrium Tropical Circulation Model - Formulation. J. Atmos. Sci., 57, 1741-1766. | Author copy: www2.atmos.umd.edu/~zeng/papers/NZ.pdf (438 kB, 26 pp); identical file is the UCLA CSI copy .../csi/REF/pdfs/NZ00.pdf | research/pdfs/nz2000_formulation.pdf ; text nz2000_flat.txt |
| ZNC2000 | Zeng, N., J. D. Neelin, and C. Chou, 2000: A Quasi-Equilibrium Tropical Circulation Model - Implementation and Simulation. J. Atmos. Sci., 57, 1767-1796. | Author copy: www2.atmos.umd.edu/~zeng/papers/ZNC2000.pdf | research/pdfs/znc2000_implementation.pdf ; text znc2000_flat.txt |
| QTCM1-MAN | The Neelin-Zeng Quasi-Equilibrium Tropical Circulation Model (QTCM1), Version 2.3, J. D. Neelin, N. Zeng, C. Chou, J. Lin, H. Su, M. Munnich, K. Hales, J. Meyerson, UCLA, September 26, 2002, 86 pp (incl. Appendix A "Summary of Model Formulation"). | csdms.colorado.edu/csdms_wiki/images/Qtcm_manv2.3.pdf (596 kB) | research/pdfs/qtcm_manv23.pdf ; text qtcm_man_flat.txt |
| ZC-CODE | Third-party Fortran copy of the Zebiak-Cane coupled model (axatmos.f, zc_model.com), GitHub repo grrrizzzz/numerical_modeling | GitHub raw | research/pdfs/zc_axatmos_f.txt, zc_model_com.txt |

### 0.2 Method notes - read this before trusting any equation below

* ZC87 is a SCAN. Its embedded OCR text layer is poor. Every ZC87 equation quoted below was re-read from rendered page images (PyMuPDF rasterisation of the actual page, then a vision-model transcription) and cross-checked against the embedded OCR text layer. Where the two agree, confidence is high; where they disagree I say so. The rendered page images are kept in research/pdfs/img/zc87_wb2_p0NN_*.png so any claim can be re-verified.
* NZ2000 / ZNC2000 / QTCM1-MAN are digital PDFs. Prose extracts cleanly. Their MATH fonts do NOT extract cleanly (glyphs are remapped: '=' extracts as '5', minus as '2', partial as ']', nabla as '=', tau as 't', epsilon as 'e', zeta as 'z', and so on). Therefore every NZ2000/QTCM1 equation quoted below was also read from rendered page images and is given in plain-text notation, cross-checked against the raw extracted text.
* Notation used below: partial_t = time derivative; subscripts with '_'; superscripts with '^'; a hat (vertical average) written hat{X}; an overbar (mean/basic state) written Xbar; INT_a^b = definite integral; SUM = summation; div, grad, curl, laplacian spelled out.
* Labels: [VERIFIED-PRIMARY] = read directly from the retrieved paper/manual. SECONDARY = read from another retrieved document. UNVERIFIED = could not be confirmed from a source I actually retrieved.
* I did NOT retrieve Zebiak (1986), Mon. Wea. Rev., 114, 1263-1271 (the source paper for ZC87's atmospheric heating): full text was not obtainable through the channels available to me. Anything attributed to Zebiak (1986) below is attributed VIA ZC87's own Appendix, which states it is taken from Zebiak (1986).

### 0.3 *** IMPORTANT CITATION CORRECTION ***

The citation given in the task for model (B) is WRONG. The task asked for:

> Neelin & Zeng (2000), J. Atmos. Sci., 57, 2955-2972, doi:10.1175/1520-0469(2000)057<2955:ANQTC>2.0.CO;2

Verified facts:

* api.crossref.org/works/10.1175/1520-0469(2000)057<2955:ANQTC>2.0.CO;2  ->  HTTP 404 Not Found. (Checked directly.)
* api.openalex.org/works/doi:...2955:ANQTC...  ->  HTTP 404 Not Found. (Checked directly.)
* The real paper is: Neelin, J. D., and N. Zeng, 2000: A Quasi-Equilibrium Tropical Circulation Model - Formulation. J. Atmos. Sci., 57, 1741-1766. doi:10.1175/1520-0469(2000)057<1741:AQETCM>2.0.CO;2
  Crossref returns exactly this title, pages 1741-1766, volume 57, authors J. David Neelin and Ning Zeng. The retrieved PDF's own running head on its first page reads: 1 JUNE 2000   1741   NEELIN AND ZENG.
* Likewise the companion paper is Zeng, Neelin & Chou (2000), J. Atmos. Sci., 57, 1767-1796, doi:10.1175/1520-0469(2000)057<1767:AQETCM>2.0.CO;2 - NOT 2915-2934 as the task states. (Retrieved PDF p.1 running head: 1 JUNE 2000   1767   ZENG ET AL.; OpenAlex and Crossref agree.)
* The UCLA group's own QTCM page (atmos.ucla.edu/csi/qtcm/) lists both papers with pages 1741-1766 and 1767-1796 respectively.

So: treat any bibliography entry with pages 2955-2972 or 2915-2934 for these titles as WRONG. What DOES occupy JAS vol. 57 pp. 2955-2972 is UNVERIFIED - I did not check it.

---

# PART A - ZEBIAK & CANE (1987)

## A.1 Bibliographic verification [VERIFIED-PRIMARY]

* Title page of the retrieved PDF p.2262: "A Model El Nino-Southern Oscillation*", STEPHEN E. ZEBIAK AND MARK A. CANE, Lamont-Doherty Geological Observatory. Running head: MONTHLY WEATHER REVIEW  VOLUME 115. Last text page p.2278. => Mon. Wea. Rev., 115, 2262-2278 (October 1987), confirmed from the artifact itself.
* DOI 10.1175/1520-0493(1987)115<2262:AMENO>2.0.CO;2 confirmed via OpenAlex and Unpaywall.
* Unpaywall reports the ONLY OA location is the AMS publisher PDF (journals.ametsoc.org/.../1520-0493_1987_115_2262_ameno_2_0_co_2.pdf); the copy I used is the Columbia Academic Commons deposit.
* Abstract (verbatim, from the JAXA repository record repository.exst.jaxa.jp/dspace/handle/a-is/373425, which reproduces ZC87's abstract and lists authors Cane, Mark A.; Zebiak, Stephen E., Lamont-Doherty Geological Observatory, issued 1987-10-01, report no. 88A20533): "A coupled atmosphere-ocean model is developed and used to study the ENSO (El Nino/Southern Oscillation) phenomenon. With no anomalous external forcing, the coupled model reproduces certain key features of the observed phenomenon, including the recurrence of warm events at irregular intervals with a preference for three to four years. ..."

## A.2 Architecture in one paragraph [VERIFIED-PRIMARY]

ZC87 is a NONLINEAR ANOMALY MODEL: both components describe perturbations about an observed monthly climatology (Climate Analysis Center dataset, per Rasmusson & Carpenter 1982). Section 2a (p.2263), verbatim: "The dynamics follow Gill (1980), i.e., steady-state, linear shallow-water equations on an equatorial beta plane. Linear dissipation in the form of Rayleigh friction and Newtonian cooling is used. The circulation is forced by a heating anomaly that depends partly on local heating associated with SST anomalies and partly on the low-level moisture convergence (parameterized in terms of the surface wind convergence)."

## A.3 ATMOSPHERIC COMPONENT - EXACT EQUATIONS

Source location: ZC87 APPENDIX, "Governing Equations of the Coupled Model", p.2277. The Appendix opens, verbatim: "The governing equations for the atmosphere (at iteration n) are as follows (see Zebiak, 1986):" [VERIFIED-PRIMARY]

Transcribed from the rendered page image (the embedded OCR layer reads essentially the same; only the overbar on the exponent of (A3a) and the accents on Q were clearer in the image):

    (A1)   epsilon * u_a^n  - beta_0 * y * v_a^n = -(p^n / rho_0)_x
    (A2)   epsilon * v_a^n  + beta_0 * y * u_a^n = -(p^n / rho_0)_y
    (A3)   epsilon * (p^n / rho_0) + c_a^2 * [ (u_a^n)_x + (v_a^n)_y ] = - Q_s - Q_I^(n-1)
    (A3a)  Q_s   = (alpha * T) * exp[ (Tbar - 30 degC) / 16.7 degC ]
    (A3b)  Q_I^n = beta * [ M(cbar + c^n) - M(cbar) ]
    (A3c)  M(x)  = 0,  if x <= 0
                  = x,  if x >  0
    (A3d)  c^n  == -(u_a^n)_x - (v_a^n)_y

Followed verbatim by (p.2277): "In (A3a), Tbar(x, y, t) is the prescribed monthly mean SST, and T is the anomalous SST. In (A3b), cbar(x, y, t) is the prescribed monthly mean surface wind convergence, and c^n is the anomalous convergence at iteration n, defined by [A3d]." [VERIFIED-PRIMARY]

### What this means structurally (reconstruction - my reading, cross-checked against the section 2a prose)

* (A1)-(A3) are the LINEAR, STEADY (no time derivative), REDUCED-GRAVITY SHALLOW-WATER EQUATIONS ON AN EQUATORIAL BETA PLANE, for the ANOMALY fields u_a^n, v_a^n, p^n/rho_0, with beta_0 the equatorial beta parameter. The dependent variable is p/rho_0 (pressure perturbation divided by density, i.e. a geopotential-like quantity), not a layer thickness as in a literal Gill (1980) 1.5-layer model - the two are equivalent here.
* RAYLEIGH DAMPING / NEWTONIAN COOLING: the SAME coefficient epsilon multiplies the two momentum equations and the mass/continuity equation. ZC87 calls this "Rayleigh friction and Newtonian cooling" (section 2a, p.2263). Numerically epsilon = (2 days)^-1 (p.2278).
* The system is solved as a STEADY problem, ITERATED. n is an iteration index, not a time index. Q_I is evaluated at the PREVIOUS iteration, n-1 - the convergence-feedback loop. The prescribed monthly-mean convergence cbar enters the nonlinear (rectified) M( ) function, so the moisture heating switches on only where the TOTAL (mean + anomaly) flow converges. Section 2a: "The convergence feedback is incorporated into the model using an iterative procedure in which the heating at each iteration depends on the convergence field from the previous iteration. ... The feedback is nonlinear because the moisture-related heating is operative only when the total wind field is convergent, and this depends not only on the calculated convergence anomaly, but also on the specified mean convergence [see Eq. (A3)]."
* c_a = 60 m/s is the atmospheric reduced-gravity wave speed (p.2278).
* Dimensional check (my own): Q_s = alpha*T*exp(...) with alpha = 0.031 m^2 s^-3 (degC)^-1 and T in degC gives m^2 s^-3, matching c_a^2 * div(v) = (m^2 s^-2)(s^-1). Consistent. [reconstruction]

### THE SST-TO-HEATING RELATION - the "alpha coefficient" the task asked about [VERIFIED-PRIMARY]

From (A3a), p.2277:

    Q_s = (alpha * T) * exp[ (Tbar - 30 degC) / 16.7 degC ]

with, verbatim from the same page: "Tbar(x, y, t) is the prescribed monthly mean SST, and T is the anomalous SST."

* alpha = 0.031 m^2 s^-3 (degC)^-1 (p.2278 parameter list, printed as: alpha = 0.031 m2 S-3 /degC). [VERIFIED-PRIMARY]
* Reading of the two independent OCR passes: the PREFACTOR is the ANOMALOUS SST T (both the vision transcription of the page image and the embedded OCR layer show the same glyph in the prefactor, and the accompanying sentence on the same page assigns T = anomalous SST, Tbar = mean SST); the EXPONENTIAL ARGUMENT uses the prescribed monthly mean SST Tbar, with an e-folding scale of 16.7 degC and a reference of 30 degC. That structure is what makes the atmosphere more responsive where the mean SST is already high (warm-pool / ITCZ focusing). High confidence, small residual risk from the scan.
* This relation is not original to ZC87: the Appendix explicitly says "(see Zebiak, 1986)". Zebiak (1986), Mon. Wea. Rev., 114, 1263-1271 is listed in ZC87's reference list (p.2278) as: "--, 1986: Atmospheric convergence feedback in a simple model for El Nino. Mon. Wea. Rev., 114, 1263-1271." I did NOT retrieve Zebiak (1986) itself - its full text is UNVERIFIED here. No other form of the heating (e.g. a different exponential constant) should be attributed to it on my authority.
* NO OTHER SST-heating expression and no second "alpha" appears in ZC87. The second heating coefficient is beta = 1.6 x 10^4 m^2 s^-2 in (A3b) (p.2278: beta = 1.6 x 10^4 m2 S-2). [VERIFIED-PRIMARY]

## A.4 OCEAN COMPONENT - EXACT EQUATIONS

Source: ZC87 Appendix, p.2277. Verbatim lead-in: "The governing equations for the ocean (see Zebiak, 1984) are" [VERIFIED-PRIMARY]

    (A4)  u_t - beta_0 * y * v = -g' * h_x + tau^(x)/(rho H) - r u
    (A5)  beta_0 * y * u     = -g' * h_y + tau^(y)/(rho H) - r v
    (A6)  h_t + H (u_x + v_y) = -r h
    (A7)  u = H^-1 ( H_1 u_1 + H_2 u_2 )
          [verbatim: "The subscripts 1 and 2 refer to the surface layer and underlying layer, respectively."]

    Shear between layers 1 and 2:
    (A8)  r_s u_s - beta_0 y v_s = tau^(x) / (rho H_1)
    (A9)  r_s v_s + beta_0 y u_s = tau^(y) / (rho H_1)          where  u_s == u_1 - u_2

    Entrainment velocity:
    (A10) w_s = H_1 [ (u_1)_x + (v_1)_y ]

Notes / caveats:

* (A4)-(A6) are the LINEAR REDUCED-GRAVITY (1.5-LAYER) SHALLOW-WATER SYSTEM for the thermocline displacement h, with g' the reduced gravity, H the mean layer thickness, r a Rayleigh damping (p.2278: r = (2.5 years)^-1), forced by wind stress tau. c = (g'H)^(1/2) = 2.9 m/s, H = 150 m (p.2278). ZC87 section 2b: "The dynamics of the model begin with the linear reduced-gravity model [Eqs. (A4)-(A7)]".
* (A5) AS PRINTED CARRIES NO v_t TERM - both of my independent readings of p.2277 agree on this. This is the LONG-WAVE (meridional-geostrophic) approximation near the equator. I state the equation as printed; the physical labelling as long-wave approximation is my inference, not a quotation. [reconstruction]
* (A8)-(A9) are the LINEAR FRICTIONAL SURFACE-LAYER (EKMAN) DYNAMICS for the 50 m surface layer. ZC87 section 2b, verbatim: "a shallow frictional layer of constant depth (50 m) is added to simulate the surface intensification of wind-driven currents in the real ocean. The dynamics of this layer are also kept linear, but only by using Rayleigh friction to stand in for nonlinear influences at the equator [Eqs. (A8)-(A9)]." r_s = (2 days)^-1, H_1 = 50 m (p.2278).

## A.5 SST EQUATION [VERIFIED-PRIMARY]

Source: ZC87 Appendix (A11)-(A13), p.2277, and main-text Eqs. (1)-(3), p.2264. Main text (p.2264) introduces it verbatim as: "Using the above formulations, the thermodynamic equation has the following form (where barred quantities represent mean fields and unbarred quantities represent anomalies):"

    (A11)  partial_t T = - u_1 . grad(Tbar + T) - ubar_1 . grad T
                        - { M(wbar_s + w_s) - M(wbar_s) } * Tbar_z
                        - M(wbar_s + w_s) * (T - T_e)/H_1
                        - alpha_s T

    (A12)  T_e  = gamma * T_sub + (1 - gamma) * T

    (A13)  T_sub =   T_1 { tanh[ b_1 (hbar + h) ] - tanh( b_1 hbar ) },   h > 0
                    T_2 { tanh[ b_2 (hbar - h) ] - tanh( b_2 hbar ) },   h < 0

with, verbatim from the page: "where ubar_1(x, y, t) and wbar_s(x, y, t) are the mean horizontal currents and upwelling, respectively, Tbar(x, y, t) is the prescribed mean SST, and Tbar_z(x) is the prescribed mean vertical temperature gradient. The entrainment temperature anomaly, T_e, is defined by [A12] ... where hbar(x) is the prescribed mean upper layer depth."

M(x) is the same ramp function as (A3c) - main text Eq. (2), p.2264: "This function accounts for the fact that surface temperature is affected by vertical advection only in the presence of upwelling."

Physical content of (A11): (i) advection of the total SST by anomalous currents; (ii) advection of the SST anomaly by mean currents; (iii) vertical advection by anomalous upwelling acting on the mean vertical temperature gradient; (iv) entrainment of subsurface water at temperature T_e; (v) a Newtonian surface-flux damping -alpha_s T toward the observed climatology. ZC87 section 2b: "The assumed surface heat flux anomaly is proportional to the local SST anomaly, acting always to adjust the temperature field toward its climatological mean state, which is specified from observations."

## A.6 GRID / RESOLUTION

UNVERIFIED for the atmosphere and the ocean - ZC87 does not state it. I searched the full 17-page text for grid, resolution, spacing, degrees, zonal grid, 5.625, etc. The only hit is on pp.2271-2272, in a SENSITIVITY EXPERIMENT about a 30% decrease in subsurface temperature and the "resolution of the ocean model near the eastern boundary" - i.e. about the empirical subsurface-temperature parameterisation, not about horizontal grid spacing. No horizontal grid dimensions, no number of points and no degree spacing is given anywhere in ZC87's text or appendix.

What IS stated (ZC87 section 2b, p.2263, verbatim): "The model ocean basin is rectangular and extends from 124 degE to 80 degW and from 29 degN to 29 degS." [VERIFIED-PRIMARY]

I did find a THIRD-PARTY Fortran copy of the ZC model (GitHub grrrizzzz/numerical_modeling, file "Zebiak-Cane ENSO Model/axatmos.f"). It contains COMMON/ZDATA/ arrays dimensioned (30,34,...), NMAX=80, MMAX=64, DELTAY=0.2, YT=7.9 and the loop "DO 21 I=6,25 ... Y=-FLOAT(I+25)*0.2+8.1", implying a 30 x 34 atmospheric grid with meridional step 0.2 in nondimensional units. THIS IS A THIRD-PARTY FILE OF UNKNOWN PROVENANCE; I could not verify it is the original LDEO code, and the dimensional latitude/longitude spacing is NOT stated in it. Treat any specific "5.625 deg x 2 deg" or "2 deg x 0.5 deg" claim about the ZC 1987 grids as UNVERIFIED unless you retrieve Zebiak (1986) or the LDEO code release directly. [UNVERIFIED]

## A.7 COUPLING PROCEDURE AND TIME STEP [VERIFIED-PRIMARY]

All from ZC87 section 2c "Coupling", pp.2264-2265, quoted verbatim:

* "The ocean component is forced by surface wind stress anomalies. A standard bulk formula is used to generate stress anomalies from the combination of surface wind anomalies produced by the atmosphere model and the background mean winds. THE OCEAN DYNAMICS TIME STEP IS 10 DAYS."
* "The atmosphere model is steady-state and was previously run with specified monthly mean SST anomalies to simulate monthly mean wind anomalies. In the present context, the wind field must be determined at 10-day increments."
* On the three options considered: "On one extreme, the model could be used exactly as before, calculating the steady response to the SST anomaly field at each time step. This implicitly assumes that the atmosphere adjusts very rapidly [O(2-3) days] to changes in boundary forcing and cannot be justified. On the other extreme, time dependence could be added explicitly to the model. This would be computationally costly, since a time step of order 2 h would be required for inertial gravity waves. Moreover, it is unnecessary because the important limiting time scale is the longer one associated with the equilibration of the heating field, i.e., the moisture convergence feedback process."
* THE CHOSEN SCHEME: "We adopt a third alternative: allowing time dependence only in the moisture convergence component of the heating. With this scheme, the change in heating is computed at each time step, and the assumed background convergence is the total convergence at the previous time step, rather than just the mean convergence (as in the steady-state model)."
* "In the model run to be presented in section 3, the recalculation was done once per month." (periodic re-solve of the full steady atmosphere from the current SST-anomaly field, to suppress the spurious small-scale anomalies that the time-marching heating can generate.)
* "In addition to the above, a maximum of three feedback iterations is performed at each time step."
* Summary (p.2265): "the calculation of the atmospheric heating has been split into two parts. The portion related directly to SST operates the same as in Z [Zebiak 1986] and gives a wind response in equilibrium with the SST field on a time scale of 10 days. The portion of the heating related to internal moisture convergence feedback operates in a time-stepping sense, and so forces a wind field adjustment on a somewhat longer time scale (of order 1 month or more)."

## A.8 COMPUTATIONAL COST

UNVERIFIED - ZC87 makes no computational-cost claim anywhere in the article (I searched the full text for computer, CPU, cost, Cray, minutes). The only adjacent statement is the negative one quoted above: explicit atmospheric time dependence "would be computationally costly, since a time step of order 2 h would be required for inertial gravity waves."

## A.9 STEADY-STATE SOLVABLE / TIME-STEPPING / MIXED

Answer: MIXED - category (c), with a clean split.

| Component | Class | Evidence |
|---|---|---|
| Atmosphere | STEADY-STATE / DIAGNOSTIC. Solved as a steady linear shallow-water problem, iterated only for the nonlinear convergence feedback. No time derivative anywhere in (A1)-(A3). | section 2a p.2263 ("steady-state, linear shallow-water equations"); section 2c p.2264 ("The atmosphere model is steady-state"); Appendix (A1)-(A3) contain no partial_t. |
| Ocean | PROGNOSTIC / requires time stepping, u, v, h from (A4)-(A6) with the shear solution (A8)-(A9); dt = 10 days. | section 2c p.2264; (A4)-(A6) carry u_t and h_t. |
| SST | PROGNOSTIC, (A11), stepped at 10 days. | sections 2b/2c. |
| Coupling | Atmosphere re-solved (steadily) once per month; the convergence-feedback part of the heating is marched at dt; max 3 feedback iterations per step. | section 2c pp.2264-2265. |

RELEVANCE TO A STEADY-STATE 2-D MODEL: the ZC atmospheric core (A1)-(A3) is EXACTLY the class of steady, linear, damped shallow-water system that can be solved directly for a given heating field - a linear elliptic problem in (u_a, v_a, p/rho_0) on the beta plane. The nonlinearity is entirely in the heating (Q_I via the ramp function M( )), and ZC87 handles it by fixed-point iteration, not by time integration. Note the horizontal domain in ZC87 is the whole tropical belt but the OCEAN basin is 124E-80W.

## A.10 PARAMETER VALUES [VERIFIED-PRIMARY - ZC87 p.2278, first paragraph, verbatim]

    "Parameter values used for the coupled simulation are as follows:
     epsilon = (2 days)^-1,        c_a = 60 m s^-1,      alpha = 0.031 m^2 s^-3 /degC,
     beta = 1.6 x 10^4 m^2 s^-2,
     r = (2.5 years)^-1,
     c = (g'H)^(1/2) = 2.9 m s^-1,   H = 150 m,
     H_1 = 50 m,
     r_s = (2 days)^-1,            alpha_s = (125 days)^-1,
     gamma = 0.75,                 T_1 = 28 degC,        T_2 = -40 degC,
     b_1 = (80 m)^-1,              b_2 = (33 m)^-1."

(epsilon is printed as a lunate epsilon; beta here is the convergence-feedback coefficient of (A3b).)

## A.11 Things I could NOT verify for ZC87

1. Horizontal grid of either component (see A.6). UNVERIFIED.
2. Computational cost / runtime. UNVERIFIED.
3. Any equation from Zebiak (1986) in its own words. UNVERIFIED - only as reproduced in ZC87's Appendix.
4. Whether the original atmospheric solve is spectral (FFT) or grid-point. The third-party axatmos.f uses FFTs in longitude (CALL FFT2C) and a convergence loop, but its provenance is uncertain. UNVERIFIED for the original.

---

# PART B - NEELIN & ZENG (2000) AND QTCM1

## B.1 Citations [VERIFIED-PRIMARY]

* NZ2000 = Neelin & Zeng, J. Atmos. Sci., 57, 1741-1766 (not 2955-2972 - see section 0.3). DOI 10.1175/1520-0469(2000)057<1741:AQETCM>2.0.CO;2.
* ZNC2000 = Zeng, Neelin & Chou, J. Atmos. Sci., 57, 1767-1796 (not 2915-2934 - see section 0.3). DOI 10.1175/1520-0469(2000)057<1767:AQETCM>2.0.CO;2.
* QTCM1-MAN = the QTCM1 v2.3 manual (Sept 26, 2002), 86 pp; its own bibliography entries [1] and [2] are exactly NZ2000 and ZNC2000.

## B.2 VERTICAL STRUCTURE - THE GALERKIN BASIS AND THE PROJECTION

### B.2.1 The expansion [VERIFIED-PRIMARY - NZ2000 section 3b, Eqs. (3.6)-(3.8), p.1747]

Verbatim from section 3b "Tailored basis functions":

    (3.6)   T = T_r(p) + SUM_{k=1..K} a_k(p) T_k(x, y, t)
    (3.7)   v = SUM_{k=0..L} V_k(p) v_k(x, y, t)
    (3.8)   q = q_r(p) + SUM_{k=1..K} b_k(p) q_k(x, y, t)

and, verbatim: "For the dynamically active part of the solution, horizontal gradients and time derivatives of T, q matter, so splitting off a portion of the solution that does not depend on space or time T_r(p), q_r(p) can improve accuracy. These will be specified as a reference state, which is not assumed to be a solution..."

So the three structure-function families are:

* a_k(p) - TEMPERATURE basis functions (projection of T),
* b_k(p) - MOISTURE basis functions (projection of q),
* V_k(p) - VELOCITY basis functions, with V_0(p) = BAROTROPIC and V_1(p) = BAROCLINIC;
* T_r(p), q_r(p) - prescribed REFERENCE PROFILES (independent of x, y, t).

For QTCM1: K = 1 for temperature and moisture (a_1, b_1) and L = 1 for velocity (V_0, V_1). The standard qtcmpar.f90 reproduced in the manual confirms this numerically: nvmod = 2  (no. v-modes); nTmod = 1  (no. T-modes).

### B.2.2 The baroclinic structure functions [VERIFIED-PRIMARY - NZ2000 Eqs. (3.2), (3.9)-(3.11), pp.1747-1748]

    (3.9)    a_1(p) = A_1(p, T_r^c)
    (3.10)   V_1(p) = a_1^+(p) - abar_1^+          [printed "V_1(p) = a_1^+ - abar_1^+"]
    (3.11)   V_0(p) = 1

with, from Eqs. (3.2)-(3.3) of the same paper:

    (3.2)   v(x, y, p, t) = V(p) v_T(x, y, t) ,
            V(p) = ( A_1^+(p) - Abar_1^+ ) ,
            A_1^+(p) = INT_p^{p_rs} A_1(p') d ln p'
    (3.3)   omega(x, y, p, t) = -V(p) div v_T(x, y, t) ,
            V(p) = -INT_p^{p_rs} V(p') dp'

and, verbatim (p.1747): "the vertical structure in A_1^+(p) simply comes from the hydrostatic equation, integrating QE temperature vertical structure to give baroclinic pressure gradients."

A_1(p) itself is defined by the convective-QE closure, NZ2000 Eqs. (2.18)-(2.20), pp.1745-1746:

    (2.18)  T^c = T_r^c(p) + A_1(p) T_1^c
    (2.20)  T^c = T_r^c(p) + SUM_{k=1..N} A_k(p) T_k^c

and, verbatim: "Here h'_b denotes departures of PBL moist static energy from this QE reference profile, and A_1(p) gives the vertical shape of the moist adiabat perturbation per h_b perturbation. Below the reference lifting condensation level, A_1(p) is a dry adiabat."

CAVEAT ON (2.18): the retrieved text for (2.18) extracts as "T c 5 1 ,ccT (p) A (p)Tr 11" - superscripts/subscripts are scrambled by the PDF's math font. The clean transcription above is my reassembly, corroborated by (2.20) and by the surrounding prose. Treat the FORM as reliable, not the literal glyph order. [reconstruction]

### B.2.3 The physical content of a_1(p) - how QTCM1 actually builds it [VERIFIED-PRIMARY - QTCM1-MAN section 3.1, p.53]

Verbatim: "For calculating a1, 900 hPa cloud base and 150 hPa cloud top are used. Below the cloud base, the atmosphere is dry adiabatic; above the cloud base, the atmosphere is moist adiabatic. Relative humidity profile is a linear fit to the observed reference relative humidity, which is 85% near surface and 60% at 150 hpa."

And, verbatim, on the model top and the velocity basis function: "Model top in the calculation of all basis functions was set at 200 mb in v2.2 and earlier. This was partly motivated by the values of V_1 that result above 200 mb from the contribution of T_1/p in the vertical integration of the hydrostatic equation and the impact of these in <V_1^2> and <V_1^3> coefficients. In v2.3, the velocity basis function V_1 is held constant above 280 mb and the model top can thus be extended to 150 mb, more typical of the tropical tropopause."

### B.2.4 p_R / pressure depth of the first baroclinic mode, and the T-to-geopotential coefficient

* REFERENCE PRESSURE DEPTH OF THE TROPOSPHERE - NZ2000, Eq. (2.16) region, p.1745, verbatim: "Likewise, p_T = p_rs - p_rt is a constant reference pressure depth of the troposphere."
  - NZ2000 Table 1 (p.1755) gives p_T = p_rs - p_rt ~ 8 x 10^4 Pa, listed under "Reference pressure depth of the troposphere". (The Table 1 row extracts with p_rt and p_rs in the opposite order from the body text; the body-text form above is unambiguous.)
  - QTCM1 v2.3 instead integrates from 1000 mb to 150 mb, i.e. p_T = 850 mb. Manual section 1.1.1, p.3, verbatim: "Two vertical components (one barotropic and one baroclinic) of the general circulation are represented. These components are calculated from 1000 mb to 150 mb." So p_T is a model-setup constant: NZ2000 quotes ~8 x 10^4 Pa, QTCM1 v2.3 uses 850 hPa. [VERIFIED-PRIMARY, both]
* REFERENCE TEMPERATURE: NZ2000 does not name a single "reference temperature" scalar; the reference state is the profile T_r(p). QTCM1's standard parameter file (manual section 1.4.1, pp.22-23) gives:
      Trefhat  = 267.77045 K   ! mean reference temperature [K]
      qrefhat  = 16.404453     ! mean reference humidity [K]
      Trefs    = 302.00000 K   ! surface reference temperature
      qrefs    = 51.955292     ! surface reference humidity [K]
      Tcrefs   = 302.00000 ;  qcrefs = 50.814919 ;  Tcrefhat = 268.98325 ;  qcrefhat = 16.159267
      Tsref    = 302.26001 K   ! surface reference temperature for radiation
  [VERIFIED-PRIMARY]
* COEFFICIENT RELATING T TO GEOPOTENTIAL - NZ2000 Eqs. (4.10)-(4.11), p.1751, verbatim:
      (4.10)  phi_s0 = phi_s - phi_s1
      (4.11)  phi_s1 = -kappa * ahat_1^+ * T_1
  i.e. the baroclinic surface-geopotential component is phi_s1 = -kappa * ahat_1^+ * T_1, with kappa = R/C_p (NZ2000 section 2a, p.1744: "The ratio kappa = R/C_p, where R is the gas constant for air, appears in the hydrostatic equation since T has absorbed C_p"). In the baroclinic momentum equation the pressure-gradient term is simply -kappa * grad(T_1) (NZ2000 (4.13)). The QTCM1 code value is a1phat = 0.24520899, annotated "NZ (2.16), (3.9)". [VERIFIED-PRIMARY]

### B.2.5 Structure-function numeric values used in the standard QTCM1 [VERIFIED-PRIMARY - QTCM1-MAN section 1.4.1, pp.22-23, verbatim reproduction of qtcmpar.f90; the "NZ (x.y)" tags are in the manual]

    nvmod = 2  ! no. v-modes ;  nTmod = 1  ! no. T-modes ;  nz = 10
    a1hat    = 0.45934841     ! hat{a_1}   [-]      NZ (3.2)
    a1phat   = 0.24520899     ! hat{a_1^+} [-]      NZ (2.16), (3.9)
    a1phatb  = 0.15463794E-01 ! average of a_1^+ over subcloud layer
    a1s      = 0.30203986     ! a_{1s}     [-]      NZ (5.16)
    V1s      = -0.24520899    ! V_{1s}     [-]      NZ (4.13)
    V1sqhat  = 0.39553840E-01 ! hat{V_1^2} [-]      NZ (4.13)
    b1hat    = 0.31574178     ! hat{b_1}   [-]      NZ (5.17)
    b1s      = 1.0000000      ! b_{1s}     [-]      NZ (4.32)
    bb1hat   = 0.37340307     ! hat{B_1}   [-]      NZ (4.26)

For comparison, NZ2000 Table 1 (p.1755) quotes "typical values": ahat_1, a_1s = 0.38, 0.24; bhat_1, b_1s = 0.45, 0.61; V_1s = -0.20. NZ2000 explicitly notes (p.1756): "The values involving b1 differ from those in ZNC since in that paper b1 is chosen separately from B1." SO DO NOT MIX the NZ2000 Table 1 values with the QTCM1 v2.3 qtcmpar.f90 values - they belong to different model configurations. [VERIFIED-PRIMARY]

## B.3 THE BAROCLINIC MOISTURE EQUATION AND THE QE MOISTURE CLOSURE

### B.3.1 The projected moisture equation [VERIFIED-PRIMARY - NZ2000 Eqs. (4.20)-(4.21), p.1752]

    (4.20)  partial_t qhat + Dtilde_q q - M_q1 * div(v_1) = <Q_q> + (g/p_T) E
    (4.21)  M_q1 == p_T^-1 * INT_{p_rt}^{p_rs}  Omega_q1 * partial_p q  dp

(Equation (4.21)'s prefactor is printed p_T^-1; the vision transcription rendered it p_s^-1, but p_T^-1 is required dimensionally and matches the parallel definitions (4.17)-(4.18). Flagged as a small transcription risk. [reconstruction])

Terminology, verbatim (p.1752): "The term Mq1 is the contribution to the gross moist stability by moisture convergence, termed the 'gross moisture stratification.'" And in section 5 (p.1754): "The static stability terms are those associated with upper-level divergence (low-level convergence) of the baroclinic wind component: the dry static stability MS1, the gross moisture stratification Mq1, and the gross moist stability M1. These give, respectively, the work to raise a large-scale air mass, the resulting moisture convergence, and the net stratification resulting from adiabatic cooling minus diabatic heating in convective zones:"

    (5.9)   M_1 = M_S1 - M_q1
    (4.35)  M_q1 = M_qr1 + M_qp1 * q_1 ,
            M_qr1 = p_T^-1 INT_{p_rt}^{p_rs} V_1 partial_p q_r dp ,
            M_qp1 = p_T^-1 INT_{p_rt}^{p_rs} V_1 partial_p b_1 dp

The dry static stability counterpart (NZ2000 Eqs. (4.16)-(4.19), p.1752), read from the page image:

    (4.16)  M_S1 = p_T^-1 INT_{p_rt}^{p_rs} Omega_1 (-partial_p s) dp
                 = M_Sr1 + M_Sp1 * T_1(x, y, t)
    (4.17)  M_Sr1 = p_T^-1 INT_{p_rt}^{p_rs} Omega_1(p) ( -partial_p s_r(p) ) dp
    (4.18)  M_Sp1 = p_T^-1 INT_{p_rt}^{p_rs} Omega_1(p) ( -partial_p s_1(p) ) dp
    (4.19)  partial_p s_1(p) = partial_p a_1 + kappa * a_1 / p

(Omega_1(p) is the vertical-velocity structure of the baroclinic mode, NZ2000 (4.1): omega_1(x,y,p,t) = -Omega_1(p) div v_1(x,y,t).)

### B.3.2 The quasi-equilibrium moisture closure [VERIFIED-PRIMARY - NZ2000 section 2b(2), Eqs. (2.21)-(2.23), p.1746]

Verbatim:

    (2.21)  Q_q = (q_c - q)/tau_c            [in regions where heating is nonzero per (2.17)]
    (2.22)  q_c = alpha_sub(p) * q_sat(T_c)
    (2.23)  q_c = q_r^c(p) + B_1(p) * T_1^c

with, verbatim: "where alpha_sub(p) is a subsaturation coefficient, which can vary in the vertical. For current purposes we need only a smooth adjustment toward a moisture profile that can be found for given temperature and moisture in the column. ... For simplicity, we use here a linear form of this dependence, giving [2.23], where B_1(p) is the vertical profile for deep convective QE moisture variations." And: "The nonlinear q_c temperature dependence in (2.22) proves important to quantitative aspects of midlatitude simulation, but the linearized form of it (2.23) is used here and in ZNC to demonstrate that it can be adequate for many purposes."

The companion TEMPERATURE closure (NZ2000 Eqs. (2.17)-(2.19), p.1745), verbatim:

    (2.17)  Q_c = c_c (T_c - T)/tau_c ,   if <T_c - T> > 0
                 = 0,  otherwise
    (2.18)  T_c = T_r^c(p) + A_1(p) T_1^c
    (2.19)  T_1^c = kappa_c * dh_b            [SEE CAVEAT]

CAVEAT ON (2.19): the raw extraction is "51 dhb.cTh 91 b", which is not a reliable transcription, so I will not state (2.19) as fact beyond the fact that it relates T_1^c to the boundary-layer moist static energy perturbation. What the prose does say, verbatim: "Here h'_b denotes departures of PBL moist static energy from this QE reference profile, and A_1(p) gives the vertical shape of the moist adiabat perturbation per h_b perturbation." [partially UNVERIFIED]

### B.3.3 THE "beta_q" QUESTION - UNVERIFIED

I FOUND NO COEFFICIENT NAMED beta_q ANYWHERE in NZ2000, in ZNC2000, or in the QTCM1 v2.3 manual. I searched all three full texts for beta, bq, betaq, and searched the web for QTCM "beta_q"; nothing matched. The QTCM quasi-equilibrium moisture closure uses these symbols instead:

* b_1(p) - the moisture basis function (NZ2000 (3.8)); standard value bhat_1 = 0.31574178, b_1s = 1.0 in QTCM1 v2.3, tagged "NZ (5.17)" / "NZ (4.32)".
* B_1(p) - the vertical profile for deep convective QE moisture variations (NZ2000 (2.23)); standard value bb1hat = hat{B_1} = 0.37340307, tagged "NZ (4.26)".
* alpha_sub(p) - the subsaturation coefficient (NZ2000 (2.22)).
* M_q1 - the gross moisture stratification (NZ2000 (4.21), (4.35)).
* epsilon_c - the convective adjustment rate (NZ2000 (4.15), (5.7)).

If your source's "beta_q" refers to something inside a LATER QTCM paper (e.g. the moisture-mode papers, J. Atmos. Sci. 2009) or to a different model, I cannot confirm it - treat it as UNVERIFIED and do not cite me for it.

### B.3.4 The QE closure as actually coded [VERIFIED-PRIMARY - NZ2000 section 5c, Eqs. (5.5)-(5.8), p.1754]

    (5.5)   -<Q_q> = <Q_c> = epsilon_c^* ( q_1 - T_1 )
    (5.7)   epsilon_c = (1/tau_c) * H(C_1)
    (5.8)   epsilon_c^* = [ ahat_1 * bhat_1 * (ahat_1 + bhat_1)^-1 ] * epsilon_c
    (4.28)  C_1 = ( Ahat_1 T_1^c - ahat_1 T_1 ) + ( That_r^c - That_r )
            "which in the simplest case is proportional to (q_1 - T_1)"

and, verbatim: "The quantity H(C1) = 0, if C1 < 0, = 1 if C1 > 0 is a Heaviside function that represents the dependence of convection on conditional instability in the column. The quantity C1 is a measure of CAPE for this model, projected on retained basis functions."

More general forms, NZ2000 Eqs. (4.25) and (4.27), pp.1752-1753 (from the rendered images):

    (4.25)  -<Q_q> = <Q_c> = epsilon_c [ Ahat_1 T_1^c - ahat_1 T_1 + (That_r^c - That_r) ]
    (4.27)  -<Q_q> = <Q_c>
              = epsilon_c * Ahat_1 (Ahat_1 + Bhat_1)^-1
                * [ bhat_1 q_1 - Bhat_1 (ahat_1/Ahat_1) T_1
                    + (Bhat_1/Ahat_1)(That_r^c - That_r) - (qhat_r^c - qhat_r) ]

## B.4 WHAT IS PROGNOSTIC AND WHAT IS DIAGNOSED

Source: NZ2000 section 5a "Main equations" (p.1754), quoted verbatim: "For the standard version of QTCM1, with choices discussed in section 4, including ahat_1 = Ahat_1, and the stress relation (4.33)-(4.34), the prognostic equations for barotropic wind component (4.12), baroclinic wind component (4.13), temperature (4.14), and moisture (4.20) become":

    (5.1)  partial_t v_1 + D_V1(v_0, v_1) + f k x v_1
             = -kappa * grad T_1 - epsilon_1 v_1 - epsilon_01 v_0
    (5.2)  partial_t zeta_0 + curl_z( D_V0(v_0, v_1) ) + beta v_0
             = -curl_z( epsilon_0 v_0 ) - curl_z( epsilon_10 v_1 )
    (5.3)  ahat_1 (partial_t + D_T1) T_1 + M_S1 div v_1
             = <Q_c> + (g/p_T)( -R_t^up - R_s^down + R_s^up + S_t - S_s + H )
    (5.4)  bhat_1 (partial_t + D_q1) q_1 - M_q1 div v_1 = <Q_q> + (g/p_T) E
    (5.6)  ahat_1 (partial_t + D_T1) T_1 + bhat_1 (partial_t + D_q1) q_1 + M_1 div v_1
             = (g/p_T) F^net        [sum of (5.3) and (5.4): column moist static energy equation]

### PROGNOSTIC

* T_1(x,y,t) - projected temperature (NZ2000 (5.3)).
* q_1(x,y,t) - projected moisture (NZ2000 (5.4)).
* v_1(x,y,t) - baroclinic velocity (NZ2000 (5.1)).
* zeta_0 (equivalently the barotropic streamfunction psi_0) - barotropic relative vorticity (NZ2000 (5.2)). NZ2000, verbatim: "Solution in terms of a streamfunction psi_0 (such that v_0 = k x grad psi_0, zeta_0 = laplacian psi_0) reduces prognostic variables by one."
* SURFACE TEMPERATURE T_s - prognostic (slab mixed-layer ocean; QTCM1-MAN section 3.4 "Slab Mixed-layer Ocean Model", p.58; over land it is part of the SLand scheme, manual section 3.2).
* CLOUD FRACTION - prognostic via a simple cloud-prediction scheme (QTCM1-MAN section 3.3.1).
* QTCM1-MAN section 2.2.3, verbatim: "T  Temperature (e.g. T1); u  Zonal velocity (e.g. u0, u1); v  Meridional velocity (e.g. v0, v1); vort0  Barotropic relative vorticity. THESE VARIABLES ARE ALL PROGNOSTIC."
* QTCM1-MAN section 1.1.1, verbatim: "The prognostic variable arrays are dimensioned as (nx,0:ny+1) to avoid..."

### DIAGNOSTIC

* THE BAROTROPIC DIVERGENCE div(v_0) IS SPECIFIED, NOT SOLVED. NZ2000 Eqs. (4.4)-(4.5): div v_0 = (omega_t - omega_s)/p_T in general, and div v_0 = 0 without topography. Verbatim (p.1751): "Because v_0 has a specified divergence (zero in cases without topography), it is determined by the vorticity equation." So the barotropic mode is prognostic in its ROTATIONAL part only; its divergent part is prescribed (zero, or set by topography / omega_t).
* VERTICAL VELOCITY omega(p) is DIAGNOSTIC, obtained from the retained velocities by continuity. NZ2000 Eqs. (4.1)-(4.3), verified from the rendered page image of p.1750:

      (4.1)  omega_1(x,y,p,t) = -Omega_1(p) * div v_1(x,y,t) ,
             Omega_1(p) = -INT_p^{p_s} ( a_1^+(p') - abar_1^+ ) dp'
      (4.2)  omega_0 = omega_t - Omega_0 div v_0 ,     Omega_0(p) = p - p_t
      (4.3)  omega = omega_0 + omega_1

  (The QTCM1 manual gives the equivalent omega_1(p) = INT_p^{p_s} V_1 dp', up to sign convention.)
* RESIDUAL COMPONENTS v_R and s_R of the vertical velocity and dry static energy: NZ2000 section 4c, verbatim: "v_R and s_R denote residual components of the solution for vertical velocity and dry static energy".
* SURFACE GEOPOTENTIAL phi_s0 - NZ2000 Appendix A, p.1762, verbatim: "Since phi_s0 is not needed in the solution using (4.12), it can be diagnosed postsolution using the divergence equation derived from (4.6)." The geopotential reconstruction is NZ2000 (A1): phi = phi_s0 + phi_s1 + (a_1^+(p) - ...) kappa T_1.
* dh_b (boundary-layer moist static energy adjustment) - verbatim: "dh_b need only be calculated as a diagnostic".
* PRECIPITATION AND CAPE - diagnosed postsolution: verbatim "CAPE and precipitation may be diagnosed after..." and "it can be diagnosed postsolution".

### Direct answer to "diagnosed from the column MSE or from the temperature equation?"

The baroclinic divergence div(v_1) - the quantity that drives omega and precipitation - is DIAGNOSED FROM THE THERMODYNAMIC EQUATIONS, AND SPECIFICALLY FROM THE COLUMN MOIST STATIC ENERGY EQUATION (5.6) in convective regions. NZ2000 section 5b, verbatim: "An alternate prognostic equation that is very useful in analyzing model dynamics is the moist static energy equation [the sum of (5.3) and (5.4)]. ... Although our coding uses separate T1 and q1 equations, it would be possible to run the model using (5.6) and either (5.3) or a CAPE equation formed from a suitably weighted difference of (5.3) and (5.4). Within convective regions, to the extent that QE ties q1 to T1, (5.6) contains all thermodynamic information necessary to solve for large scales." And section 7a: "In convective regions, for motions for which tau_c can be assumed small, the thermodynamics is largely governed by (5.6). Thus M_1 acts as the static stability for..."

THE BAROTROPIC MODE IS NOT DIAGNOSED - it is prognostic in vorticity, with only its divergence prescribed.

## B.5 GRID, RESOLUTION, TIME STEP, COST

### Grid [VERIFIED-PRIMARY - QTCM1-MAN section 1.1.3 p.9 and section 2.2.3 p.36]

Verbatim (section 1.1.3):

> "The standard version of the QTCM1 provides coverage from 78.75 deg S to 78.75 deg N in latitude and coverage over all longitudes. The grid has dimensions nx x ny equal to 64 x 42. The east/west boundaries are periodic, and the north/south boundaries are solid walls. Thus, the grid spacing is 5.625 [i.e. 360/nx] degrees longitude and 3.75 [i.e. 2 x 78.75/ny] degrees latitude."

Verbatim (section 2.2.3):

> "The grid-scale variables in the QTCM1 are located on a staggered C-grid [Arakawa & Lamb 1977] of dimensions NX x NY ... For the standard version, NX = 64 and NY = 42. ... Thus, temperature is located at grid point (i, j), while zonal velocity is located at (i + 1/2, j), meridional velocity is located at (i, j + 1/2)..."

Manual section 2.1, verbatim: "The QTCM1 prognostic equations are finite-differenced on an Arakawa C-grid. The barotropic component is solved in a vorticity/streamfunction formulation using an Adams-Bashforth scheme. The baroclinic component is similar to a shallow-water equation and is solved by applying a 'forward-backward' scheme using the updated variables instantly."

=> STANDARD QTCM1: 64 x 42, 5.625 deg lon x 3.75 deg lat, 78.75S-78.75N, periodic in longitude, solid walls at the meridional boundaries. That is already the resolution class a lon x lat model would use.

ZNC2000 DIFFERS. Verbatim (ZNC2000, section 2): "The model domain covers the whole Tropics in longitude and extends to 60 degN and 60 degS in latitude. A sponge boundary is applied outside 45 deg latitude that involves a relaxation toward..." - so the SIMULATION in the 2000 implementation paper used a 60N-60S domain with a sponge, not the later v2.3 78.75N-78.75S hard-wall configuration.

### Time step [VERIFIED-PRIMARY]

* Manual section 2.1, verbatim: "The numerical CFL instability criterion limits the model time-step at about 20 minutes, mostly due to the momentum advection associated with mid-latitude baroclinic waves."
* Manual section 1.3.4 parameter table, verbatim: "dt   Time step for the atmosphere (sec). Type REAL." and "mt0   Dimensionless multiplier to set barotropic mode time step. The time step (sec) for the barotropic mode equals dt*mt0. Default value is 1. Type INTEGER."
* The manual's sample namelist gives "! dt=1200. ! time step [seconds]" = 1200 s = 20 min.
* ZNC2000 ITSELF DOES NOT STATE A dt in the text I retrieved (searched; no match). [UNVERIFIED for ZNC2000 specifically]

### Computational cost [VERIFIED-PRIMARY - two independent statements]

* QTCM1-MAN section 1.1.2, p.3, verbatim: "The reduction of the vertical degrees of freedom considerably cuts computational time. The current version takes approximately 5 minutes of CPU time to run one year of model simulation on a Sun Ultra 80 workstation (1.5 minutes on a Pentium-4/Linux workstation). A GCM with only a few layers would have the same speed, but an advantage of the current approach is that it is more quantitative in and near deep convection zones."
* UCLA CSI web page (atmos.ucla.edu/csi/qtcm/), verbatim: "It is computationally light (5min on a Sun Ultra2 at 5.625x3.75 resolution and 1.5 minutes on a Pentium-4/Linux workstation) and easy to diagnose."
* NZ2000 abstract / section 8: "The model is computationally economical, since part of the solution has been carried out analytically"; "The model is computationally very economical, essentially because part of the solution has been carried out analytically before turning to the numerics." (no numbers).

=> about 1.5-5 minutes of CPU per simulated year at 5.625 deg x 3.75 deg. That is the cost figure to quote.

## B.6 IS QTCM1 STEADY-STATE SOLVABLE?

Answer: category (b) as released - it is a time-stepping model - BUT category (a) is explicitly stated to be well posed, and the authors tested steady solvers. NZ2000 section 7d, item (1), p.1760, VERBATIM (this is the direct answer):

> "1) The steady solution to these equations is well posed and can be a reasonable approximation to the full solution in the Tropics under some circumstances. The tropical solution is well posed as a steady solution because of the positive gross moist stability in convecting regions, as discussed in section 7a. The presence of positive CAPE in some regions leads to a well-defined mean convective heating in those regions, even without transient effects at the large scale. We have tested steady solvers for versions of the model, using multigrid methods, and obtained reasonable solutions for idealized cases. For the particular implementation tested, we did not obtain sufficient increase of solution speed compared to time integration to justify the added code complexity. However, in principle a steady solution version could be fruitful for study of climate anomalies, such as ENSO response. Omission of (or necessity of approximating) midlatitude transient eddy transports is the leading effect with consequences for the climatology, as discussed below. Soil moisture is not well approximated by steady solutions over the seasonal cycle."

Additional simplifications explicitly blessed for steady/diagnostic work (NZ2000 section 7d, items 2-8, same page):

* item 2: midlatitude eddies can be "turned off" by replacing the v.grad T term, D_T1 T in (5.3), with a climatological value from a control run.
* item 3: for simplified studies, solutions can be obtained omitting all the v.grad advection terms - with the caveat, verbatim, that their effect is "often not as small as the simplest scaling arguments would suggest", that "moisture and temperature advection can be important to wave phenomena and hence to teleconnection phenomena within the Tropics", and that "when these terms are omitted, the solution for the convergent motions can be reduced to an effectively local the[ory]".
* item 7: "it can sometimes be acceptable to neglect epsilon_01 v_0 in (5.1). Then if baroclinic advection terms are small, the baroclinic component solution decouples from the barotropic solution, so a baroclinic mode solution can be studied, akin to simpler models."
* item 8: "Linearization can hold reasonably well for many problems ... Exceptions include shifts of the edge of the tropical convergence zones, which can be important in some interannual climate anomalies."

PRACTICAL IMPLICATION FOR A STEADY 2-D MODEL: QTCM1's baroclinic subsystem with partial_t -> 0 reduces to a closed diagnostic problem - T_1 and q_1 from (5.3)-(5.4) with div(v_1) as the unknown, closed by M_1 = M_S1 - M_q1 acting as a positive effective static stability (5.6)/(5.9). That is precisely the "gross moist stability" closure that makes a steady tropical circulation well posed. The barotropic mode remains a separate, individually solvable elliptic (vorticity/streamfunction) problem with prescribed divergence.

## B.7 QTCM1 DOCUMENTATION AND SOURCE CODE - WHAT I FOUND (WITH URLs)

### (a) The model description / user manual - FOUND AND DOWNLOADED [VERIFIED]

* QTCM1 v2.3 manual, 86 pp, "The Neelin-Zeng Quasi-Equilibrium Tropical Circulation Model (QTCM1) Version 2.3", UCLA, September 26, 2002. Working URL:
  https://csdms.colorado.edu/csdms_wiki/images/Qtcm_manv2.3.pdf
  (also fetchable over plain http://). Saved locally as research/pdfs/qtcm_manv23.pdf + qtcm_man_flat.txt.
  Contents: section 1 basics/running the model; section 2 code and numerics (grid, C-grid, numerics, variable naming mapped to the "NZ" equation numbers); section 3 model physics (basis-function construction, SLand, cloud/radiation, slab ocean, convective closure, diffusion, ABL, gross moist stability); section 4 = an 8-page Appendix A "Summary of Model Formulation" with the full derivation and Eqs. (A.1)-(A.15); plus the full qtcmpar.f90 parameter listing annotated with NZ2000 equation numbers.

### (b) Official QTCM home page [VERIFIED - live]

* https://atmos.ucla.edu/csi/qtcm/ - "QTCM - Quasi-Equilibrium Tropical Circulation Model" (Neelin Group). Verbatim highlights: "QTCM1 includes a single deep convective mode in the vertical thermodynamic structure and two components (baroclinic and barotropic) in the vertical structure of velocity. It is computationally light (5min on a Sun Ultra2 at 5.625x3.75 resolution and 1.5 minutes on a Pentium-4/Linux workstation) and easy to diagnose..."; QTCM1 Version 2.3 (August 2002); links to "What's new in this release", the code download, the Manual, boundary data files, "QTCM Tools and related", "Unofficial, non-released QTCM versions", "Archive of released versions", "Archive of sample figures"; and the citation list giving NZ2000 = 1741-1766 and ZNC2000 = 1767-1796.
* Also live: https://atmos.ucla.edu/csi/csi-group-github-organization/ (links to https://github.com/Climate-Systems), and https://atmos.ucla.edu/csi/publications/.

### (c) The former download/registration pages - live links are DEAD, archived listing FOUND [VERIFIED via Wayback]

* Live 404 (I checked each): https://research.atmos.ucla.edu/csi/QTCM/qtcm_rgstr.html ; https://research.atmos.ucla.edu/csi/QTCM/qtcmv23announ.html ; https://www.atmos.ucla.edu/~csi/qtcm_man ; http://research.atmos.ucla.edu/csi/ (root).
* Archived directory listing (WORKS):
  https://web.archive.org/web/2016id_/http://www.atmos.ucla.edu/~csi/QTCM/
  It lists, among many files:
  - QTCM1V2.3.tar.gz (5.2 M, 23-Oct-2002)
  - QTCM1V2.2.tar.gz (1.9 M)
  - bnddatav2.1.tar.gz (18 M - boundary data files; the live UCLA page still advertises research.atmos.ucla.edu/csi//QTCM/bnddatav2.1.tar.gz)
  - qtcmparv2.2.tar.gz, qtcm_log, qtcm_log_2002july16, qtcm_log_may99, qtcm_rgstr.html, qtcm_rgstr.pl, qtcmv23announ.html, rcs.html, unoff.html, sampfigs.html, qtcmref.html, qtcmref_other.html, qtcm.html, qtcm_old.html, anonftp.msg, plus FIGS_2.0/ FIGS_2.1/ FIGS_2.2/ FIGS_2.3/ FIGS_index/ FIGS_obs/ directories and unoff/ unoffqtcmMatt/ unoffqtcmRoel/.
* The manual itself points at the canonical URL http://www.atmos.ucla.edu/~csi/QTCM/ (manual section 1.4.3 p.18 and the references section pp.65-66) - that host path is now dead but the Wayback copy resolves.
* I also retrieved the archived qtcmref.html ("QTCM referenced Publications within the CSI group"), which links the NZ2000 paper PDF as ../REF/pdfs/NZ00.pdf on the UCLA server, i.e. http://www.atmos.ucla.edu/~csi/REF/pdfs/NZ00.pdf (438 KB) - the same file as the Zeng-hosted NZ.pdf.

### (d) QTCM source code on GitHub - searched; NO QTCM1 repo found in the group's own org [VERIFIED, negative result]

* The UCLA page links to the "climate-systems GitHub organization": https://github.com/Climate-Systems. I queried api.github.com/orgs/Climate-Systems/repos?per_page=100. The returned repositories are: plume_model, MOOSE-MDTF, .github, MDTF-diagnostics, POD_MCS_precip_buoy_stats, POD_precip_pdf. NO QTCM1 / QTCM2 REPOSITORY IS PRESENT IN THAT ORGANIZATION.
* A separate, third-party artifact exists: a Python/Fortran hybrid distribution "qtcm-0.1.2" documented in a CiteSeerX record (https://citeseerx.ist.psu.edu/document?repid=rep1&type=pdf&doi=cf42fdb3a8a4c6b26f913b74859e8dc32bb0a2fb), whose indexed text includes lines such as "3. Go to the directory test/benchmarks/create in the qtcm-0.1.2 qtcm distribution directory", "_qtcm_parts_365.so Creates the extension module _qtcm_parts_365.so", and "Field variables with ghost latitudes, such as u1, on the Python and Fortran ends are always the full variables". I did NOT successfully download this document - the direct CiteSeerX URL returned HTTP 404 for me. Treat the qtcm-0.1.2 claim as UNVERIFIED; it is a lead, not a confirmed source.
* I also did NOT find a working qtcm.sourceforge.net site (searched; nothing retrieved). Any claim that QTCM1 is hosted on SourceForge is UNVERIFIED on my evidence.

### (e) Other verified QTCM-related URLs

* NZ2000 full text (author copy): http://www2.atmos.umd.edu/~zeng/papers/NZ.pdf
* ZNC2000 full text (author copy): http://www2.atmos.umd.edu/~zeng/papers/ZNC2000.pdf
* Ning Zeng's publication list (source of the above): http://www2.atmos.umd.edu/~zeng/papers/
* UCLA archived paper PDF path: http://www.atmos.ucla.edu/~csi/REF/pdfs/NZ00.pdf (via Wayback)
* CSDMS-hosted manual (working): https://csdms.colorado.edu/csdms_wiki/images/Qtcm_manv2.3.pdf
* I guessed https://csdms.colorado.edu/csdms_wiki/index.php/QTCM and it returned 404 - whether a CSDMS wiki ARTICLE on QTCM exists is UNVERIFIED.

## B.8 Model parameters useful for a 2-D steady build [VERIFIED-PRIMARY - NZ2000 Table 1, p.1755]

Selected values, verbatim as printed (the PDF's glyph remapping undone: '3' -> 'x', '2' -> '-'):

| Term | Value | Definition (as printed) |
|---|---|---|
| ahat_1, a_1s | 0.38, 0.24 (unitless) | Vertical integral and surface value of temperature basis function |
| bhat_1, b_1s | 0.45, 0.61 (unitless) | Vertical integral and surface value of moisture basis function |
| V_1s | -0.20 (unitless) | Surface value of baroclinic wind basis function |
| C_D | 0.9 x 10^-3, 3.7 x 10^-3 (unitless) | Surface drag coefficient for ocean and forest |
| tau_c | 2 h | Convective adjustment time |
| epsilon_0(V_s), epsilon_1(V_s) | (5.6 day)^-1, (3.4 day)^-1 for V_s = 10 m/s, C_D = 10^-3 | Projected vertical momentum transfer, barotropic / baroclinic |
| epsilon_10(V_s), epsilon_01(V_s) | (-28 day)^-1, (-0.9 day)^-1 | Transfer by surface stress between baroclinic and barotropic components |
| epsilon_1(V_s), epsilon_1^int | (4.53 day)^-1, (14.4 day)^-1 | Surface-stress and internal-mixing contributions to epsilon_1 |
| M_Sr1 | 3.5 x 10^3 (units printed as J K^-1 g^-1 - see caveat) | Reference value of the dry static stability component of the gross moist stability |
| M_qr1 | 3.0 x 10^3 (same caveat) | Reference value of the gross moisture stratification |
| M_Sp1 | 3.4 x 10^-2 (unitless) | Change in dry static stability per T_1 change |
| M_qp1 | 2.7 x 10^-2 (unitless) | Change in gross moisture stratification per q_1 change |
| p_T = p_rs - p_rt | ~8 x 10^4 Pa | Reference pressure depth of the troposphere |
| K_H | 3.0 x 10^5 m^2 s^-1 | Horizontal diffusion |

CAUTION: the units of M_Sr1 / M_qr1 extract as J K^-1 g^-1; whether the intended unit is per-kg or per-g I cannot resolve from the text - do not quote the units without checking the page image. The numeric values (3.5 and 3.0) match the QTCM1 v2.3 code (GMsr = 3.5, GMqr = 3.0, annotated "M_{sr} [K]" and "M_{qr} [K]", i.e. the code divides by C_p). [partially UNVERIFIED - units only]

---

# PART C - SIDE-BY-SIDE SUMMARY (for model scoping)

| | ZC87 atmosphere | QTCM1 (NZ2000) atmosphere |
|---|---|---|
| Solved system | steady 3-D (u_a, v_a, p/rho_0) on a beta plane, reduced gravity, c_a = 60 m/s | 2-D horizontal plus 1 vertical mode each for T, q and 2 for v; 3-D fields reconstructed from basis functions |
| Vertical structure | none (single equivalent layer) | T = T_r(p) + a_1(p)T_1 ; q = q_r(p) + b_1(p)q_1 ; v = V_0(p)v_0 + V_1(p)v_1, with V_0 = 1 and V_1 = a_1^+ - abar_1^+ |
| Prognostic | none in the atmosphere (steady); ocean u, v, h; SST T | T_1, q_1, v_1, zeta_0 (psi_0), surface T_s, cloud fraction |
| Diagnostic | u_a, v_a, p/rho_0 from SST + previous-iteration convergence | omega(p) from (4.1)-(4.3); div(v_0) specified; phi_s0, residual v_R / s_R, dh_b, precipitation, CAPE diagnosed postsolution |
| Damping | single epsilon = (2 day)^-1 on all three equations (Rayleigh friction + Newtonian cooling) | epsilon_1, epsilon_01, epsilon_0, epsilon_10 from surface stress and internal mixing (e.g. (3.4 day)^-1, (5.6 day)^-1, (4.53 day)^-1, (14.4 day)^-1) |
| Heating / closure | Q_s = alpha T exp[(Tbar-30)/16.7], alpha = 0.031 m^2 s^-3 K^-1; Q_I = beta[M(cbar+c)-M(cbar)], beta = 1.6e4 m^2 s^-2; ramp M; iterated | Betts-Miller QE: Q_c = c_c(T_c-T)/tau_c if <T_c-T> > 0 ; Q_q = (q_c-q)/tau_c ; q_c = q_r^c + B_1 T_1^c ; tau_c = 2 h ; in the simplest case -<Q_q> = <Q_c> = epsilon_c^*(q_1 - T_1) |
| Effective static stability | none (heating parameterised directly from SST and convergence) | gross moist stability M_1 = M_S1 - M_q1, positive in convecting regions |
| Steady-state solvable? | YES for the atmosphere - it IS steady, solved by fixed-point iteration | "Well posed" per NZ2000 section 7d(1); multigrid steady solvers tested and gave "reasonable solutions for idealized cases", but not faster than time integration in their implementation. The released QTCM1 time-steps. |
| Grid | not stated in ZC87 (UNVERIFIED); ocean basin 124E-80W, 29S-29N | standard: 64 x 42, 5.625 deg lon x 3.75 deg lat, 78.75S-78.75N, periodic in lon, walls in lat |
| Time step | ocean + SST 10 days; atmosphere re-solved monthly, convergence-feedback part marched, max 3 iterations/step | dt = 1200 s (20 min) standard; CFL-limited to about 20 min by midlatitude momentum advection |
| Cost | not stated (UNVERIFIED) | ~5 min CPU / model-year on a Sun Ultra 80; ~1.5 min on a Pentium-4/Linux box |

---

# PART D - EVERYTHING I COULD NOT VERIFY (explicit list)

1. THE TASK'S CITATION FOR MODEL (B) IS WRONG - pages 2955-2972 and the DOI ...057<2955:ANQTC>... do not exist (Crossref 404, OpenAlex 404). Correct: 1741-1766, DOI ...057<1741:AQETCM>.... Same for the companion paper: 1767-1796, not 2915-2934. What occupies JAS 57 pp. 2955-2972 is UNVERIFIED.
2. NO beta_q SYMBOL EXISTS in NZ2000, ZNC2000, or the QTCM1 v2.3 manual, as far as I can determine. UNVERIFIED as a QTCM quantity.
3. ZC87 horizontal grid / resolution of the atmosphere and ocean: UNVERIFIED (not stated in the paper). Basin geometry is stated.
4. ZC87 computational cost: UNVERIFIED (not stated).
5. Zebiak (1986), MWR 114, 1263-1271: FULL TEXT NOT RETRIEVED. The heating parameterisation is reported here only as reproduced in ZC87's Appendix, which cites Zebiak (1986). No independent check of Zebiak (1986)'s own equation numbering, symbols, or additional forms.
6. ZC87 Eq. (A5) as printed lacks a meridional time derivative - both of my independent readings of p.2277 agree, but I could not consult a second copy of the article to rule out a scan artifact. The long-wave-approximation interpretation is my inference.
7. ZC87 (A3a): prefactor = anomalous SST T, exponent uses the mean Tbar - my reading, supported by the accompanying sentence on the same page; residual risk from the scan remains.
8. NZ2000 Eqs. (2.18) and (2.19) - the PDF's math-font scrambling means my transcription of these two is a REASSEMBLY, not a faithful read. (2.20), (2.21)-(2.23) and (5.5)-(5.8) are solid.
9. NZ2000 Table 1 row p_T - the raw text has p_rt and p_rs in the opposite order from the body text; and the units of M_Sr1 / M_qr1 are ambiguous in extraction.
10. ZNC2000's own grid dimensions and dt are not stated in the text I retrieved; the numbers quoted are the QTCM1 v2.3 MANUAL standard values (a later configuration).
11. QTCM1 model top / p_rt: NZ2000 Table 1 implies p_T ~ 8 x 10^4 Pa; the v2.3 manual integrates 1000 -> 150 mb (p_T = 850 mb). These are different configurations; I did not resolve which p_rt NZ2000 itself used.
12. qtcm-0.1.2 (the Python/Fortran distribution seen only in a CiteSeerX search snippet): UNVERIFIED - the document itself would not download (HTTP 404).
13. qtcm.sourceforge.net: searched, nothing retrieved - UNVERIFIED whether such a site ever existed.
14. The third-party ZC model Fortran on GitHub (grrrizzzz/numerical_modeling) is of UNKNOWN PROVENANCE; I used it only to establish that an independent copy of ZC model code exists, not as evidence for ZC87's grid.
15. NO RETRIEVAL OF journals.ametsoc.org MATERIAL WAS POSSIBLE (Cloudflare-blocked, as expected). Every AMS paper cited here was obtained from an author or repository copy. Where an author copy differed in pagination from the AMS DOI record, I flagged it.

---

# PART E - FILES SAVED (all under K:/moder/EyeOfHarmonyBuffer/research/pdfs/)

PRIMARY SOURCE TEXTS (the raw extracted text of everything I relied on):

* zc87_wb2.pdf, zc87_flat.txt, zc87_lines.txt - Zebiak & Cane 1987, full 17 pp incl. Appendix and parameter list.
* nz2000_formulation.pdf, nz2000_flat.txt - Neelin & Zeng 2000, 26 pp.
* znc2000_implementation.pdf, znc2000_flat.txt - Zeng, Neelin & Chou 2000, 30 pp.
* qtcm_manv23.pdf, qtcm_man_flat.txt - QTCM1 v2.3 manual, 86 pp incl. Appendix A and qtcmpar.f90.
* thompson_battisti2000.pdf / .txt - Thompson & Battisti 2000 (used only as a cross-check on ZC87's architecture).
* zc_axatmos_f.txt, zc_model_com.txt - third-party ZC Fortran (provenance uncertain).
* Supporting web pages: ucla_qtcm_page.txt, qtcm_ref_wb.txt, qtcm_old_wb.txt, qtcm_rgstr_wb2.txt, ucla_github_org.txt, hawaii_ams.txt, columbia_zc87_wb.txt, jaxa_zc87.txt.

PAGE IMAGES USED FOR EQUATION TRANSCRIPTION (so any claim can be re-checked):

* img/zc87_wb2_p016_ap*.png - ZC87 Appendix (A1)-(A13), p.2277.
* img/zc87_wb2_p017_params*.png - ZC87 parameter list, p.2278.
* img/nz2000_formulation_p009..p013_*.png - NZ2000 Eqs. (4.1)-(4.28) and section 5.
* img/qtcm_manv23_p0*.png - QTCM1 manual pages.

HELPER SCRIPTS ADDED: fetch_pdf2.py (junk-tolerant PDF fetch), fetch_text.py (generic HTML/PDF fetch), rex.py / rexflat.py (PyMuPDF text extraction; flat mode preserves paragraph flow), render.py / crop.py (page rasterisation for vision transcription), pageof.py, oa3.py / oa4.py (OpenAlex), findoa.py (Semantic Scholar / Unpaywall), cdx2.py (Wayback CDX).
