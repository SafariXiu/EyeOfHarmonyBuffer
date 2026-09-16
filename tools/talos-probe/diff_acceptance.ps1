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
  $a = @(Get-Content -Encoding Default $fa | Where-Object { $_.StartsWith($tag) } | ForEach-Object { $_.Substring($tag.Length).Trim() } | Where-Object { $_ -notlike ('*' + $metaRe + '*') })
  $b = @(Get-Content -Encoding Default $fb | Where-Object { $_.StartsWith($tag) } | ForEach-Object { $_.Substring($tag.Length).Trim() } | Where-Object { $_ -notlike ('*' + $metaRe + '*') })
  [void]$lines.Add('==================== ' + $pr + '   OLD ' + $a.Count + ' lines / NEW ' + $b.Count + ' lines')
  $max = [Math]::Max($a.Count, $b.Count)
  $diff = 0
  for ($i = 0; $i -lt $max; $i++) {
    $x = '<absent>'
    $y = '<absent>'
    if ($i -lt $a.Count) { $x = $a[$i] }
    if ($i -lt $b.Count) { $y = $b[$i] }
    if ($x -ne $y) {
      $diff++
      [void]$lines.Add(('  @' + $i))
      [void]$lines.Add(('    OLD: ' + $x))
      [void]$lines.Add(('    NEW: ' + $y))
    }
  }
  if ($diff -eq 0) { [void]$lines.Add('  IDENTICAL'); $nIdentical++ } else { [void]$lines.Add(('  ' + $diff + ' line(s) differ')); $nMoved++ }
  [void]$lines.Add('')
}
[void]$lines.Add('SUMMARY: identical=' + $nIdentical + '  moved=' + $nMoved + '  missing=' + $nMissing)
Set-Content -Path $out -Value $lines -Encoding UTF8
Write-Output ('DIFF_OUT=' + $out)
Write-Output ('identical=' + $nIdentical + ' moved=' + $nMoved + ' missing=' + $nMissing)