$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& "$PSScriptRoot\backend-test.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$PSScriptRoot\frontend-test.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Push-Location $projectRoot
try {
    docker compose config | Out-Null
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
