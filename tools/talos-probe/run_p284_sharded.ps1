# ASCII-ONLY (PowerShell 5.1 reads BOM-less .ps1 as ANSI; Chinese comments
# silently swallow the next code line -- this file must stay ASCII).
#
# D76: run P284 as N **x-column** shards IN PARALLEL, then merge the parts into ONE log that is
# byte-identical to the single-process run (only the five ms values differ, by construction).
#
# WHY X AND NOT Z (measured 2026-09-16): grad() probes GRAD = 500 km in BOTH axes.
#   z-shard: 500 km / TILE_Z(50 km) = 10 tile ROWS; a 3-row band still solves 3+20 = 23 of the
#            88 tile rows => every shard does about a third of the whole job. Measured: 986 s for
#            the low-z band and 1790 s+ for a high-z band (full run = 3313 s). FAILED.
#   x-shard: 500 km / TILE_X(100 km) = 5 tile COLUMNS; x spans 400 tile columns, so 8 shards of
#            50 columns + 10 reach = 60 => 15% of the whole job, and the load is naturally BALANCED
#            because every shard covers all 18 latitude rows.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File run_p284_sharded.ps1 [-Shards 8]
param(
  [int]$Shards = 8
)
$ErrorActionPreference = 'Continue'
$root = 'K:\moder\EyeOfHarmonyBuffer'
$probe = Join-Path $root 'tools\talos-probe'
$mtn  = Join-Path $root 'build\eoh_probe\mtn'
$dir  = Join-Path $mtn '_sharded'
if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
foreach ($v in 'no_proxy','http_proxy','https_proxy','all_proxy','NO_PROXY','HTTP_PROXY','HTTPS_PROXY','ALL_PROXY') {
  [System.Environment]::SetEnvironmentVariable($v, $null, 'Process')
}
# MUST match probe.P284. Checked against the source so it cannot silently drift.
$NX = 2001
$src = Get-Content (Join-Path $probe 'probe\P284.java') -Raw
if ($src -notmatch 'NX = 2001') {
  Write-Output 'ABORT: probe.P284 XSTEP/NX changed -- update this script'
  exit 3
}
# ---- shard boundaries ----
# MEASURED COST PROFILE (2026-09-16, 8 uniform x-shards): columns 0..999 took 118/119/126/130 s,
# while columns 1000..2000 took 831/875/911/>1676 s. The far-x half is roughly 20x more
# expensive PER COLUMN (the ocean warm window is x in [-MAX_D, +MAX_D] = [-10,000, +10,000] km,
# so beyond it every query discovers basins on demand). A uniform split therefore leaves the
# wall time to one far-x shard. The split below gives the cheap half only a quarter of the
# shards and spends the rest where the cost actually is.
$SPLIT_AT = 1000            # column where the measured cost kicks up (x = 20,000 km)
$SHARDS_NEAR = [int][math]::Max(1, [math]::Round($Shards * 0.25))
$SHARDS_FAR  = [int][math]::Max(1, $Shards - $SHARDS_NEAR)
$t0 = Get-Date
$parts = @()
$logs = @()
# Build only the BOUNDARY list (plain ints -- appending nested arrays via += ,@() tripped
# PowerShell 5.1 with 'op_Addition' on Object[]; a flat int list cannot have that problem).
$bounds = @()
for ($i = 0; $i -le $SHARDS_NEAR; $i++) { $bounds += [int][math]::Round($i * $SPLIT_AT / $SHARDS_NEAR) }
for ($i = 1; $i -le $SHARDS_FAR; $i++) { $bounds += [int][math]::Round($SPLIT_AT + $i * ($NX - $SPLIT_AT) / $SHARDS_FAR) }
$idx = 0
for ($b = 0; $b -lt $bounds.Count - 1; $b++) {
  $idx++
  $lo = $bounds[$b]; $hi = $bounds[$b + 1] - 1
  if ($hi -lt $lo) { continue }
  $part = Join-Path $dir ('part_' + $lo + '_' + $hi + '.txt')
  $slog = Join-Path $dir ('shard_' + $lo + '.log')
  $serr = Join-Path $dir ('shard_' + $lo + '.err')
  if (Test-Path $part) { Remove-Item $part -Force }
  if (Test-Path $slog) { Remove-Item $slog -Force }
  Start-Process -FilePath (Join-Path $probe 'run_p284_shard.bat') `
    -ArgumentList @($lo, $hi, $part, $slog) -WorkingDirectory $mtn -WindowStyle Hidden
  $parts += $part
  $logs  += $slog
  Write-Output ('    shard cols ' + $lo + '..' + $hi + ' -> ' + (Split-Path $part -Leaf))
}
while ($true) {
  $pending = 0
  foreach ($lg in $logs) {
    $ok = (Test-Path $lg) -and (Select-String -Path $lg -Pattern 'EOH_RUNNER_DONE=' -Quiet -ErrorAction SilentlyContinue)
    if (!$ok) { $pending++ }
  }
  if ($pending -eq 0) { break }
  Start-Sleep -Milliseconds 1000
}
foreach ($lg in $logs) {
  $line = (Get-Content $lg -Encoding Default | Where-Object { $_ -match 'EOH_RUNNER_DONE=' } | Select-Object -Last 1)
  Write-Output ('    ' + (Split-Path $lg -Leaf) + '  ' + $line + '  t+' + [int](((Get-Date) - $t0).TotalSeconds) + 's')
}
# ---- merge (MUST run from the probe tree root: -cp out is relative) ----
$logPath = Join-Path $dir 'merged_log.txt'
Push-Location $mtn
$cmd = 'java -Xmx2g -cp out probe.P284 merge ' + ($parts -join ' ') + ' > "' + $logPath + '" 2>&1'
& cmd /c $cmd
$mc = $LASTEXITCODE
Pop-Location
Add-Content -Path $logPath -Value ('EOH_RUNNER_DONE=' + $mc + ' JAVA_EXIT=' + $mc) -Encoding ASCII
$el = [int](((Get-Date) - $t0).TotalSeconds)
Write-Output ('MERGE exit=' + $mc + '   WALL=' + $el + 's   (shards=' + $parts.Count + ')')
Write-Output ('LOG=' + $logPath)
Write-Output 'ALL_DONE'