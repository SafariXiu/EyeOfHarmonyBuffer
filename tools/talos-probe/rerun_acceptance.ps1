# ASCII-ONLY (PowerShell 5.1 reads a BOM-less .ps1 as ANSI; a non-ASCII byte inside a
# comment or literal can swallow the next code line -- this file must stay ASCII).
#
# Acceptance re-run, PARALLEL -- with MEASUREMENT PROVENANCE (design phase B).
#
# WHY THE PROVENANCE BLOCK EXISTS: this script used to name its output directory
# <srcfp>_<state> and Force-remove every per-probe log on start, so a run in a DIFFERENT
# configuration silently DELETED the other configuration evidence. Measured 2026-09-19:
# one ON run erased the OFF baseline, and the reported "P292 92% -> 46% regression"
# turned out to be a change of WORLD, not of src. Design:
#   build/eoh_probe/mtn/measurement_provenance_design.md sections 2.2-2.6.
#
# WHAT IS NOW DIFFERENT (sections 2.2/2.3/2.5/2.6):
#   1. DIRECTORY = rerun_acceptance\<srcfp8>_<cfgfp8>_<WORLD>. Each of the three
#      segments comes from a MEASURED configuration (src fingerprint / cfgStamp / P900
#      world dump), so two different configurations cannot land in the same directory
#      and one run cannot delete another configuration evidence.
#   2. The per-probe log is ARCHIVED into prev\<name>.<runid>.txt instead of being
#      Force-deleted: a same-config re-run no longer destroys the previous reading.
#   3. runs.tsv is an APPEND-ONLY ledger, one tab-separated line per run.
#   4. Every log carries 3 ASCII evidence lines at its head (EOH_CFG_SRCFP/CFGFP/WORLD)
#      and every directory carries manifest.json (all dimensions + raw dump + params).
#   5. WORLD is what the JVM reports through P900, never an env guess. If the measured
#      world differs from the measured JAVA DEFAULT, the run is flagged
#      NON_PRODUCTION_CONFIG (a variant must be named explicitly with -Variant).
#
# WHY THE COLLECTOR RUNS TWICE: the first run names the directory (it must happen before
# the directory exists), the second runs AFTER the build and its CFGFP must be IDENTICAL.
# Without that check the switch vector in the identity could describe a STALE class tree
# while the probes ran on a freshly compiled one. Any difference ABORTS before a single
# probe starts (measured hazard: fingerprint.ps1 re-run after src edits, no rebuild yet).
#
# WHY PARALLEL: the battery is 20 probes that are COMPLETELY independent -- separate JVMs,
# separate output files, no shared state. Sequentially the wall time is the SUM; in
# parallel it is the MAX, bounded by P284.
#
# WHY NOT just call runprobe4.bat N times at once: that script does
#     if exist out rmdir /S /Q out & mkdir out & javac ... -d out
# so concurrent calls delete each other class tree WHILE another JVM is reading it.
# This script builds ONCE (runprobe4.bat <probe> --build-only), then launches plain
# `java -cp out probe.Pxxx` -- exactly what runprobe4.bat would have run.
#
# TWO PS 5.1 LANDMINES, both hit and fixed on 2026-09-16 (recorded in the design freeze):
#  (1) Start-Process builds a CASE-SENSITIVE env dictionary, so an environment block holding
#      BOTH NO_PROXY and no_proxy makes it throw. The probes do no network I/O, so the proxy
#      variables are dropped from this session before spawning.
#  (2) Start-Process -PassThru gives a Process object whose ExitCode reads back EMPTY, so the
#      runner does NOT use process handles at all: it polls the log file for a done marker.
#
# The probe list is defined HERE, and only here, so there is one list to keep true.
#
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File rerun_acceptance.ps1
#         ... -Jobs 4            limit concurrency
#         ... -Sequential        old behaviour (one at a time), for A/B of the runner
#         ... -Only P293,P294    run a SUBSET (flagged as a subset: NOT a full acceptance)
#         ... -Variant legacy    explicit variant tag, appended to the directory name
# EXIT:   0 = finished ; 2 = build failed ; 3 = stale SOURCE_FINGERPRINT (E81 guard) ;
#         5 = configuration could not be verified (fail CLOSED: no probe is started) ;
#         6 = bad -Only / -Variant argument
param(
  [int]$Jobs = 0,
  [switch]$Sequential,
  [string]$Only = '',
  [string]$Variant = ''
)
$ErrorActionPreference = 'Continue'
$rootDir  = 'K:\moder\EyeOfHarmonyBuffer'
$probeDir = Join-Path $rootDir 'tools\talos-probe'
$mtnDir   = Join-Path $rootDir 'build\eoh_probe\mtn'
$suiteDir = Join-Path $mtnDir 'rerun_acceptance'

