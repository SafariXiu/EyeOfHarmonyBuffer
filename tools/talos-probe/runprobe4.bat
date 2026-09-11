@echo off
setlocal
rem ============================================================================
rem  Talos probe harness (TRACKED copy).
rem
rem  WHY THIS LIVES IN tools\ AND NOT IN build\:
rem    build\ and run\ are gitignored, so the whole probe suite (217 probes + these
rem    guards) used to exist ONLY on this machine. Clearing build\ destroyed the
rem    evidence that any of the climate/terrain claims were ever checked. The
rem    harness is now version-controlled; only its GENERATED artifacts (copied
rem    sources, out\, *.txt reports, maps\) stay in the gitignored staging dir.
rem
rem  LAYOUT
rem    %~dp0            = this script + precheck.ps1 + sync_check.ps1 + probe\*.java
rem    PDIR             = staging (gitignored): copied com\** sources, out\, reports
rem
rem  KEEP THIS FILE ASCII-ONLY. cmd.exe mis-parses non-ASCII bytes in a .bat
rem  (it once read Chinese rem lines as commands and failed with 'haper' is not
rem   recognized). Probe sources may be UTF-8; this script may not.
rem ============================================================================
set ROOT=K:\moder\EyeOfHarmonyBuffer
set SRC=%ROOT%\src\main\java\com\EyeOfHarmonyBuffer
set PDIR=%ROOT%\build\eoh_probe\mtn
set PROBES=%~dp0probe
if not exist "%PDIR%" mkdir "%PDIR%"
cd /d %PDIR%
if not exist com\EyeOfHarmonyBuffer\space\talos\chunk\world mkdir com\EyeOfHarmonyBuffer\space\talos\chunk\world
if not exist com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer mkdir com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer
if not exist com\EyeOfHarmonyBuffer\space\talos\chunk\continent_layer mkdir com\EyeOfHarmonyBuffer\space\talos\chunk\continent_layer
if not exist com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer mkdir com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer
if not exist com\EyeOfHarmonyBuffer\space\talos\chunk\climate_layer mkdir com\EyeOfHarmonyBuffer\space\talos\chunk\climate_layer
if not exist com\EyeOfHarmonyBuffer\space\talos\chunk\util mkdir com\EyeOfHarmonyBuffer\space\talos\chunk\util
if not exist com\EyeOfHarmonyBuffer\space\talos\chunk\cave_layer\runtime mkdir com\EyeOfHarmonyBuffer\space\talos\chunk\cave_layer\runtime
copy /Y "%SRC%\space\talos\chunk\world\V2TerrainGen.java" com\EyeOfHarmonyBuffer\space\talos\chunk\world\V2TerrainGen.java >nul
copy /Y "%SRC%\space\talos\chunk\world\V2BiomeSelect.java" com\EyeOfHarmonyBuffer\space\talos\chunk\world\V2BiomeSelect.java >nul
copy /Y "%SRC%\space\talos\chunk\world\V2BiomeField.java" com\EyeOfHarmonyBuffer\space\talos\chunk\world\V2BiomeField.java >nul
copy /Y "%SRC%\space\talos\chunk\world\LandformField.java" com\EyeOfHarmonyBuffer\space\talos\chunk\world\LandformField.java >nul
copy /Y "%SRC%\space\talos\chunk\world\ClimateCoords.java" com\EyeOfHarmonyBuffer\space\talos\chunk\world\ClimateCoords.java >nul
copy /Y "%SRC%\space\talos\chunk\world\MountainLayerV2.java" com\EyeOfHarmonyBuffer\space\talos\chunk\world\MountainLayerV2.java >nul
copy /Y "%SRC%\space\talos\chunk\terrain_layer\PeriodicNoise.java" com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer\PeriodicNoise.java >nul
copy /Y "%SRC%\space\talos\chunk\terrain_layer\TerrainMath.java" com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer\TerrainMath.java >nul
copy /Y "%SRC%\space\talos\chunk\terrain_layer\TerrainNoise.java" com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer\TerrainNoise.java >nul
copy /Y "%SRC%\space\talos\chunk\terrain_layer\BaseTerrainProfile.java" com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer\BaseTerrainProfile.java >nul
copy /Y "%SRC%\space\talos\chunk\terrain_layer\BaseTerrainPreset.java" com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer\BaseTerrainPreset.java >nul
copy /Y "%SRC%\space\talos\chunk\terrain_layer\TerrainBaseHeight.java" com\EyeOfHarmonyBuffer\space\talos\chunk\terrain_layer\TerrainBaseHeight.java >nul
copy /Y "%SRC%\space\talos\chunk\continent_layer\TectonicMath.java" com\EyeOfHarmonyBuffer\space\talos\chunk\continent_layer\TectonicMath.java >nul
copy /Y "%SRC%\space\talos\chunk\continent_layer\OrographyField.java" com\EyeOfHarmonyBuffer\space\talos\chunk\continent_layer\OrographyField.java >nul
copy /Y "%SRC%\space\talos\chunk\continent_layer\AirMassType.java" com\EyeOfHarmonyBuffer\space\talos\chunk\continent_layer\AirMassType.java >nul
copy /Y "%SRC%\space\talos\chunk\circulation_layer\RelaxedClimate.java" com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer\RelaxedClimate.java >nul
copy /Y "%SRC%\space\talos\chunk\circulation_layer\BarotropicGyre.java" com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer\BarotropicGyre.java >nul
copy /Y "%SRC%\space\talos\chunk\circulation_layer\TalosContract.java" com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer\TalosContract.java >nul
copy /Y "%SRC%\space\talos\chunk\circulation_layer\ClimateSample.java" com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer\ClimateSample.java >nul
copy /Y "%SRC%\space\talos\chunk\circulation_layer\GlobalClimate.java" com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer\GlobalClimate.java >nul
copy /Y "%SRC%\space\talos\chunk\continent_layer\NoiseContinentGrid.java" com\EyeOfHarmonyBuffer\space\talos\chunk\continent_layer\NoiseContinentGrid.java >nul
copy /Y "%SRC%\space\talos\chunk\continent_layer\PolarZone.java" com\EyeOfHarmonyBuffer\space\talos\chunk\continent_layer\PolarZone.java >nul
copy /Y "%SRC%\space\talos\chunk\circulation_layer\ThermalForcing.java" com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer\ThermalForcing.java >nul
copy /Y "%SRC%\space\talos\chunk\circulation_layer\GlobalCirculation.java" com\EyeOfHarmonyBuffer\space\talos\chunk\circulation_layer\GlobalCirculation.java >nul
copy /Y "%SRC%\space\talos\chunk\climate_layer\ClimateLatitudes.java" com\EyeOfHarmonyBuffer\space\talos\chunk\climate_layer\ClimateLatitudes.java >nul
copy /Y "%SRC%\space\talos\chunk\util\NoiseUtil.java" com\EyeOfHarmonyBuffer\space\talos\chunk\util\NoiseUtil.java >nul
copy /Y "%SRC%\space\talos\chunk\util\SimplexNoise2D.java" com\EyeOfHarmonyBuffer\space\talos\chunk\util\SimplexNoise2D.java >nul
copy /Y "%SRC%\space\talos\chunk\util\WindowKey.java" com\EyeOfHarmonyBuffer\space\talos\chunk\util\WindowKey.java >nul
copy /Y "%SRC%\space\talos\chunk\cave_layer\runtime\CaveMath.java" com\EyeOfHarmonyBuffer\space\talos\chunk\cave_layer\runtime\CaveMath.java >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0sync_check.ps1"
if errorlevel 1 (echo SYNC_FAIL & exit /b 2)
rem precheck: BIDIRECTIONAL copy-list check + probe-coverage audit. Warns only -- exits 0 unless -Strict,
rem so it can never block a probe run (a guard that cries wolf daily would just be bypassed).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0precheck.ps1" -Probe "%1"
if errorlevel 1 (echo PRECHECK_FAIL & exit /b 3)
rem ---- which probes get compiled ----
rem   TRACKED guards (this repo dir) are ALWAYS compiled.
rem   The LOCAL-ONLY archive (206 historical probes in the gitignored staging dir) is compiled
rem   ONLY when the requested probe lives there. Two reasons:
rem     (a) guards stay fast and self-contained -- 18 probes instead of 224;
rem     (b) archive rot (an old probe referencing a symbol that was since deleted) can no
rem         longer block a guard run. That trap already fired once: five ICE_BAND-era probes
rem         broke EVERY run after the polar refactor until they were repointed.
set ARCHIVE=%PDIR%\probe
set PFOUND=0
if exist "%PROBES%\%1.java" set PFOUND=1
if exist "%ARCHIVE%\%1.java" set PFOUND=1
if "%PFOUND%"=="0" (echo PROBE_NOT_FOUND %1 & exit /b 4)
if exist out rmdir /S /Q out
dir /S /B /A:-D com\*.java > srcs.txt
dir /S /B /A:-D "%PROBES%\*.java" >> srcs.txt
rem archive: compile ONLY the one requested probe. Compiling all 199 at once means any single
rem stale historical probe breaks every archive run -- the same rot-coupling we just removed.
if exist "%ARCHIVE%\%1.java" echo %ARCHIVE%\%1.java >> srcs.txt
mkdir out
javac -encoding UTF-8 -nowarn -d out @srcs.txt 2>&1
if errorlevel 1 (echo JAVAC_FAIL & exit /b 1)
java -Xmx6g %2 %3 %4 -cp out probe.%1
echo JAVA_EXIT=%errorlevel%
