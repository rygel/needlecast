param(
    [int]$TimeoutSeconds = 180,
    [string]$LogFile = "$PSScriptRoot\ktlint-check.log"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

if (Test-Path -LiteralPath $LogFile) {
    Remove-Item -LiteralPath $LogFile -Force
}

Write-Host "[ktlint-check] Project: $projectRoot"
Write-Host "[ktlint-check] Log:     $LogFile"
Write-Host "[ktlint-check] Timeout: ${TimeoutSeconds}s"

$proc = Start-Process `
    -FilePath "mvn.cmd" `
    -ArgumentList @("com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:check", "-pl", "needlecast-desktop") `
    -WorkingDirectory $projectRoot `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError "$LogFile.err"

$exited = $proc.WaitForExit($TimeoutSeconds * 1000)
if (-not $exited) {
    Write-Host "[ktlint-check] Timeout reached, killing mvn"
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch { }
    exit 2
}

if (Test-Path -LiteralPath "$LogFile.err") {
    Get-Content -LiteralPath "$LogFile.err" | Add-Content -LiteralPath $LogFile
    Remove-Item -LiteralPath "$LogFile.err" -Force
}

$exit = $proc.ExitCode
Write-Host "[ktlint-check] mvn exited with code $exit"
exit $exit
