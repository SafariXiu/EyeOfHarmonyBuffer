@echo off
rem ============================================================================
rem Run ONE z-row shard of P284 against an ALREADY-BUILT out\ tree (D76).
rem Usage: run_p284_shard.bat <rLo> <rHi> <partFile> <logFile>
rem   All four arguments are plain tokens WITHOUT SPACES on purpose (see
rem   run_one_probe.bat for why Start-Process cannot take a quoted command line).
rem KEEP THIS FILE ASCII-ONLY.
rem ============================================================================
rem -Xmx2g is deliberate: a shard holds only (rows in band) x 2001 doubles (a few hundred KB),
rem   so 6g would just make many concurrent shards needlessly fat. The MERGE step needs the
rem   full 36018-double kappa array -- still trivial -- and runs in run_p284_sharded.ps1.
java -Xmx2g -cp out probe.P284 shard %1 %2 %3 > "%~4" 2>&1
echo EOH_RUNNER_DONE=%errorlevel% JAVA_EXIT=%errorlevel% >> "%~4"
