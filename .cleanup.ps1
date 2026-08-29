
Get-ChildItem "K:\moder\EyeOfHarmonyBuffer\" -Filter ".s*.jsonl" -Force -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue;
Get-ChildItem "K:\moder\EyeOfHarmonyBuffer\" -Filter ".time_*.jsonl" -Force -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue;
Get-ChildItem "K:\moder\EyeOfHarmonyBuffer\" -Filter ".diag_*.ps1" -Force -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue;
Write-Output (Get-ChildItem "K:\moder\EyeOfHarmonyBuffer\" -Force | Where-Object { $_.Name -like '.*431*' -or $_.Name -like '.diag*' -or $_.Name -like '.s.*' } | Select-Object Name).Count;
Write-Output "cleaned";