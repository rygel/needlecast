# Runs ktlint format on the needlecast-desktop module.
# Exits 0 on success, non-zero on failure.

param(
    [int]$TimeoutSeconds = 240,
    [string]$LogFile = "$PSScriptRoot\ktlint-format.log"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

if (Test-Path -LiteralPath $LogFile) {
    Remove-Item -LiteralPath $LogFile -Force
}

$cmdLine = "mvn com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format -pl needlecast-desktop"

Write-Host "[ktlint] Project:  $projectRoot"
Write-Host "[ktlint] Command:  $cmdLine"
Write-Host "[ktlint] Log:      $LogFile"
Write-Host "[ktlint] Timeout:  ${TimeoutSeconds}s"

$proc = Start-Process `
    -FilePath "mvn.cmd" `
    -ArgumentList @("com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format", "-pl", "needlecast-desktop") `
    -WorkingDirectory $projectRoot `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError "$LogFile.err"

$exited = $proc.WaitForExit($TimeoutSeconds * 1000)
if (-not $exited) {
    Write-Host "[ktlint] Timeout reached, killing mvn"
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch { }
    exit 2
}

if (Test-Path -LiteralPath "$LogFile.err") {
    Get-Content -LiteralPath "$LogFile.err" | Add-Content -LiteralPath $LogFile
    Remove-Item -LiteralPath "$LogFile.err" -Force
}

$exit = $proc.ExitCode
Write-Host "[ktlint] mvn exited with code $exit"
exit $exit
