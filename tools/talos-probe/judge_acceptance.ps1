# 546 (P0-4) -- the MISSING MACHINE CONSUMER for the acceptance suite.
#
# WHY (audit face 4, item A4):
#   run_one_probe.bat:23 uses the JVM PROCESS EXIT CODE, while 258 of 262
#   JAVA_EXIT prints inside probes are HARDCODED "=0"  =>  SUMMARY.txt's
#   failed=0 holds STRUCTURALLY and means nothing.
#   The 20 gates' criteria live ONLY as log TEXT, with NO machine consumer.
#
# This script IS that consumer: it extracts a machine-readable verdict per gate
# and reports real PASS / FAIL / UNJUDGED counts. UNJUDGED is listed EXPLICITLY --
# "no criterion" is never silently counted as "passed".
#
# PROVENANCE (measurement_provenance_design.md section 2.4, added phase B):
#   A reading is judged only if it was produced by the SAME configuration as the tree
#   that is being judged. Three consequences, all deliberate:
#     1. the target directory name is PARSED for its cfgfp8 and compared with the
#        stamp RECOMPUTED from the current working tree; a difference is
#        CONFIG_MISMATCH and exit 4 -- judging never silently crosses a configuration;
#     2. the DEFAULT (no directory argument) is no longer "the newest directory" but
#        "the newest directory WHOSE CONFIGURATION MATCHES"; with no such directory it
#        reports NO_MATCHING_BASELINE and lists the candidates with their cfgfp;
#     3. the recomputation runs fingerprint_config.ps1 -NoRebuild and RESTORES every
#        file it touches, so judging has no side effects (and can never trigger the
#        `rmdir /S /Q out` rebuild under a live 55-minute P284 JVM).
#   Legacy directories (bare <32hex>, or <32hex>_OFF / <32hex>_ON, or anything else)
#   carry NO cfgfp => their configuration cannot be proven => they are never chosen
#   automatically and need -AllowStale, after which EVERY output line is tagged
#   [STALE-CONFIG].
#
# ASCII-ONLY ON PURPOSE: Windows PowerShell 5 reads BOM-less .ps1 as ANSI; a UTF-8
# Chinese string terminator then gets corrupted and the whole script fails to parse
# (measured 2026-09-19: parseErrors=5). Keep this file ASCII.
#
# USAGE:  powershell -File judge_acceptance.ps1                 # newest CONFIG-MATCHING dir
#         powershell -File judge_acceptance.ps1 <dirName>       # one specific dir
#         ... -AllowStale                                       # tolerate a config mismatch
# EXIT:   0 = all PASS ; 1 = some FAIL ; 2 = no FAIL but some UNJUDGED ;
#         3 = no baseline dir / no such dir / no config-matching dir ;
#         4 = CONFIG_MISMATCH (refused) ; 5 = the current stamp could not be recomputed
param([string]$Which = '', [switch]$AllowStale)

$ErrorActionPreference = 'Stop'
$jdBase = Join-Path $PSScriptRoot '..\..\build\eoh_probe\mtn\rerun_acceptance'
$jdMtn  = Join-Path $PSScriptRoot '..\..\build\eoh_probe\mtn'
$jdCollector = Join-Path $PSScriptRoot 'fingerprint_config.ps1'

if (-not (Test-Path $jdBase)) { Write-Output 'NO_BASELINE_DIR'; exit 3 }

