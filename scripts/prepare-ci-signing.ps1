[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$applicationDirectory = Join-Path $repositoryRoot 'app'
$propertiesFile = Join-Path $repositoryRoot 'sign.properties'
$keystoreFile = Join-Path $applicationDirectory 'ci-only-signing.jks'

if (-not (Test-Path -LiteralPath (Join-Path $applicationDirectory 'build.gradle.kts') -PathType Leaf)) {
    throw "Unexpected repository layout: $applicationDirectory"
}
if (Test-Path -LiteralPath $propertiesFile) {
    throw "Refusing to replace existing signing properties: $propertiesFile"
}
if (Test-Path -LiteralPath $keystoreFile) {
    throw "Refusing to replace existing CI keystore: $keystoreFile"
}

$keytool = Get-Command 'keytool.exe' -ErrorAction SilentlyContinue
if ($null -eq $keytool) {
    $keytool = Get-Command 'keytool' -ErrorAction Stop
}

$password = [Guid]::NewGuid().ToString('N')
$alias = 'ci-only'
$keytoolArguments = @(
    '-genkeypair'
    '-noprompt'
    '-storetype', 'JKS'
    '-keystore', $keystoreFile
    '-storepass', $password
    '-keypass', $password
    '-alias', $alias
    '-keyalg', 'RSA'
    '-keysize', '2048'
    '-sigalg', 'SHA256withRSA'
    '-validity', '2'
    '-dname', 'CN=CI Only, OU=Non-Release, O=AutoJs6, C=CN'
)

try {
    & $keytool.Source @keytoolArguments
    if ($LASTEXITCODE -ne 0) {
        throw "keytool exited with code $LASTEXITCODE"
    }

    $properties = @(
        'storeFile=ci-only-signing.jks'
        "storePassword=$password"
        "keyAlias=$alias"
        "keyPassword=$password"
    )
    [IO.File]::WriteAllLines(
        $propertiesFile,
        $properties,
        [Text.UTF8Encoding]::new($false)
    )
}
catch {
    if ([IO.File]::Exists($propertiesFile)) {
        [IO.File]::Delete($propertiesFile)
    }
    if ([IO.File]::Exists($keystoreFile)) {
        [IO.File]::Delete($keystoreFile)
    }
    throw
}

if (-not [IO.File]::Exists($propertiesFile) -or -not [IO.File]::Exists($keystoreFile)) {
    throw 'CI-only signing material was not created completely'
}

Write-Host 'Generated an ephemeral CI-only keystore; it is not valid for release or device evidence.'
