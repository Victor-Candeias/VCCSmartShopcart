[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [ValidatePattern('^([0-9]+)\.([0-9]+)\.([0-9]+)([-+][0-9A-Za-z.-]+)?$')]
    [string] $VersionName,

    [Parameter(Mandatory = $false)]
    [ValidateRange(1, 2147483647)]
    [int] $VersionCode = 0,

    [Parameter(Mandatory = $false)]
    [string] $KeystoreProperties = 'keystore.properties',

    [Parameter(Mandatory = $false)]
    [switch] $SkipTests
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $projectRoot

function Fail([string] $Message) {
    throw "ERRO: $Message"
}

$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    Fail 'gradlew.bat não foi encontrado na raiz do projeto.'
}

$gradleFile = Join-Path $projectRoot 'app\build.gradle.kts'
$gradleText = Get-Content -LiteralPath $gradleFile -Raw
if ([string]::IsNullOrWhiteSpace($VersionName)) {
    $versionMatch = [regex]::Match($gradleText, 'orElse\("([^"]+)"\)')
    if (-not $versionMatch.Success) {
        Fail 'Não foi possível determinar a versão. Indique -VersionName X.Y.Z.'
    }
    $VersionName = $versionMatch.Groups[1].Value
}

if ($VersionCode -eq 0) {
    $codeMatch = [regex]::Match($gradleText, 'appVersionCode.*?orElse\("([0-9]+)"\)')
    if (-not $codeMatch.Success) {
        Fail 'Não foi possível determinar o versionCode. Indique -VersionCode N.'
    }
    $VersionCode = [int] $codeMatch.Groups[1].Value
}

$keystorePropertiesPath = [IO.Path]::GetFullPath((Join-Path $projectRoot $KeystoreProperties))
if (-not (Test-Path -LiteralPath $keystorePropertiesPath -PathType Leaf)) {
    Fail "Falta '$KeystoreProperties'. Crie-o a partir da secção 'Gerar APK assinado' do README.md."
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $keystorePropertiesPath) {
    if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') {
        $properties[$Matches[1]] = $Matches[2]
    }
}
foreach ($required in @('storeFile', 'storePassword', 'keyAlias', 'keyPassword')) {
    if (-not $properties.ContainsKey($required) -or [string]::IsNullOrWhiteSpace($properties[$required])) {
        Fail "A propriedade '$required' não está definida em '$KeystoreProperties'."
    }
}

$keystorePath = [IO.Path]::GetFullPath((Join-Path $projectRoot $properties['storeFile']))
if (-not (Test-Path -LiteralPath $keystorePath -PathType Leaf)) {
    Fail "A keystore indicada em '$KeystoreProperties' não existe: $keystorePath"
}

$sdkRoot = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($sdkRoot)) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if ([string]::IsNullOrWhiteSpace($sdkRoot) -or -not (Test-Path -LiteralPath $sdkRoot)) {
    Fail 'Defina ANDROID_HOME ou ANDROID_SDK_ROOT com o caminho do Android SDK.'
}

$apksigner = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Filter 'apksigner.bat' -File -Recurse -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1
if ($null -eq $apksigner) {
    Fail 'apksigner.bat não foi encontrado em Android SDK\build-tools.'
}

$gradleArgs = @(':app:assembleRelease', '--no-daemon', "-PappVersionName=$VersionName", "-PappVersionCode=$VersionCode")
if (-not $SkipTests) { $gradleArgs = @(':app:testReleaseUnitTest') + $gradleArgs }

Write-Host "A compilar VCCSmartShopcart $VersionName (versionCode $VersionCode)..." -ForegroundColor Cyan
& $gradleWrapper @gradleArgs
if ($LASTEXITCODE -ne 0) { Fail 'O Gradle terminou com erro.' }

$apk = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\release') -Filter '*.apk' -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $apk) { Fail 'O Gradle terminou sem gerar um APK release.' }

Write-Host 'A verificar assinatura...' -ForegroundColor Cyan
& $apksigner.FullName verify --verbose --print-certs $apk.FullName
if ($LASTEXITCODE -ne 0) { Fail 'O APK não passou a verificação de assinatura.' }

$distDirectory = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $distDirectory -Force | Out-Null
$artifact = Join-Path $distDirectory "VCCSmartShopcart-$VersionName-release.apk"
Copy-Item -LiteralPath $apk.FullName -Destination $artifact -Force

Write-Host "`nRelease assinado criado:" -ForegroundColor Green
Write-Host $artifact -ForegroundColor Green
