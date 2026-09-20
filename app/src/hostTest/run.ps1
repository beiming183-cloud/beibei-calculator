param([string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot/../../..").Path
$androidJar = "$AndroidSdk/platforms/android-34/android.jar"
$appClasses = "$repo/app/build/hostProduction"
$coreClasses = "$repo/core/build/classes/java/main"
$out = "$repo/app/build/hostTest"
$classpathSeparator = [IO.Path]::PathSeparator
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $appClasses | Out-Null
$production = @(Get-ChildItem "$repo/app/src/main/java" -Filter '*.java' -Recurse | ForEach-Object FullName)
$production += "$repo/app/build/generated/source/buildConfig/cn991/debug/com/codex/fx991smooth/BuildConfig.java"
& javac -encoding UTF-8 -cp (@($androidJar, $coreClasses) -join $classpathSeparator) -d $appClasses @production
if ($LASTEXITCODE -ne 0) { throw 'Production View compilation failed' }
$sources = @(Get-ChildItem "$PSScriptRoot/java" -Filter '*.java' -Recurse | ForEach-Object FullName)
& javac -encoding UTF-8 -cp (@($androidJar, $appClasses, $coreClasses) -join $classpathSeparator) -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Host test compilation failed' }
& java -cp (@($out, $appClasses, $coreClasses, $androidJar) -join $classpathSeparator) com.codex.fx991smooth.CalculatorViewHostSuite
if ($LASTEXITCODE -ne 0) { throw 'Host behavioral regression failed' }
