# ASCII-ONLY. Recomputes build/eoh_probe/mtn/SOURCE_FINGERPRINT.txt from the landed source.
#
# RECIPE (recovered this round by brute force; it was never written down before -- D42):
#   per file  : SHA256(bytes) first 16 hex, UPPERCASE
#   aggregate : SHA256 of the concatenation of "<hash16>  <relpath>\r\n" for all listed files
#               (CRLF after every line, including the last), first 32 hex, UPPERCASE
#   99 files  : 23 under sim\ + 76 under space\talos\
#               (24 -> 25 on 2026-09-13: sim\ocean\OceanWiring.java added by the step-3 wiring, S162)
#               (25 -> 99 on 2026-09-23, section 731: the registry was MISSING the whole
#                sim\atmos\ module -- HadleyCell.java, SoilMoisture.java, Vegetation.java,
#                StationaryWave.java, Radiation.java, ParcelLift.java, VerticalColumn.java --
#                and 30 of 38 files under space\talos\chunk\. Evidence: section 716 showed
#                editing HadleyCell.java left SRCFP byte-identical while editing
#                PrecipField.java changed it.)
#               SCOPE (deliberate): ALL of sim\** and ALL of space\talos\** = the simulation +
#               Talos world-generation source. The other ~550 of 652 .java files (machines,
#               recipes, mixins, client, utils) do NOT affect world/climate output and stay
#               out of the fingerprint on purpose.
$ErrorActionPreference = 'Stop'
$root = 'K:\moder\EyeOfHarmonyBuffer'
$src  = Join-Path $root 'src\main\java\com\EyeOfHarmonyBuffer'
$out  = Join-Path $root 'build\eoh_probe\mtn\SOURCE_FINGERPRINT.txt'
$rel  = @(
  'sim\atmos\Atmosphere.java','sim\atmos\HadleyCell.java','sim\atmos\ParcelLift.java',
  'sim\atmos\PrecipField.java','sim\atmos\Radiation.java','sim\atmos\SoilMoisture.java',
  'sim\atmos\StationaryWave.java','sim\atmos\Vegetation.java','sim\atmos\VerticalColumn.java',
  'sim\atmos\ZonalTables.java','sim\export\MapWriter.java','sim\litho\PlateField.java',
  'sim\litho\TalosField.java','sim\ocean\BasinFinder.java','sim\ocean\CoastalLayer.java',
  'sim\ocean\GyreRow.java','sim\ocean\OceanField.java','sim\ocean\OceanWiring.java',
  'sim\ocean\SeaSurfaceTemp.java','sim\ocean\SurfaceLayer.java','sim\runtime\SimClimate.java',
  'sim\runtime\SimTerrain.java','sim\world\WorldContract.java','space\talos\BiomeDecoratorTalos2.java',
  'space\talos\CaveLifecycleHandler.java','space\talos\TalosUndergroundFluidRegister.java','space\talos\WorldGenYuanShiDoubleConeCluster.java',
  'space\talos\WorldProviderTalos1.java','space\talos\WorldProviderTalos2.java','space\talos\biome\BiomeGenTalos2Alpine.java',
  'space\talos\biome\BiomeGenTalos2Basin.java','space\talos\biome\BiomeGenTalos2CoolForest.java','space\talos\biome\BiomeGenTalos2Desert.java',
  'space\talos\biome\BiomeGenTalos2Mountains.java','space\talos\biome\BiomeGenTalos2Ocean.java','space\talos\biome\BiomeGenTalos2Plains.java',
  'space\talos\biome\BiomeGenTalos2Plateau.java','space\talos\biome\BiomeGenTalos2PolarDesert.java','space\talos\biome\BiomeGenTalos2Savanna.java',
  'space\talos\biome\BiomeGenTalos2Shelf.java','space\talos\biome\BiomeGenTalos2SubpolarTundra.java','space\talos\biome\BiomeGenTalos2TemperateForest.java',
  'space\talos\biome\BiomeGenTalos2TemperateSteppe.java','space\talos\biome\BiomeGenTalos2TropicalRain.java','space\talos\biome\BiomeGenTalos2WarmSteppe.java',
  'space\talos\biome\TalosBiomeBase.java','space\talos\biome\TalosBiomes.java','space\talos\biome\TalosBoundedFeature.java',
  'space\talos\biome\TalosBoundedFeatures.java','space\talos\biome\TalosSurfaceProfile.java','space\talos\biome\TalosSurfaceRegistry.java',
  'space\talos\biome\TalosTreeBlueprints.java','space\talos\biome\api\TalosHeightModProvider.java','space\talos\chunk\cave_layer\api\TalosCaveSystem.java',
  'space\talos\chunk\cave_layer\format\CaveTag.java','space\talos\chunk\cave_layer\runtime\CaveChamber.java','space\talos\chunk\cave_layer\runtime\CaveChunkData.java',
  'space\talos\chunk\cave_layer\runtime\CaveEntrance.java','space\talos\chunk\cave_layer\runtime\CaveFlavorRegistry.java','space\talos\chunk\cave_layer\runtime\CaveGenerator.java',
  'space\talos\chunk\cave_layer\runtime\CaveMath.java','space\talos\chunk\cave_layer\runtime\CaveMegaHall.java','space\talos\chunk\cave_layer\runtime\CaveNode.java',
  'space\talos\chunk\cave_layer\runtime\CaveSegment.java','space\talos\chunk\cave_layer\runtime\CaveWorldState.java','space\talos\chunk\circulation_layer\ClimateSample.java',
  'space\talos\chunk\circulation_layer\GlobalClimate.java','space\talos\chunk\continent_layer\AirMassType.java','space\talos\chunk\continent_layer\NoiseContinentGrid.java',
  'space\talos\chunk\continent_layer\OrographyField.java','space\talos\chunk\continent_layer\PolarZone.java','space\talos\chunk\continent_layer\TectonicMath.java',
  'space\talos\chunk\terrain_layer\BaseTerrainPreset.java','space\talos\chunk\terrain_layer\BaseTerrainProfile.java','space\talos\chunk\terrain_layer\PeriodicNoise.java',
  'space\talos\chunk\terrain_layer\TerrainBaseHeight.java','space\talos\chunk\terrain_layer\TerrainMath.java','space\talos\chunk\terrain_layer\TerrainNoise.java',
  'space\talos\chunk\util\NoiseUtil.java','space\talos\chunk\util\SimplexNoise2D.java','space\talos\chunk\util\WindowKey.java',
  'space\talos\chunk\world\ChunkProviderTalos2.java','space\talos\chunk\world\ClimateCoords.java','space\talos\chunk\world\LandformField.java',
  'space\talos\chunk\world\MountainLayerV2.java','space\talos\chunk\world\TalosSeed.java','space\talos\chunk\world\V2BiomeField.java',
  'space\talos\chunk\world\V2BiomePicker.java','space\talos\chunk\world\V2BiomeSelect.java','space\talos\chunk\world\V2TerrainGen.java',
  'space\talos\chunk\world\WorldChunkManagerTalos2.java','space\talos\client\render\DysonPreviewRenderer.java','space\talos\client\render\DysonSphereRenderer.java',
  'space\talos\client\render\HugePlanetSkyRenderer.java','space\talos\client\resources\ResourcesDimensions.java','space\talos\station\ChunkProviderTalos2Station.java',
  'space\talos\station\SkyProviderTalos2Station.java','space\talos\station\WorldGenTileStructure.java','space\talos\station\WorldProviderTalos2Station.java'
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
# and a mangled byte that happens to be a quote breaks the parse (E33b).
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
