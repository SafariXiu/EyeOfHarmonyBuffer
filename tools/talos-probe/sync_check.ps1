# sync_check.ps1 -- stale-copy + source-drift guard for the probe tree.
#
# WHY (part 1, STALE_COPY): runprobe4.bat copies a HAND-MAINTAINED LIST of individual files
# from src into the staging tree, but javac then compiles ALL of com\*.java in that tree. Any
# file that exists in the staging tree but is NOT in the copy list silently stays at an old
# revision, so a probe can "measure old code" while looking perfectly healthy. This has bitten
# us three times (RelaxedClimate.java isLandCell fix appeared to have no effect; PeriodicNoise.java
# and MountainLayerV2.java nearly invalidated a fingerprint check).
#
# WHY (part 2, SRC_DRIFT): _baseline_src_sha256.txt records the 27 probe-visible files at the
# revision the current definition values (P221 golds, P215 contract rows, P235 polar thresholds)
# were measured at. Without this, "when did this number move, and what moved with it?" has no
# answer. Drift is REPORTED, never fatal: editing src is the normal case, so a guard that blocked
# on it would be bypassed within a day (same policy as precheck.ps1).
#
# 2026-09: this script moved to tools\talos-probe\ (tracked). $dst is the gitignored STAGING
# tree that runprobe4.bat fills with copies of src, NOT this script's own directory.
#
# FEASIBILITY NOTE (evaluated): copying whole directories instead of a list is NOT viable.
# The probe tree deliberately compiles only a small subset of src (the rest depends on
# GTNH/Minecraft types that are not on the probe classpath), and it also contains
# hand-written stubs that do not exist in src. A full-tree copy makes javac fail outright.
# Correct approach: keep the minimal copy list AND let this script prove the list is complete.
#
# Exit 0 = all staging copies match src.  Exit 1 = at least one STALE_COPY.
#
# Usage:
#   sync_check.ps1                  # verify (called by runprobe4.bat)
#   sync_check.ps1 -WriteBaseline   # re-record the baseline after an intentional src change

param(
    [switch]$WriteBaseline
)

$ErrorActionPreference = 'Stop'
$dir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$dst  = 'K:\moder\EyeOfHarmonyBuffer\build\eoh_probe\mtn\com\EyeOfHarmonyBuffer'
$src  = 'K:\moder\EyeOfHarmonyBuffer\src\main\java\com\EyeOfHarmonyBuffer'
$base = Join-Path $dir '_baseline_src_sha256.txt'

if (-not (Test-Path $dst)) { Write-Output 'SYNC_CHECK: staging tree missing, skipped'; exit 0 }
if (-not (Test-Path $src)) { Write-Output 'SYNC_CHECK: src missing'; exit 1 }

# ---- part 1: staging copy vs src -------------------------------------------------
$stale = New-Object System.Collections.ArrayList
$rels  = New-Object System.Collections.ArrayList
$ok = 0
foreach ($f in Get-ChildItem -Recurse -File -Filter *.java $dst) {
    $rel = $f.FullName.Substring($dst.Length + 1)
    $s = Join-Path $src $rel
    if (-not (Test-Path $s)) { continue }
    if ((Get-FileHash $s).Hash -ne (Get-FileHash $f.FullName).Hash) { [void]$stale.Add($rel) } else { $ok++ }
    [void]$rels.Add($rel)
}
Write-Output ('SYNC_CHECK: in-sync ' + $ok + '; stale ' + $stale.Count)
foreach ($r in $stale) { Write-Output ('STALE_COPY ' + $r) }

# ---- part 2: source drift vs the recorded baseline -------------------------------
if ($WriteBaseline) {
    $lines = @()
    foreach ($rel in ($rels | Sort-Object)) {
        $lines += ((Get-FileHash (Join-Path $src $rel)).Hash + '  ' + $rel)
    }
    [IO.File]::WriteAllText($base, (($lines -join [Environment]::NewLine) + [Environment]::NewLine), (New-Object Text.UTF8Encoding($false)))
    Write-Output ('SYNC_CHECK: baseline rewritten, ' + $lines.Count + ' files -> ' + $base)
} elseif (Test-Path $base) {
    $recorded = @{}
    foreach ($ln in (Get-Content $base)) {
        if ($ln -match '^([0-9A-Fa-f]{64})\s+(.+)$') { $recorded[$Matches[2].Trim()] = $Matches[1].ToUpper() }
    }
    $drift = New-Object System.Collections.ArrayList
    $missing = New-Object System.Collections.ArrayList
    foreach ($rel in ($rels | Sort-Object)) {
        $now = (Get-FileHash (Join-Path $src $rel)).Hash
        if (-not $recorded.ContainsKey($rel)) { [void]$missing.Add($rel); continue }
        if ($recorded[$rel] -ne $now) { [void]$drift.Add($rel + '  ' + $recorded[$rel].Substring(0,8) + ' -> ' + $now.Substring(0,8)) }
    }
    Write-Output ('SYNC_CHECK: baseline ' + $recorded.Count + ' entries; drifted ' + $drift.Count + '; not-in-baseline ' + $missing.Count)
    foreach ($d in $drift) { Write-Output ('SRC_DRIFT ' + $d) }
    foreach ($m in $missing) { Write-Output ('SRC_NEW ' + $m) }
    if ($drift.Count -gt 0 -or $missing.Count -gt 0) {
        Write-Output 'SRC_DRIFT means src moved since the definition values were recorded.'
        Write-Output 'It is NOT an error (editing src is normal). Re-run the affected probes, then'
        Write-Output 'refresh with: sync_check.ps1 -WriteBaseline'
    }
}

if ($stale.Count -gt 0) {
    Write-Output 'STALE_COPY means the probe tree holds an OLD revision; any conclusion from it is void.'
    Write-Output 'Fix: add the file to the copy list in runprobe4.bat, or Copy-Item it once by hand.'
    exit 1
}
exit 0
