# precheck.ps1 -- probe-tree pre-flight: BIDIRECTIONAL copy-list check + probe-coverage audit.
#
# WHY THIS EXISTS (two holes that sync_check.ps1 structurally cannot see):
#
#  (1) sync_check.ps1 compares only files that exist in BOTH trees. runprobe4.bat copies a
#      HAND-MAINTAINED LIST (23 entries) while javac compiles EVERY com\*.java in the probe tree
#      (29 files = 27 src-derived + 2 hand-written stubs). So a file that IS in the probe tree and
#      IS in src but is NOT in the copy list stays silently frozen at an old revision the moment
#      anyone edits src. Today those 4 files happen to be identical, so sync_check reports
#      "in-sync 27; stale 0" -- i.e. it looks perfectly healthy while the trap is armed.
#      => DIRECTION 2: every file in the probe tree must be either in the copy list or a known stub.
#
#  (2) A probe that compiles a file does not necessarily EXECUTE it. "Which probe can verify
#      this file?" was never recorded anywhere. => coverage audit below.
#
# NAME-MATCHING RULE: comments AND string/char literals are stripped from BOTH probe sources and
# src files before matching -- only real code tokens count. This is not cosmetic, and it was
# tightened twice by measurement:
#   * with javadoc left in, {@link} mentions fabricate call edges: V2BiomeField's transitive
#     coverage inflates from a real 43 probes to 187/187 (LandformField 81->187, TerrainNoise
#     83->187, BarotropicGyre 148->189).
#   * with string literals left in, a class name inside a log message also fabricates an edge:
#     P216 mentions V2TerrainGen only in a say(...) string, yet that alone inflated
#     BaseTerrainPreset's transitive coverage from 81 to 82.
# Any number in this file depends on this rule, so the rule is stated here rather than assumed.
# Note the direction of the residual error: stripping only ever makes coverage look SMALLER,
# i.e. the guard errs toward warning, never toward silence.
#
# Only probe\*.java counts as a probe: the directory also holds _OBSOLETE_P*.txt (retired probes).
# From 2026-09 the corpus is split: 18 guards live in the repo (tools/talos-probe/probe), the other
# ~206 historical probes live in the gitignored staging dir. Both are read here; each row is labelled.
# Mixing them in inflates RelaxedClimate's direct coverage from 141 to 147.
#
# EXIT CODE: 0 by default (warnings only). -Strict => 1 when any gap/warning exists.
# Rationale (agreed with the reviewer): 8 files already have no/indirect coverage today, so
# strict-by-default would block EVERY probe run and the guard would simply be bypassed.
# A guard that cries wolf daily is not a guard. Hard blocking stays with sync_check (STALE_COPY).
#
# Usage:
#   precheck.ps1 -Probe P168        # what this probe will NOT see (called by runprobe4.bat)
#   precheck.ps1 -Files a.java,b.java
#   precheck.ps1 -Table             # full 27-row coverage table
#   precheck.ps1 -WriteTable        # also (re)generate COVERAGE.md
#   precheck.ps1 -Strict            # non-zero exit if anything is flagged

param(
    [string]$Probe = '',
    [string]$Files = '',
    [switch]$Table,
    [switch]$WriteTable,
    [switch]$Strict
)


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
# 2026-09: the harness moved from build\ (gitignored) to tools\talos-probe\ (tracked).
# $dir = tracked sources/scripts; $dst = the gitignored STAGING tree that runprobe4.bat fills.
$dir      = Split-Path -Parent $MyInvocation.MyCommand.Path
$dst      = 'K:\moder\EyeOfHarmonyBuffer\build\eoh_probe\mtn\com\EyeOfHarmonyBuffer'
# $probeDir = TRACKED live guards (18). $archiveDir = LOCAL-ONLY archaeology (gitignored).
# Both are audited here so the coverage/FINGERPRINT table keeps covering the whole corpus;
# each entry is labelled with where it lives.
$probeDir   = Join-Path $dir 'probe'
$archiveDir = 'K:\moder\EyeOfHarmonyBuffer\build\eoh_probe\mtn\probe'
$bat      = Join-Path $dir 'runprobe4.bat'
$src      = 'K:\moder\EyeOfHarmonyBuffer\src\main\java\com\EyeOfHarmonyBuffer'

