# ASCII-ONLY (PowerShell 5.1 reads BOM-less .ps1 as ANSI; Chinese comments
# silently swallow the next code line -- this file must stay ASCII).
#
# Acceptance re-run, PARALLEL.
#
# WHY PARALLEL: the battery is 17 probes that are COMPLETELY independent -- separate JVMs,
# separate output files, no shared state. Sequentially the wall time is the SUM (about 82 min,
# of which P284 alone is about 55). In parallel it is the MAX, bounded by P284.
#
# WHY NOT just call runprobe4.bat N times at once: that script does
#     if exist out rmdir /S /Q out & mkdir out & javac ... -d out
# so concurrent calls delete each other's class tree WHILE another JVM is reading it.
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
#         ... -Jobs 4          limit concurrency
#         ... -Sequential      old behaviour (one at a time), for A/B of the runner itself
param(
  [int]$Jobs = 0,
  [switch]$Sequential
)
$ErrorActionPreference = 'Continue'
$root  = 'K:\moder\EyeOfHarmonyBuffer'
$probe = Join-Path $root 'tools\talos-probe'
$mtn   = Join-Path $root 'build\eoh_probe\mtn'

# ---- landmine (1): normalise the environment BEFORE any Start-Process ----
foreach ($v in 'no_proxy','http_proxy','https_proxy','all_proxy','NO_PROXY','HTTP_PROXY','HTTPS_PROXY','ALL_PROXY') {
  [System.Environment]::SetEnvironmentVariable($v, $null, 'Process')
}

$fpLine = Get-Content (Join-Path $mtn 'SOURCE_FINGERPRINT.txt') -TotalCount 1
$fp = ($fpLine -split '=')[1].Trim().Split(' ')[0]
$out = Join-Path $mtn ('rerun_acceptance\' + $fp)
if (!(Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }
Write-Output ('OUT=' + $out)

$list = @(
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
  'P484'    # -  D72/D73 regression guard
)

if ($Sequential) { $Jobs = 1 }
if ($Jobs -le 0) {
  $cores  = [Environment]::ProcessorCount
  $os     = Get-CimInstance Win32_OperatingSystem
  $freeGB = $os.FreePhysicalMemory / 1MB
  $byRam  = [math]::Floor($freeGB * 0.7 / 6.0)     # every JVM is -Xmx6g
  $Jobs   = [math]::Min($list.Count, [math]::Min($cores, $byRam))
  if ($Jobs -lt 1) { $Jobs = 1 }
}
Write-Output ('JOBS=' + $Jobs)

$summary = Join-Path $out 'SUMMARY.txt'
Set-Content -Path $summary -Value ('ACCEPTANCE RE-RUN (parallel)  started=' + (Get-Date -Format s)) -Encoding ASCII
Add-Content -Path $summary -Value $fpLine -Encoding ASCII
Add-Content -Path $summary -Value ('JOBS=' + $Jobs) -Encoding ASCII

# ---------- 1) build once ----------
$buildLog = Join-Path $out 'build_log.txt'
Write-Output '=== BUILD (staging + sync_check + precheck + javac, shared by all probes)'
Push-Location $probe
cmd /c ('runprobe4.bat ' + $list[0] + ' --build-only > "' + $buildLog + '" 2>&1')
$bc = $LASTEXITCODE
Pop-Location
Add-Content -Path $summary -Value ('BUILD exit=' + $bc) -Encoding ASCII
if ($bc -ne 0) {
  Write-Output ('BUILD_FAIL exit=' + $bc + '  see ' + $buildLog)
  Add-Content -Path $summary -Value 'BUILD_FAIL' -Encoding ASCII
  exit 2
}
Write-Output '    build OK'

# ---------- 2) run all probes through a job pool ----------
$java = (Get-Command java).Source
$t0 = Get-Date
$pending = @()
$failed = 0
$done = 0

function Start-Probe($name) {
  $log = Join-Path $script:out ('log_' + $name + '.txt')
  $err = Join-Path $script:out ('err_' + $name + '.txt')
  if (Test-Path $log) { Remove-Item $log -Force }
  # Launch through a tiny wrapper .bat rather than passing the whole command line to
  # Start-Process: an inline command line with quotes/redirection gets mangled by
  # Start-Process' argument joining (hit and fixed 2026-09-16). The three arguments
  # here are plain tokens with no spaces.
  Start-Process -FilePath (Join-Path $script:probe 'run_one_probe.bat') `
    -ArgumentList @($name, $log, $err) -WorkingDirectory $script:mtn -WindowStyle Hidden
  return [pscustomobject]@{ Name = $name; Log = $log }
}

function Finish-Probe($j, $started) {
  $code = 'unknown'
  foreach ($ln in (Get-Content $j.Log -Tail 6 -ErrorAction SilentlyContinue)) {
    if ($ln -match 'EOH_RUNNER_DONE=(\S+)\s+JAVA_EXIT=(\S+)') { $code = $Matches[2] }
  }
  Add-Content -Path $script:summary -Value ($j.Name + '  exit=' + $code + '  JAVA_EXIT=' + $code) -Encoding ASCII
  Write-Output ('    ' + $j.Name + ' done  exit=' + $code + '  t+' + [int](((Get-Date) - $started).TotalSeconds) + 's')
  if ($code -ne '0') { $script:failed = $script:failed + 1 }
  $script:done = $script:done + 1
}

foreach ($p in $list) {
  while ($pending.Count -ge $Jobs) {
    Start-Sleep -Milliseconds 1000
    $still = @()
    foreach ($j in $pending) {
      if ((Test-Path $j.Log) -and (Select-String -Path $j.Log -Pattern 'EOH_RUNNER_DONE=' -Quiet -ErrorAction SilentlyContinue)) {
        Finish-Probe $j $t0
      } else { $still += $j }
    }
    $pending = $still
  }
  $pending += Start-Probe $p
  Write-Output ('    started ' + $p + '  (running=' + $pending.Count + ')')
}
while ($pending.Count -gt 0) {
  Start-Sleep -Milliseconds 1000
  $still = @()
  foreach ($j in $pending) {
    if ((Test-Path $j.Log) -and (Select-String -Path $j.Log -Pattern 'EOH_RUNNER_DONE=' -Quiet -ErrorAction SilentlyContinue)) {
      Finish-Probe $j $t0
    } else { $still += $j }
  }
  $pending = $still
}

$el = [int](((Get-Date) - $t0).TotalSeconds)
Add-Content -Path $summary -Value ('FINISHED ' + (Get-Date -Format s) + '  elapsed=' + $el + 's  failed=' + $failed) -Encoding ASCII
Write-Output ('ELAPSED=' + $el + 's  FAILED=' + $failed + '  DONE=' + $done + '/' + $list.Count)
Write-Output 'ALL_DONE'