# ---------- 1) RECOMPUTE the current configuration stamp, SIDE-EFFECT FREE ----------
# The collector writes four files. Their bytes are saved here and restored afterwards, so
# a judgement never changes what a later run would read back. -NoRebuild is mandatory:
# without it the collector recovers from a stale class tree by running runprobe4.bat,
# which begins with `rmdir /S /Q out` -- i.e. it would delete the class tree from under a
# live JVM.
$jdSnapNames = @('CFG_FINGERPRINT.txt','cfg_classes.txt','cfg_dump.txt','cfg_dump_err.txt')
$jdSnap = @{}
foreach ($jdSnapName in $jdSnapNames) {
  $jdSnapPath = Join-Path $jdMtn $jdSnapName
  if (Test-Path -LiteralPath $jdSnapPath) { $jdSnap[$jdSnapName] = [IO.File]::ReadAllBytes($jdSnapPath) }
  else { $jdSnap[$jdSnapName] = $null }
}
$jdLines = @(& powershell -NoProfile -ExecutionPolicy Bypass -File $jdCollector -NoRebuild 2>&1 | ForEach-Object { $_.ToString() })
$jdExit = $LASTEXITCODE
foreach ($jdSnapName in $jdSnapNames) {
  $jdSnapPath = Join-Path $jdMtn $jdSnapName
  if ($null -eq $jdSnap[$jdSnapName]) {
    if (Test-Path -LiteralPath $jdSnapPath) { Remove-Item -LiteralPath $jdSnapPath -Force }
  } else {
    [IO.File]::WriteAllBytes($jdSnapPath, $jdSnap[$jdSnapName])
  }
}
# The collector head keys are SPACE separated on ONE line (see the array-precedence note in
# fingerprint_config.ps1), so parse with a per-key regex, never by line position.
function Get-JdValue([string]$jdKey) {
  foreach ($jdLine in $script:jdLines) {
    if ($jdLine -match ($jdKey + '=(\S+)')) { return $Matches[1] }
  }
  return ''
}
$jdCfgFp   = Get-JdValue 'CFGFP'
$jdSrcFp   = Get-JdValue 'SRCFP'
$jdProbeFp = Get-JdValue 'PROBEFP'
$jdWorld   = Get-JdValue 'WORLD'
$jdSwitch  = Get-JdValue 'SWITCHCOUNT'
$jdSeed    = Get-JdValue 'SEEDBARE'
$jdForge   = Get-JdValue 'FORGECFG'
$jdEnv     = Get-JdValue 'ENVBLOCK'
if (($jdExit -ne 0) -or ($jdCfgFp -notmatch '^[0-9A-Fa-f]{32}$')) {
  Write-Output 'CANNOT_RECOMPUTE_CONFIG'
  Write-Output ('  fingerprint_config.ps1 -NoRebuild exit=' + $jdExit + ' CFGFP=[' + $jdCfgFp + ']')
  $jdLines | Select-Object -Last 6 | ForEach-Object { Write-Output ('  CFG | ' + $_) }
  Write-Output '  A judgement must compare against the CURRENT working tree, so there is no fallback:'
  Write-Output '  build the probe tree first (runprobe4.bat <probe> --build-only) and re-run.'
  exit 5
}
$jdFp8 = $jdCfgFp.Substring(0,8)
$jdSrc8 = ''
if ($jdSrcFp -match '^[0-9A-Fa-f]{32}$') { $jdSrc8 = $jdSrcFp.Substring(0,8) }

# ---------- 2) ENUMERATE AND CLASSIFY EVERY DIRECTORY BY ITS NAME -------------------
# NEW      : <srcfp8>_<cfgfp8>_<WORLD>[_<variant>]  -- carries a provable configuration
# LEGACYST : <32hex>_OFF | <32hex>_ON                 -- world labelled by the state suffix
# LEGACYFP : <32hex>                                  -- no state segment at all
# OTHER    : prefix_..., ..._seq, anything else
$jdAll = @()
foreach ($jdItem in (Get-ChildItem -LiteralPath $jdBase -Directory | Sort-Object LastWriteTime -Descending)) {
  $jdLogs = @(Get-ChildItem -LiteralPath $jdItem.FullName -Filter 'log_*.txt' -File -ErrorAction SilentlyContinue).Count
  $jdRec = [pscustomobject]@{
    Name = $jdItem.Name; Full = $jdItem.FullName; Time = $jdItem.LastWriteTime; Logs = $jdLogs;
    Kind = 'OTHER'; Src8 = ''; Cfg8 = ''; World = 'UNKNOWN'; Variant = ''
  }
  if ($jdItem.Name -match '^(?<nSrc>[0-9A-Fa-f]{8})_(?<nCfg>[0-9A-Fa-f]{8})_(?<nWorld>TALOS|LEGACY|UNKNOWN)(_(?<nVar>.+))?$') {
    $jdRec.Kind = 'NEW'
    $jdRec.Src8 = $Matches['nSrc'].ToUpper()
    $jdRec.Cfg8 = $Matches['nCfg'].ToUpper()
    $jdRec.World = $Matches['nWorld']
    if ($Matches['nVar']) { $jdRec.Variant = $Matches['nVar'] }
  } elseif ($jdItem.Name -match '^(?<nFp>[0-9A-Fa-f]{32})_(?<nState>OFF|ON)$') {
    $jdRec.Kind = 'LEGACYST'
    $jdRec.Src8 = $Matches['nFp'].Substring(0,8).ToUpper()
    if ($Matches['nState'] -ceq 'OFF') { $jdRec.World = 'LEGACY' } else { $jdRec.World = 'TALOS' }
  } elseif ($jdItem.Name -match '^(?<nFp>[0-9A-Fa-f]{32})$') {
    $jdRec.Kind = 'LEGACYFP'
    $jdRec.Src8 = $Matches['nFp'].Substring(0,8).ToUpper()
  }
  $jdAll += $jdRec
}

