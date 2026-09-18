# Literature report: published LINEAR STATIONARY WAVE / LINEAR BAROCLINIC models, and monsoon-desert mechanism literature

Prepared for a physically-grounded STEADY-STATE 2-D (lon x lat) atmospheric circulation model.
Working dir: K:\moder\EyeOfHarmonyBuffer  |  Raw extracted texts: research\pdfs\*.txt

---

## 0. READ THIS FIRST — headline corrections and verification status

**Three of the citations in the assignment are WRONG. I verified this against Crossref and OpenAlex metadata (DOI resolution), not from memory.**

| Assigned | Verified reality |
|---|---|
| "Ting & Held (1990), J. Atmos. Sci., 47, 495-515, 'On the Stationary Flow of a Nonlinear Stationary Wave Model', doi:10.1175/1520-0469(1990)047<0495:OVTFOA>2.0.CO;2" | **The DOI does not exist.** 10.1175/1520-0469(1990)047<0495:OVTFOA>2.0.CO;2 returns HTTP 404 from both Crossref and OpenAlex. The real AMS article at JAS **47(4), 495-500** has DOI suffix **OVTFOS** and is: **Held, I. M., and M. Ting, 1990: "Orographic versus Thermal Forcing of Stationary Waves: The Importance of the Mean Low-Level Wind." J. Atmos. Sci., 47(4), 495-500.** (Crossref: authors "Isaac M. Held; Mingfang Ting"). **The title "On the Stationary Flow of a Nonlinear Stationary Wave Model" could not be found anywhere and is UNVERIFIED — I believe it does not exist.** |
| "Watanabe & Kimoto (2000), J. Atmos. Sci., 57, 2621-2639" | **Wrong journal/volume/pages.** JAS vol. 57 (2000) pages 2607-2624 and 2625-2648 are ship-track/aerosol papers (verified by scanning Crossref/OpenAlex for JAS vol. 57 pages 2590-2680). The LBM reference is: **Watanabe, M., and M. Kimoto, 2000: "Atmosphere-ocean thermal coupling in the North Atlantic: A positive feedback." Q. J. R. Meteorol. Soc., 126(570), 3343-3369** (Crossref/OpenAlex verified; DOI 10.1002/qj.49712657017), plus **Watanabe, M., and M. Kimoto, 2001: "Corrigendum." Q. J. R. Meteorol. Soc., 127, 733-734.** These two are named as the source of the LBM equations *by the LBM's own manual* (quoted in section 2 below). |
| "Dry Linear Baroclinic Model (DLBM) if it exists as a named model" | **It does not exist as a distinct named model.** The LBM *is* dry. What exists is the dry LBM (the standard package) plus an explicitly separate **moist** LBM (mLBM). See section 3. |

**Also note:** I could **not** retrieve the full text of Ting & Held (1990) JAS 47(21) 2546-2566 or Held & Ting (1990) JAS 47(4) 495-500 (AMS = blocked; no OA copy exists — Semantic Scholar reports isOpenAccess: False; Wayback holds no snapshot of the AMS PDF). Therefore **their model equations, damping constants, basic state and resolution are UNVERIFIED in this report.** I say so again explicitly in section 1.

**Everything quoted below comes from a file I actually downloaded and saved.** The one exception is the University of Tokyo LBM manual, which is a PDF built with a custom-encoded Type1 font: pypdf returns glyph names (/CP, /D8, ...) instead of letters. I decoded it by empirical substitution (the mapping is a bijection; it reproduces long English prose perfectly, e.g. "Mathematical basis of the linear model", "biharmonic", "spherical harmonic coefficients"). **Prose is reliable; individual mathematical symbols in the equations are partly inferred and are flagged as such.** The decoded text is saved as research\pdfs\lbm_doc2.2_decoded.txt alongside lbm_doc2.2.txt (raw) so the decoding can be audited. The decoder is research\decode_lbm.py.

---

## 1. Ting & Held (1990) and the "nonlinear stationary wave model" (NSWM)

### 1.1 What is verified

**Citation A (correct):**
> Ting, M., and I. M. Held, 1990: The Stationary Wave Response to a Tropical SST Anomaly in an Idealized GCM. J. Atmos. Sci., 47(21), 2546-2566. doi:10.1175/1520-0469(1990)047<2546:TSWRTA>2.0.CO;2
> — Crossref verified: title, volume 47, issue 21, pages 2546-2566, authors Mingfang Ting; Isaac M. Held.

**Citation B (the one the assigned DOI actually resolves to):**
> Held, I. M., and M. Ting, 1990: Orographic versus Thermal Forcing of Stationary Waves: The Importance of the Mean Low-Level Wind. J. Atmos. Sci., 47(4), 495-500. doi:10.1175/1520-0469(1990)047<0495:OVTFOS>2.0.CO;2
> — Crossref verified. **This is a 6-page note, not a 21-page model description, and its title has nothing to do with a "nonlinear stationary wave model".**

**Citation C (the NSWM):**
> Ting, M., H. Wang, and L. Yu, 2001: Nonlinear Stationary Wave Maintenance and Seasonal Cycle in the GFDL R30 GCM. J. Atmos. Sci., 58(16), 2331-2354. doi:10.1175/1520-0469(2001)058<2331:NSWMAS>2.0.CO;2
> — Crossref verified: title, volume 58, issue 16, pages 2331-2354, authors Mingfang Ting; Hailan Wang; Linhai Yu. The DOI suffix **NSWMAS** = "Nonlinear Stationary Wave Maintenance And Seasonal cycle".

**Related, verified (the linear/nonlinear baroclinic model pair):**
> Ting, M., and L. Yu, 1998: Steady Response to Tropical Heating in Wavy Linear and Nonlinear Baroclinic Models. J. Atmos. Sci., 55(24), 3565-3582. doi:10.1175/1520-0469(1998)055<3565:SRTTHI>2.0.CO;2 (OpenAlex verified: v55 p3565-3582.)
> Ting, M., 1996: Steady Linear Response to Tropical Heating in Barotropic and Baroclinic Models. J. Atmos. Sci., 53(12), 1698-1709. doi:10.1175/1520-0469(1996)053<1698:SLRTTH>2.0.CO;2 (Crossref verified.)

### 1.2 What the LBM manual says about Ting & Held (VERBATIM, decoded from lbm_doc2.2_decoded.txt)

The University of Tokyo LBM manual (section 3.2, "Matrix inversion. Part I: Solve full matrix", p. 18 of the PDF) lists Ting & Held (1990) among the studies whose steady forced problem was solved by **direct linear matrix inversion**:

> "In many studies that use LBM as a diagnostic tool, the steady forced problem has been solved with a linear matrix inversion (Hoskins and Karoly 1981; Nigam et al. 1986; Held et al. 1989; Valdes and Hoskins 1989; **Ting and Held 1990**; Nigam 1994; Watanabe and Kimoto 1999; DeWeaver and Nigam 2000; Kimoto et al. 2001, among others). The method is described in this and the next sections."
> — research\pdfs\lbm_doc2.2_decoded.txt, section 3.2, PDF p.18 (file lines 762-766)

and about the damping:

> "Linear drag which mimics Rayleigh friction and Newtonian damping is commonly applied to LBM. The drag term also imitates a dissipative process due to nonlinearity (**Ting and Yu 1998**)."
> — section 3.1.3, PDF p.16 (file lines 679-681)

**Consequence for the assignment:** the *only* thing I can verify about the model the assignment describes ("steady, linearized primitive equations on sigma coordinates with Rayleigh friction and Newtonian cooling", solved by matrix inversion, basic state = zonal mean) is that this class of model exists and that Ting & Held (1990) is cited as an instance of it using matrix inversion. **I could NOT verify from a primary source that Ting & Held (1990) used sigma coordinates, what their Rayleigh-friction/Newtonian-cooling coefficients were, what basic state they linearized about (GCM zonal mean? analysed climatology?), or at what resolution.** All of those specifics are **UNVERIFIED**.

The best *verified* description of the linear-stationary-wave-model architecture (because I have the primary manual) is the University of Tokyo LBM in its **SWM** mode — see section 2.6. The generic element of that architecture that recurs across the cited literature is:
- **Prescribed:** a basic state X-bar (zonal-mean or fully 3-D analysed climatology), a steady forcing F (thermal and/or orographic), and the damping coefficients.
- **Solved (diagnostic, not prognostic):** the steady linear perturbation X for vorticity, divergence, temperature, ln(surface pressure).
- **Method:** invert the linear operator: X = L^-1 F.

### 1.3 Whether it is steady-state solvable
**Yes, fully steady-state solvable by direct matrix inversion** — this is precisely the class the LBM manual calls the "matrix inversion" method, and it is *contrasted* by that manual with time integration (which "results in only a good approximation to a steady solution ... unless the dissipation is enough strong"). See sections 2.5/2.6 for the verbatim contrast and for the important caveat about marginally unstable operators.

---

## 2. Watanabe & Kimoto — the Linear Baroclinic Model (LBM)

