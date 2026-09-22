$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    docker compose --profile app up --detach --build
    docker compose --profile app ps
} finally {
    Pop-Location
}