if (-not (Test-Path $dst)) { Write-Output 'PRECHECK: probe tree missing, skipped'; exit 0 }
if (-not (Test-Path $src)) { Write-Output 'PRECHECK: src missing'; exit 1 }

# ------------------------------------------------------------------ helpers
# Strip everything that is not executable code: block comments, line comments, string literals,
# char literals. Order matters (comments first, so quotes inside comments cannot desync the
# literal regex). No reflection is used in this worldgen layer, so a class named only inside a
# string literal genuinely cannot be executed by a probe.
function Strip-NonCode([string]$t) {
    $t = [regex]::Replace($t, '(?s)/\*.*?\*/', ' ')
    $t = [regex]::Replace($t, '(?m)//.*$', ' ')
    $t = [regex]::Replace($t, '"(?:\\.|[^"\\])*"', '""')
    $t = [regex]::Replace($t, '''(?:\\.|[^''\\])*''', '''''')
    return $t
}

# Hand-curated: which deterministic artifact is the STRONGEST evidence for a file.
# Every entry below was verified by reading the named probe's own javadoc/output paths.
# (Curated on purpose: only a human knows which output is a fingerprint. Machine-derived
#  columns are dir=/trans=; this column is a hint, not a derivation.)
$FINGERPRINT = @{
    'RelaxedClimate'   = 'P195 -> run\talos_maps\signstats.txt (SHA-256; ONLY deterministic artifact covering it) ; P205 (isLandCell)'
    'BarotropicGyre'   = 'P221 (production RMS u/v + wind RMS + closed-basin gold cross-check) ; P200 (idealized basins). P210 archived: its switch WRAP_Y was deleted 2026-09'
    'LandformField'    = 'P168 (tile fingerprint) ; P169 (banded fingerprints) ; P176 (duplicate band)'
    'MountainLayerV2'  = 'P169 / P176 (fingerprints) ; P170 (NZ-exception evidence)'
    'V2BiomeField'     = 'P169 (banded) ; P209 (biasHash/scaleHash must be bit-identical) ; P214 (kind string)'
    'V2BiomeSelect'    = 'P209 (biome jitter acceptance)'
    'V2TerrainGen'     = 'P172 (Z no-fold end-to-end)'
    'PeriodicNoise'    = 'P172 (cell-count convention + direct A/B)'
    'WindowKey'        = 'TalosContract T6a via P215 (brute-force injectivity: 48223 keys, 0 collisions; the OLD packed formula collides 21979 times on the SAME sample = reverse control). dir=0 only because no probe names the class -- T6a calls WindowKey.of directly.'
    'TalosContract'    = 'P215 (whole table; declared-defects register is now EMPTY) ; P216 (negative self-test: INFINITE_Z=false). The WRAP_Y injection sentinel retired with the switch itself'
}
# Hand-curated notes for files with NO probe coverage at all (machine-detected below).
$BLIND_NOTE = @{
    'TalosSeafloorShaper' = 'V1-track blind spot (HIGH): called by ChunkProviderTalos2.generateTerrainWithBaseHeightSimple (the v2Track=false branch only; V1 is not retired). NO probe can verify a change to it.'
    'GlobalClimate'       = 'Referenced in code only by CommandTalosMap (not in the probe tree) => probes can never reach it. Not on the playable path (mentions in ClimateCoords/NoiseContinentGrid are comments only).'
    'ClimateSample'       = 'Referenced in code only by CommandTalosMap / GlobalClimate => probes can never reach it.'
    'AirMassType'         = 'Referenced in code only by CommandTalosMap / GlobalClimate / ClimateSample => probes can never reach it.'
}

# ------------------------------------------------------------------ copy list (direction 1 source of truth)
$copyList = New-Object System.Collections.Generic.List[string]
foreach ($ln in (Get-Content $bat)) {
    if ($ln -match 'copy /Y "%SRC%\\(.+?)"\s') { [void]$copyList.Add($Matches[1]) }
}

# ------------------------------------------------------------------ enumerate probe tree
$treeRel   = New-Object System.Collections.Generic.List[string]
$nameOf    = @{}
foreach ($f in Get-ChildItem -Recurse -File -Filter *.java $dst) {
    $rel = $f.FullName.Substring($dst.Length + 1)
    [void]$treeRel.Add($rel)
    $nameOf[$rel] = [IO.Path]::GetFileNameWithoutExtension($f.Name)
}
$treeRel = $treeRel | Sort-Object