### 2.1 The reference (VERIFIED)
> **Watanabe, M., and M. Kimoto, 2000:** Atmosphere-ocean thermal coupling in the North Atlantic: A positive feedback. Q. J. R. Meteorol. Soc., 126(570), 3343-3369. doi:10.1002/qj.49712657017
> **Watanabe, M., and M. Kimoto, 2001:** Corrigendum. Q. J. R. Meteorol. Soc., 127, 733-734.

Both citations appear verbatim in the LBM manual's own reference list (file lines 3450-3452). The manual's section 1.3 states which references define the equations (see section 2.2).

### 2.2 What the LBM is, and what is prescribed vs solved — VERBATIM

From research\pdfs\lbm_doc2.2_decoded.txt (Watanabe, M., "Linear Baroclinic Model (LBM) Package Users' Guide, Version 2.2, 31 August 2005", Faculty of Environmental Earth Science, Hokkaido University; 91 PDF pages):

Section 1.1 Purpose and application (PDF p.2; file lines 109-111):
> "This numerical package has been built up in order to examine a linear dynamics in the atmosphere, such as to compute steady linear response to a prescribed forcing, eigenanalysis, and so on."

Listing of supported solution modes (section 1.1; file lines 124-136):
> "* steady linear response to prescribed forcing (cf. section 3), solved with
>   - time integration of the linear model (cf. section 3.1)
>   - direct method for full matrix (cf. section 3.2)
>   - direct method as a stationary wave model (cf. section 3.3)
>   - iterative method for full matrix (cf. section 3.4)
> * storm track model (cf. section 4)
> * eigenmode and singular mode computation (cf. section 5)
> * time integration of the nonlinear dynamical model (cf. section 6)
> * moist linear model (cf. section 7)
> * barotropic model (cf. section 8)"

Section 1.3 About the numerics (PDF p.3; file lines 157-165):
> "All the programs contained is written in Fortran, and the model part has been constructed based on a dynamical core of AGCM cooperatively developed at Center for Climate System Research (CCSR), University of Tokyo, and National Institute for Environmental Studies (NIES), called CCSR/NIES AGCM, version 5.4g. **The spectral representation of primitive equations with a vertical sigma coordinate has been explicitly linearized about a basic state.** Since the model is used, in most cases, with multiple levels although a single-layer model (barotropic model) is available, it is hereafter referred to as the linear baroclinic model (LBM) in this document. **The form of linear equations are given by Watanabe and Kimoto (2000, 2001)** and also briefly described in the Appendix of this manual."

**So, explicitly: the LBM equations are NOT written out in the manual — they are delegated to Watanabe & Kimoto (2000, 2001).** Those two are Wiley/QJRMS, are not open access (Semantic Scholar: isOpenAccess: False, no OA PDF; OpenAlex oa_status: closed), and Wiley is on the blocked list. **I therefore could NOT transcribe the LBM's primitive-equation system verbatim. That is an explicit gap — see section 2.7.**

### 2.3 Symbolic formulation — VERBATIM (Appendix A.1, PDF pp.78-79; file lines 2976-3013)

> "In this Appendix, a brief description of the linear atmospheric dynamics, based on primitive equations (PE), is given using a set of symbolic equations of state. Readers can refer to Watanabe and Kimoto (2000, 2001) and Watanabe and Jin (2003) for further details.
>
> **A.1 Linear dynamical system.** First, let X be a vector containing prognostic variables in PE, vorticity (zeta), divergence (D), temperature (T), and logarithm of surface pressure (Pi = ln Ps), namely, X = X(zeta; D; T; Pi). A symbolic representation of the dynamical system is denoted as
>
>   d/dt X + (L + NL) X = F,   (1)
>
> where L and NL are a linear and nonlinear part of the dynamical operator that consists of, for example, advection, Coriolis, pressure gradient, and dissipation terms, while F indicates forcing. Now Eq.(1) is linearized about a basic state X-bar and the nonlinear part is neglected, yielding a linear system for perturbations X'.
>
>   d/dt X' + L X' = F',   (2)
>
> Note that the operator L is now a function of the basic state, i.e. L = L(X-bar). Considering the steady forced problem of Eq.(2), we rewrite it as (prime is dropped for convenience)
>
>   L X = F,   (3)
>
> Equation (3) corresponds to a set of linear simultaneous equations, so that it can be readily solved as
>
>   X = L^-1 F,   (4)"

**Symbol caveat:** in the decoded text the equals sign inside the equation blocks renders as "!"; I have transcribed it as "=". The "+" signs and the X = X(zeta; D; T; Pi) decomposition are directly decoded and cross-checked numerically against the manual's own rank formula (see section 2.5).

### 2.4 Damping, diffusion, resolution — VERBATIM (section 3.1.3, PDF pp.15-16; file lines 655-688)

**Exact namelist defaults printed in the manual (T21L20 example):**

    &nmhdif order=4, tefold=6, tunit='HOUR' &end
    &nmdamp ddragv=1,1,1,5,15,30,30,30, 30, 30,30,30,30, 30,30,30,30,30, 1,1,
            ddragd=1,1,1,5,15,30,30,30, 30, 30,30,30,30, 30,30,30,30,30, 1,1,
            ddragt=1,1,1,5,15,30,30,30, 30, 30,30,30,30, 30,30,30,30,30, 1,1,
            tunit='DAY' &end
    &nmvdif vdifv=1.d3,...(20 values)..., vdifd=1.d3,...(20)..., vdift=1.d3,...(20)..., &end

(The "=" that appears as "!" in the decoded text has been transcribed as "=".)

**Horizontal diffusion (VERBATIM):**
> "&nmhdif defines the shape and strength of the horizontal diffusion. **The linear model usually employs biharmonic (nabla^4) diffusion, so that order=4.** Since the horizontal diffusion is scale-selective, the magnitude of the diffusion coefficient is defined by the **e-folding decay time for the largest wavenumber** (tefold=6, tunit='HOUR' means **6hr decay time**). It has been confirmed that a weaker diffusion up to 24hr decay time does not seriously alter the response. For reference, the CCSR/NIES AGCM, from which LBM has been developed, employs much weaker diffusion of order=8, tefold=24, tunit='HOUR'."

**Rayleigh friction / Newtonian cooling (VERBATIM):**
> "**Linear drag which mimics Rayleigh friction and Newtonian damping is commonly applied to LBM.** The drag term also imitates a dissipative process due to nonlinearity (Ting and Yu 1998). The response often becomes sensitive to the choice of drag coefficient. A set of parameter **&nmdamp defines the coefficient for zeta (ddragv), D (ddragd), and T (ddragt)** with the time unit tunit. In the above example, **damping time scale is set at 1dy for the lowest three levels and the topmost two levels, 5 and 15dy for the fourth and fifth levels, and 30dy elsewhere.**"
> "**Vertical diffusion** is also included in order to suppress a vertical computational mode. You do not need to change the magnitude, set at **(1000dy)^-1** in the above."

Note the structure: **the damping is a separate Rayleigh/Newtonian timescale for each of vorticity, divergence and temperature, specified level-by-level, in days.** Separate coefficients are given for vorticity, divergence and temperature — i.e. there is no requirement that friction and cooling share a timescale (contrast Gill 1980, section 7).

A *different* damping profile appears in the AIM note (research\pdfs\lbm_note_decoded.txt, Watanabe, "Note on AIM in LBM and an 'artificial response' problem", 06/28/05, PDF p.3):
> "the model damping coefficients (0.5,0.5,0.5,20,20,...,20,1,1 days) are altered to (0.5,1,1,20,20,...,20,1,1 days), which bring the linear operator L marginally unstable."

**Resolution (VERBATIM, section 2.2, PDF p.6; file lines 255-256):**
> "The current version supports **four vertical resolutions (5, 8, 11, and 20 levels)** while **horizontal resolution of T21 is ordinary recommended**."

