# ASCII-ONLY (PowerShell 5.1 reads a BOM-less .ps1 as ANSI; a non-ASCII byte inside a
# comment or literal can swallow the next code line -- five recorded accidents).
#
# migrate_provenance.ps1 -- phase B, design section 4: label the PRE-PROVENANCE baseline
# directories that are already on disk.
#
# WHY LABELS INSTEAD OF RENAMES: the 58 directories under build/eoh_probe/mtn/rerun_acceptance
# are referenced by name from design sections, calibration tables, t2_adjudication.tsv and a
# dozen ad-hoc scripts. Renaming them would break those references AND would invent a
# provenance claim the data does not support. So every directory keeps its name and gets a
# PROVENANCE_NOTE.txt that says, honestly, what is known and what is not.
#
# THE CLASSIFICATION RULE (design section 3(ii), all of it derived from names + timestamps):
#   <32hex>_OFF / <32hex>_ON  -> world KNOWN, confidence HIGH. The old runner set the state
#        segment from EOH_TALOS_TERRAIN, and until section 566 the Java default was LEGACY, so
#        "unset => LEGACY / set => TALOS" was consistent with the default of the day.
#   <32hex> alone             -> world UNKNOWN, confidence LOW. The pre-state-fix runner had
#        no state segment at all, so the world caliber is UNRECOVERABLE from the disk.
#   anything else (prefix_..., ..._seq) -> UNKNOWN / LOW for the same reason.
#   <srcfp8>_<cfgfp8>_<WORLD> -> ALREADY HAS PROVENANCE (phase B); nothing is written.
#
# AN OPEN QUESTION, RECORDED BUT NOT RESOLVED (design section 5.1): the single _ON directory
# may in fact be a LEGACY run. The HEAD revision of runp.bat passed -Dtalos.terrain=true on the
# command line, where the shift loop split it at "=", so the property was never set and the
# default applied. Whether that happened for THIS directory is not decidable from the disk, so
# its note carries the caveat instead of a silent downgrade.
#
# ERA BOUNDARIES (measured, and the design doc disagrees on the first one -- see below):
#   state fix  2026-09-17 22:50:56 = mtime of the OLDEST state-tagged directory on disk
#               (27F86E87..._OFF). The design doc section 1.2 says 2026-09-19; the disk says
#               09-17. The DISK is used here, and the contradiction is reported.
#   section 566 2026-09-19 22:17:33 = mtime of src/.../sim/litho/PlateField.java, whose
#               content is Boolean.parseBoolean(System.getProperty("talos.terrain","true")),
#               i.e. the moment the Java default flipped from LEGACY to TALOS.
#
# USAGE:  powershell -NoProfile -ExecutionPolicy Bypass -File migrate_provenance.ps1
#              -> DRY RUN: classify, print the table, write the TSV, change nothing
#         powershell -NoProfile -ExecutionPolicy Bypass -File migrate_provenance.ps1 -Apply
#              -> also write PROVENANCE_NOTE.txt into every OLD-format directory
# EXIT: 0 = ok ; 3 = root directory not found
param(
  [string]$Root = 'K:\moder\EyeOfHarmonyBuffer\build\eoh_probe\mtn\rerun_acceptance',
  [switch]$Apply
)
$ErrorActionPreference = 'Stop'
$mgMtn = Join-Path (Split-Path -Parent $Root) ''
if (-not (Test-Path -LiteralPath $Root)) { Write-Output ('NO_SUCH_ROOT ' + $Root); exit 3 }

$mgStateFix = Get-Date '2026-09-17 22:50:56'
$mgS566     = Get-Date '2026-09-19 22:17:33'
$mgNow      = Get-Date -Format s
$mgRows     = New-Object System.Collections.ArrayList

# ---- classify every directory -------------------------------------------------------
$mgAll = @(Get-ChildItem -LiteralPath $Root -Directory | Sort-Object LastWriteTime)
$mgSiblings = @{}
foreach ($mgItem in $mgAll) {
  if ($mgItem.Name -match '^([0-9A-Fa-f]{32})') {
    $mgKey = $Matches[1].ToUpper()
    if (-not $mgSiblings.ContainsKey($mgKey)) { $mgSiblings[$mgKey] = @() }
    $mgSiblings[$mgKey] += $mgItem.Name
  }
}

