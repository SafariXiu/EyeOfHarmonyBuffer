# ASCII-ONLY. PowerShell 5.1 reads a BOM-less .ps1 as ANSI (GBK): any non-ASCII
# literal gets mangled -- and a mangled byte that happens to be a quote BREAKS THE
# PARSE. That happened once (E33b). Keep every literal in this file ASCII.
#
# Diff two acceptance re-runs line by line, probe by probe.
#
# WHY: after any source change the acceptance table must be re-measured, and the
# question is always the same -- WHICH readings moved. Doing that by eye across
# 11 logs is exactly how a wrong number gets into the table.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File diff_acceptance.ps1 -Old <FP1> -New <FP2>
param(
  [Parameter(Mandatory=$true)][string]$Old,
  [Parameter(Mandatory=$true)][string]$New
)
$ErrorActionPreference = 'Stop'
$root  = 'K:\moder\EyeOfHarmonyBuffer'
$mtn   = Join-Path $root 'build\eoh_probe\mtn'
$adir  = Join-Path $mtn 'rerun_acceptance'
$dOld  = Join-Path $adir $Old
$dNew  = Join-Path $adir $New
$out   = Join-Path $mtn ('acceptance_diff_' + $Old.Substring(0,8) + '_vs_' + $New.Substring(0,8) + '.txt')
$probes = @('P258','P284','P296','P268','P452','P293','P292','P285','P442','P294','P295','P297','P477','P478','P479','P480','P484')

# Run-METADATA lines are dropped before comparing. The wired probes print one extra
# caliber line at the top; comparing raw by index then reports EVERY line as
# differing -- a false-positive cascade. Metadata is about the run, not a reading,
# so it must not shift the alignment. Matched by an ASCII substring on purpose.
$metaRe = 'installedSeed='

# ---- READING NORMALIZATION: zero out TIMINGS, keep the READINGS on the same line ----
#
# WHY (measured 2026-09-18): the acceptance suite runs 6 probes CONCURRENTLY, so every
# elapsed-time print jitters run to run. Without normalization, a diff of two runs of the
# SAME code reported '9 of 17 probes moved' and ALL NINE were timing lines -- so the
# judgement 'which READING moved' was worthless.
#
# WHY normalize instead of dropping the line: P258 prints
#     T_summer(K)  299.7  300.0  299.9  17545
# i.e. THREE READINGS PLUS a timing on ONE line. Dropping the whole line would hide a
# real drift in the T values -- exactly what we are looking for.
#
# NOTE: this file must stay ASCII-ONLY (PS 5.1 reads a BOM-less .ps1 as ANSI/GBK and a
# mangled byte that happens to be a quote breaks the parse -- E33b). Chinese literals are
# therefore written as \uXXXX escapes.
function Normalize-Reading([string]$s) {
  # a number followed by an ASCII time unit (U+00B5 = micro sign)
  $s = [regex]::Replace($s, '\d+(?:\.\d+)?\s*(?:ms|us|\u00b5s|ns|s)\b', '<T>')
  # a number followed by a CJK time unit: U+79D2 = sec, U+5206U+949F = min
  $s = [regex]::Replace($s, '\d+(?:\.\d+)?\s*(?:\u79d2|\u5206\u949f)', '<T>')
  # the word for 'elapsed' (U+8017U+65F6) followed by a bare number
  $s = [regex]::Replace($s, '\u8017\u65f6\s*=?\s*\d+(?:\.\d+)?', '<T>')
  return $s
}

$lines = New-Object System.Collections.ArrayList
[void]$lines.Add('ACCEPTANCE DIFF')
[void]$lines.Add('  OLD = ' + $Old)
[void]$lines.Add('  NEW = ' + $New)
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
Set-Content -Path $out -Value $lines -Encoding UTF8
Write-Output ('DIFF_OUT=' + $out)
Write-Output ('identical=' + $nIdentical + ' moved=' + $nMoved + ' missing=' + $nMissing)