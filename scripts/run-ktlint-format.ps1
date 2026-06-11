#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"
$logFile = "scripts/ktlint-format.log"
New-Item -ItemType Directory -Path "scripts" -Force | Out-Null

$proc = Start-Process -FilePath "mvn" -ArgumentList @(
    "com.github.gantsign.maven:ktlint-maven-plugin:3.7.1:format",
    "-pl", "needlecast-desktop",
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
    exit 1
}

Write-Host "Done in ${elapsed}s with exit code $($proc.ExitCode)"
exit $proc.ExitCode
