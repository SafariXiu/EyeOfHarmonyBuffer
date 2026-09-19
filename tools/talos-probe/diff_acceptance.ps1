# ASCII-ONLY. PowerShell 5.1 reads a BOM-less .ps1 as ANSI (GBK): any non-ASCII
# literal gets mangled -- and a mangled byte that happens to be a quote BREAKS THE
# PARSE. That happened once (E33b). Keep every literal in this file ASCII.
#
# MEASURED 2026-09-19, THIS VERY FILE: it had two UTF-8 Chinese comment lines at 18:51.
# Under the ANSI read the trailing byte of the second one paired with the following CR/LF,
# so the NEXT CODE LINE -- the "$probes = @(...)" declaration -- was swallowed into the
# comment. The script still RAN, then compared NOTHING and reported
# "identical=0 moved=0 missing=0" with exit 0 for every pair, i.e. a silent no-op
# instrument. The lines are gone; keep it ASCII.
#
# Diff two acceptance re-runs line by line, probe by probe.
#
# WHY: after any source change the acceptance table must be re-measured, and the
# question is always the same -- WHICH readings moved. Doing that by eye across
# 20 logs is exactly how a wrong number gets into the table.
#
# PROVENANCE (measurement_provenance_design.md section 2.4, third item): an A/B
# comparison is only meaningful when BOTH sides were produced by the SAME configuration.
# Diffing across a configuration change measures the CONFIGURATION, not the src -- that
# is how the P292 "92% -> 46% regression" was manufactured out of nothing but a world
# flip. So:
#   * two NEW-format directories  => their cfgfp8 must be EQUAL;
#   * two LEGACY directories with the same state suffix (both _OFF or both _ON) or two
#     identical names => the world labels agree, but the rest of the configuration is
#     UNPROVABLE (a bare name has no cfgfp) => REFUSED unless -AllowStale;
#   * _OFF vs _ON, or any unprovable mix => REFUSED unless -AllowStale.
# With -AllowStale the comparison runs and EVERY line of the report and of the output file
# is tagged [STALE-CONFIG].
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File diff_acceptance.ps1 -Old <DIR1> -New <DIR2>
#   <DIR> is the rerun_acceptance SUBDIRECTORY NAME:
#     NEW format    <srcfp8>_<cfgfp8>_<WORLD>[_<variant>]   (since phase B)
#     older formats <32hex>_OFF / <32hex>_ON / <32hex>       (bare = world unknown)
# EXIT: 0 = compared ; 3 = no such directory ; 4 = CONFIG_MISMATCH (refused)
param(
  [Parameter(Mandatory=$true)][string]$Old,
  [Parameter(Mandatory=$true)][string]$New,
  [switch]$AllowStale
)
$ErrorActionPreference = 'Stop'
$root  = 'K:\moder\EyeOfHarmonyBuffer'
$mtn   = Join-Path $root 'build\eoh_probe\mtn'
$adir  = Join-Path $mtn 'rerun_acceptance'
$dOld  = Join-Path $adir $Old
$dNew  = Join-Path $adir $New

if (-not (Test-Path -LiteralPath $dOld)) { Write-Output ('NO_SUCH_DIR ' + $Old); exit 3 }
if (-not (Test-Path -LiteralPath $dNew)) { Write-Output ('NO_SUCH_DIR ' + $New); exit 3 }

# ---- classify a directory name: the name IS the declared identity -------------------
function Get-DirIdentity([string]$dfName) {
  $dfId = [pscustomobject]@{ Kind = 'OTHER'; Src8 = ''; Cfg8 = ''; World = 'UNKNOWN'; Variant = ''; LegacyState = '' }
  if ($dfName -match '^(?<dfSrc>[0-9A-Fa-f]{8})_(?<dfCfg>[0-9A-Fa-f]{8})_(?<dfWorld>TALOS|LEGACY|UNKNOWN)(_(?<dfVar>.+))?$') {
    $dfId.Kind = 'NEW'
    $dfId.Src8 = $Matches['dfSrc'].ToUpper()
    $dfId.Cfg8 = $Matches['dfCfg'].ToUpper()
    $dfId.World = $Matches['dfWorld']
    if ($Matches['dfVar']) { $dfId.Variant = $Matches['dfVar'] }
  } elseif ($dfName -match '^(?<dfFp>[0-9A-Fa-f]{32})_(?<dfState>OFF|ON)$') {
    $dfId.Kind = 'LEGACYST'
    $dfId.Src8 = $Matches['dfFp'].Substring(0,8).ToUpper()
    $dfId.LegacyState = $Matches['dfState']
    if ($Matches['dfState'] -ceq 'OFF') { $dfId.World = 'LEGACY' } else { $dfId.World = 'TALOS' }
  } elseif ($dfName -match '^(?<dfFp>[0-9A-Fa-f]{32})$') {
    $dfId.Kind = 'LEGACYFP'
    $dfId.Src8 = $Matches['dfFp'].Substring(0,8).ToUpper()
  }
  return $dfId
}
$idOld = Get-DirIdentity $Old
$idNew = Get-DirIdentity $New

