@echo off
rem ASCII-ONLY (cmd.exe mis-parses non-ASCII bytes in a .bat).
rem
rem runp.bat -- build once, then run the probe WITH PROGRAM ARGUMENTS from the staging dir.
rem
rem WHY THIS EXISTS (four documented landmines it removes in one shot):
rem   E88: runprobe4.bat places %2 %3 %4 BEFORE `-cp out`, so they become JVM args
rem        (java then reports ClassNotFoundException: <first arg>).
rem   E89: `java -cp out ...` must run with cwd = the staging dir; running it from
rem        tools\talos-probe silently uses no classpath, and filtering build output once
rem        hid a STALE binary whose numbers were reported.
rem   E91: the first version forwarded only %2..%9 (8 args); the 9th was silently dropped
rem        (%10 in cmd is parsed as %1 followed by '0'), so a switch looked like a no-op.
rem        Fixed with a shift loop that forwards an UNBOUNDED number of arguments.
rem   E92: `shift` shifts %0 AS WELL. After the shift loop %~dp0 is therefore NOT this
rem        script's directory any more -- it follows whatever %0 now holds (it became
rem        K:\moder\EyeOfHarmonyBuffer\, i.e. the cwd). So `call "%~dp0runprobe4.bat"`
rem        failed with "'...\runprobe4.bat' is not recognized as an internal or external
rem        command" and the run surfaced ONLY as a bare BUILD_FAIL with no reason at all.
rem        Fix: pin %~dp0 into HERE before anything shifts, and call "%HERE%...".
rem
rem Usage:  runp.bat P499 0 TAG 5 1 0.43033 0 0.40 5.45 1
setlocal
set HERE=%~dp0
set PDIR=K:\moder\EyeOfHarmonyBuffer\build\eoh_probe\mtn
set PROBE=%1
shift
set ARGS=
:collect
if "%1"=="" goto collected
set ARGS=%ARGS% %1
shift
goto collect
:collected
call "%HERE%runprobe4.bat" %PROBE% --build-only
if errorlevel 1 (echo BUILD_FAIL %PROBE% & exit /b 1)
cd /d %PDIR%
echo RUNPROBE_ARGS=%PROBE%%ARGS%
rem SECTION 567: the terrain selector is GONE. EOH_TALOS_TERRAIN / -Dtalos.terrain and the
rem legacy PlateField terrain were deleted together; exactly ONE terrain implementation
rem remains (TalosField), so no flag can select one any more.
rem Do NOT put a raw -Dname=value on this command line: the shift loop above splits it at
rem '=' (E122), so the JVM would see a differently-named property and the flag would look
rem like a silent no-op.
java -Xmx6g -cp out probe.%PROBE% %ARGS%
echo JAVA_EXIT=%errorlevel%