# ---- landmine (1): normalise the environment BEFORE any Start-Process ----
foreach ($dropVar in 'no_proxy','http_proxy','https_proxy','all_proxy','NO_PROXY','HTTP_PROXY','HTTPS_PROXY','ALL_PROXY') {
  [System.Environment]::SetEnvironmentVariable($dropVar, $null, 'Process')
}

# ---- GUARD (E81, added 2026-09-17) --------------------------------------------------
# fingerprint.ps1 is the ONLY writer of SOURCE_FINGERPRINT.txt, and this script only READS it
# to name the output directory. Twice now a run was launched right after editing src/, so it
# was filed under a STALE fingerprint AND deleted the previous baseline logs. Refuse to run.
$srcFpPath = Join-Path $mtnDir 'SOURCE_FINGERPRINT.txt'
$srcFpTime = (Get-Item $srcFpPath).LastWriteTimeUtc
$newestSrc = Get-ChildItem (Join-Path $rootDir 'src') -Recurse -Filter *.java -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if ($newestSrc -and ($newestSrc.LastWriteTimeUtc -gt $srcFpTime)) {
  Write-Output ('STALE_FINGERPRINT: ' + $newestSrc.FullName)
  Write-Output ('  is newer than ' + $srcFpPath)
  Write-Output '  Run tools/talos-probe/fingerprint.ps1 first (design freeze E81). Refusing to run.'
  exit 3
}
$srcFpLine = Get-Content $srcFpPath -TotalCount 1
$srcFp     = ($srcFpLine -split '=')[1].Trim().Split(' ')[0]

# ---- CONFIGURATION COLLECTOR (dimensions D2/D3/D4/D6 -> cfgStamp) --------------------
# ONE recipe, ONE implementation: tools/talos-probe/fingerprint_config.ps1. Re-deriving the
# stamp here instead would let the two recipes drift apart, which is precisely how a
# measurement identity rots. It is run as a CHILD process so that its own
# $ErrorActionPreference and exit codes can never terminate this runner.
# Its stdout head keys are SPACE separated on ONE line (see the array-precedence note in
# fingerprint_config.ps1), so parse with a per-key regex, never by line position.
$collectorPath = Join-Path $probeDir 'fingerprint_config.ps1'
function Invoke-CfgCollector {
  $script:collectorText = @(& powershell -NoProfile -ExecutionPolicy Bypass -File $script:collectorPath 2>&1 | ForEach-Object { $_.ToString() })
  $script:collectorExit = $LASTEXITCODE
}
function Get-CfgValue([string]$cfgKey) {
  foreach ($cfgLine in $script:collectorText) {
    if ($cfgLine -match ($cfgKey + '=(\S+)')) { return $Matches[1] }
  }
  return ''
}
Invoke-CfgCollector
$cfgFp        = Get-CfgValue 'CFGFP'
$cfgSrcFp     = Get-CfgValue 'SRCFP'
$worldLabel   = Get-CfgValue 'WORLD'
$probeFp      = Get-CfgValue 'PROBEFP'
$switchCount  = Get-CfgValue 'SWITCHCOUNT'
$seedBare     = Get-CfgValue 'SEEDBARE'
$forgePart    = Get-CfgValue 'FORGECFG'
$envBlockVal  = Get-CfgValue 'ENVBLOCK'
# FAIL CLOSED. A directory named from a broken stamp is worse than no directory at all:
# it would look like provenance while proving nothing (measured 2026-09-19: a stale class
# tree produced SWITCHCOUNT=0 / WORLD=UNKNOWN and the collector still emitted a CFGFP).
if (($collectorExit -ne 0) -or ($cfgFp -notmatch '^[0-9A-Fa-f]{32}$') -or ($worldLabel -notmatch '^(TALOS|LEGACY)$')) {
  Write-Output ('CFG_COLLECTOR_FAILED exit=' + $collectorExit + ' CFGFP=[' + $cfgFp + '] WORLD=[' + $worldLabel + ']')
  $collectorText | Select-Object -Last 8 | ForEach-Object { Write-Output ('  CFG | ' + $_) }
  Write-Output 'REFUSING to create a baseline directory from an unverified configuration.'
  exit 5
}
if ($cfgSrcFp -ne $srcFp) {
  Write-Output ('CFG_SRC_MISMATCH guard=' + $srcFp + ' collector=' + $cfgSrcFp)
  Write-Output '  SOURCE_FINGERPRINT.txt changed while the stamp was being computed; re-run.'
  exit 5
}
$srcFp8 = $srcFp.Substring(0,8)
$cfgFp8 = $cfgFp.Substring(0,8)