$mgCount = @{ NewFormat = 0; StateTagged = 0; Bare = 0; Other = 0; Applied = 0 }
foreach ($mgItem in $mgAll) {
  $mgName = $mgItem.Name
  $mgCreated = $mgItem.LastWriteTime
  $mgKind = 'OTHER'; $mgSrc = 'UNKNOWN'; $mgWorld = 'UNKNOWN'; $mgConf = 'LOW'; $mgState = ''; $mgEra = ''
  $mgAnomaly = ''
  if ($mgName -match '^[0-9A-Fa-f]{8}_[0-9A-Fa-f]{8}_(TALOS|LEGACY|UNKNOWN)(_.+)?$') {
    $mgKind = 'NEW_FORMAT'
  } elseif ($mgName -match '^(?<mgFp>[0-9A-Fa-f]{32})_(?<mgSt>OFF|ON)$') {
    $mgKind = 'STATE_TAGGED'
    $mgSrc = $Matches['mgFp'].ToUpper()
    $mgState = $Matches['mgSt']
    if ($mgState -ceq 'OFF') { $mgWorld = 'LEGACY' } else { $mgWorld = 'TALOS' }
    $mgConf = 'HIGH'
  } elseif ($mgName -match '^(?<mgFp>[0-9A-Fa-f]{32})$') {
    $mgKind = 'BARE_FP'
    $mgSrc = $Matches['mgFp'].ToUpper()
  } elseif ($mgName -match '^(?<mgFp>[0-9A-Fa-f]{32})') {
    # a fingerprint directory carrying an extra suffix (measured: E0561D55..._seq, the
    # -Sequential variant of the runner). The srcfp IS recoverable from the name; the world
    # caliber is not.
    $mgKind = 'OTHER'
    $mgSrc = $Matches['mgFp'].ToUpper()
  }
  if ($mgKind -ne 'NEW_FORMAT') {
    if ($mgCreated -ge $mgS566) { $mgEra = 'post-566' }
    elseif ($mgCreated -ge $mgStateFix) { $mgEra = 'post-state-fix' }
    else { $mgEra = 'pre-state-fix' }
    if (($mgKind -ne 'STATE_TAGGED') -and ($mgCreated -ge $mgStateFix)) {
      $mgAnomaly = 'bare name although created after the first state-tagged directory (' + $mgStateFix.ToString('yyyy-MM-dd HH:mm:ss') + '); producer not determinable from the disk evidence'
    }
    if (($mgKind -ne 'BARE_FP') -and ($mgKind -ne 'STATE_TAGGED')) {
      $mgAnomaly = 'directory name follows neither documented scheme; treated as world-unknown'
    }
  }

  $mgPair = ''
  if ($mgSiblings.ContainsKey($mgSrc)) {
    $mgOthers = @($mgSiblings[$mgSrc] | Where-Object { $_ -ne $mgName })
    if ($mgOthers.Count -gt 0) { $mgPair = ($mgOthers -join ', ') }
  }
  if ($mgKind -eq 'NEW_FORMAT') { $mgCount.NewFormat++ }
  elseif ($mgKind -eq 'STATE_TAGGED') { $mgCount.StateTagged++ }
  elseif ($mgKind -eq 'BARE_FP') { $mgCount.Bare++ }
  else { $mgCount.Other++ }

  $mgEvidence = ''
  if ($mgKind -eq 'STATE_TAGGED') {
    $mgEvidence = 'state suffix ' + $mgState + ' written by the pre-566 runner; the Java default of that era was LEGACY, so the label is consistent'
    if ($mgState -ceq 'ON') { $mgEvidence = $mgEvidence + '; CAVEAT: an _ON directory can also mean the -D flag was split at "=" by the shift loop and never applied (design section 5.1) -- not decidable from the disk'
    }
  } elseif ($mgKind -eq 'BARE_FP') {
    $mgEvidence = 'no state segment in the name; whichever runner produced it did not record the world caliber, so it is UNRECOVERABLE from the disk (principle, not a parsing gap)'
  } elseif ($mgKind -eq 'OTHER') {
    $mgEvidence = 'not a fingerprint directory; no provenance information in the name'
  } else {
    $mgEvidence = 'name carries srcfp8 + cfgfp8 + WORLD, i.e. this directory already has a measured identity'
  }

  $mgNote = @()
  $mgNote += 'dir        = ' + $mgName
  $mgNote += 'srcfp      = ' + $mgSrc
  $mgNote += 'cfgfp      = UNKNOWN (this name has no cfgfp segment)'
  $mgNote += 'world      = ' + $(if ($mgWorld -eq 'UNKNOWN') { 'UNKNOWN' } else { 'KNOWN(' + $mgWorld + ')' })
  $mgNote += 'confidence = ' + $mgConf
  $mgNote += 'era        = ' + $mgEra
  $mgNote += 'created    = ' + $mgCreated.ToString('yyyy-MM-dd HH:mm:ss')
  $mgNote += 'note       = see measurement_provenance_design.md section 3(ii)'
  $mgNote += 'evidence   = ' + $mgEvidence
  if ($mgPair -ne '') { $mgNote += 'pair_note  = the same srcfp also appears as: ' + $mgPair + ' (each directory is labelled SEPARATELY; the bare and the state-tagged one are NOT the same measurement)' }
  if ($mgAnomaly -ne '') { $mgNote += 'anomaly    = ' + $mgAnomaly }
  $mgNote += 'renamed    = NO (names are referenced by history; see design section 4)'
  $mgNote += 'migrated   = ' + $mgNow + ' by migrate_provenance.ps1'

  $mgAction = 'LABEL'
  if ($mgKind -eq 'NEW_FORMAT') { $mgAction = 'SKIP_NEW_FORMAT' }
  if ($Apply -and $mgKind -ne 'NEW_FORMAT') {
    $mgNotePath = Join-Path $mgItem.FullName 'PROVENANCE_NOTE.txt'
    $mgExisted = Test-Path -LiteralPath $mgNotePath
    # CREATING A FILE UPDATES THE PARENT DIRECTORY LastWriteTime, and that timestamp is
    # EVIDENCE (it dates the run), input to the era rule above, and the ordering key that
    # judge_acceptance.ps1 uses to pick the newest matching baseline. Measured
    # 2026-09-19: the first -Apply run stamped all 58 legacy directories with its own
    # write time. They were restored from the pre-run listing (see
    # build/eoh_probe/mtn/prov_restore_dir_mtimes.ps1), and this save/restore makes the
    # hazard impossible to repeat.
    $mgDirItem = Get-Item -LiteralPath $mgItem.FullName
    $mgSavedTime = $mgDirItem.LastWriteTime
    Set-Content -LiteralPath $mgNotePath -Value $mgNote -Encoding ASCII
    $mgDirItem.LastWriteTime = $mgSavedTime
    $mgCount.Applied++
    if ($mgExisted) { $mgAction = 'NOTE_REWRITTEN' } else { $mgAction = 'NOTE_WRITTEN' }
  }

  [void]$mgRows.Add([pscustomobject]@{
    Dir = $mgName; Kind = $mgKind; Src = $mgSrc; World = $mgWorld; Conf = $mgConf; Era = $mgEra;
    Created = $mgCreated.ToString('yyyy-MM-dd HH:mm'); Pair = $mgPair; Anomaly = $(if ($mgAnomaly -ne '') { 'YES' } else { '' }); Action = $mgAction
  })
}

