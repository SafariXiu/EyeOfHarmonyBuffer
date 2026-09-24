# ASCII-ONLY. calibers_check.ps1 -- machine-check the physical-quantity caliber registry.
#
# WHY THIS FILE EXISTS (objective item 2 + the D67/D75/D78 defect class, three times over):
# a registry that is only as complete as the author's memory is not a registry. This script
#   1. EXTRACTS every public static member of the 22 production files on the calibration path;
#   2. reads the FIRST CELL of every markdown table row in CALIBERS.md as a registered name;
#   3. HARD-FAILS on a registered name that no longer exists in the landed source (stale entry);
#   4. HARD-FAILS on an acceptance-table probe that is not tracked in git (D67/D75/D78);
#   5. COMPARES the unclassified set against the TRACKED baseline file CALIBERS_UNCLASSIFIED.txt
#      (set equality, not a count: a count ratchet would miss a rename);
#   6. -WriteBaseline rewrites that tracked file (only after reviewing the diff).
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File calibers_check.ps1 [-WriteBaseline]
#   exit 0 = no hard failure,  exit 2 = stale registry,  exit 3 = untracked acceptance probe,
#   exit 4 = a public static producer appeared that is in no registry table
param([switch]$WriteBaseline)
$ErrorActionPreference = 'Stop'
$root = 'K:\moder\EyeOfHarmonyBuffer'
$src  = Join-Path $root 'src\main\java\com\EyeOfHarmonyBuffer'
$md   = Join-Path $root 'tools\talos-probe\CALIBERS.md'
$mtn  = Join-Path $root 'build\eoh_probe\mtn'
if (!(Test-Path $mtn)) { New-Item -ItemType Directory -Path $mtn | Out-Null }
$files = @(
  'sim\atmos\Atmosphere.java','sim\atmos\ZonalTables.java','sim\atmos\PrecipField.java',
  # ---- 520 SCOPE FIX: these files were NOT in the scan list, so every public static
  #      member in them was INVISIBLE to the registry (the D67/D75/D78 class).
  #      Evidence: none of the 84 UNREG entries came from VerticalColumn/ParcelLift,
  #      yet StationaryWave.OLR_K (reused in 504) and QRAD_ASR_MINUS_OLR (default
  #      flipped in 487) live in these files -> the blind spot sat exactly where the
  #      most consequential edits were made. Adding them makes the count LARGER but HONEST.
  'sim\atmos\Radiation.java','sim\atmos\StationaryWave.java','sim\atmos\HadleyCell.java',
  'sim\atmos\SoilMoisture.java','sim\atmos\Vegetation.java',
  'sim\atmos\ParcelLift.java','sim\atmos\VerticalColumn.java','sim\litho\TalosField.java',
  'sim\litho\PlateField.java','sim\world\WorldContract.java',
  'sim\ocean\OceanField.java','sim\ocean\OceanWiring.java','sim\ocean\GyreRow.java',
  'sim\ocean\SurfaceLayer.java','sim\ocean\CoastalLayer.java','sim\ocean\SeaSurfaceTemp.java',
  'sim\ocean\BasinFinder.java','sim\export\MapWriter.java',
  'sim\runtime\SimClimate.java','sim\runtime\SimTerrain.java',
  'space\talos\chunk\world\V2BiomeSelect.java','space\talos\chunk\world\V2BiomeField.java',
  'space\talos\chunk\world\ClimateCoords.java','space\talos\chunk\world\V2TerrainGen.java',
  'space\talos\chunk\world\LandformField.java','space\talos\chunk\world\MountainLayerV2.java',
  'space\talos\chunk\continent_layer\OrographyField.java'
)
# ---- 1. extract ----
$have = @{}                       # member -> file
$classFile = @{}                  # class -> file
foreach ($f in $files) {
  $full = Join-Path $src $f
  if (!(Test-Path $full)) { Write-Output ('MISSING_SRC ' + $f); continue }
  $classFile[[IO.Path]::GetFileNameWithoutExtension($f)] = $f
  foreach ($line in [IO.File]::ReadAllLines($full)) {
    $t = $line.Trim()
    if ($t.StartsWith('*') -or $t.StartsWith('//') -or $t.StartsWith('/*')) { continue }
    # modifier ORDER is not fixed (E65) and one statement can declare several members.
    $m = [regex]::Match($t, '^public\s+((?:static|final|synchronized|volatile|transient)\s+)+[\w\.\[\]<>]+\s+(\w+)\s*[=(]')
    if ($m.Success -and $t -match '\bstatic\b') {
      $nm = $m.Groups[2].Value
      if (!$have.ContainsKey($nm)) { $have[$nm] = (New-Object System.Collections.ArrayList) }
      [void]$have[$nm].Add($f)
      foreach ($mm in [regex]::Matches($t, ',\s*(\w+)\s*=')) {
        $nm2 = $mm.Groups[1].Value
        if (!$have.ContainsKey($nm2)) { $have[$nm2] = (New-Object System.Collections.ArrayList) }
        [void]$have[$nm2].Add($f)
      }
    }
  }
}
# ---- 2. read registry first cells ----
$reg = @{}                        # member (or wildcard) -> list of "class.member"
$pats = New-Object System.Collections.ArrayList
$inBlock = $false
$probes = New-Object System.Collections.ArrayList
foreach ($line in [IO.File]::ReadAllLines($md)) {
  if ($line -match '^\s*```acceptance-probes') { $inBlock = $true; continue }
  if ($inBlock) {
    if ($line -match '^\s*```') { $inBlock = $false; continue }
    foreach ($w in ($line.Trim() -split '\s+')) { if ($w) { [void]$probes.Add($w) } }
    continue
  }
  if ($line.StartsWith('|')) {
    $cell = ($line -split '\|')[1]
    foreach ($mm in [regex]::Matches($cell, '`([^`]+)`')) {
      foreach ($part in ($mm.Groups[1].Value -split ',')) {
        $p = $part.Trim()
        if ($p -match '^([A-Za-z_]\w*)\.([A-Za-z_]\w*|\*)$') { [void]$pats.Add(@($Matches[1], $Matches[2])) }
        elseif ($p -match '^([A-Za-z_]\w*)$') { [void]$pats.Add(@($null, $Matches[1])) }
        elseif ($p -match '^([A-Za-z_]\w*)\*$') { [void]$pats.Add(@($null, ($Matches[1] + '*'))) }   # bare wildcard e.g. COAST_TAN_*
      }
    }
  }
}
# ---- 3. stale check ----
$stale = New-Object System.Collections.ArrayList
$okCount = 0
foreach ($p in $pats) {
  $cls = $p[0]; $mem = $p[1]
  if ($mem -eq '*') {
    if ($cls -and !$classFile.ContainsKey($cls)) { [void]$stale.Add(($cls + '.*  (class not in the extraction list)')) } else { $okCount++ }
    continue
  }
  if ($mem.Contains('*')) {
    $pre = $mem.Replace('*','')
    $hit = $false
    foreach ($k in $have.Keys) { if ($k.StartsWith($pre)) { $hit = $true; break } }   # wildcard over member names
    if ($hit) { $okCount++ } else { [void]$stale.Add(($cls + '.' + $mem + '  (wildcard matched nothing)')) }
    continue
  }
  if (!$have.ContainsKey($mem)) { [void]$stale.Add((($(if ($cls) { $cls + '.' } else { '' })) + $mem + '  (not found in source)')) ; continue }
  if ($cls -and $classFile.ContainsKey($cls) -and ($have[$mem] -notcontains $classFile[$cls])) {
    [void]$stale.Add(($cls + '.' + $mem + '  (found in ' + ($have[$mem] -join ',') + ', not in ' + $classFile[$cls] + ')')); continue
  }
  $okCount++
}
# ---- 4. acceptance probes tracked in git ----
$tracked = & git -C $root ls-files 'tools/talos-probe/probe/*.java'
$tset = @{}
foreach ($t in $tracked) { $tset[[IO.Path]::GetFileNameWithoutExtension([IO.Path]::GetFileName($t))] = $true }
$untracked = New-Object System.Collections.ArrayList
foreach ($pr in $probes) { if (!$tset.ContainsKey($pr)) { [void]$untracked.Add($pr) } }
# ---- 5. unclassified = extracted but never mentioned anywhere in CALIBERS.md ----
$text = [IO.File]::ReadAllText($md)
$un = New-Object System.Collections.ArrayList
foreach ($k in ($have.Keys | Sort-Object)) {
  if ($text -notmatch ('\b' + [regex]::Escape($k) + '\b')) { [void]$un.Add((($have[$k] -join ',') + '|' + $k)) }
}
[IO.File]::WriteAllLines((Join-Path $mtn '_calibers_unclassified.txt'), $un, [Text.UTF8Encoding]::new($false))
Write-Output ('CALIBERS: extracted=' + $have.Count + '  registered-patterns=' + $pats.Count + '  resolved=' + $okCount)
Write-Output ('CALIBERS: acceptance-probes=' + $probes.Count + '  untracked=' + $untracked.Count)
# ---- 6. unclassified BASELINE FILE (set equality, not a count) ----
# A count ratchet misses a rename (one in, one out). A SET comparison catches it: any member that
# is unclassified NOW but was not in the tracked baseline is a NEW unregistered producer -- the
# D67/D75/D78 defect class. Members that disappear from the list are reported as information only.
$tracked = Join-Path $root 'tools\talos-probe\CALIBERS_UNCLASSIFIED.txt'
$now = @{}
foreach ($x in $un) { $now[$x] = $true }
if ($WriteBaseline) {
  [IO.File]::WriteAllLines($tracked, (@('# GENERATED by calibers_check.ps1 -WriteBaseline; do not hand-edit') + ($un | Sort-Object)), [Text.UTF8Encoding]::new($false))
  Write-Output ('CALIBERS: baseline WRITTEN ' + $un.Count + ' entries -> tools\talos-probe\CALIBERS_UNCLASSIFIED.txt')
} else {
  $old = @{}
  if (Test-Path $tracked) { foreach ($x in [IO.File]::ReadAllLines($tracked)) { $t = $x.Trim(); if ($t -and -not $t.StartsWith('#')) { $old[$t] = $true } } }
  $newOnes = New-Object System.Collections.ArrayList
  foreach ($k in $now.Keys) { if (!$old.ContainsKey($k)) { [void]$newOnes.Add($k) } }
  $gone = 0
  foreach ($k in $old.Keys) { if (!$now.ContainsKey($k)) { $gone++ } }
  Write-Output ('CALIBERS: unclassified = ' + $un.Count + '  baseline = ' + $old.Count + '  gone = ' + $gone + '  -> build\eoh_probe\mtn\_calibers_unclassified.txt')
  if ($newOnes.Count -gt 0) {
    Write-Output ('CALIBERS: NEW UNREGISTERED MEMBERS = ' + $newOnes.Count)
    Write-Output '  (a public static producer appeared that is in no registry table -- the D67/D75/D78 class)'
    foreach ($x in ($newOnes | Sort-Object)) { Write-Output ('  UNREG ' + $x) }
    exit 4
  }
}
if ($stale.Count -gt 0) {
  Write-Output ('CALIBERS: STALE ENTRIES = ' + $stale.Count)
  foreach ($s in $stale) { Write-Output ('  STALE ' + $s) }
  exit 2
}
if ($untracked.Count -gt 0) {
  Write-Output ('CALIBERS: UNTRACKED ACCEPTANCE PROBES = ' + $untracked.Count)
  foreach ($u in $untracked) { Write-Output ('  UNTRACKED ' + $u) }
  exit 3
}
Write-Output 'CALIBERS_STATUS=OK'
exit 0
