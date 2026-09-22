$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
function Get-DotEnv {
    $values = @{}
    $path = Join-Path $projectRoot '.env'
    if (Test-Path -LiteralPath $path) {
        foreach ($line in Get-Content -LiteralPath $path) {
            if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*)\s*$') {
                $values[$matches[1]] = $matches[2].Trim('"', "'")
            }
        }
    }
    return $values
}
function Get-Setting([string] $name, [string] $fallback, $dotEnv) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value) -and $dotEnv.ContainsKey($name)) {
        $value = $dotEnv[$name]
    }
    if ([string]::IsNullOrWhiteSpace($value)) { return $fallback }
    return $value
}
$dotEnv = Get-DotEnv
$mysqlPort = Get-Setting 'MYSQL_PORT' '3306' $dotEnv
$mysqlDatabase = Get-Setting 'MYSQL_DATABASE' 'skillpath' $dotEnv
$mysqlUser = Get-Setting 'MYSQL_USER' 'skillpath_app' $dotEnv
$mysqlPassword = Get-Setting 'MYSQL_PASSWORD' 'local-skillpath-only' $dotEnv
Push-Location $projectRoot
try {
    docker compose up --detach --wait mysql
    & ".\backend\mvnw.cmd" `
        '-f' 'backend\pom.xml' `
        '-DskipTests' `
        "-Dflyway.url=jdbc:mysql://localhost:$mysqlPort/$mysqlDatabase" `
        "-Dflyway.user=$mysqlUser" `
        "-Dflyway.password=$mysqlPassword" `
        'flyway:migrate'
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
