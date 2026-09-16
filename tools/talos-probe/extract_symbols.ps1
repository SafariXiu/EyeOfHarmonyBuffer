# ASCII-ONLY. Extract every public static member of the 15 production sim\ files plus the
# space\talos\chunk\** files that sit on the climate path, so CALIBERS.md can be checked against it.
# WHY: the objective requires a "physical-quantity caliber registry" listing EVERY producer and
# consumer. A registry that is only as complete as the author's memory is exactly the D67/D75/D78
# defect class (three times over). So the symbol list is EXTRACTED, and the registry is CHECKED
# against it by calibers_check.ps1.
$ErrorActionPreference = 'Stop'
$root = 'K:\moder\EyeOfHarmonyBuffer'
$src  = Join-Path $root 'src\main\java\com\EyeOfHarmonyBuffer'
$out  = Join-Path $root 'build\eoh_probe\mtn\_calibers_symbols.txt'
$files = @(
  'sim\atmos\Atmosphere.java','sim\atmos\ZonalTables.java','sim\atmos\PrecipField.java',
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
$rows = New-Object System.Collections.ArrayList
foreach ($f in $files) {
  $full = Join-Path $src $f
  if (!(Test-Path $full)) { Write-Output ('MISSING ' + $f); continue }
  $i = 0
  foreach ($line in [IO.File]::ReadAllLines($full)) {
    $i++
    $t = $line.Trim()
    if ($t.StartsWith('*') -or $t.StartsWith('//') -or $t.StartsWith('/*')) { continue }
    # modifier ORDER is not fixed (E65: 'public static synchronized double anomalyAt' was invisible),
    # and one statement can declare several members ('long memoHits = 0, memoMisses = 0;').
    $m = [regex]::Match($t, '^public\s+((?:static|final|synchronized|volatile|transient)\s+)+[\w\.\[\]<>]+\s+(\w+)\s*[=(]')
    if ($m.Success -and $t -match '\bstatic\b') {
      [void]$rows.Add(($f + '|' + $m.Groups[2].Value + '|' + $i))
      $stmt = $t
      foreach ($mm in [regex]::Matches($stmt, ',\s*(\w+)\s*=')) { [void]$rows.Add(($f + '|' + $mm.Groups[1].Value + '|' + $i)) }
    }
  }
}
[IO.File]::WriteAllLines($out, $rows, [Text.UTF8Encoding]::new($false))
Write-Output ('SYMBOLS=' + $rows.Count)
$byFile = @{}
foreach ($r in $rows) { $p = $r.Split('|')[0]; if (!$byFile.ContainsKey($p)) { $byFile[$p] = 0 }; $byFile[$p]++ }
foreach ($k in ($byFile.Keys | Sort-Object)) { Write-Output ('  ' + $k + '  ' + $byFile[$k]) }
