$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$toolsRoot = Join-Path (Split-Path $projectRoot -Parent) '.tools'
if (-not (Test-Path -LiteralPath (Join-Path $toolsRoot 'android-sdk'))) {
    throw '상위 .tools 도구 폴더가 없습니다. Android Studio 또는 gradlew로 빌드해 주세요.'
}
# Android Windows native tools work most reliably in an ASCII build path.
# Each invocation has a new staging directory; no existing files are deleted.
$staging = Join-Path ([IO.Path]::GetTempPath()) ('moapic-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging | Out-Null
$toolsLink = Join-Path $staging 'tools'
New-Item -ItemType Junction -Path $toolsLink -Target $toolsRoot | Out-Null
$buildRoot = Join-Path $staging 'project'
New-Item -ItemType Directory -Path (Join-Path $buildRoot 'app') | Out-Null
foreach ($name in 'settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','gradle') {
    Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination $buildRoot -Recurse
}
foreach ($name in 'src','build.gradle','proguard-rules.pro') {
    Copy-Item -LiteralPath (Join-Path $projectRoot "app/$name") -Destination (Join-Path $buildRoot 'app') -Recurse
}
$sdk = (Join-Path $toolsLink 'android-sdk').Replace('\','/').Replace(':','\:')
Set-Content -LiteralPath (Join-Path $buildRoot 'local.properties') -Value "sdk.dir=$sdk" -Encoding utf8
$localJdk = Get-ChildItem -LiteralPath (Join-Path $toolsLink 'jdk') -Directory | Select-Object -First 1
$env:JAVA_HOME = $localJdk.FullName
$env:GRADLE_USER_HOME = Join-Path $toolsLink 'gradle-cache'
$gradle = Join-Path $toolsLink 'gradle/gradle-8.13/bin/gradle.bat'
if (-not (Test-Path -LiteralPath $gradle)) { $gradle = Join-Path $buildRoot 'gradlew.bat' }
& $gradle -p $buildRoot -PdeviceApk=true testDebugUnitTest lintPreview assemblePreview @args
if ($LASTEXITCODE -ne 0) { throw "빌드 실패: $LASTEXITCODE" }
$destination = Join-Path $projectRoot 'app/build'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
foreach ($name in 'outputs','reports','test-results') {
    Copy-Item -LiteralPath (Join-Path $buildRoot "app/build/$name") -Destination $destination -Recurse -Force
}
Write-Host "APK: $(Join-Path $destination 'outputs/apk/preview/app-arm64-v8a-preview.apk')"
Write-Host "빌드 작업 폴더: $staging"