# ---- configuration verdict (section 2.4, third item) --------------------------------
$dfVerdict = 'MISMATCH'
$dfReason  = ''
if (($idOld.Kind -eq 'NEW') -and ($idNew.Kind -eq 'NEW')) {
  if ($idOld.Cfg8 -ceq $idNew.Cfg8) { $dfVerdict = 'MATCH' }
  else { $dfReason = 'cfgfp8 ' + $idOld.Cfg8 + ' (' + $idOld.World + ') vs ' + $idNew.Cfg8 + ' (' + $idNew.World + ')' }
} elseif ($Old -ceq $New) {
  $dfVerdict = 'MATCH'
  $dfReason  = 'the two names are identical, so both sides are the same declared identity'
} elseif (($idOld.Kind -eq 'LEGACYST') -and ($idNew.Kind -eq 'LEGACYST') -and ($idOld.LegacyState -ceq $idNew.LegacyState)) {
  $dfVerdict = 'MISMATCH'
  $dfReason  = 'legacy names: both say ' + $idOld.World + ' (state suffix ' + $idOld.LegacyState + '), but the rest of the configuration has no cfgfp and cannot be proven equal'
} elseif (($idOld.Kind -eq 'LEGACYST') -and ($idNew.Kind -eq 'LEGACYST')) {
  $dfReason = 'legacy world labels DIFFER: ' + $idOld.LegacyState + ' (' + $idOld.World + ') vs ' + $idNew.LegacyState + ' (' + $idNew.World + ')'
} else {
  $dfReason = 'configuration is UNPROVABLE on at least one side (kind ' + $idOld.Kind + '/' + $idNew.Kind + ', no cfgfp segment)'
}

$dfTag = ''
if ($dfVerdict -ne 'MATCH') {
  $dfHead = @()
  $dfHead += 'CONFIG_MISMATCH'
  $dfHead += ('  OLD = ' + $Old + '   kind=' + $idOld.Kind + ' cfgfp8=' + $(if ($idOld.Cfg8 -ne '') { $idOld.Cfg8 } else { 'UNKNOWN' }) + ' world=' + $idOld.World)
  $dfHead += ('  NEW = ' + $New + '   kind=' + $idNew.Kind + ' cfgfp8=' + $(if ($idNew.Cfg8 -ne '') { $idNew.Cfg8 } else { 'UNKNOWN' }) + ' world=' + $idNew.World)
  $dfHead += ('  reason = ' + $dfReason)
  if (-not $AllowStale) {
    $dfHead += '  REFUSED. An A/B across a configuration boundary measures the configuration change,'
    $dfHead += '  not the src. Re-run one side in the other side configuration, compare two directories'
    $dfHead += '  of the SAME configuration, or pass -AllowStale to compare anyway (the whole report is'
    $dfHead += '  then tagged [STALE-CONFIG]).'
    $dfHead | ForEach-Object { Write-Output $_ }
    exit 4
  }
  $dfTag = '[STALE-CONFIG] '
  $dfHead += '  TOLERATED by -AllowStale: the whole report is tagged [STALE-CONFIG].'
  $dfHead | ForEach-Object { Write-Output $_ }
  Write-Output ''
}

# Short tag for the OUTPUT FILENAME.
#   legacy <32hex>_OFF|_ON  ->  <8hex>_OFF|_ON   (unchanged, so historical file names
#                               keep their meaning)
#   new <s8>_<c8>_<WORLD>   ->  <s8>_<c8>_<WORLD>[ _<variant> ]  (a bare <8hex> would make
#                               two different configurations write the same file)
function Short-Tag([string]$dfName) {
  $dfId2 = Get-DirIdentity $dfName
  if ($dfId2.Kind -eq 'NEW') {
    $dfTagOut = $dfId2.Src8 + '_' + $dfId2.Cfg8 + '_' + $dfId2.World
    if ($dfId2.Variant -ne '') { $dfTagOut = $dfTagOut + '_' + $dfId2.Variant }
    return $dfTagOut
  }
  if ($dfId2.Kind -eq 'LEGACYST') { return $dfId2.Src8 + '_' + $dfId2.LegacyState }
  if ($dfId2.Kind -eq 'LEGACYFP') { return $dfId2.Src8 }
  return ($dfName -replace '[^A-Za-z0-9_\-]', '_')
}
$out   = Join-Path $mtn ('acceptance_diff_' + (Short-Tag $Old) + '_vs_' + (Short-Tag $New) + '.txt')