$gapList = New-Object System.Collections.Generic.List[string]   # in tree + in src, NOT in copy list
$stubList = New-Object System.Collections.Generic.List[string]  # in tree, not in src, not in copy list (stubs)
foreach ($rel in $treeRel) {
    $inList = $copyList -contains $rel
    $inSrc  = Test-Path (Join-Path $src $rel)
    if ($inList) { continue }
    if ($inSrc) { [void]$gapList.Add($rel) } else { [void]$stubList.Add($rel) }
}

$orphanList = New-Object System.Collections.Generic.List[string]  # in copy list, missing from tree
foreach ($c in $copyList) { if (-not ($treeRel -contains $c)) { [void]$orphanList.Add($c) } }

# hash comparison for every tree file that also exists in src
$hashDiffInList = New-Object System.Collections.Generic.List[string]
$hashDiffOutList = New-Object System.Collections.Generic.List[string]
foreach ($rel in $treeRel) {
    $sp = Join-Path $src $rel
    if (-not (Test-Path $sp)) { continue }
    if ((Get-EohHash $sp).Hash -ne (Get-EohHash (Join-Path $dst $rel)).Hash) {
        if ($copyList -contains $rel) { [void]$hashDiffInList.Add($rel) } else { [void]$hashDiffOutList.Add($rel) }
    }
}

# ------------------------------------------------------------------ coverage graph
$srcDerived = @($treeRel | Where-Object { Test-Path (Join-Path $src $_) })
$srcText = @{}
foreach ($rel in $srcDerived) { $srcText[$nameOf[$rel]] = Strip-NonCode (Get-Content -Raw (Join-Path $src $rel)) }
$names = @($srcText.Keys | Sort-Object)

$probeText = @{}
$probeOrigin = @{}
foreach ($f in Get-ChildItem -File -Filter *.java $probeDir) {
    $probeText[$f.BaseName] = Strip-NonCode (Get-Content -Raw $f.FullName)
    $probeOrigin[$f.BaseName] = 'tracked'
}
$nTracked = $probeText.Count
if (Test-Path $archiveDir) {
    foreach ($f in Get-ChildItem -File -Filter *.java $archiveDir) {
        if ($probeText.ContainsKey($f.BaseName)) { continue }
        $probeText[$f.BaseName] = Strip-NonCode (Get-Content -Raw $f.FullName)
        $probeOrigin[$f.BaseName] = 'local'
    }
}
$nLocal = $probeText.Count - $nTracked

$graph = @{}
foreach ($a in $names) {
    $t = New-Object System.Collections.Generic.List[string]
    foreach ($b in $names) { if ($b -ne $a -and $srcText[$a] -match ('\b' + [regex]::Escape($b) + '\b')) { [void]$t.Add($b) } }
    $graph[$a] = $t
}

$direct = @{}
foreach ($n in $names) {
    $hit = New-Object System.Collections.Generic.List[string]
    foreach ($p in $probeText.Keys) { if ($probeText[$p] -match ('\b' + [regex]::Escape($n) + '\b')) { [void]$hit.Add($p) } }
    $direct[$n] = @($hit | Sort-Object)
}

# transitive coverage: for each probe, walk DOWN the graph from what it names
$trans = @{}
foreach ($n in $names) { $trans[$n] = New-Object System.Collections.Generic.HashSet[string] }
foreach ($p in $probeText.Keys) {
    $q = New-Object System.Collections.Generic.Queue[string]
    foreach ($n in $names) { if ($probeText[$p] -match ('\b' + [regex]::Escape($n) + '\b')) { $q.Enqueue($n) } }
    $seen = New-Object System.Collections.Generic.HashSet[string]
    while ($q.Count -gt 0) {
        $c = $q.Dequeue()
        if (-not $seen.Add($c)) { continue }
        if ($graph.ContainsKey($c)) { foreach ($g in $graph[$c]) { $q.Enqueue($g) } }
    }
    foreach ($c in $seen) { [void]$trans[$c].Add($p) }
}

$noCoverage   = @($names | Where-Object { @($direct[$_]).Count -eq 0 -and @($trans[$_]).Count -eq 0 } | Sort-Object)
$indirectOnly = @($names | Where-Object { @($direct[$_]).Count -eq 0 -and @($trans[$_]).Count -gt 0 } | Sort-Object)