# ---------- 3) CHOOSE THE TARGET ----------------------------------------------------
$jdTarget = $null
if ($Which -ne '') {
  $jdTarget = $jdAll | Where-Object { $_.Name -eq $Which } | Select-Object -First 1
  if ($null -eq $jdTarget) { Write-Output ('NO_SUCH_DIR ' + $Which); exit 3 }
} else {
  # SECTION 2.4(2): newest AND configuration-matching. A directory with no log_*.txt is
  # not a baseline (an aborted run leaves one), so it can never be chosen either.
  $jdMatch = @($jdAll | Where-Object { $_.Kind -eq 'NEW' -and $_.Cfg8 -eq $jdFp8 -and $_.Logs -gt 0 })
  if ($jdMatch.Count -eq 0) {
    Write-Output 'NO_MATCHING_BASELINE'
    Write-Output ('  current cfgfp8=' + $jdFp8 + ' world=' + $jdWorld + '  (from a fresh P900 dump)')
    Write-Output '  candidates (newest first):'
    foreach ($jdCand in $jdAll) {
      $jdCfgShow = 'UNKNOWN'
      if ($jdCand.Cfg8 -ne '') { $jdCfgShow = $jdCand.Cfg8 }
      Write-Output ('    ' + $jdCand.Name.PadRight(46) + ' kind=' + $jdCand.Kind.PadRight(9) + ' world=' + $jdCand.World.PadRight(7) + ' cfgfp8=' + $jdCfgShow.PadRight(9) + ' logs=' + $jdCand.Logs.ToString().PadLeft(3) + '  ' + $jdCand.Time.ToString('yyyy-MM-dd HH:mm'))
    }
    Write-Output '  Run the acceptance suite in the current configuration, or judge a legacy'
    Write-Output '  directory explicitly with -AllowStale.'
    exit 3
  }
  $jdTarget = $jdMatch[0]
}

# ---------- 4) CONFIGURATION VERDICT ------------------------------------------------
$jdTag = ''
$jdStale = $false
function Write-JdLine([string]$jdText) {
  if ($jdText -eq '') { Write-Output ''; return }
  Write-Output ($script:jdTag + $jdText)
}

# dimension detail: only a NEW directory carries a manifest.json with every dimension
$jdManifest = $null
$jdManifestPath = Join-Path $jdTarget.Full 'manifest.json'
if (Test-Path -LiteralPath $jdManifestPath) {
  try { $jdManifest = (Get-Content -LiteralPath $jdManifestPath -Raw | ConvertFrom-Json) } catch { $jdManifest = $null }
}

$jdVerdict = 'MISMATCH'
$jdReason = ''
if ($jdTarget.Kind -eq 'NEW') {
  if ($jdTarget.Cfg8 -ceq $jdFp8) { $jdVerdict = 'MATCH' }
  else { $jdReason = 'directory cfgfp8 ' + $jdTarget.Cfg8 + ' != current cfgfp8 ' + $jdFp8 }
} elseif ($jdTarget.Kind -eq 'LEGACYST') {
  $jdReason = 'legacy directory: no cfgfp in the name (only the state suffix ' + $jdTarget.World + ')'
} elseif ($jdTarget.Kind -eq 'LEGACYFP') {
  $jdReason = 'legacy directory: bare <32hex>, neither cfgfp nor world recoverable'
} else {
  $jdReason = 'directory name does not follow either naming scheme'
}