# ---- report ------------------------------------------------------------------------
Write-Output ('MIGRATE ROOT = ' + $Root)
Write-Output ('MODE = ' + $(if ($Apply) { 'APPLY (PROVENANCE_NOTE.txt is written)' } else { 'DRY RUN (nothing is written; pass -Apply to write)' }))
Write-Output ''
foreach ($mgRow in $mgRows) {
  Write-Output ($mgRow.Action.PadRight(17) + $mgRow.Dir.PadRight(36) + ' kind=' + $mgRow.Kind.PadRight(12) + ' world=' + $mgRow.World.PadRight(7) + ' conf=' + $mgRow.Conf.PadRight(5) + ' era=' + $mgRow.Era.PadRight(14) + ' created=' + $mgRow.Created + $(if ($mgRow.Anomaly -ne '') { '  ANOMALY' } else { '' }))
}
Write-Output ''
Write-Output ('TOTAL=' + $mgRows.Count + '  old-format=' + ($mgRows.Count - $mgCount.NewFormat) + '  new-format-skipped=' + $mgCount.NewFormat)
Write-Output ('BY CLASS: state-tagged=' + $mgCount.StateTagged + ' (world KNOWN, confidence HIGH)  bare-fp=' + $mgCount.Bare + '  other=' + $mgCount.Other + '  (both world UNKNOWN, confidence LOW)')
Write-Output ('BY ERA  : pre-state-fix=' + @($mgRows | Where-Object { $_.Era -eq 'pre-state-fix' }).Count + '  post-state-fix=' + @($mgRows | Where-Object { $_.Era -eq 'post-state-fix' }).Count + '  post-566=' + @($mgRows | Where-Object { $_.Era -eq 'post-566' }).Count)
Write-Output ('NOTES WRITTEN = ' + $mgCount.Applied)

$mgTsv = Join-Path $mgMtn 'migrate_provenance_report.tsv'
$mgTsvLines = @('# migrate_provenance.ps1 ' + $(if ($Apply) { 'APPLY' } else { 'DRY-RUN' }) + ' ' + $mgNow + '  root=' + $Root)
$mgTsvLines += (@('action','dir','kind','srcfp','world','confidence','era','created','same_srcfp_siblings','anomaly') -join "`t")
foreach ($mgRow in $mgRows) {
  $mgTsvLines += (@($mgRow.Action, $mgRow.Dir, $mgRow.Kind, $mgRow.Src, $mgRow.World, $mgRow.Conf, $mgRow.Era, $mgRow.Created, $mgRow.Pair, $mgRow.Anomaly) -join "`t")
}
Set-Content -LiteralPath $mgTsv -Value $mgTsvLines -Encoding ASCII
Write-Output ('REPORT = ' + $mgTsv)
if (-not $Apply) { Write-Output 'DRY_RUN_COMPLETE (no PROVENANCE_NOTE.txt was created, nothing was renamed)' }
exit 0