# The probe list. WHY 20 AND NOT 17: section 534 (P1-7) -- the list used to omit
# P683/P692/P712, so the claim "a diff reports NEW" was simply FALSE for those three.
$probes = @('P258','P284','P296','P268','P452','P293','P292','P285','P442','P294','P295','P297','P477','P478','P479','P480','P484','P692','P683','P712')

# Run-METADATA lines are dropped before comparing. The wired probes print one extra
# caliber line at the top; comparing raw by index then reports EVERY line as
# differing -- a false-positive cascade. Metadata is about the run, not a reading,
# so it must not shift the alignment. Matched by an ASCII substring on purpose.
$metaRe = 'installedSeed='

# ---- READING NORMALIZATION: zero out TIMINGS, keep the READINGS on the same line ----
#
# WHY (measured 2026-09-18): the acceptance suite runs several probes CONCURRENTLY, so every
# elapsed-time print jitters run to run. Without normalization, a diff of two runs of the
# SAME code reported "9 of 17 probes moved" and ALL NINE were timing lines -- so the
# judgement "which READING moved" was worthless.
#
# WHY normalize instead of dropping the line: P258 prints
#     T_summer(K)  299.7  300.0  299.9  17545
# i.e. THREE READINGS PLUS a timing on ONE line. Dropping the whole line would hide a
# real drift in the T values -- exactly what we are looking for.
#
# NOTE: this file must stay ASCII-ONLY (PS 5.1 reads a BOM-less .ps1 as ANSI/GBK and a
# mangled byte that happens to be a quote breaks the parse -- E33b). Chinese literals are
# therefore written as \uXXXX escapes. The \uXXXX below are INTENTIONAL and must stay.
function Normalize-Reading([string]$s) {
  # a number followed by an ASCII time unit (U+00B5 = micro sign)
  $s = [regex]::Replace($s, '\d+(?:\.\d+)?\s*(?:ms|us|\u00b5s|ns|s)\b', '<T>')
  # a number followed by a CJK time unit: U+79D2 = sec, U+5206U+949F = min
  $s = [regex]::Replace($s, '\d+(?:\.\d+)?\s*(?:\u79d2|\u5206\u949f)', '<T>')
  # the word for 'elapsed' (U+8017U+65F6) followed by a bare number
  $s = [regex]::Replace($s, '\u8017\u65f6\s*=?\s*\d+(?:\.\d+)?', '<T>')
  return $s
}

# ---- evidence lines actually present inside the logs (section 2.6) ------------------
# A log whose EOH_CFG_CFGFP disagrees with the directory it sits in was copied into the
# wrong directory; that is worth knowing BEFORE trusting a line-by-line comparison.
function Get-EvidenceSummary([string]$dfDirPath) {
  $dfFound = @{}
  $dfWith = 0
  foreach ($dfEvFile in (Get-ChildItem -LiteralPath $dfDirPath -Filter 'log_*.txt' -File -ErrorAction SilentlyContinue)) {
    try { $dfEvBytes = [IO.File]::ReadAllBytes($dfEvFile.FullName) } catch { continue }
    $dfEvText = [Text.Encoding]::GetEncoding(936).GetString($dfEvBytes)
    if ($dfEvText -match 'EOH_CFG_CFGFP=([0-9A-Fa-f]{32})') {
      $dfWith = $dfWith + 1
      $dfKey = $Matches[1].ToUpper()
      if ($dfFound.ContainsKey($dfKey)) { $dfFound[$dfKey] = $dfFound[$dfKey] + 1 } else { $dfFound[$dfKey] = 1 }
    }
  }
  $dfParts = @()
  foreach ($dfKey in ($dfFound.Keys | Sort-Object)) { $dfParts += ($dfKey.Substring(0,8) + 'x' + $dfFound[$dfKey]) }
  if ($dfParts.Count -eq 0) { $dfParts += '(none)' }
  return ('with_header=' + $dfWith + ' cfgfp_seen=' + ($dfParts -join ','))
}