# ---- PROBE LIST --------------------------------------------------------------------
$acceptList = @(
  'P258',   # 3  B1 Legendre anchors
  'P284',   # 3  B3 inland/ocean reversal        <- the long pole (about 55 min alone)
  'P296',   # 3  B2 mid-latitude belt
  'P268',   # -  B4 rain shadow (RETIRED instrument since 2026-09-13; diagnostic only)
  'P452',   # 3  B4 rain shadow (THE acceptance instrument: continuous weights)
  'P293',   # -  equator continuity
  'P292',   # 2  A3 eastern boundary
  'P285',   # 2  A2 western boundary
  'P442',   # 2  A1 sign rate
  'P294',   # 1  terrain (tile-bounded)
  'P295',   # 1  terrain / equator
  'P297',   # 1  snow line
  'P477',   # 2  A4/A5 warm-tongue scale + anomaly amplitude
  'P478',   # 2  A6 coverage
  'P479',   # 2  A7 west/east intensification ratio (production caliber)
  'P480',   # 2  A2 double-prime subtropical transport (psi_max x H_TOTAL)
  'P484',   # -  D72/D73 regression guard
  'P692',   # 4  section 486 named-station COASTAL gate (desert vs warm-current); P600 criterion
            #    WHY NAMED STATIONS: each has an unambiguous geographic identity, so no
            #    global bucketing is needed (design freeze section 402).
  'P683',   # 4  section 477 seasonal-cycle PHASE gate (in-sample + HOLDOUT); GPCP anchor
            #    WHY PHASE: it is set by the subsolar latitude and the land-sea thermal
            #    contrast, so no magnitude tuning can fake it (design freeze section 476).
  'P712'    # 4  section 518 GATE_QDIV_SIGN - regional MSE advection budget SIGN gate
            #    WHY SIGN: the literature criterion is only about the SIGN
            #    (monsoon EXPORTS MSE => Qdiv>0 ; desert IMPORTS MSE => Qdiv<0),
            #    so it needs NO Earth magnitude and cannot be tuned into passing.
            #    Baseline (current, FAILING): ASIA Qdiv=+144.787 P=3.525 ;
            #                                 SAHARA Qdiv=+202.371 P=4.029  (1/3)
)
$runList   = $acceptList
$isPartial = $false
if ($Only -ne '') {
  $runList = @()
  foreach ($onlyName in ($Only -split ',')) {
    $onlyOne = $onlyName.Trim().ToUpper()
    if ($onlyOne -eq '') { continue }
    # A typo must not silently turn a 20-gate acceptance into a 1-gate run.
    if (-not ($acceptList -contains $onlyOne)) {
      Write-Output ('UNKNOWN_PROBE ' + $onlyOne + ' -- -Only accepts acceptance-list names only')
      exit 6
    }
    $runList += $onlyOne
  }
  $isPartial = ($runList.Count -ne $acceptList.Count)
}
if ($runList.Count -eq 0) { Write-Output 'EMPTY_PROBE_LIST'; exit 6 }

