[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'
$verificationTasks = @(
    ':app:testDebugUnitTest'
    ':app:testReleaseUnitTest'
    ':app:lintDebug'
    ':app:assembleDebug'
)

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}

$exitCode = 1
Push-Location -LiteralPath $repositoryRoot
try {
    & $gradleWrapper @verificationTasks '--offline' @GradleArguments
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $exitCode