$lines = New-Object System.Collections.ArrayList
[void]$lines.Add('ACCEPTANCE DIFF')
[void]$lines.Add('  OLD = ' + $Old)
[void]$lines.Add('  NEW = ' + $New)
[void]$lines.Add('  CFG OLD = kind=' + $idOld.Kind + ' cfgfp8=' + $(if ($idOld.Cfg8 -ne '') { $idOld.Cfg8 } else { 'UNKNOWN' }) + ' world=' + $idOld.World)
[void]$lines.Add('  CFG NEW = kind=' + $idNew.Kind + ' cfgfp8=' + $(if ($idNew.Cfg8 -ne '') { $idNew.Cfg8 } else { 'UNKNOWN' }) + ' world=' + $idNew.World)
[void]$lines.Add('  CFG VERDICT = ' + $dfVerdict + $(if ($dfReason -ne '') { '  (' + $dfReason + ')' } else { '' }))
[void]$lines.Add('  EVIDENCE OLD = ' + (Get-EvidenceSummary $dOld))
[void]$lines.Add('  EVIDENCE NEW = ' + (Get-EvidenceSummary $dNew))
[void]$lines.Add('')
$nIdentical = 0; $nMoved = 0; $nMissing = 0
foreach ($pr in $probes) {
  $fa = Join-Path $dOld ('log_' + $pr + '.txt')
  $fb = Join-Path $dNew ('log_' + $pr + '.txt')
  if (!(Test-Path $fa)) { [void]$lines.Add(($pr + ': OLD LOG MISSING')); $nMissing++; continue }
  if (!(Test-Path $fb)) { [void]$lines.Add(($pr + ': NEW LOG MISSING (probe not run yet?)')); $nMissing++; continue }
  $tag = '[' + $pr + ']'
  $a = @(Get-Content -Encoding Default $fa | Where-Object { $_.StartsWith($tag) } | ForEach-Object { $_.Substring($tag.Length).Trim() } | Where-Object { $_ -notlike ('*' + $metaRe + '*') } | ForEach-Object { Normalize-Reading $_ })
  $b = @(Get-Content -Encoding Default $fb | Where-Object { $_.StartsWith($tag) } | ForEach-Object { $_.Substring($tag.Length).Trim() } | Where-Object { $_ -notlike ('*' + $metaRe + '*') } | ForEach-Object { Normalize-Reading $_ })
  [void]$lines.Add('==================== ' + $pr + '   OLD ' + $a.Count + ' lines / NEW ' + $b.Count + ' lines')
  # ---- CONTENT ALIGNMENT (LCS), NOT index-by-index ----
  #
  # WHY (measured 2026-09-18): comparing by index means ANY inserted or deleted line
  # shifts every following line and reports them ALL as differing. P296 gained ONE
  # diagnostic line (a timing print) and the diff reported 44 differing lines whose
  # content was byte-identical -- merely displaced by one. "Add one probe print" is the
  # most common probe edit there is, so index comparison fails exactly when it is needed.
  #
  # LCS produces a proper edit script: an insertion is ONE "+ line", and lines that
  # merely moved are recognised as unchanged.
  $n = $a.Count; $m = $b.Count; $w = $m + 1
  $dp = New-Object 'int[]' (($n + 1) * $w)
  for ($i = $n - 1; $i -ge 0; $i--) {
    for ($j = $m - 1; $j -ge 0; $j--) {
      if ($a[$i] -ceq $b[$j]) { $dp[$i * $w + $j] = $dp[($i + 1) * $w + ($j + 1)] + 1 }
      else { $dp[$i * $w + $j] = [Math]::Max($dp[($i + 1) * $w + $j], $dp[$i * $w + ($j + 1)]) }
    }
  }
  $diff = 0; $i = 0; $j = 0
  while ($i -lt $n -and $j -lt $m) {
    if ($a[$i] -ceq $b[$j]) { $i++; $j++ }
    elseif ($dp[($i + 1) * $w + $j] -ge $dp[$i * $w + ($j + 1)]) { [void]$lines.Add('  - ' + $a[$i]); $i++; $diff++ }
    else { [void]$lines.Add('  + ' + $b[$j]); $j++; $diff++ }
  }
  while ($i -lt $n) { [void]$lines.Add('  - ' + $a[$i]); $i++; $diff++ }
  while ($j -lt $m) { [void]$lines.Add('  + ' + $b[$j]); $j++; $diff++ }
  if ($diff -eq 0) { [void]$lines.Add('  IDENTICAL'); $nIdentical++ } else { [void]$lines.Add(('  ' + $diff + ' line(s) differ')); $nMoved++ }
  [void]$lines.Add('')
}
[void]$lines.Add('SUMMARY: identical=' + $nIdentical + '  moved=' + $nMoved + '  missing=' + $nMissing)
if ($dfTag -ne '') {
  $dfTagged = New-Object System.Collections.ArrayList
  foreach ($dfOne in $lines) {
    if ([string]$dfOne -eq '') { [void]$dfTagged.Add('') } else { [void]$dfTagged.Add($dfTag + [string]$dfOne) }
  }
  $lines = $dfTagged
}
Set-Content -Path $out -Value $lines -Encoding UTF8
Write-Output ($dfTag + 'DIFF_OUT=' + $out)
Write-Output ($dfTag + 'identical=' + $nIdentical + ' moved=' + $nMoved + ' missing=' + $nMissing)
if ($dfVerdict -ne 'MATCH') { Write-Output ($dfTag + 'CFG VERDICT = MISMATCH (tolerated by -AllowStale)') }
exit 0