The directory listing of the distribution (research\pdfs\lbm_dir.txt, https://ccsr.aori.u-tokyo.ac.jp/~hiro/lbm/) confirms the shipped basic states and forcing archives are named ncepwin.t21l20.grd, frc.edy.anm.t42l20.grd, frc.q1.anm.t42l20.grd, and that the package contains ln_solver3.0.tar.gz, lbm3.0_090605.tar.gz, plus a documented moist/climate-zone variant mLBM-CZ. It also contains the manual files doc2.2.pdf, doc2.2.AIM.pdf, hs-note-eng.pdf, lsc-note-eng.pdf, note-on-lbm.062805.pdf, AIM.rev2.v3.pdf.

**Basic states (VERBATIM, section 2.4; file lines 368-370):** the package ships NCEP/NCAR and ERA-40 derived basic states, e.g.
> "For example, ncepwin.t21l20.grd is a basic state for T21L20 model, obtained from winter ... erawin.t21l20.grd is a basic state for T21L20 model as well but from the winter climatology ..."

### 2.5 How it is solved — the three routes (VERBATIM)

**(a) Time integration — approximate steady state (section 3.1.3, PDF pp.15-16; file lines 639-651):**
> "The duration of the model integration depends on how long the system remains stable. If, for example, you set weak dissipations, some of eigenmodes in the system may grow faster that prevent you to integrate the model beyond certain time. When the dissipation is unrealistically strong such as not to allow any mode growing, you can continue to integrate the model without limitation. With relevant dissipation, which will be explained next, **the model integration will be continued up to around 30 days (after that baroclinic waves rapidly grow and blow up). Since, at least in lower latitudes, the response is going to be equilibrated after day 10 or so, you may set TEND at 25 then examine the response around day 15 or 20. Note that this method results in only a good approximation to a steady solution to prescribed forcing unless the dissipation is enough strong. If you need to obtain the exact steady solution to a forcing, choose matrix inversion methods as explained in sections 3.2 and 3.3.**"

**(b) Direct matrix inversion, full matrix (3-D basic state) — VERBATIM (section 3.2, PDF pp.18-19; file lines 761-789):**
> "A matrix for the linear dynamical operator, which consists of a set of spherical harmonic coefficients, is obtained basically by the so-called '**residual method**' (Hoskins and Karoly 1981). However, amplitude of delta function in perturbation vectors is not necessary to be tiny any more **since the model code has been exactly linearized**. While mathematical expression for the steady equations is independent of the structure of the basic state, practical method to solve the system linearized about the zonally varying, 3D basic state is somewhat different from the case of the zonally uniform basic state. ... Note that the latter method requires lower computational cost since it solves small block matrices (cf. 3.3), while the former case which solves one full matrix demands sufficiently large computer memory."
>
> "Suppose you use the T21L20 LBM for a steady problem. Total wavenumber for T21 resolution is 483, then the rank of the linear matrix N becomes
>   **N = 483 x (3(variables; zeta, D, T) x 20(levels) + 1(variable, Ps)) = 29643.**
> It implies that you have to solve a huge matrix of around 30000 x 30000, for which your computer must have a memory greater than **7.5GB**. To avoid such a computational burden, we simply suggest to reduce the rank by selecting coarser vertical resolution (L5 or L11) besides truncating the zonal wavenumber of 21 at 5, 10, or 15. For your reference, **solving T21L11 model with zonal waves truncated at 10 will take about 15 minutes on a HITACHI super computer, SR8000.** If you would like to obtain the steady response with higher resolution, you may choose an accelerated iterative method (AIM) which we newly developed (see 3.4) but not direct method described here."
>
> (section B.5, PDF p.85, file lines 3362-3363:) "Notice that it takes **about 1hr to make the linear matrix for T21L11m10 LBM**."

**(c) Direct matrix inversion, block-diagonal "Stationary Wave Model" (SWM) mode — zonal-mean basic state — VERBATIM (section 3.3, PDF pp.25-26; file lines 1044-1067):**
> "As argued in the introduction of section 3.2, solving a full matrix is computationally expensive when we select a higher model resolution. ... An alternative way to compromise is to use a **zonally uniform basic state**. It means that in such a case you eliminate a wave-wave interaction in steady equations. Actually, LBM with a zonal mean basic state was quite popular until mid-1980s (e.g. Hoskins and Karoly 1981) when no one has had a computer with large enough memory. Even now, such a model (**here referred to as the stationary wave model, SWM**) is used to figure out a simple prototype for the complicated nature. Unless you expect a crucial role of the wave-wave interaction, you do not need to hesitate to use SWM."
>
> "In a wave space, wave-wave interaction terms are located in the off-diagonal part of the linear operator matrix. **When we use the zonal mean basic state, those elements become 0, so that the full matrix is divided into several block diagonal matrices each of which corresponds to a particular zonal wavenumber.** Unlike in section 3.2 you can solve those relatively small matrices but not a huge matrix, this reduction in the size of matrix greatly saves your computation time. For example, **T21L20 SWM is now separated into 21 different matrices and a size of one matrix, say, for zonal wavenumber 1, will be N1 = 21 x 2 x (3(variables; zeta, D, T) x 20(levels) + 1(variable, Ps)) = 2562.**"

**(d) Accelerated Iterative Method (AIM) — research\pdfs\lbm_note_decoded.txt (Watanabe, 06/28/05):**
> "Here the linear baroclinic model (LBM) is used to solve forced steady solutions with AIM. Since most of the true LBM solutions cannot be obtained by the direct matrix inversion due to insufficient computer memory, it is not available to evaluate error epsilon. Instead, as in the barotropic model the norm ratio lambda is used to evaluate convergence of the iteration. The norm ratio at iteration step n is defined as lambda_n = ||X_n - X_(n-1)|| / ||X_1 - X_0||"
> "It is clear that the AIM converges faster than the time integration."
> "Because of increasing numerical error and very slight difference in the implementation, the RMS error epsilon is yet around 0.1 (10%) at this convergence [lambda < 1e-3]. But the response is sufficiently similar to each other, so that the lambda < 1e-3 seems a reasonable threshold."

**IMPORTANT WARNING for a steady-state project (VERBATIM, same note, PDF p.3):**
> "the AIM (and probably matrix inversion as well) does not allow such a propagation, so that the response may only amplify at particular area (i.e. the most baroclinically unstable region) without phase propagation, resulting in an **artificially steady response** near Siberia as has been sometimes found in the matrix inversion."

=> For a steady 2-D model, the linear operator must be strictly stable; if it is marginally unstable, direct steady solution methods return an artifact, not a physical steady state.

The published AIM reference is (OpenAlex verified):
> Watanabe, M., F.-F. Jin, and L. Pan, 2006: Accelerated Iterative Method for Solving Steady Problems of Linearized Atmospheric Models. J. Atmos. Sci., 63(12), 3366-3382. doi:10.1175/JAS3807.1
(The 2005 manual's reference list has it as "J.Atmos.Sci. submitted".)

The moist extension is (OpenAlex verified):
> Watanabe, M., and F.-F. Jin, 2003: A Moist Linear Baroclinic Model: Coupled Dynamical-Convective Response to El Nino. J. Climate, 16(8), 1121-1139. doi:10.1175/1520-0442(2003)16<1121:AMLBMC>2.0.CO;2

### 2.6 Summary table for the LBM

| Aspect | LBM (standard = dry) |
|---|---|
| **Prescribed** | Basic state X-bar (zonal-mean for SWM mode, or full 3-D analysed climatology for full-matrix mode); steady forcing F (thermal and/or orographic); damping coefficients (ddragv/ddragd/ddragt, per level, days), biharmonic diffusion (order 4, 6 h e-folding at largest wavenumber), vertical diffusion (1e-3 day^-1). |
| **Solved (diagnostic)** | Steady linear perturbations of (vorticity, divergence, temperature, ln Ps): X = L^-1 F. |
| **Not solved** | No prognostic time evolution in the steady mode; no moisture in the dry LBM. |
| **Grid/resolution** | Spectral in horizontal: T21 standard (483 total wavenumbers), optional zonal-wave truncation m5/m10/m15. Vertical: sigma coordinates, L5 / L8 / L11 / L20. |
| **Computational cost (as documented)** | Full matrix T21L20: 29643 x 29643, > 7.5 GB memory. T21L11m10 direct solve: ~15 min on HITACHI SR8000. Building the T21L11m10 operator matrix: ~1 h. SWM T21L20: 21 block matrices, largest block (m=1) 2562 x 2562. |
| **Category** | **(c) MIXED** — but only in the sense that the same package *offers* time integration; **for a genuinely steady answer the documented answer is (a): direct matrix inversion is the exact steady solver, and the manual says time integration is "only a good approximation to a steady solution ... unless the dissipation is enough strong."** |

### 2.7 Explicit gap
**UNVERIFIED: the LBM's primitive-equation system as actually written (the momentum, thermodynamic, continuity and hydrostatic equations with their sigma-coordinate linearization) — I could not retrieve Watanabe & Kimoto (2000, 2001).** The manual itself delegates them there. What I *can* state with a document in hand is: sigma-coordinate spectral primitive equations explicitly linearized about a basic state; prognostic vector (vorticity, divergence, temperature, ln Ps); dissipation = biharmonic horizontal diffusion + per-level Rayleigh friction for vorticity and divergence + per-level Newtonian cooling for temperature + vertical diffusion.

---

## 3. Is there a "Dry Linear Baroclinic Model" (DLBM)?

**Finding: "DLBM" does not exist as a separately named model in the literature I could retrieve. The LBM is itself dry.**

Evidence:
- The LBM manual's own option list distinguishes the **"dry model"** (the default CLASSIC build) from the **"moist model"** (section 7, "Moist LBM"); see lbm_doc2.2_decoded.txt file lines 809-813, 1074-1078.
- A literature search for the phrase returns only descriptive uses such as: "As an alternative approach to the composite analysis above, **the dry linear baroclinic model (LBM) of Watanabe and Kimoto [2000]** ..." (J. Adv. Model. Earth Syst., doi:10.1002/2016MS000843 — **this page is on agupubs/Wiley and I could not open it; the phrase is quoted from the search-result snippet, so treat the wording as UNVERIFIED and the substance as confirmed by the LBM manual itself.**)
- The named *moist* variant is **mLBM** (Watanabe & Jin 2003, verified above), and the distribution page lists an mLBM-CZ package.

**Conclusion: if you want a dry linear baroclinic model, you want the standard LBM (Watanabe & Kimoto 2000, 2001). "DLBM" is not a distinct published model.**

---

## 4. Rodwell & Hoskins — the monsoon-desert mechanism

### 4.1 Citations (VERIFIED)

> **Rodwell, M. J., and B. J. Hoskins, 1996:** Monsoons and the dynamics of deserts. Q. J. R. Meteorol. Soc., 122(534), 1385-1404. doi:10.1002/qj.49712253408
> **Rodwell, M. J., and B. J. Hoskins, 2001:** Subtropical Anticyclones and Summer Monsoons. J. Climate, 14(15), 3192-3211. doi:10.1175/1520-0442(2001)014<3192:SAASM>2.0.CO;2 (OpenAlex verified: v14, issue 15, pp. 3192-3211.)
> Supporting: **Hoskins, B. J., and M. J. Rodwell, 1995:** A Model of the Asian Summer Monsoon. Part I: The Global Scale. J. Atmos. Sci., 52, 1329-1340.

### 4.2 The 1996 abstract — VERBATIM (retrieved from the Crossref API, https://api.crossref.org/works/10.1002/qj.49712253408)

> "The existence of subtropical deserts, such as the Sahara, has often been attributed to the annual-mean, zonal-mean Hadley circulation which shows strong descent in the subtropics. However, the zonal-mean Hadley circulation shows considerable evolution over the course of the year with very strong subtropical descent during winter, but practically no zonal-mean subtropical descent during summer when rainfall over the eastern Sahara and the Mediterranean is least. Charney (1975) proposed a biosphere-albedo feedback mechanism whereby local anthropogenic effects related to over-grazing could affect the radiative balance, enhancing summertime diabatic descent and leading to desertification of the subtropics in general. **The present study, which uses an idealized model, suggests a monsoon-desert mechanism for desertification whereby remote diabatic heating in the Asian monsoon region can induce a Rossby-wave pattern to the west. Integral with the Rossby-wave solution is a warm thermal structure that interacts with air on the southern flank of the mid-latitude westerlies causing it to descend. This adiabatic descent is localized over the eastern Sahara and Mediterranean, and over the Kyzylkum desert to the south-east of the Aral Sea, by the mountains of north Africa and south-west Asia.** Trajectories indicate that the monsoon-desert mechanism does not represent a simple 'Walker-type' overturning cell. Instead, the descending air is seen to be mainly of mid-latitude origin. It is speculated that the monsoon-forced adiabatic descent may result in clear air and, therefore, a ..."
> — Crossref abstract for doi:10.1002/qj.49712253408 (the abstract text is truncated at 1600 chars by my retrieval script; the final ellipsis marks my truncation, not the paper's wording.)

**Note the word "idealized model".** I could **not** retrieve the body of RH1996 (Wiley; no OA copy — Semantic Scholar isOpenAccess: False; no Wayback snapshot found). **Therefore RH1996's own model equations, its resolution and its damping are UNVERIFIED.**

### 4.3 RH1996's model and heating, as described in RH2001 — VERBATIM from the cached file research\pdfs\rodwell_hoskins2001_wb.txt

**The model (RH2001 section 2 "Model and data", file line 692):**
> "The model used here is discussed in detail by Hoskins and Rodwell (1995). It is a **time-dependent, global, hydrostatic, primitive equation model derived from Hoskins and Simmons (1975)**. It is **nonlinear**, **spectral in the horizontal**, and uses **finite differences in sigma coordinates in the vertical**. The **horizontal resolution is triangular truncation 31, and there are 15 levels in the vertical.** The model includes damping that will be discussed below."

**How a "steady" state is obtained — VERBATIM (file lines 693-696):**
> "The model is initiated with a zonal-mean climatology. If orography is to be used then this is raised over the first five days of an integration. Hydrostatic adjustments are made to the temperature and surface pressure so that the growing mountains do not unrealistically disturb the atmospheric circulation and so that a near-steady-state solution to topography is achieved as quickly as possible."
> "At day 5 (day 0 if no orography is used), diabatic forcing is applied as a constant forcing. Depending on the experiment, this forcing is either an idealized field or an 'observed' field, derived as a residual in the time-mean thermodynamic energy equation (see later). **Although the model does not include moisture explicitly, the diabatic forcing mimics, in a noninteractive way, the effects of, for example, latent heat release and radiation budgets.**"
> "The model responds quickly to the orography and diabatic forcing, producing, in the control experiments, a very realistic time-mean atmospheric circulation. **For the June-August season, the flow is quasi-steady over the period 5-15 days after the heating is turned on, and we show results for either 10 or 11 days after the heating is applied (i.e., days 15 or 16 in an integration with orography).** The precise choice of day has no effect on the conclusions drawn in this paper. After this period of nearly steady flow, midlatitude baroclinic instability produces unsteadiness in the subtropics and Tropics."

**Damping — VERBATIM (file line 695):**
> "The model uses a **linear drag in the lowest two levels, sigma = 0.967, 0.887, on timescales of 1 and 5 days, respectively, over the oceans, and 1/4 and 5/4 days, respectively, over land areas.** Unless otherwise specified, **Newtonian relaxation toward the basic state ... is applied with a timescale of 25 days over much of the atmosphere, but decreasing to 5 days in the boundary layer** to simulate surface effects. With the exception of the initial adjustments for the growing orography, **the zonal averages of vorticity, divergence, temperature, and surface pressure are held constant throughout the integration.**"

**The canonical heating used, and the RH1996 heating — VERBATIM (RH2001 section 3b, file line 708):**
> "The first experiment is an integration with **no mountains** and with **idealized elliptical deep-convective monsoon heating centered at 25N, 90E and 400 hPa and maximizing at 5 K day^-1 (as used in RH)**. The zonal mean initial state is from the 1983-88 data. There is a **surface drag applied globally in the lowest and second-to-lowest model levels with timescales of 1 and 5 days, respectively, but there is no Newtonian relaxation applied.**"

**The "diabatic enhancement" experiment — VERBATIM (RH2001 section 3b, file line 715):**
> "To demonstrate the diabatic enhancement hypothesis in the third experiment with this simple model, we make use of the thermal anomaly associated with the adiabatic descent and apply **Newtonian relaxation of the temperature toward that of the basic state. The timescale for relaxation (4 days) was chosen** so that the implied damping of the temperature anomaly seen in the descent region for the previous experiment agreed with the rate of cooling seen in observations (Fig. 3a). ... **When this Newtonian relaxation is applied without making any other changes to the model, the descent is indeed strengthened by 18%.** However, because the relaxation is applied globally, the total heating in the monsoon region is reduced and the ascent there is simultaneously weakened (by 22.3%). ... Figure 5e shows the results from an integration when, additionally, **the monsoon heating was increased by 33%** to counteract the effect of Newtonian cooling in the monsoon region [(100-22.3) x 1.33 = 103%, i.e., approximately unchanged]. The monsoon ascent is indeed little changed (it is actually less than 1% stronger), but **the descent is significantly strengthened, by 54%**, and the low-level equatorward flow is markedly strengthened (Fig. 5f)."

**Idealized continent — VERBATIM (file line 713):**
> "The continent is applied in the **box 1.9-61.2N and 63.75-116.25E**, and its effect is felt through a **fourfold increase in the drag coefficients at the lowest two model levels.**"

**Observed heating boxes and amplitudes — VERBATIM (file line 726):**
> "Box NAm encompasses the monsoon heating. The monsoon is considered here to be triggered by land-sea sensible heating contrasts and is taken as a given. **This heating is deep convective in the Tropics, maximizing at 4 K day^-1 at 400 hPa, but becomes shallower in the extratropics. NPac includes the local cooling in the region of subtropical descent.** This is thought to be at least partly due to diabatic enhancement. **The cooling is greater than 1 K day^-1 between about 250 and 900 hPa, maximizing at -3 K day^-1 at about 700 hPa.** Here HC encloses the forcing in the 'local Hadley circulation' over the ocean."

**The equations — PARTIAL.** RH2001's equations are rendered as images on the AMS page (placeholders [IMG:i1520-0442-14-15-3192-e1] and [IMG:i1520-0442-14-15-3192-e2] in the cached text), so **I could not transcribe their algebraic form.** What the surrounding verbatim text establishes:

> **Eq. (1)** (file lines 700-701): the **time-mean thermodynamic energy equation in pressure coordinates** — "where T is temperature, t is time, Q is (diabatic) heating and cooling, cp is the specific heat of dry air at constant pressure, p is pressure, p0 is a standard constant pressure, kappa = R/cp, R is the gas constant for dry air, omega = vertical velocity Dp/Dt, theta = (p/p0)^(-kappa) T is potential temperature, and v is horizontal wind velocity. An overbar implies a time mean, and a prime signifies a deviation from the time mean. **On the seasonal timescale, the time-dependence term, A, is negligible.** ... the dominant balance is between the diabatic forcing, B, the mean 'vertical advection of theta', C, and the mean horizontal advection, D." (Note: the exponent is printed as "(p/p0)^(-kappa)" in the extracted HTML text; the standard definition is theta = (p/p0)^kappa T, so the sign in the extracted text is suspect — treat the exponent sign as UNVERIFIED and the definition as standard.)

> **Eq. (2)** (file line 709-710): "In agreement with **Sverdrup vorticity balance of the steady flow**, [IMG:e2] strong poleward flow is seen below the maximum ascent ... **In (2), upsilon is meridional wind, f is the Coriolis parameter, and beta is its meridional gradient.**" — i.e. beta*v = f * (divergence), the classic Sverdrup balance. The exact algebraic rendering is an image and is **UNVERIFIED**; the text and the numerical check that follows ("At 35 latitude, and assuming omega = 0 at the surface, Sverdrup balance would imply that 0.25 hPa h^-1 descent at 674 hPa would be accompanied by about 1 m s^-1 equatorward flow at 887 hPa", file line 732) are VERBATIM and consistent with beta*v = f*d(omega)/dp.

### 4.4 The 2001 mechanism — VERBATIM (RH2001 abstract, file lines 662-666)

> "The summer subtropical circulation in the lower troposphere is characterized by continental monsoon rains and anticyclones over the oceans. In winter, the subtropical circulation is more strongly dominated by the zonally averaged flow and its interactions with orography. Here, the mechanics of the summer and winter lower-tropospheric subtropical circulation are explored through the use of a primitive equation model and comparison with observations.
> **By prescribing in the model the heatings associated with several of the world's monsoons, it is confirmed that the equatorward portion of each subtropical anticyclone may be viewed as the Kelvin wave response to the monsoon heating over the continent to the west. A poleward-flowing low-level jet into a monsoon (such as the Great Plains jet) is required for Sverdrup vorticity balance.** This jet effectively closes off the subtropical anticyclone to the east and also transports moisture into the monsoon region. ...
> **The Rossby wave response to the west of subtropical monsoon heating, interacting with the midlatitude westerlies, produces a region of adiabatic descent. It is demonstrated here that a local 'diabatic enhancement' can lead to a strengthening of the descent. Longitudinal mountain chains act to block the westerly flow and also tend to produce descent in this region. Below the descent, Sverdrup vorticity balance implies equatorward flow that closes off the subtropical anticyclone to the west and induces cool upwelling in the ocean through Ekman transport.** ... The conclusion is that **the Mediterranean-type climates of regions such as California and Chile may be induced remotely by the monsoon to the east.**"

Also VERBATIM (RH2001 section 1, file line 685), the explicit framing that the monsoon is taken as given:
> "Rodwell and Hoskins (1996), hereinafter RH, demonstrated that part of the summertime descent over the eastern Mediterranean and Sahara could be induced by forcing an atmospheric primitive equation model with Asian monsoon heating. **The monsoon itself was considered to be an inevitable consequence of land-sea contrasts in sensible heating and therefore was taken as a given.** The descent was seen as the result of the interaction between the westward-propagating Rossby wave response to the heating and the mean westerly flow on its poleward side. **The descent was highly sensitive to the latitude of the heating, being negligible for pre-Asian monsoon heating that is centered nearer the equator at 10N.** ... The magnitude of the descent forced by the monsoon and mountains was about one-half that observed, and RH argued that this adiabatic descent would imply a reduction in relative humidity and convection and a lowering of the level of radiative emission to space that could lead to a local diabatic enhancement of the descent over the eastern Mediterranean and Sahara. In contrast, RH showed that the cooling in the descent region had little or no effect on the Asian monsoon."

And, crucially for a **steady** model, RH2001's own justification for NOT using a linear steady model (file line 690):
> "**Theoretical investigations of the stationary waves usually depend on a linearization about a zonal-mean westerly flow. Such a linearization is clearly questionable for the subtropics, particularly in the summer hemisphere. The June-August zonal-mean winds between 10 and 30N throughout the troposphere generally lie in the range -5 to +5 m s^-1. The critical line at which it is zero will clearly play a central role in any linearized, steady-state model.** In Hoskins and Rodwell (1995) it was shown that initial value integrations with a primitive equation model gave a realistic nonlinear response to imposed heating fields in the presence of mountains."

### 4.5 RH2001 summary
| Aspect | RH2001 (and RH1996) |
|---|---|
| **Prescribed** | Zonal-mean basic state (from ECMWF climatology); orography (raised over first 5 days); constant diabatic heating fields (idealized elliptical or observed residual); drag coefficients; optionally Newtonian relaxation to the basic state; zonal means of vorticity, divergence, temperature and surface pressure held fixed. |
| **Solved (prognostic)** | Full nonlinear 3-D fields, time-integrated. |
| **Grid/resolution** | Spectral T31 horizontal, 15 sigma levels, finite differences in the vertical. |
| **Cost** | Not stated in the paper. (UNVERIFIED.) |
| **Category** | **(b) requires time-stepping** — a quasi-steady state is read off at days 15-16 after heating onset. RH2001 explicitly argues against a linearized steady-state approach in the summer subtropics because of the critical line. |

---

## 5. Published LINEAR STEADY models forced by idealized land-sea / monsoon diabatic heating

I found fewer *strictly linear-and-steady* monsoon/desert models than the assignment hoped. Here is the honest inventory. **Two of these are genuinely linear + steady; the rest are nonlinear/idealized or time-stepping.**

### 5.1 Gill (1980) — linear, steady, forced by prescribed heating. **Category (a).**
See section 7 for the full treatment. Gill's own abstract (VERBATIM, Crossref) explicitly includes a monsoon application:
> "Another model solution with the heating displaced north of the equator provides a flow similar to the monsoon circulation of July and a simple model solution can also be found for heating concentrated along an inter-tropical convergence line."

**What is forced:** a prescribed mass/heat source Q in the continuity equation. **What is found:** Kelvin-wave easterlies east of the forcing, Rossby-wave westerlies west of it, poleward low-level flow within the heating. **Linear? Yes. Steady? Yes** (the steady problem is obtained by dropping d/dt; see section 7). **Desert? No** — Gill does not produce a subtropical desert; that requires the *interaction with the midlatitude westerlies* on the poleward flank of an off-equatorial Rossby response, which is Rodwell & Hoskins' contribution.

### 5.2 Rodwell & Hoskins (1996, 2001) — nonlinear PE, time-integrated. **Category (b).**
See section 4. This is the canonical "monsoon-desert mechanism" reference. **Note: it is NOT a linear steady model.** RH2001 section 2 explicitly rejects the linear steady route for the summer subtropics (critical line).

### 5.3 A genuinely linear, zonally-symmetric-basic-state stationary wave model solving the monsoon/desert problem — the LBM SWM mode
The University of Tokyo LBM's **SWM** mode is a linear steady stationary-wave model with a zonal-mean basic state solved by block matrix inversion (section 2.5c). It is *designed* for exactly the "linear response to prescribed thermal/orographic forcing" question, and the manual explicitly advertises reduction in cost via removing wave-wave interaction. **It is the closest documented ready-made tool to the assignment's target model.** Category **(a) steady-state solvable**. Its limitation relative to the target: it is spectral (spherical harmonics), not a lon-lat grid model; and by construction it excludes wave-wave interaction.

### 5.4 Rupp & Haynes (2021) — idealized 3-D dry model with a locally confined steady heat source. **Category (b).**
> Rupp, P., and P. Haynes, 2021: Zonal scale and temporal variability of the Asian monsoon anticyclone in an idealised numerical model. Weather and Climate Dynamics, 2, 413-431. doi:10.5194/wcd-2-413-2021 (Copernicus, open access — I downloaded the discussion preprint wcd-2020-64-manuscript-version2.pdf.)

VERBATIM from the abstract (file research\pdfs\wcd_monsoon_anticyclone.txt):
> "The upper-level monsoon anticyclone is studied in a 3-D dry dynamical model as the response of a background circulation without any imposed zonal structure to a steady imposed zonally confined heat source. ... For a resting background state the time-mean anticyclone is highly extended in longitude to the west of the forcing region. When the active mid-latitude dynamics is included the zonal extent of the time-mean anticyclone is limited, **without any need for the explicit upper-level momentum dissipation which is often included in simple theoretical models, but difficult to justify physically.**"
> "Unlike the Gill-Matsuno model (see Section 1), **our model does not include any mechanical friction above the boundary layer**" (file line 381).
> "... the response to a steady localised forcing in a 3D numerical model show evidence for westward eddy shedding ..." (file line 205).

**Note the top-of-file framing:** "without any imposed zonal structure to a steady imposed zonally confined heat source" — i.e. it uses a *steady* forcing but a *time-integrated* model. **Category (b).** Relevant because it directly questions the Rayleigh-friction crutch used by Gill-type steady models.

### 5.5 Zhou & Xie (2018) — idealized monsoons with idealized land-sea geometry. **Category (b).**
> Zhou, W., and S.-P. Xie, 2018: A Hierarchy of Idealized Monsoons in an Intermediate GCM. J. Climate, 31(22), 9021-9036. doi:10.1175/JCLI-D-18-0084.1 (open copy retrieved from eScholarship, saved as research\pdfs\wu_battisti_sarachik2000.txt — **note: this filename is misleading, the PDF is the Zhou & Xie paper**.)

VERBATIM from the abstract:
> "A hierarchy of idealized monsoons with increased degrees of complexity is built using an intermediate model with simplified physics and **idealized land-sea geometry**. This monsoon hierarchy helps formulate a basic understanding about the distribution of the surface equivalent potential temperature theta_e, which proves to provide a general guide on the monsoon rainfall. The zonally uniform monsoon in the simplest aquaplanet simulations is explained by a **linearized model of the meridional distribution of theta_e**, which is driven by the seasonally varying solar insolation and damped by both the monsoon overturning circulation and the local negative feedback. ... Monsoons with a zonally confined continent can be understood based on the zonally uniform monsoon by considering the ocean influence on the land through the westerly jet advection, which reduces the monsoon extent and induces zonal asymmetry."

**Category (b)** for the model hierarchy (an intermediate GCM, time-integrated); but it *contains* a linearized diagnostic sub-model of the zonal-mean monsoon. It does **not** produce a desert.

### 5.6 Wu, Battisti & Sarachik — linear steady tropical heating with Rayleigh friction and Newtonian cooling. **Category (a) in principle — EQUATIONS NOT RETRIEVED.**
> Wu, Z., D. S. Battisti, and E. S. Sarachik, 2000: Rayleigh Friction, Newtonian Cooling, and the Linear Response to Steady Tropical Heating. J. Atmos. Sci., 57(12), 1937-1957. doi:10.1175/1520-0469(2000)057<1937:RFNCAT>2.0.CO;2 (Crossref verified.)
> Wu, Z., E. S. Sarachik, and D. S. Battisti, 2001: Thermally Driven Tropical Circulations under Rayleigh Friction and Newtonian Cooling: Analytic Solutions. J. Atmos. Sci., 58(7), 724-741. doi:10.1175/1520-0469(2001)058<0724:TDTCUR>2.0.CO;2 (Crossref verified.)

**These are the most directly on-point titles I found for "linear steady tropical heating + Rayleigh friction + Newtonian cooling".** Both are AMS and closed; OpenAlex/Semantic Scholar report no OA copy. **I could NOT retrieve their contents, so their equations and findings are UNVERIFIED.** I flag them as the top priority for follow-up retrieval because the titles match the target model exactly.

### 5.7 Other verified, relevant-but-not-retrieved items
> Cherchi, A., H. Annamalai, S. Masina, and A. Navarra, 2016: Twenty-first century projected summer mean climate in the Mediterranean interpreted through the monsoon-desert mechanism. Climate Dynamics, 47, 2361-2371. doi:10.1007/s00382-015-2968-4 (OpenAlex verified; oa_status: closed, so **content UNVERIFIED**.) — This is a *downstream application* of the RH96 monsoon-desert mechanism to future projections, not a new steady model.
> Hoskins, B. J., and M. J. Rodwell, 1995: A Model of the Asian Summer Monsoon. Part I: The Global Scale. J. Atmos. Sci., 52, 1329-1340.
> Rodwell, M. J., and B. J. Hoskins, 1995: A Model of the Asian Summer Monsoon. Part II: Cross-Equatorial Flow and PV Behavior. J. Atmos. Sci., 52, 1341-1356.

### 5.8 Bottom line for section 5
For a **steady 2-D lon-lat model of a monsoon + subtropical desert forced by an idealized land-sea heating contrast**, the literature gives you:
1. **Gill (1980)** — exact linear steady analytic solution for a localized heat source; gives the monsoon-like flow for off-equatorial heating, but **no desert**.
2. **Rodwell & Hoskins (1996, 2001)** — the desert mechanism, but obtained with a **nonlinear, time-integrated PE model**, not a steady linear solve. The mechanism needs the *interaction of the Rossby response with the midlatitude westerlies*, which is a basic-state-dependent effect.
3. **LBM in SWM mode** — a working, documented linear steady solver for exactly this class of prescribed-forcing problem.
4. **RH2001's own caveat** is the key design constraint: with summer subtropical zonal-mean winds in -5 to +5 m/s, **the critical line dominates any linearized steady-state model**. A steady 2-D model will need either a basic state without a critical line, or strong enough damping, or acceptance that the linear steady solution is a diagnostic of the *adiabatic* part only.

---

## 6. Was there a "Linear Atmospheric Model" (LAM) intercomparison project?

**I could not find one. I report this honestly rather than guessing.**

Searches performed (web_search, multiple phrasings): "Linear Atmospheric Model intercomparison project", "linear model intercomparison stationary waves atmosphere project", "Linear Atmospheric Model LAM intercomparison project stationary waves models compared", plus OpenAlex/Crossref title searches. **No such project, acronym, or multi-model comparison of linear stationary wave models appeared.** The only intercomparison-adjacent items surfaced were:
- a single-model comparison of a GCM against a linear model (e.g. "The Stationary Response to Large-Scale Orography in a General Circulation Model and a Linear Model", J. Atmos. Sci. 1992, ADS bibcode 1992JAtS...49..525C — **only the ADS record was seen; the paper itself was not retrieved and is UNVERIFIED**);
- "Interactions between stationary waves and ice sheets: linear versus nonlinear atmospheric response" (Clim. Dyn., doi:10.1007/s00382-011-1004-6 — again **only aggregator records seen, content UNVERIFIED**).

**Conclusion: UNVERIFIED / NOT FOUND. There is no evidence in the sources I could reach of a formal "LAM intercomparison project" or any coordinated multi-model intercomparison of linear stationary wave models.** If one exists it is not indexed under those names in the venues I could query. Related large-scale intercomparison projects that are real (AMIP, CMIP, CFMIP, APE) compare *GCMs*, not linear stationary wave models.

---

## 7. Gill (1980), Q. J. R. Meteorol. Soc. 106, 447-462

### 7.1 Citation (VERIFIED)
> Gill, A. E., 1980: Some simple solutions for heat-induced tropical circulation. Q. J. R. Meteorol. Soc., 106(449), 447-462. doi:10.1002/qj.49710644905 (also registered as 10.1256/smsqj.44904)
> — Crossref verified: title, container, volume 106, issue 449, pages 447-462, author A. E. Gill. OpenAlex oa_status: closed; Semantic Scholar isOpenAccess: False. **The original PDF is not retrievable in this environment (Wiley blocked, no OA copy, no Wayback snapshot found).**

### 7.2 The abstract — VERBATIM (retrieved from Crossref)

> "A simple analytic model is constructed to elucidate some basic features of the response of the tropical atmosphere to diabatic heating. In particular, there is considerable east-west asymmetry which can be illustrated by solutions for heating concentrated in an area of finite extent. This is of more than academic interest because heating in practice tends to be concentrated in specific areas. For instance, a model with heating symmetric about the equator at Indonesian longitudes produces low-level easterly flow over the Pacific through propagation of Kelvin waves into the region. It also produces low-level westerly inflow over the Indian Ocean (but in a smaller region) because planetary waves propagate there. In the heating region itself the low-level flow is away from the equator as required by the vorticity equation. The return flow toward the equator is farther west because of planetary wave propagation, and so cyclonic flow is obtained around lows which form on the western margins of the heating zone. Another model solution with the heating displaced north of the equator provides a flow similar to the monsoon circulation of July and a simple model solution can also be found for heating concentrated along an inter-tropical convergence line."

### 7.3 The equations — what is VERIFIABLE about them

Because I could not open the original, I give the equations in two independent, citable forms: (i) the **spherical analogue** stated in a peer-reviewed paper that explicitly maps onto Gill's numbering, and (ii) the **standard equatorial beta-plane form** as reproduced in two independent teaching sources. **I mark the second as secondary-source, not from the original.**

**(i) Peer-reviewed statement about what Gill (1980) equations (2.6)-(2.8) are.**
Source: Shamir, O., C. I. Garfinkel, E. P. Gerber, and N. Paldor, 2023: The Matsuno-Gill model on the sphere. J. Fluid Mech., 964, A32. doi:10.1017/jfm.2023.369 (open access, downloaded to research\pdfs\matsuno_gill_sphere.txt).

VERBATIM (p. 964 / A32-6, file lines 326-332):
> "which we shall refer to as the Matsuno-Gill model on the sphere. Aside from some trivial changes associated with the choice of scaling and the spherical geometry, this is precisely the fundamental system studied in the planar versions of the model, i.e. **system (30) of Matsuno (1966), and equations (2.6)-(2.8) of Gill (1980)**. Note, however, that **Gill subsequently imposed the long-wave approximation, which is tantamount to neglecting -gamma*v on the right-hand side of (2.3b)**."

VERBATIM (p. 964 / A32-5, their eqs 2.1a-c) — the **forced-dissipated rotating shallow-water equations** in non-dimensional spherical form:
> "d u/d t - epsilon^(1/2) v sin(phi) + (1/cos phi) dPhi/dlambda = - gamma u,   (2.1a)
>  d v/d t + epsilon^(1/2) u sin(phi) + dPhi/dphi = - gamma v,   (2.1b)
>  dPhi/d t + (1/cos phi) [ d u/dlambda + d/dphi (v cos phi) ] = - gamma Phi + Q,   (2.1c)"
> "... Phi denotes the geopotential height anomaly ... **gamma > 0 denotes the damping/cooling coefficient**, Q = Q(lambda, phi) is a prescribed forcing ..., and epsilon is the Lamb number, defined by epsilon = (2 Omega a)^2 / (g H)."

VERBATIM (their description of the dissipation assumptions, p.964/A32-5, file lines 242-249):
> "(i) There is no momentum forcing, only a prescribed 'heat/mass' source added to the continuity equation. ... (ii) **We let the dissipative terms in the momentum equations and the continuity equation take the forms of Rayleigh friction and Newtonian cooling, respectively, and assume that they can all be characterized by the same time scale.** While such forms of dissipation are likely not the most suitable ones for any particular application, as noted by Gill (1980), they are the simplest."

VERBATIM (their steady system, eqs 2.3a-c, obtained by setting d/dt = 0, file lines 311-325):
> "**The stationary system is obtained by setting d/dt equal to zero in system (2.1a-c)**, yielding
>  -epsilon^(1/2) v sin(phi) + (1/cos phi) dPhi/dlambda = - gamma u,   (2.3a)
>  epsilon^(1/2) u sin(phi) + dPhi/dphi = - gamma v,   (2.3b)
>  (1/cos phi)[ d u/dlambda + d/dphi (v cos phi) ] = - gamma Phi + Q,   (2.3c)"

VERBATIM (their beta-plane reduction, eqs 5.6a-c, file lines 843-848) — this is the equatorial beta-plane form, in Matsuno's scaling (which "differs from Gill's by a factor of sqrt(2)", their file line 780-782):
> "-v_k y + i k Phi_k = - alpha u_k,   (5.6a)
>  u_k y + dPhi_k/dy = - alpha v_k,   (5.6b)
>  i k u_k + d v_k/dy = - alpha Phi_k + Qtilde_k,   (5.6c)"
> "... which is identical to system (30) of Matsuno (1966) with Fx = Fy = 0."

**(ii) The standard equatorial beta-plane form of the "Gill model" as used in the literature (SECONDARY SOURCES — treat the exact signs/scaling as needing confirmation against the original).**

Source A — MPI-M wiki, "Numerical Matsuno-Gill Model" (research\pdfs\mpi_gill_wiki.txt):
> "This model is based on the works of Matsuno (1966) and Gill (1980). 3 prognostic variables u, v and p on a 2-dimensional spatial domain are integrated in time forced by a convective heating Q.
>  du/dt + epsilon u - (1/2) y v = - dp/dx
>  dv/dt + epsilon v + (1/2) y u = - dp/dy
>  dp/dt + epsilon p + du/dx + dv/dy = Q"
> "Note that here **the sign of the forcing is reversed compared to Gill 1980**, so that a positive heating induces a positive geopotential or layer thickness anomaly (in contrast to negative anomalies corresponding to a low pressure system at the surface)."

Source B — LMD/IPSL master's course notes, "Variabilite climatique dans les Tropiques", C. Frankignoul, 2012-2013, Chap. 4 (research\pdfs\lmd_tropics_chap4.txt):
> "Gill (1980) introduced a very simple model of the steady response of the tropical atmosphere to deep diabatic heating that has been widely used. The main assumptions are
> - the response can be explained by the linear equations around a basic state at rest with mechanical damping and radiative cooling.
> - The heating is taken to be a half sinusoid, which is obtained by considering a normal mode. The vertical distribution of the response is assumed to be that of the diabatic heating.
> - This implicitly assumes that there is a rigid lid at the troposphere."
> "... **Gill uses c = 70 m/s.** ... In the model, **the Rayleigh damping and the Newtonian cooling are taken to be identical** and, to get realistic zonal scales, **very strong, epsilon^-1 ~ 1.5 days** (for realistic friction the zonal scales would be much too large)."
> "The response to equatorial forcing with a gaussian meridional distribution that projects onto the first Rossby mode and the Kelvin mode, and is centered around x = 0 and has the form cos kx for |x| < L and zero otherwise, is illustrated in Fig. 1 (Gill, 1980). Here **the signature of a forced damped Kelvin wave is seen to the right (eastward group velocity) and that of a damped symmetric Rossby mode (westward group velocity) is seen to the left.** The Kelvin mode shows equatorial easterlies extending eastward a distance ... and the Rossby mode shows equatorial westerlies extending westward a distance ..., as expected in a steady state and consistent with the faster propagation speed of the Kelvin wave."
> "**The heating, which primarily takes place above the cloud base, is artificially brought down to the surface.** ... **The model only has one vertical mode, but the implicit rigid lid approximation is arbitrary, and its height influences the solution.** ... **the damping times are much too short for tropospheric motions.**"

Source C — Niklas Schneider, OCN666 course notes, "Forced tropical motions", Univ. of Hawaii (research\pdfs\gill_notes_hawaii.txt). VERBATIM:
> "Consider a layer of constant depth ... Forced atmospheric circulation. Interpret Q as convective latent heating that efficiently projects onto the first baroclinic mode. Assume long wave approximation and steady state response. Nondimensionalize equations by Rossby Radius L, L/c. **Gill, 1980, Quart. J. R. Met. Soc., 106, 447-462**
>  epsilon u - (1/2) y v = - d_x p
>  (1/2) y u = - d_y p
>  epsilon p + d_x u + d_y v = - Q"
> (the extraction lost the epsilon glyphs; the "epsilon" symbols are restored here from the standard form and are the one element of this transcription I am inferring)
> "Note that in the steady limit the flow field is divergent. With the transformation q = p + u, r = p - u and the expansion in Parabolic Cylinder functions D_n with the recursion relation ... and yields
>  epsilon q_0 + d_x q_0 = -Q_0
>  epsilon q_(n+1) - d_x q_(n+1) + v_n = -Q_(n+1), n >= 0
>  epsilon r_(n+1) + d_x r_(n+1) + n v_n = -Q_(n+1), n >= 1
> with q_1 = 0, r_(n+1) = -(n+1) q_(n+1), n >= 1"
> "Example of solution with Q = Q_0(x) D_0(y) [symmetric]: forced Kelvin wave q_0(x) = -e^(-epsilon x) * integral_x^inf dx' e^(epsilon x') F(x'); forced Rossby wave q_2(x) = -e^(3 epsilon x) * integral_{-inf}^x dx' e^(-3 epsilon x') F(x')"
> "**Kelvin wave: no response west of the forcing region, response decays with scale epsilon^-1 to the east, meridional structure is Gaussian. Rossby wave: no response east of the forcing region, response decays with (3 epsilon)^-1 to the west, meridional structure is D_1.** In response to heating there is poleward flow in the boundary layer due to vortex stretching."

**Restoring the epsilon glyphs in the three bullet equations, the canonical steady form is:**
    epsilon*u - (1/2)*y*v = -dp/dx
    (1/2)*y*u = -dp/dy            [long-wave approximation: the -epsilon*v term has been dropped]
    epsilon*p + du/dx + dv/dy = -Q

### 7.4 Gill (1980) summary

| Aspect | Gill (1980) |
|---|---|
| **Prescribed** | A prescribed heat/mass source Q(x, y) added to the continuity equation only (no momentum forcing). Rayleigh friction and Newtonian cooling, both with the same coefficient epsilon. Equivalent depth / gravity wave speed c (c = 70 m/s per the LMD notes). |
| **Solved (diagnostic, analytic)** | Steady u, v, p (or q = p+u, r = p-u). |
| **Method** | Analytic. Long-wave approximation; expansion in parabolic cylinder functions D_n in y and integration of first-order ODEs in x. Kelvin component east of the forcing (decay scale epsilon^-1, Gaussian meridional structure); first symmetric Rossby component (n=2) west of the forcing (decay scale (3 epsilon)^-1, D_1 meridional structure). |
| **Grid/resolution** | None — continuous analytic solution on the equatorial beta-plane. |
| **Computational cost** | None (closed form). |
| **Damping** | Rayleigh friction + Newtonian cooling, **assumed identical**, epsilon^-1 ~ 1.5 days (strong; LMD notes). Note this is *different* from the LBM, which uses separate vorticity/divergence/temperature timescales. |
| **Category** | **(a) steady-state solvable — analytically.** |
| **Monsoon?** | Yes — "a model solution with the heating displaced north of the equator provides a flow similar to the monsoon circulation of July" (verbatim abstract). |
| **Desert?** | No. Gill's model has no mechanism for a *subtropical* desert: on the beta-plane with an equatorially trapped response there is no midlatitude westerly basic state for the Rossby response to interact with. That step is Rodwell & Hoskins'. |

**UNVERIFIED in this section:** the exact layout/sign conventions and equation numbers as printed in the original Gill (1980) (only the *numbering* (2.6)-(2.8) is verified, via Shamir et al. 2023); the exact value of epsilon used by Gill himself (the "1.5 days" figure comes from the LMD lecture notes, not from Gill); the vertical structure function and the equivalent depth.

---

## 8. Cross-model comparison table

| Model | Prescribed | Solved (prognostic vs diagnostic) | Grid / resolution | Cost (as documented) | (a) steady / (b) time-step / (c) mixed |
|---|---|---|---|---|---|
| **Gill (1980)** | Q(x,y) in continuity eq.; Rayleigh friction + Newtonian cooling (equal coefficient epsilon); c = 70 m/s; long-wave approximation | Diagnostic, analytic: steady u, v, p (Kelvin + Rossby components) | none (analytic, equatorial beta-plane) | none | **(a) steady-state solvable analytically** |
| **LBM / SWM** (Watanabe & Kimoto 2000, 2001; manual v2.2) | Basic state X-bar; steady forcing F (thermal/orographic); ddragv/ddragd/ddragt damping (per-level, days); biharmonic horizontal diffusion (order 4, 6 h); vertical diffusion 1e-3 day^-1 | Diagnostic: X = L^-1 F for (vorticity, divergence, temperature, ln Ps). Time-integration mode also available but documented as only approximate. | Spectral T21 (483 total wavenumbers; m5/m10/m15 truncation options); sigma coordinates; L5/L8/L11/L20 | T21L20 full matrix 29643^2, >7.5 GB; T21L11m10 solve ~15 min on SR8000; matrix build ~1 h; SWM T21L20 largest block 2562^2 | **(a) for the steady solve** (matrix inversion is exact); package also offers (b) time integration |
| **Rodwell & Hoskins (1996, 2001)** | Zonal-mean basic state; orography; constant diabatic heating (elliptical, 25N 90E 400 hPa, 5 K/day in the canonical experiment); linear drag 1 d / 5 d (ocean), 0.25 d / 1.25 d (land) at sigma = 0.967/0.887; Newtonian relaxation 25 d (5 d in BL); optionally 4 d relaxation for the diabatic-enhancement experiment | **Prognostic, nonlinear**: full 3-D time integration; quasi-steady state sampled at days 15-16 | Spectral T31; 15 sigma levels; finite differences in the vertical | not stated (UNVERIFIED) | **(b) requires time-stepping**; the paper explicitly argues a linearized steady-state model is questionable in the summer subtropics (critical line) |
| **Ting & Held (1990) / Ting & Yu (1998) / Ting, Wang & Yu (2001) NSWM** | **UNVERIFIED** | **UNVERIFIED**, except that the LBM manual cites Ting & Held (1990) as an instance of the *linear matrix inversion* method for the steady forced problem | **UNVERIFIED** | **UNVERIFIED** | **(a) per the LBM manual's citation**; the NSWM (Ting, Wang & Yu 2001) is by construction a *nonlinear* stationary wave model, so it must be **(b)** |
| **Rupp & Haynes (2021)** | Steady, zonally confined heat source; thermal relaxation toward a meridionally varying state; no mechanical friction above the BL | Prognostic; time-mean over >=3000 days after 1000 days spin-up | 3-D dry dynamical model (resolution not extracted) | not extracted | **(b) requires time-stepping** |
| **Zhou & Xie (2018)** | Idealized land-sea geometry; seasonally varying insolation | Prognostic intermediate GCM (plus a linearized zonal-mean diagnostic sub-model) | intermediate GCM | not extracted | **(b)**, with a linear diagnostic component |

---

## 9. Source inventory (all files under K:\moder\EyeOfHarmonyBuffer\research\pdfs)

| File | What it is | How obtained |
|---|---|---|
| rodwell_hoskins2001_wb.txt | RH2001 full text (AMS landing page converted) — pre-existing cache; used heavily in section 4 | pre-existing |
| lbm_doc2.2.pdf / lbm_doc2.2.txt | U. Tokyo LBM Users' Guide v2.2 (91 pp.) — raw pypdf extraction (glyph names) | https://ccsr.aori.u-tokyo.ac.jp/~hiro/lbm/doc2.2.pdf |
| lbm_doc2.2_decoded.txt | Same, after glyph-substitution decoding | research\decode_lbm.py |
| lbm_note.pdf / lbm_note.txt / lbm_note_decoded.txt | Watanabe, "Note on AIM in LBM and an 'artificial response' problem", 06/28/05 | https://ccsr.aori.u-tokyo.ac.jp/~hiro/lbm/note-on-lbm.062805.pdf |
| lbm_doc_aim.pdf / lbm_doc_aim_decoded.txt | AIM documentation (6 pp.) | .../lbm/doc2.2.AIM.pdf |
| lbm_hs_note_decoded.txt, lbm_lsc_note_decoded.txt | Notes on basic-state initialization and the LSC scheme | .../lbm/hs-note-eng.pdf, lsc-note-eng.pdf |
| lbm_tokyo_page.txt, lbm_dir.txt, watanabe_home.txt | Distribution web page + directory listing + author page | ccsr.aori.u-tokyo.ac.jp |
| matsuno_gill_sphere.txt | Shamir, Garfinkel, Gerber & Paldor (2023), JFM 964 A32 — used in section 7.3 | Cambridge Core OA PDF |
| gill_notes_hawaii.txt | N. Schneider, OCN666 "Forced tropical motions" lecture notes | rcfftp.soest.hawaii.edu |
| lmd_tropics_chap4.txt | C. Frankignoul, "Variabilite climatique dans les Tropiques" ch.4 | master-mocis.lmd.jussieu.fr |
| mpi_gill_wiki.txt | MPI-M wiki "Numerical Matsuno-Gill Model" | wiki.mpimet.mpg.de |
| wcd_monsoon_anticyclone.txt | Rupp & Haynes (2021) WCD discussion preprint | wcd.copernicus.org |
| wu_battisti_sarachik2000.txt | **Content is actually Zhou & Xie (2018), J. Climate 31, 9021-9036** (filename is wrong — the eScholarship URL served a different "previously published works" item) | beta.escholarship.org |
| esd2020_mediterranean.txt | Barcikowska et al. (2020), ESD 11, 161-181 (downloaded; not analysed in depth) | esd.copernicus.org |
| nasa_19940010709.txt | NASA CR — turned out to be about Venus; **not relevant, do not cite** | ntrs.nasa.gov |
| ecmwf1983_gill.pdf/.txt | ECMWF 1983 report "Simulations of directly forced motions in the tropics using simple analytic models" — **scanned images, no text layer; could not be read** | ecmwf.int |
| hal_monsoon_deserts_html.txt | HAL landing page for "South Asian summer monsoon and subtropical deserts" — **blocked by an Anubis proof-of-work wall; content not obtained** | hal.science |

### Helper scripts written for this task
- research\decode_lbm.py — glyph-substitution decoder for the LBM manual's custom font
- research\meta.py, meta2.py, meta3.py, meta4.py — Crossref/OpenAlex metadata + abstract lookups
- research\oaq.py — OpenAlex search
- research\gettext.py — generic HTML/PDF -> text fetcher
- research\s2lookup.py — Semantic Scholar OA-PDF lookup
- research\absget.py — Crossref abstract retrieval
- research\reextract.py — re-run pypdf on a downloaded PDF

---

## 10. Consolidated list of things I could NOT verify

1. **Ting & Held (1990) model equations, damping coefficients, basic state, resolution and solver details.** Not retrieved (AMS blocked, no OA copy, no Wayback snapshot). The assignment's title "On the Stationary Flow of a Nonlinear Stationary Wave Model" **does not exist** as far as I can determine, and the assigned DOI suffix OVTFOA is invalid.
2. **The LBM's primitive-equation system as written** (delegated by the manual to Watanabe & Kimoto 2000/2001, both closed-access Wiley).
3. **RH1996's own model equations, resolution and damping** (Wiley, closed, no OA copy, no Wayback snapshot). Only its abstract (Crossref, verbatim) and its description inside RH2001 were obtained.
4. **The algebraic form of RH2001's eqs (1) and (2)** — rendered as images on the AMS page.
5. **Gill (1980)'s exact printed equations, his own value of epsilon, and his vertical structure function.** Only the equation *numbers* (2.6)-(2.8) are verified (via Shamir et al. 2023). The beta-plane forms quoted in section 7.3(ii) are from secondary teaching sources; the epsilon symbols in the Hawaii notes are my restoration.
6. **Wu, Battisti & Sarachik (2000) and Wu, Sarachik & Battisti (2001)** — content not retrieved (AMS, closed). Titles and full bibliographic data ARE verified.
7. **Any "Linear Atmospheric Model" (LAM) intercomparison project** — NOT FOUND. I found no evidence one exists.
8. **Cherchi et al. (2016)** content (closed access). Bibliographic data verified.
9. **RH2001 computational cost** — not stated in the paper.
10. **Exact resolution of Rupp & Haynes (2021)** — not extracted.
11. **The exponent sign in RH2001's potential-temperature definition** as extracted ("(p/p0)^(-kappa) T") is suspect; the standard definition is theta = (p/p0)^kappa T.
12. **The [IMG:...] equation placeholders** in the cached RH2001 text could not be resolved (the AMS equation images are not archived in a form I could reach).
