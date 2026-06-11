#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"
$logFile = "scripts/test-shell-ai.log"
New-Item -ItemType Directory -Path "scripts" -Force | Out-Null

$proc = Start-Process -FilePath "mvn" -ArgumentList @(
    "test",
    "-pl", "needlecast-desktop",
    "-Dtest=ShellDetectorTest,AiCliDetectorTest",
    "-q"
) -NoNewWindow -PassThru -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err"

$timeout = 180
$elapsed = 0
while (-not $proc.HasExited -and $elapsed -lt $timeout) {
    Start-Sleep -Seconds 2
    $elapsed += 2
}

if (-not $proc.HasExited) {
    Write-Host "TIMEOUT after ${timeout}s - killing process"
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
    Get-Process -Name "mvn", "java" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*surefire*" -or $_.CommandLine -like "*needlecast-desktop*" } | Stop-Process -Force -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "Done in ${elapsed}s with exit code $($proc.ExitCode)"
exit $proc.ExitCode
