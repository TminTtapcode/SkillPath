$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    npm.cmd --prefix frontend ci
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    npm.cmd --prefix frontend run format
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    npm.cmd --prefix frontend run lint
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    npm.cmd --prefix frontend run test -- --run
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    npm.cmd --prefix frontend run build
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
