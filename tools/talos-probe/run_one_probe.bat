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
rem EOH_TALOS_TERRAIN (optional): when set, run the probe with the V8 terrain switch ON.
rem Unset => the command line is byte-identical to before (zero behaviour change).
set TALOS_FLAG=
if defined EOH_TALOS_TERRAIN set TALOS_FLAG=-Dtalos.terrain=true
java -Xmx6g %TALOS_FLAG% -cp out probe.%1 > "%~2" 2> "%~3"
echo EOH_RUNNER_DONE=%errorlevel% JAVA_EXIT=%errorlevel% >> "%~2"