# ------------------------------------------------------------------ report
$issues = 0
Write-Output ('PRECHECK: compiled=' + $treeRel.Count + ' src-derived=' + $srcDerived.Count + ' stubs=' + $stubList.Count + ' copyList=' + $copyList.Count + ' probes=' + $probeText.Count + ' (tracked=' + $nTracked + ' local-only=' + $nLocal + ')')

Write-Output ('--- DIRECTION 1: copy list -> probe tree copy is current (' + $copyList.Count + ' entries) ---')
foreach ($r in $hashDiffInList) { $issues++; Write-Output ('COPY_STALE ' + $r) }
foreach ($r in $orphanList)    { $issues++; Write-Output ('COPYLIST_ORPHAN ' + $r + ' (listed in runprobe4.bat but absent from the probe tree)') }
if ($hashDiffInList.Count -eq 0 -and $orphanList.Count -eq 0) { Write-Output ('  OK: all ' + $copyList.Count + ' listed files present and identical to src') }

Write-Output ('--- DIRECTION 2: probe tree -> copy list (NEW; sync_check cannot see this) ---')
if ($gapList.Count -eq 0) {
    Write-Output '  OK: every compiled src-derived file is in the copy list'
} else {
    foreach ($r in $gapList) {
        $issues++
        $nowSame = (Get-EohHash (Join-Path $src $r)).Hash -eq (Get-EohHash (Join-Path $dst $r)).Hash
        Write-Output ('COPYLIST_GAP ' + $r + '  (identical to src RIGHT NOW=' + $nowSame + ')')
    }
    Write-Output ('WARN: ' + $gapList.Count + ' file(s) are COMPILED by the probe tree but NOT in runprobe4.bat''s copy list.')
    Write-Output 'WARN: they happen to match src today, so sync_check reports healthy -- but the next src edit will NOT reach them.'
    Write-Output 'WARN: this is exactly the "probe measured an old revision" trap (3 recorded incidents).'
}
foreach ($r in $stubList) { Write-Output ('  stub (intentional, not in src): ' + $r) }
foreach ($r in $hashDiffOutList) { $issues++; Write-Output ('COPYLIST_GAP_STALE ' + $r + ' (NOT in copy list AND already differs from src)') }

Write-Output ('--- COVERAGE: no probe can execute these (' + $noCoverage.Count + ') ---')
if ($noCoverage.Count -eq 0) { Write-Output '  (none)' }
foreach ($n in $noCoverage) {
    $issues++
    $rel = @($srcDerived | Where-Object { $nameOf[$_] -eq $n })[0]
    $note = ''
    if ($BLIND_NOTE.ContainsKey($n)) { $note = ' :: ' + $BLIND_NOTE[$n] }
    Write-Output ('NO_PROBE_COVERAGE ' + $rel + $note)
}
Write-Output ('--- COVERAGE: reachable only through another class, never named by a probe (' + $indirectOnly.Count + ') ---')
foreach ($n in $indirectOnly) {
    $rel = @($srcDerived | Where-Object { $nameOf[$_] -eq $n })[0]
    Write-Output ('INDIRECT_ONLY ' + $rel + '  (trans=' + @($trans[$n]).Count + ' probes)')
}

# per-file detail / scope
$notInScope = @()
if ($Probe -ne '') {
    $pn = $Probe
    if ($pn.EndsWith('.java')) { $pn = $pn.Substring(0, $pn.Length - 5) }
    if (-not $probeText.ContainsKey($pn)) {
        Write-Output ('SCOPE: probe ' + $pn + ' not found under probe\ -- scope check skipped')
    } else {
        $q = New-Object System.Collections.Generic.Queue[string]
        foreach ($n in $names) { if ($probeText[$pn] -match ('\b' + [regex]::Escape($n) + '\b')) { $q.Enqueue($n) } }
        $seen = New-Object System.Collections.Generic.HashSet[string]
        while ($q.Count -gt 0) {
            $c = $q.Dequeue()
            if (-not $seen.Add($c)) { continue }
            if ($graph.ContainsKey($c)) { foreach ($g in $graph[$c]) { $q.Enqueue($g) } }
        }
        $notInScope = @($names | Where-Object { -not $seen.Contains($_) } | Sort-Object)
        Write-Output ('--- SCOPE for ' + $pn + ': it can NOT see these ' + $notInScope.Count + ' file(s) ---')
        foreach ($n in $notInScope) {
            $rel = @($srcDerived | Where-Object { $nameOf[$_] -eq $n })[0]
            Write-Output ('NOT_IN_SCOPE ' + $rel)
        }
        if ($notInScope.Count -gt 0) {
            Write-Output ('WARN: a green ' + $pn + ' run proves NOTHING about the ' + $notInScope.Count + ' file(s) above.')
        }
    }
}

