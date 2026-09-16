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
  'sim\export\MapWriter.java','sim\litho\PlateField.java','sim\ocean\BasinFinder.java',
  'sim\ocean\CoastalLayer.java','sim\ocean\GyreRow.java','sim\ocean\SeaSurfaceTemp.java',
  'sim\ocean\SurfaceLayer.java','sim\ocean\OceanField.java','sim\ocean\OceanWiring.java',
  'sim\runtime\SimClimate.java','sim\runtime\SimTerrain.java',
  'sim\world\WorldContract.java',
  'space\talos\chunk\circulation_layer\GlobalCirculation.java',
  'space\talos\chunk\climate_layer\ClimateLatitudes.java',
  'space\talos\chunk\continent_layer\OrographyField.java',
  'space\talos\chunk\world\ChunkProviderTalos2.java',
  'space\talos\chunk\world\ClimateCoords.java',
  'space\talos\chunk\world\LandformField.java',
  'space\talos\chunk\world\MountainLayerV2.java',
  'space\talos\chunk\world\V2BiomeField.java',
  'space\talos\chunk\world\V2BiomeSelect.java',
  'space\talos\chunk\world\V2TerrainGen.java'
)
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