# ---- variant tag (section 2.5): a variant must be named, and lands elsewhere ---------
$variantTag = ''
if ($Variant -ne '') {
  $variantTag = ($Variant -replace '[^A-Za-z0-9\-]', '')
  if ($variantTag -eq '') { Write-Output 'BAD_VARIANT_TAG'; exit 6 }
}
$runId   = Get-Date -Format 'yyyyMMdd-HHmmss'
$dirName = $srcFp8 + '_' + $cfgFp8 + '_' + $worldLabel
if ($variantTag -ne '') { $dirName = $dirName + '_V' + $variantTag }
$outDir  = Join-Path $suiteDir $dirName
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$startStamp = Get-Date -Format s
# The evidence lines (section 2.6) are pure ASCII; every log is cp936, and ASCII bytes are
# identical in cp936, so the 3 lines can be prepended at byte level without re-encoding.
$evidenceText = 'EOH_CFG_SRCFP=' + $srcFp + "`r`n" + 'EOH_CFG_CFGFP=' + $cfgFp + "`r`n" + 'EOH_CFG_WORLD=' + $worldLabel + "`r`n"

Write-Output ('OUT=' + $outDir)
Write-Output ('DIR=' + $dirName)
Write-Output ('RUNID=' + $runId)
Write-Output ('SRCFP=' + $srcFp)
Write-Output ('CFGFP=' + $cfgFp)
Write-Output ('WORLD=' + $worldLabel)

# ---- ARCHIVE, NEVER DELETE (section 2.3) ---------------------------------------------
# The old line was: if (Test-Path $log) { Remove-Item $log -Force }
# One re-run therefore destroyed the previous reading. Now the previous artifacts move to
# prev\<name>.<runid>.<ext>, which costs nothing and keeps the evidence.
$prevDir = Join-Path $outDir 'prev'
$archiveMoved = 0
$archiveList = @()
foreach ($oldItem in (Get-ChildItem -LiteralPath $outDir -File -ErrorAction SilentlyContinue)) {
  if ($oldItem.Name -notmatch '^(log_.*|err_.*|SUMMARY|build_log)\.(txt)$' -and $oldItem.Name -ne 'manifest.json') { continue }
  if (!(Test-Path $prevDir)) { New-Item -ItemType Directory -Path $prevDir | Out-Null }
  if ($oldItem.Name -match '^(?<stem>.+)\.(?<ext>[A-Za-z0-9]+)$') {
    $archName = $Matches['stem'] + '.' + $runId + '.' + $Matches['ext']
  } else {
    $archName = $oldItem.Name + '.' + $runId
  }
  try {
    Move-Item -LiteralPath $oldItem.FullName -Destination (Join-Path $prevDir $archName) -Force
    $archiveMoved = $archiveMoved + 1
    $archiveList += $archName
  } catch {
    Write-Output ('ARCHIVE_SKIP ' + $oldItem.Name + ' : ' + $_.Exception.Message)
  }
}
Write-Output ('ARCHIVED=' + $archiveMoved)

if ($Sequential) { $Jobs = 1 }
if ($Jobs -le 0) {
  $coreCount = [Environment]::ProcessorCount
  $osInfo    = Get-CimInstance Win32_OperatingSystem
  $freeGB    = $osInfo.FreePhysicalMemory / 1MB
  $byRam     = [math]::Floor($freeGB * 0.7 / 6.0)     # every JVM is -Xmx6g
  $Jobs      = [math]::Min($runList.Count, [math]::Min($coreCount, $byRam))
  if ($Jobs -lt 1) { $Jobs = 1 }
}
Write-Output ('JOBS=' + $Jobs)

