# Start the packaged OPC UA Web Console.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'backend\target\opcua-web.jar'
if (-not (Test-Path $jar)) {
    throw "Jar not found. Run scripts/build.ps1 first."
}
if (-not $env:OPCUA_WEB_DATA_DIR) {
    $env:OPCUA_WEB_DATA_DIR = Join-Path $root 'data'
}
Write-Host "Starting OPC UA Web Console on http://127.0.0.1:8088" -ForegroundColor Cyan
Write-Host "Data directory: $env:OPCUA_WEB_DATA_DIR" -ForegroundColor DarkGray
java -jar $jar
