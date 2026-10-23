param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipTests,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectDirectory = $PSScriptRoot
$originalJavaHome = $env:JAVA_HOME
$originalGitSha = $env:GITHUB_SHA
$originalVersionRoot = $env:VERSION_ROOT
$originalVersionType = $env:VERSION_TYPE
$originalBuildNumber = $env:BUILD_NUMBER

if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw '请使用 -JavaHome 指定 JDK 21 或更高版本。'
}

Push-Location -LiteralPath $projectDirectory
try {
    & python .\scripts\verify_account_surface.py
    if ($LASTEXITCODE -ne 0) { throw '不支持的账户接入仍存在于源码中。' }
    $env:JAVA_HOME = $JavaHome
    $env:GITHUB_SHA = (& git rev-parse HEAD).Trim()
    $upstream = Get-Content -LiteralPath (Join-Path $projectDirectory 'config\upstream.json') -Raw | ConvertFrom-Json
    $versionParts = $upstream.displayVersion.Split('.')
    $env:VERSION_ROOT = $versionParts[0..($versionParts.Length - 2)] -join '.'
    $env:BUILD_NUMBER = $versionParts[-1]
    $env:VERSION_TYPE = $upstream.versionType
    $buildTasks = @('clean', ':HMCL:makeExecutables')
    if (-not $SkipTests) {
        $buildTasks += @(':HMCL:test', ':HMCLCore:test', ':HMCLBoot:test')
    }
    $buildTasks += @(':HMCL:checkstyleMain', ':HMCL:checkstyleTest', ':HMCLBoot:checkstyleMain')
    $buildTasks += @(':HMCLCore:checkstyleMain', ':HMCLCore:checkstyleTest')
    $buildTasks += @('--no-daemon', '--max-workers=4', '--console=plain')
    if ($Offline) { $buildTasks += '--offline' }
    & .\gradlew.bat @buildTasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败，退出码 $LASTEXITCODE" }

    & python .\scripts\verify_account_surface.py --artifacts
    if ($LASTEXITCODE -ne 0) { throw '不支持的账户接入仍存在于构建中。' }

    & python .\scripts\package.py
    if ($LASTEXITCODE -ne 0) { throw "打包失败，退出码 $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:GITHUB_SHA = $originalGitSha
    $env:VERSION_ROOT = $originalVersionRoot
    $env:VERSION_TYPE = $originalVersionType
    $env:BUILD_NUMBER = $originalBuildNumber
    Pop-Location
}