$summary = Join-Path $outDir 'SUMMARY.txt'
Set-Content -Path $summary -Value ('ACCEPTANCE RE-RUN (parallel)  started=' + $startStamp) -Encoding ASCII
Add-Content -Path $summary -Value $srcFpLine -Encoding ASCII
Add-Content -Path $summary -Value ('DIR=' + $dirName) -Encoding ASCII
Add-Content -Path $summary -Value ('RUNID=' + $runId) -Encoding ASCII
Add-Content -Path $summary -Value ('SRCFP=' + $srcFp) -Encoding ASCII
Add-Content -Path $summary -Value ('CFGFP=' + $cfgFp) -Encoding ASCII
Add-Content -Path $summary -Value ('WORLD=' + $worldLabel) -Encoding ASCII
Add-Content -Path $summary -Value 'WORLD_SOURCE=JVM_P900_DUMP' -Encoding ASCII
Add-Content -Path $summary -Value ('PROBEFP=' + $probeFp) -Encoding ASCII
Add-Content -Path $summary -Value ('SWITCHCOUNT=' + $switchCount) -Encoding ASCII
Add-Content -Path $summary -Value ('SEEDBARE=' + $seedBare) -Encoding ASCII
Add-Content -Path $summary -Value ('ARCHIVED=' + $archiveMoved) -Encoding ASCII
Add-Content -Path $summary -Value ('JOBS=' + $Jobs) -Encoding ASCII
Add-Content -Path $summary -Value ('GATES=' + ($runList -join ',')) -Encoding ASCII
Add-Content -Path $summary -Value ('SUBSET=' + [int]$isPartial) -Encoding ASCII
if ($isPartial) {
  Add-Content -Path $summary -Value 'PARTIAL_SUBSET_NOT_A_FULL_ACCEPTANCE' -Encoding ASCII
  Write-Output ('PARTIAL_SUBSET n=' + $runList.Count + '/' + $acceptList.Count + ' -- this is NOT a full acceptance run')
}

# ---- LEDGER (section 2.3): append-only, never overwritten ---------------------------
$ledgerPath = Join-Path $suiteDir 'runs.tsv'
if (!(Test-Path $ledgerPath)) {
  # A single-quoted PS string does NOT expand `t, so the header is built by joining.
  $ledgerHead = @('# runid','started','srcfp','cfgfp','WORLD','JOBS','GATES','result','elapsed_s','dir','variant') -join "`t"
  Set-Content -Path $ledgerPath -Value $ledgerHead -Encoding ASCII
}

