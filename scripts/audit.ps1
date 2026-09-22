$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    git diff --check
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    npm.cmd --prefix frontend audit --audit-level=high
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