if ($jdVerdict -ne 'MATCH') {
  $jdLines2 = @()
  $jdLines2 += 'CONFIG_MISMATCH'
  $jdLines2 += ('  dir          = ' + $jdTarget.Name)
  $jdLines2 += ('  reason       = ' + $jdReason)
  $jdLines2 += ('  cfgfp8  dir  = ' + $(if ($jdTarget.Cfg8 -ne '') { $jdTarget.Cfg8 } else { 'UNKNOWN' }) + '   now = ' + $jdFp8)
  $jdLines2 += ('  world   dir  = ' + $jdTarget.World + '   now = ' + $jdWorld)
  $jdLines2 += ('  srcfp8  dir  = ' + $(if ($jdTarget.Src8 -ne '') { $jdTarget.Src8 } else { 'UNKNOWN' }) + '   now = ' + $jdSrc8)
  if ($null -ne $jdManifest) {
    $jdLines2 += '  dimension detail (dir manifest.json vs a fresh collector run):'
    $jdPairs = @(
      @('cfgFp',            $jdManifest.cfgFp,            $jdCfgFp),
      @('srcFp',            $jdManifest.srcFp,            $jdSrcFp),
      @('probeFp',          $jdManifest.probeFp,          $jdProbeFp),
      @('world',            $jdManifest.world,            $jdWorld),
      @('switchCount',      $jdManifest.switchCount,      $jdSwitch),
      @('seedBareLiterals', $jdManifest.seedBareLiterals, $jdSeed),
      @('forgeCfg',         $jdManifest.forgeCfg,         $jdForge),
      @('envBlock',         $jdManifest.envBlock,         $jdEnv)
    )
    foreach ($jdPair in $jdPairs) {
      $jdFlag = 'MATCH'
      if ([string]$jdPair[1] -ne [string]$jdPair[2]) { $jdFlag = 'DIFFER' }
      $jdLines2 += ('    ' + $jdPair[0].PadRight(18) + ' dir=' + ([string]$jdPair[1]).PadRight(34) + ' now=' + ([string]$jdPair[2]).PadRight(34) + ' ' + $jdFlag)
    }
  } else {
    $jdLines2 += '  dimension detail: no manifest.json in this directory (pre-provenance run)'
  }
  if (-not $AllowStale) {
    $jdLines2 += '  REFUSED. This reading was produced by a DIFFERENT configuration than the tree'
    $jdLines2 += '  being judged, so comparing it would measure the configuration change, not the src.'
    $jdLines2 += '  Re-run the acceptance suite in the current configuration, or pass -AllowStale to'
    $jdLines2 += '  judge it anyway (every output line is then tagged [STALE-CONFIG]).'
    $jdLines2 | ForEach-Object { Write-Output $_ }
    exit 4
  }
  $jdStale = $true
  $jdTag = '[STALE-CONFIG] '
  $jdLines2 += '  TOLERATED by -AllowStale: every line below is tagged [STALE-CONFIG].'
  $jdLines2 | ForEach-Object { Write-Output $_ }
  Write-Output ''
}

$jdGates = @('P258','P284','P296','P268','P452','P293','P292','P285','P442','P294',
             'P295','P297','P477','P478','P479','P480','P484','P692','P683','P712')

Write-JdLine ('JUDGE dir = ' + $jdTarget.Name)
Write-JdLine ('JUDGE config: cfgfp8=' + $jdFp8 + ' world=' + $jdWorld + ' srcfp8=' + $jdSrc8 + '  verdict=' + $jdVerdict)
Write-JdLine ('JUDGE dir config: kind=' + $jdTarget.Kind + ' cfgfp8=' + $(if ($jdTarget.Cfg8 -ne '') { $jdTarget.Cfg8 } else { 'UNKNOWN' }) + ' world=' + $jdTarget.World + ' logs=' + $jdTarget.Logs)
Write-JdLine ''

