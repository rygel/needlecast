$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$appName    = "Needlecast"
$appVersion = if ($env:APP_VERSION) { $env:APP_VERSION } else {
    # Fall back to reading from pom.xml when not set by CI
    ([xml](Get-Content (Join-Path $root "desktop/pom.xml"))).project.version
}
$jarPath    = Join-Path $root "desktop/target/needlecast.jar"
$buildDir   = Join-Path $root "build"
$runtimeDir = Join-Path $buildDir "runtime"
$appCdsDir  = Join-Path $buildDir "appcds"
$classlist  = Join-Path $appCdsDir "classlist.txt"
$archive    = Join-Path $appCdsDir "appcds.jsa"

if (-not (Test-Path $jarPath)) {
  Write-Host "Missing jar: $jarPath"
  Write-Host "Build it with: mvn -pl desktop -am package -DskipTests"
  exit 1
}

if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
New-Item -ItemType Directory -Force $appCdsDir | Out-Null

$deps = & jdeps --multi-release 21 --ignore-missing-deps --print-module-deps $jarPath

& jlink --add-modules $deps `
  --strip-debug `
  --no-header-files `
  --no-man-pages `
  --compress=zip-6 `
  --output $runtimeDir

$javaExe = "$runtimeDir\bin\java.exe"

& $javaExe "-Djava.awt.headless=true" -Xshare:off "-XX:DumpLoadedClassList=$classlist" -cp $jarPath io.github.rygel.needlecast.tools.CdsTraining

& $javaExe -Xshare:dump "-XX:SharedClassListFile=$classlist" "-XX:SharedArchiveFile=$archive"

New-Item -ItemType Directory -Force "$runtimeDir\lib\server" | Out-Null
Copy-Item $archive "$runtimeDir\lib\server\appcds.jsa" -Force

$javaOpts = "-XX:SharedArchiveFile=`$APPDIR\runtime\lib\server\appcds.jsa"

$iconPath = Join-Path (Join-Path (Join-Path (Join-Path (Join-Path (Join-Path $root "desktop") "src") "main") "resources") "icons") "needlecast.ico"

# jpackage requires major version >= 1; map 0.x.y to 1.x.y for the native package only
$jpackageVersion = $appVersion
if ($jpackageVersion -match '^0\.') { $jpackageVersion = '1' + $jpackageVersion.Substring(1) }

& jpackage `
  --type app-image `
  --dest (Join-Path $buildDir "jpackage") `
  --input (Split-Path $jarPath) `
  --name $appName `
  --app-version $jpackageVersion `
  --icon $iconPath `
  --main-jar (Split-Path $jarPath -Leaf) `
  --main-class io.github.rygel.needlecast.MainKt `
  --runtime-image $runtimeDir `
  --java-options $javaOpts

Write-Host "App image created under $buildDir\jpackage"

# ── Inno Setup installer ──────────────────────────────────────────────────────
$iscc = Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\iscc.exe"
if (-not (Test-Path $iscc)) {
    Write-Warning "Inno Setup not found -- skipping installer build."
    Write-Warning "Install from https://jrsoftware.org/isinfo.php or run in CI."
} else {
    $issScript = Join-Path (Join-Path $root "scripts") "needlecast.iss"
    Write-Host "Building Inno Setup installer (version $appVersion)..."
    & $iscc "/DAppVersion=$appVersion" $issScript
    $installer = Join-Path $buildDir "needlecast-$appVersion-windows.exe"
    Write-Host "Installer: $installer"
}
