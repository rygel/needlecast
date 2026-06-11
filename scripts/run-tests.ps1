# Runs the targeted Maven tests and streams output to a log file.
# Exits 0 on test pass, 1 on failure, 2 on timeout.

param(
    [string]$TestSelector = "BuildFileWatcherTest,TextChunkerTest",
    [int]$TimeoutSeconds = 240,
    [string]$LogFile = "$PSScriptRoot\test-build.log"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

if (Test-Path -LiteralPath $LogFile) {
    Remove-Item -LiteralPath $LogFile -Force
}

$cmdLine = "mvn test -pl needlecast-desktop -Dtest=`"$TestSelector`""

Write-Host "[run-tests] Project: $projectRoot"
Write-Host "[run-tests] Command: $cmdLine"
Write-Host "[run-tests] Log:     $LogFile"
Write-Host "[run-tests] Timeout: ${TimeoutSeconds}s"

$proc = Start-Process `
    -FilePath "mvn.cmd" `
    -ArgumentList @("test", "-pl", "needlecast-desktop", "-Dtest=`"$TestSelector`"") `
    -WorkingDirectory $projectRoot `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput $LogFile `
    -RedirectStandardError "$LogFile.err"

$exited = $proc.WaitForExit($TimeoutSeconds * 1000)
if (-not $exited) {
    Write-Host "[run-tests] Timeout reached, killing mvn"
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch { }
    exit 2
}

# Merge stderr into stdout log for convenience
if (Test-Path -LiteralPath "$LogFile.err") {
    Get-Content -LiteralPath "$LogFile.err" | Add-Content -LiteralPath $LogFile
    Remove-Item -LiteralPath "$LogFile.err" -Force
}

$exit = $proc.ExitCode
Write-Host "[run-tests] mvn exited with code $exit"
exit $exit
