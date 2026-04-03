$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$appName = "Needlecast"
$jarPath = Join-Path $root "desktop/target/needlecast.jar"
$buildDir = Join-Path $root "build"
$runtimeDir = Join-Path $buildDir "runtime"
$appCdsDir = Join-Path $buildDir "appcds"
$classlist = Join-Path $appCdsDir "classlist.txt"
$archive = Join-Path $appCdsDir "appcds.jsa"

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

& jpackage `
  --type app-image `
  --dest (Join-Path $buildDir "jpackage") `
  --input (Split-Path $jarPath) `
  --name $appName `
  --main-jar (Split-Path $jarPath -Leaf) `
  --main-class io.github.rygel.needlecast.MainKt `
  --runtime-image $runtimeDir `
  --java-options $javaOpts

Write-Host "App image created under $buildDir\jpackage"
