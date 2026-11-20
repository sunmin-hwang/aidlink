param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = "Stop"
$backendPath = (Resolve-Path -LiteralPath $PSScriptRoot).Path

if ($backendPath -notmatch "[^\x00-\x7F]") {
    & "$PSScriptRoot\gradlew.bat" @GradleArgs
    exit $LASTEXITCODE
}

$workspacePath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$temporaryDrive = "W:"

if (Test-Path "$temporaryDrive\") {
    throw "$temporaryDrive drive is already in use. Run gradlew.bat from an ASCII-only path instead."
}

try {
    subst $temporaryDrive $workspacePath
    Push-Location "$temporaryDrive\backend"
    & ".\gradlew.bat" @GradleArgs
    $gradleExitCode = $LASTEXITCODE
} finally {
    if ((Get-Location).Path -like "$temporaryDrive*") {
        Pop-Location
    }
    subst $temporaryDrive /D
}

exit $gradleExitCode