if ($Files -ne '') {
    Write-Output '--- REQUESTED FILES ---'
    foreach ($raw in ($Files -split ',')) {
        $fn = $raw.Trim()
        if ($fn -eq '') { continue }
        $n = [IO.Path]::GetFileNameWithoutExtension($fn)
        if (-not $srcText.ContainsKey($n)) { Write-Output ('NOT_COMPILED_BY_PROBE ' + $fn); continue }
        $d = @($direct[$n]); $t = @($trans[$n])
        if ($d.Count -gt 0) {
            Write-Output ('COVERED_BY ' + $fn + ' -> direct=' + ($d -join ','))
        } elseif ($t.Count -gt 0) {
            Write-Output ('INDIRECT_ONLY ' + $fn + ' -> trans=' + $t.Count + ' probes (no probe names it)')
        } else {
            $issues++
            Write-Output ('NO_PROBE_COVERAGE ' + $fn + ' -> NO probe can execute it')
        }
        if ($FINGERPRINT.ContainsKey($n)) { Write-Output ('FINGERPRINT ' + $n + ' -> ' + $FINGERPRINT[$n]) }
    }
}

if ($Table -or $WriteTable) {
    Write-Output '--- FULL COVERAGE TABLE (code tokens only; dir = probe names it, trans = reachable) ---'
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add('# Probe coverage table (GENERATED by precheck.ps1 -WriteTable; do not hand-edit)')
    $lines.Add('')
    $lines.Add('Rule: comments + string/char literals stripped from probe sources and src before name matching (code tokens only); only probe\*.java counts.')
    $lines.Add('dir = probes naming the class; trans = probes that can reach it through the call graph.')
    $lines.Add('')
    $lines.Add('| file | dir | trans | direct probes | strongest artifact (hand-curated) |')
    $lines.Add('|---|---|---|---|---|')
    foreach ($n in $names) {
        $rel = @($srcDerived | Where-Object { $nameOf[$_] -eq $n })[0]
        $d = @($direct[$n]); $t = @($trans[$n])
        $fp = '-'; if ($FINGERPRINT.ContainsKey($n)) { $fp = $FINGERPRINT[$n] }
        $dCell = '-'; if ($d.Count -gt 0) { $dCell = $d -join ' ' }
        Write-Output ('  ' + $rel.PadRight(58) + ' dir=' + $d.Count.ToString().PadLeft(3) + ' trans=' + $t.Count.ToString().PadLeft(3))
        $lines.Add('| ' + $rel + ' | ' + $d.Count + ' | ' + $t.Count + ' | ' + $dCell + ' | ' + $fp + ' |')
    }
    $lines.Add('')
    $lines.Add('## NO probe coverage (' + $noCoverage.Count + ')')
    foreach ($n in $noCoverage) {
        $rel = @($srcDerived | Where-Object { $nameOf[$_] -eq $n })[0]
        $note = ''; if ($BLIND_NOTE.ContainsKey($n)) { $note = ' -- ' + $BLIND_NOTE[$n] }
        $lines.Add('- ' + $rel + $note)
    }
    $lines.Add('')
    $lines.Add('## Indirect coverage only (' + $indirectOnly.Count + ')')
    foreach ($n in $indirectOnly) {
        $rel = @($srcDerived | Where-Object { $nameOf[$_] -eq $n })[0]
        $lines.Add('- ' + $rel + ' (trans=' + @($trans[$n]).Count + ')')
    }
    if ($WriteTable) {
        $out = Join-Path $dir 'COVERAGE.md'
        [IO.File]::WriteAllText($out, ($lines -join [Environment]::NewLine) + [Environment]::NewLine, (New-Object Text.UTF8Encoding($false)))
        Write-Output ('WROTE ' + $out)
    }
}

if ($issues -eq 0) {
    Write-Output 'PRECHECK_STATUS=OK (exit 0)'
    exit 0
}
Write-Output ('PRECHECK_STATUS=WARN issues=' + $issues + ' (exit 0 by default; -Strict => exit 1)')
if ($Strict) { exit 1 }
exit 0
