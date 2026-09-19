@echo off
rem ============================================================================
rem Run ONE probe against an ALREADY-BUILT out\ tree, then append a done marker.
rem
rem WHY THIS FILE EXISTS:
rem   (a) runprobe4.bat cannot be called concurrently -- it does `rmdir /S /Q out`
rem       and rebuilds, so two concurrent calls destroy each other's class tree;
rem   (b) rerun_acceptance.ps1 therefore builds once and then needs a way to run
rem       one probe and RECORD ITS EXIT CODE. Start-Process -PassThru on PS 5.1
rem       reads ExitCode back empty, so cmd writes it into the log instead.
rem
rem Usage: run_one_probe.bat <ProbeName> <stdoutLog> <stderrLog>
rem   The three arguments are used UNQUOTED-BY-THE-CALLER on purpose: pass paths
rem   WITHOUT SPACES (the probe tree is under build\eoh_probe\mtn, which has none).
rem
rem KEEP THIS FILE ASCII-ONLY.
rem ============================================================================
rem SECTION 567: the terrain selector is GONE with the legacy terrain itself
rem (EOH_TALOS_TERRAIN / -Dtalos.terrain / PlateField.TALOS_TERRAIN were deleted together);
rem exactly ONE terrain implementation remains (TalosField).
java -Xmx6g -cp out probe.%1 > "%~2" 2> "%~3"
echo EOH_RUNNER_DONE=%errorlevel% JAVA_EXIT=%errorlevel% >> "%~2"
