# Runs the CodexScreenshotTest and streams output to a log file.
param(
    [int]$TimeoutSeconds = 180,
    [string]$LogFile = "$PSScriptRoot\test-codex.log"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

if (Test-Path -LiteralPath $LogFile) {
    Remove-Item -LiteralPath $LogFile -Force
}

Write-Host "[run-codex] Project: $projectRoot"
Write-Host "[run-codex] Log:     $LogFile"
Write-Host "[run-codex] Timeout: ${TimeoutSeconds}s"

$proc = Start-Process `
    -FilePath "mvn.cmd" `
    -ArgumentList @("test", "-pl", "needlecast-desktop", "-Dtest=CodexScreenshotTest") `
    -WorkingDirectory $projectRoot `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError "$LogFile.err"

$exited = $proc.WaitForExit($TimeoutSeconds * 1000)
if (-not $exited) {
    Write-Host "[run-codex] Timeout reached, killing mvn"
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch { }
    exit 2
}

if (Test-Path -LiteralPath "$LogFile.err") {
    Get-Content -LiteralPath "$LogFile.err" | Add-Content -LiteralPath $LogFile
    Remove-Item -LiteralPath "$LogFile.err" -Force
}

$exit = $proc.ExitCode
Write-Host "[run-codex] mvn exited with code $exit"
exit $exit