$jdPass = 0; $jdFail = 0; $jdUnj = 0; $jdRows = @()
$jdEvTotal = 0; $jdEvWith = 0; $jdEvCfgOk = 0; $jdEvWorldOk = 0; $jdEvBad = @()
foreach ($jdGate in $jdGates) {
  $jdFile = Join-Path $jdTarget.Full ('log_' + $jdGate + '.txt')
  if (-not (Test-Path $jdFile)) {
    $jdRows += ('{0,-6} | MISSING   | no log for this gate' -f $jdGate); $jdUnj++; continue
  }
  # TOLERATE A LIVE RUN: the acceptance suite may still be writing this very log
  # (measured: P284 holds its own log open for about 55 min). Report and move on --
  # never let one locked file abort the whole judgement.
  #
  # ENCODING (section 5.2): log_Pxxx.txt is cp936 (Windows console redirect), so read the
  # BYTES and decode explicitly as 936. Reading it as UTF8 only worked because the
  # criterion tokens (GATE_, <<<) are ASCII; any future Chinese criterion text would have
  # been silently mangled into U+FFFD and never matched. A UTF-8 BOM, if some other writer
  # ever adds one, is honoured.
  try {
    $jdBytes = [IO.File]::ReadAllBytes($jdFile)
  } catch {
    $jdRows += ('{0,-6} | IN-PROG   | log is locked (suite still writing it)' -f $jdGate); $jdUnj++; continue
  }
  $jdText = ''
  try {
    if (($jdBytes.Length -ge 3) -and ($jdBytes[0] -eq 239) -and ($jdBytes[1] -eq 187) -and ($jdBytes[2] -eq 191)) {
      $jdText = [Text.Encoding]::UTF8.GetString($jdBytes, 3, $jdBytes.Length - 3)
    } else {
      $jdText = [Text.Encoding]::GetEncoding(936).GetString($jdBytes)
    }
  } catch {
    $jdRows += ('{0,-6} | IN-PROG   | log could not be decoded' -f $jdGate); $jdUnj++; continue
  }

  # Evidence lines (section 2.6) are checked against the directory they live in: a log that
  # carries ANOTHER configuration stamp was copied into the wrong directory.
  $jdEvTotal = $jdEvTotal + 1
  if ($jdText -match 'EOH_CFG_CFGFP=([0-9A-Fa-f]{32})') {
    $jdEvWith = $jdEvWith + 1
    $jdEvFp = $Matches[1].ToUpper()
    $jdWantFp = $jdFp8
    if ($jdTarget.Cfg8 -ne '') { $jdWantFp = $jdTarget.Cfg8 }
    if ($jdEvFp.Substring(0,8) -ceq $jdWantFp) { $jdEvCfgOk = $jdEvCfgOk + 1 } else { $jdEvBad += ($jdGate + ' cfgfp=' + $jdEvFp) }
    if ($jdText -match 'EOH_CFG_WORLD=(\w+)') {
      if ($Matches[1] -ceq $jdTarget.World) { $jdEvWorldOk = $jdEvWorldOk + 1 } else { $jdEvBad += ($jdGate + ' world=' + $Matches[1] + ' dir=' + $jdTarget.World) }
    }
  }

  $jdGv  = [regex]::Matches($jdText, 'GATE_[A-Z0-9_]+\s*=\s*(PASS|FAIL|PARTIAL|REVIEW|OK|BAD)')
  $jdBad = ([regex]::Matches($jdText, '<<<')).Count
  $jdNeg = ([regex]::Matches($jdText, '(?i)FAIL|MISMATCH|NOT_OK')).Count

  if ($jdGv.Count -gt 0) {
    $jdVals = $jdGv | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    if (($jdVals -contains 'FAIL') -or ($jdVals -contains 'BAD')) { $jdOne = 'FAIL' }
    elseif ($jdVals -contains 'PARTIAL')                        { $jdOne = 'PARTIAL' }
    elseif ($jdVals -contains 'REVIEW')                         { $jdOne = 'REVIEW' }
    else                                                         { $jdOne = 'PASS' }
    $jdEv = 'GATE line: ' + ($jdVals -join ',')
  } elseif ($jdBad -gt 0) {
    $jdOne = 'FAIL'; $jdEv = 'marker <<< x' + $jdBad
  } elseif ($jdNeg -gt 0) {
    $jdOne = 'REVIEW'; $jdEv = 'negative token x' + $jdNeg
  } else {
    $jdOne = 'UNJUDGED'; $jdEv = 'no machine-readable verdict in log'
  }

  if ($jdOne -eq 'PASS') { $jdPass++ } elseif ($jdOne -eq 'FAIL') { $jdFail++ } else { $jdUnj++ }
  $jdRows += ('{0,-6} | {1,-9} | {2}' -f $jdGate, $jdOne, $jdEv)
}

$jdRows | ForEach-Object { Write-JdLine $_ }
Write-JdLine ''
Write-JdLine ('EVIDENCE logs=' + $jdEvTotal + ' with_header=' + $jdEvWith + ' cfgfp_match=' + $jdEvCfgOk + ' world_match=' + $jdEvWorldOk + ' mismatch=' + $jdEvBad.Count)
if ($jdEvBad.Count -gt 0) {
  Write-JdLine '  EVIDENCE_MISMATCH (log does not belong to this directory):'
  foreach ($jdBadOne in $jdEvBad) { Write-JdLine ('    ' + $jdBadOne) }
}
Write-JdLine ('REAL_VERDICT  pass=' + $jdPass + '  fail=' + $jdFail + '  unjudged/review=' + $jdUnj + '  total=' + $jdGates.Count)
Write-JdLine 'NOTE: SUMMARY.txt failed=0 comes from the JVM exit code and is structurally always 0.'
if ($jdStale) { Write-JdLine 'NOTE: [STALE-CONFIG] -- this judgement crossed a configuration boundary.' }
if ($jdFail -gt 0) { exit 1 }
if ($jdUnj -gt 0) { exit 2 }
exit 0
