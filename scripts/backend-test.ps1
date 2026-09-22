$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & ".\backend\mvnw.cmd" -f "backend\pom.xml" clean verify
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
