# ASCII-ONLY. Recomputes build/eoh_probe/mtn/SOURCE_FINGERPRINT.txt from the landed source.
#
# RECIPE (recovered this round by brute force; it was never written down before -- D42):
#   per file  : SHA256(bytes) first 16 hex, UPPERCASE
#   aggregate : SHA256 of the concatenation of "<hash16>  <relpath>\r\n" for all listed files
#               (CRLF after every line, including the last), first 32 hex, UPPERCASE
#   25 files  : 15 under sim\ + 10 under space\talos\chunk\
#               (24 -> 25 on 2026-09-13: sim\ocean\OceanWiring.java added by the step-3 wiring, S162)
$ErrorActionPreference = 'Stop'
$root = 'K:\moder\EyeOfHarmonyBuffer'
$src  = Join-Path $root 'src\main\java\com\EyeOfHarmonyBuffer'
$out  = Join-Path $root 'build\eoh_probe\mtn\SOURCE_FINGERPRINT.txt'
$rel  = @(
  'sim\atmos\Atmosphere.java','sim\atmos\PrecipField.java','sim\atmos\ZonalTables.java',
  'sim\export\MapWriter.java','sim\litho\PlateField.java','sim\litho\TalosField.java','sim\ocean\BasinFinder.java',
  'sim\ocean\CoastalLayer.java','sim\ocean\GyreRow.java','sim\ocean\SeaSurfaceTemp.java',
  'sim\ocean\SurfaceLayer.java','sim\ocean\OceanField.java','sim\ocean\OceanWiring.java',
  'sim\runtime\SimClimate.java','sim\runtime\SimTerrain.java',
  'sim\world\WorldContract.java',
  'space\talos\chunk\continent_layer\OrographyField.java',
  'space\talos\chunk\world\ChunkProviderTalos2.java',
  'space\talos\chunk\world\ClimateCoords.java',
  'space\talos\chunk\world\LandformField.java',
  'space\talos\chunk\world\MountainLayerV2.java',
  'space\talos\chunk\world\V2BiomeField.java',
  'space\talos\chunk\world\V2BiomeSelect.java',
  'space\talos\chunk\world\V2TerrainGen.java'
)
# M6 MIRROR LESSON (2026-09-18, "one contract" step 6): the registry must be updated not
# only when an src file is ADDED but also when one is RETIRED/renamed. Otherwise this
# script dies with a cryptic 'Could not find file ...' and the real cause (a stale
# registry) is invisible.
#
# Removed from the list on retirement:
#   space/talos/chunk/circulation_layer/GlobalCirculation.java
#   space/talos/chunk/climate_layer/ClimateLatitudes.java
#
# NOTE: this file is ASCII-ONLY for a reason -- PS 5.1 reads a BOM-less .ps1 as ANSI/GBK,
# and a mangled byte that happens to be a quote breaks the parse (E33b). I broke that rule
# once while writing this very block (wrote the comment in Chinese) and it failed exactly
# as documented. Keep every literal ASCII.
$missing = @($rel | Where-Object { !(Test-Path (Join-Path $src $_)) })
if ($missing.Count -gt 0) {
  Write-Output ('FINGERPRINT_REGISTRY_STALE: ' + $missing.Count + ' listed file(s) do not exist:')
  foreach ($m in $missing) { Write-Output ('  MISSING ' + $m) }
  Write-Output '  => that file was retired/renamed; delete its entry from $rel (M6 mirror).'
  exit 2
}

$sha = [System.Security.Cryptography.SHA256]::Create()
$sb = New-Object System.Text.StringBuilder
foreach ($r in $rel) {
  $full = Join-Path $src $r
  $bytes = [IO.File]::ReadAllBytes($full)
  $h = [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-','')
  $h16 = $h.Substring(0,16)
  [void]$sb.Append($h16 + '  ' + 'src\main\java\com\EyeOfHarmonyBuffer\' + $r + "`r`n")
}
$agg = [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::ASCII.GetBytes($sb.ToString()))).Replace('-','')
$fp = $agg.Substring(0,32)
$head = 'FINGERPRINT=' + $fp + '  FILES=' + $rel.Count + "`r`n"
[IO.File]::WriteAllText($out, $head + $sb.ToString(), [Text.UTF8Encoding]::new($false))
Write-Output ('FINGERPRINT=' + $fp)
