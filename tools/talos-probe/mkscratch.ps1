# mkscratch.ps1 -- build a fresh sub-agent scratch tree FROM src.
#
# WHY: the task template used to say "robocopy from build\eoh_probe\mtn\com", which is a
#      SNAPSHOT of whatever probe ran last. It goes stale silently -- caught three times:
#      section 91.1b (old terrain), 94.1 (missing tropicGate), 97 (4 files behind,
#      PrecipField 108 -> 263 lines). Old recipe => you silently measure OLD PHYSICS.
#
# HOW: the copy list is READ OUT OF runprobe4.bat (its hand-curated, proven-working set of
#      sources) and re-executed with src as the source. So the list stays single-sourced:
#      change the .bat, this script follows. Then every copied file is verified by hash.
#
# USAGE: powershell -NoProfile -ExecutionPolicy Bypass -File mkscratch.ps1 -Name basin
#
# KEEP THIS FILE ASCII-ONLY. Windows PowerShell 5.1 reads BOM-less .ps1 as ANSI;
# non-ASCII bytes here once mojibake'd a comment and swallowed the NEXT line of code.
param([Parameter(Mandatory=$true)][string]$Name)

# ---- SHA256 helper -------------------------------------------------------------------
# WHY: this script used Get-FileHash, which stopped resolving in the runprobe4.bat child
# process mid-session (2026-09-14) -- 'Get-FileHash is not recognized as the name of a
# cmdlet' -- while the same call worked from an interactive prompt. Rather than depend on
# module autoloading, compute SHA256 with the crypto class (the same recipe fingerprint.ps1
# already uses, and it produces the SAME uppercase hex as Get-FileHash, so the recorded
# baselines stay valid).
$script:EohSha = [System.Security.Cryptography.SHA256]::Create()
# Drop-in for Get-FileHash: returns an object with a .Hash property so that every
# existing '(Get-FileHash x).Hash' call site keeps working unchanged.
function Get-EohHash([string]$path) {
    $b = [IO.File]::ReadAllBytes($path)
    $h = [BitConverter]::ToString($script:EohSha.ComputeHash($b)).Replace('-','')
    return New-Object psobject -Property @{ Hash = $h }
}
# --------------------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$S = 'K:\moder\EyeOfHarmonyBuffer'
$W = "$S\build\eoh_scratch_$Name"
$SRCROOT = "$S\src\main\java\com"
$SRCJ = "$SRCROOT\EyeOfHarmonyBuffer"

if (Test-Path "$W\com") { Remove-Item -Recurse -Force "$W\com" }
New-Item -ItemType Directory -Force "$W\com" | Out-Null
New-Item -ItemType Directory -Force "$W\out" | Out-Null

$bat = Get-Content "$S\tools\talos-probe\runprobe4.bat"
$rx = [regex]'copy /Y "%SRC%\\([^"]+)"\s+(\S+)'
$n = 0
foreach ($line in $bat) {
    $m = $rx.Match($line)
    if (-not $m.Success) { continue }
    $rel = $m.Groups[1].Value
    $dst = Join-Path $W $m.Groups[2].Value
    $dir = Split-Path $dst -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
    Copy-Item (Join-Path $SRCJ $rel) $dst -Force
    $n++
}
Write-Output "COPIED_FROM_SRC=$n  (list read from runprobe4.bat)"

# runprobe4.bat's 41-file list is INCOMPLETE and silently relied on staging residue
# (e.g. Config/TalosConfig.java is needed by MountainLayerV2 but is not in the list).
# So: also take anything the staging tree has that src does not, and NAME it.
$add = @()
$STG = "$S\build\eoh_probe\mtn\com"
$base0 = Join-Path $W 'com'
if (Test-Path $STG) {
    Get-ChildItem -Recurse -Filter *.java $STG | ForEach-Object {
        $r = $_.FullName.Substring($STG.Length + 1)
        $dst = Join-Path $base0 $r
        if (Test-Path $dst) { return }
        $sp = Join-Path $SRCROOT $r
        $dir = Split-Path $dst -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
        if (Test-Path $sp) { Copy-Item $sp $dst -Force; $add += "src:$r" }
        else               { Copy-Item $_.FullName $dst -Force; $add += "STAGING_ONLY:$r" }
    }
}
foreach ($a in $add) { Write-Output "  ADDED $a" }
robocopy "$S\tools\talos-probe\probe" "$W\probe" /E /NFL /NDL /NJH /NJS /NP | Out-Null

# verify: every copied file must be bit-identical to src
# These two live ONLY under build\ (never in src) and have done so for a long time -- the
# harness has silently depended on this residue. Named here so it is never invisible again.
$stagingOnly = @('EyeOfHarmonyBuffer\Config\TalosConfig.java',
                 'EyeOfHarmonyBuffer\probeorig\OrigMountainV2.java')
$mismatch = @()
$base = $base0
Get-ChildItem -Recurse -Filter *.java $base | ForEach-Object {
    $r = $_.FullName.Substring($base.Length + 1)
    if ($stagingOnly -contains $r) { Write-Output "  ALLOWED_STAGING_ONLY $r"; return }
    $o = Join-Path $SRCROOT $r
    if (-not (Test-Path $o)) { $mismatch += "NO_SRC $r" }
    elseif ((Get-EohHash $_.FullName).Hash -ne (Get-EohHash $o).Hash) { $mismatch += "DIFFERS $r" }
}
if ($mismatch.Count -gt 0) {
    Write-Output "SCRATCH_SYNC_FAIL ($($mismatch.Count)):"
    $mismatch | Select-Object -First 20 | ForEach-Object { Write-Output "  $_" }
    exit 2
}
Write-Output "SCRATCH_SYNC_OK  $n files bit-identical to src"

cd $W
Get-ChildItem -Recurse -Filter *.java -Path com,probe | ForEach-Object { $_.FullName } | Set-Content srcs.txt -Encoding ASCII
javac -encoding UTF-8 -nowarn -d out "@srcs.txt"
if ($LASTEXITCODE -ne 0) { Write-Output "JAVAC_FAIL"; exit 1 }
Write-Output "SCRATCH_READY=$W"
