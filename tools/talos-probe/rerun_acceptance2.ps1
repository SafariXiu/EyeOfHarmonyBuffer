# ASCII-ONLY. Second batch: the three probes that were still pending.
$ErrorActionPreference = 'Continue'
$root  = 'K:\moder\EyeOfHarmonyBuffer'
$probe = Join-Path $root 'tools\talos-probe'
$mtn   = Join-Path $root 'build\eoh_probe\mtn'
$out   = Join-Path $mtn 'rerun_acceptance'
if (!(Test-Path $out)) { New-Item -ItemType Directory -Path $out | Out-Null }
$list = @('P295', 'P297', 'P261')
$summary = Join-Path $out 'SUMMARY.txt'
foreach ($p in $list) {
  $log = Join-Path $out ('log_' + $p + '.txt')
  Write-Output ('=== RUN ' + $p)
  Push-Location $probe
  $cmdline = 'runprobe4.bat ' + $p + ' > "' + $log + '" 2>&1'
  cmd /c $cmdline
  $code = $LASTEXITCODE
  Pop-Location
  $jx = 'no-JAVA_EXIT'
  if (Test-Path $log) {
    $tail = Get-Content $log -Tail 3 -ErrorAction SilentlyContinue
    foreach ($t in $tail) { if ($t -match 'JAVA_EXIT=') { $jx = $t.Trim() } }
  }
  Write-Output ('    exit=' + $code + '  ' + $jx)
  Add-Content -Path $summary -Value ($p + '  exit=' + $code + '  ' + $jx) -Encoding ASCII
}
Add-Content -Path $summary -Value ('BATCH2 FINISHED ' + (Get-Date -Format s)) -Encoding ASCII
Write-Output 'ALL_DONE'