# ---- manifest.json (section 2.6): all dimensions + raw dump + run parameters --------
$manifestPath = Join-Path $outDir 'manifest.json'
function Save-Manifest([string]$runState, [string]$finishedAt, [int]$probeFailed, [int]$probeDone, [int]$elapsedSeconds) {
  # [IO.File]::ReadAllLines, NOT Get-Content: on PS 5.1 the FileSystem provider decorates
  # every returned string with note properties (PSPath/PSDrive/PSProvider/...) and
  # ConvertTo-Json serializes them -- PSProvider alone drags in ProviderInfo -> Module ->
  # Assembly, which produced a 276 MB manifest.json on 2026-09-19.
  $dumpRawLines = @()
  $dumpPath = Join-Path $script:mtnDir 'cfg_dump.txt'
  if (Test-Path -LiteralPath $dumpPath) { $dumpRawLines = [string[]][IO.File]::ReadAllLines($dumpPath) }
  $cfgFileText = ''
  $cfgFilePath = Join-Path $script:mtnDir 'CFG_FINGERPRINT.txt'
  if (Test-Path -LiteralPath $cfgFilePath) { $cfgFileText = [IO.File]::ReadAllText($cfgFilePath) }
  $manifestObj = [ordered]@{
    dir             = $script:dirName
    runid           = $script:runId
    status          = $runState
    started         = $script:startStamp
    finished        = $finishedAt
    srcFp           = $script:srcFp
    cfgFp           = $script:cfgFp
    srcFp8          = $script:srcFp8
    cfgFp8          = $script:cfgFp8
    world           = $script:worldLabel
    worldSource     = 'JVM_P900_DUMP'
    worldDefault    = $script:worldProd
    production      = $script:prodState
    probeFp         = $script:probeFp
    switchCount     = $script:switchCount
    seedBareLiterals= $script:seedBare
    forgeCfg        = $script:forgePart
    envBlock        = $script:envBlockVal
    variant         = $script:variantTag
    partialSubset   = $script:isPartial
    jobs            = $script:Jobs
    sequential      = [bool]$script:Sequential
    gates           = $script:runList
    archivedMoved   = $script:archiveMoved
    archivedFiles   = $script:archiveList
    evidenceStamped = $script:stampOk
    evidenceFailed  = $script:stampFail
    probeResults    = $script:probeResults
    probeFailed     = $probeFailed
    probeDone       = $probeDone
    elapsedSeconds  = $elapsedSeconds
    collectorExit   = $script:collectorExit
    collectorStdout = $script:collectorText
    cfgFingerprint  = $cfgFileText
    cfgDumpRaw      = $dumpRawLines
  }
  $manifestJson = $manifestObj | ConvertTo-Json -Depth 6
  [IO.File]::WriteAllText($script:manifestPath, $manifestJson + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}
$stampOk = 0
$stampFail = 0
$probeResults = @()
$worldProd = 'UNKNOWN'
$prodState = 'UNKNOWN'

# ---------- 1) build once ----------
$buildLog = Join-Path $outDir 'build_log.txt'
Write-Output '=== BUILD (staging + sync_check + precheck + javac, shared by all probes)'
Push-Location $probeDir
cmd /c ('runprobe4.bat ' + $runList[0] + ' --build-only > "' + $buildLog + '" 2>&1')
$buildCode = $LASTEXITCODE
Pop-Location
Add-Content -Path $summary -Value ('BUILD exit=' + $buildCode) -Encoding ASCII
if ($buildCode -ne 0) {
  Write-Output ('BUILD_FAIL exit=' + $buildCode + '  see ' + $buildLog)
  Add-Content -Path $summary -Value 'BUILD_FAIL' -Encoding ASCII
  Save-Manifest 'BUILD_FAIL' (Get-Date -Format s) -1 -1 -1
  exit 2
}
Write-Output '    build OK'

# ---------- 2) RE-COLLECT on the FRESH class tree, and require the same identity ------
Invoke-CfgCollector
$cfgFpFresh    = Get-CfgValue 'CFGFP'
$worldFresh    = Get-CfgValue 'WORLD'
$switchFresh   = Get-CfgValue 'SWITCHCOUNT'
$srcFpFresh    = Get-CfgValue 'SRCFP'
$probeFpFresh  = Get-CfgValue 'PROBEFP'
if (($collectorExit -ne 0) -or ($cfgFpFresh -ne $cfgFp) -or ($worldFresh -ne $worldLabel)) {
  Write-Output ('CFG_STALE_CLASS_TREE dir_cfgfp=' + $cfgFp + ' rebuild_cfgfp=' + $cfgFpFresh)
  Write-Output ('  dir_world=' + $worldLabel + ' rebuild_world=' + $worldFresh)
  Write-Output '  The stamp that names this directory does not describe the tree just built.'
  Write-Output '  NO probe was started. Re-run this script (the directory stays log-free, so'
  Write-Output '  a judge treats it as not-a-baseline).'
  Add-Content -Path $summary -Value 'CFG_STALE_CLASS_TREE' -Encoding ASCII
  Add-Content -Path $summary -Value ('CFGFP_NOW=' + $cfgFpFresh) -Encoding ASCII
  Save-Manifest 'ABORTED_STALE_CLASS_TREE' (Get-Date -Format s) -1 -1 -1
  exit 5
}
$probeFp      = $probeFpFresh
$switchCount  = $switchFresh
$seedBare     = Get-CfgValue 'SEEDBARE'

# ---- WORLD, MEASURED TWICE: the run world and the SOURCE-DEFAULT world --------------
# Section 2.5: the production default must NOT be hardcoded. Since section 567 the world is not
# selectable at run time at all any more -- it is the SOURCE constant PlateField.WORLD_IS_TALOS
# -- but it is still MEASURED rather than assumed, so a run whose class tree disagrees with this
# script is caught instead of being filed as production. (The old EOH_TALOS_TERRAIN env
# save/clear/restore is gone with the switch it overrode.)
function Get-DefaultWorld {
  $prodDump = Join-Path $script:mtnDir 'cfg_prodworld_dump.txt'
  $prodErr  = Join-Path $script:mtnDir 'cfg_prodworld_err.txt'
  if (Test-Path -LiteralPath $prodDump) { Remove-Item -LiteralPath $prodDump -Force }
  Push-Location $script:mtnDir
  & (Join-Path $script:probeDir 'run_one_probe.bat') 'P900' $prodDump $prodErr | Out-Null
  Pop-Location
  if (-not (Test-Path -LiteralPath $prodDump)) { return 'UNKNOWN' }
  $prodText = Get-Content -LiteralPath $prodDump -Raw
  if ($prodText -match 'PlateField\.WORLD_IS_TALOS=true')  { return 'TALOS' }
  if ($prodText -match 'PlateField\.WORLD_IS_TALOS=false') { return 'LEGACY' }
  return 'UNKNOWN'
}
$worldProd = Get-DefaultWorld
if ($worldProd -ne 'UNKNOWN') {
  if ($worldProd -eq $worldLabel) { $prodState = 'YES' } else { $prodState = 'NO' }
}
Add-Content -Path $summary -Value ('WORLDPROD=' + $worldProd) -Encoding ASCII
Add-Content -Path $summary -Value ('PRODUCTION=' + $prodState) -Encoding ASCII
if ($prodState -eq 'NO') {
  Add-Content -Path $summary -Value 'NON_PRODUCTION_CONFIG' -Encoding ASCII
  Write-Output '****************************************************************'
  Write-Output ('* NON_PRODUCTION_CONFIG: this run measures ' + $worldLabel + ', the JVM default is ' + $worldProd)
  Write-Output '* The reading is a VARIANT, not the production caliber. It is filed in its own
  Write-Output '* directory and flagged NON_PRODUCTION_CONFIG in SUMMARY.txt and manifest.json.
  Write-Output '****************************************************************'
}
if ($variantTag -ne '') {
  Add-Content -Path $summary -Value ('VARIANT=' + $variantTag) -Encoding ASCII
  Write-Output ('VARIANT=' + $variantTag)
}
Write-Output ('WORLDPROD=' + $worldProd + '  PRODUCTION=' + $prodState)
Add-Content -Path $summary -Value ('PROBEFP=' + $probeFp) -Encoding ASCII
Add-Content -Path $summary -Value ('SWITCHCOUNT=' + $switchCount) -Encoding ASCII
Save-Manifest 'RUNNING' '' -1 -1 -1
Write-Output ('MANIFEST=' + $manifestPath)

# ---------- 3) run all probes through a job pool ----------
$startTime = Get-Date
$pending = @()
$failedCount = 0
$doneCount = 0

function Add-EvidenceHeader([string]$evLogPath) {
  if (-not (Test-Path -LiteralPath $evLogPath)) { return $false }
  $evBytes = [IO.File]::ReadAllBytes($evLogPath)
  $evStamp = [Text.Encoding]::ASCII.GetBytes($script:evidenceText)
  if ($evBytes.Length -ge $evStamp.Length) {
    $evSame = $true
    for ($evIdx = 0; $evIdx -lt $evStamp.Length; $evIdx++) {
      if ($evBytes[$evIdx] -ne $evStamp[$evIdx]) { $evSame = $false; break }
    }
    if ($evSame) { return $true }
  }
  # BYTE-level prepend: the log is cp936 (Windows console redirect) and the 3 evidence
  # lines are pure ASCII, so the probe output survives byte for byte.
  for ($evTry = 0; $evTry -lt 5; $evTry++) {
    try {
      $evStream = New-Object System.IO.FileStream($evLogPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
      try {
        $evStream.Write($evStamp, 0, $evStamp.Length)
        $evStream.Write($evBytes, 0, $evBytes.Length)
      } finally { $evStream.Close() }
      return $true
    } catch {
      Start-Sleep -Milliseconds 300
    }
  }
  return $false
}

function Start-Probe([string]$probeName) {
  $probeLog = Join-Path $script:outDir ('log_' + $probeName + '.txt')
  $probeErr = Join-Path $script:outDir ('err_' + $probeName + '.txt')
  if (Test-Path -LiteralPath $probeLog) {
    Move-Item -LiteralPath $probeLog -Destination (Join-Path $script:prevDir ($probeName + '.late.' + $script:runId + '.txt')) -Force
  }
  # Launch through a tiny wrapper .bat rather than passing the whole command line to
  # Start-Process: an inline command line with quotes/redirection gets mangled by
  # Start-Process argument joining (hit and fixed 2026-09-16). The three arguments
  # here are plain tokens with no spaces.
  Start-Process -FilePath (Join-Path $script:probeDir 'run_one_probe.bat') `
    -ArgumentList @($probeName, $probeLog, $probeErr) -WorkingDirectory $script:mtnDir -WindowStyle Hidden
  return [pscustomobject]@{ Name = $probeName; Log = $probeLog }
}

function Finish-Probe($jobInfo, $poolStart) {
  $codeVal = 'unknown'
  foreach ($tailLine in (Get-Content $jobInfo.Log -Tail 6 -ErrorAction SilentlyContinue)) {
    if ($tailLine -match 'EOH_RUNNER_DONE=(\S+)\s+JAVA_EXIT=(\S+)') { $codeVal = $Matches[2] }
  }
  if (Add-EvidenceHeader $jobInfo.Log) {
    $script:stampOk = $script:stampOk + 1
  } else {
    $script:stampFail = $script:stampFail + 1
    Write-Output ('EVIDENCE_HEADER_FAILED ' + $jobInfo.Name)
  }
  Add-Content -Path $script:summary -Value ($jobInfo.Name + '  exit=' + $codeVal + '  JAVA_EXIT=' + $codeVal) -Encoding ASCII
  Write-Output ('    ' + $jobInfo.Name + ' done  exit=' + $codeVal + '  t+' + [int](((Get-Date) - $poolStart).TotalSeconds) + 's')
  if ($codeVal -ne '0') { $script:failedCount = $script:failedCount + 1 }
  $script:doneCount = $script:doneCount + 1
  $script:probeResults += ($jobInfo.Name + ' exit=' + $codeVal)
}

foreach ($oneProbe in $runList) {
  while ($pending.Count -ge $Jobs) {
    Start-Sleep -Milliseconds 1000
    $stillRunning = @()
    foreach ($jobItem in $pending) {
      if ((Test-Path $jobItem.Log) -and (Select-String -Path $jobItem.Log -Pattern 'EOH_RUNNER_DONE=' -Quiet -ErrorAction SilentlyContinue)) {
        Finish-Probe $jobItem $startTime
      } else { $stillRunning += $jobItem }
    }
    $pending = $stillRunning
  }
  $pending += Start-Probe $oneProbe
  Write-Output ('    started ' + $oneProbe + '  (running=' + $pending.Count + ')')
}
while ($pending.Count -gt 0) {
  Start-Sleep -Milliseconds 1000
  $stillRunning = @()
  foreach ($jobItem in $pending) {
    if ((Test-Path $jobItem.Log) -and (Select-String -Path $jobItem.Log -Pattern 'EOH_RUNNER_DONE=' -Quiet -ErrorAction SilentlyContinue)) {
      Finish-Probe $jobItem $startTime
    } else { $stillRunning += $jobItem }
  }
  $pending = $stillRunning
}

$elapsedSec  = [int](((Get-Date) - $startTime).TotalSeconds)
$finishStamp = Get-Date -Format s
Add-Content -Path $summary -Value ('FINISHED ' + $finishStamp + '  elapsed=' + $elapsedSec + 's  failed=' + $failedCount) -Encoding ASCII
Add-Content -Path $summary -Value ('EVIDENCE stamped=' + $stampOk + ' failed=' + $stampFail) -Encoding ASCII
Save-Manifest 'FINISHED' $finishStamp $failedCount $doneCount $elapsedSec

# ---- ledger row: appended AFTER the run, so it carries the real result and elapsed ---
# APPEND ONLY. A run killed mid-way therefore leaves manifest.json status=RUNNING and a
# SUMMARY.txt without a FINISHED line, which is exactly the evidence that it was killed.
$ledgerRow = @($runId, $startStamp, $srcFp, $cfgFp, $worldLabel, $Jobs, ($runList -join ','),
                ('failed=' + $failedCount), $elapsedSec, $dirName, $variantTag) -join "`t"
Add-Content -Path $ledgerPath -Value $ledgerRow -Encoding ASCII
Write-Output ('LEDGER=' + $ledgerPath)

Write-Output ('ELAPSED=' + $elapsedSec + 's  FAILED=' + $failedCount + '  DONE=' + $doneCount + '/' + $runList.Count)
Write-Output ('EVIDENCE_HEADERS ok=' + $stampOk + ' failed=' + $stampFail)
Write-Output 'ALL_DONE'
