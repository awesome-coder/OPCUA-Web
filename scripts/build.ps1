# Build the React frontend and package the Spring Boot application with it.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root 'frontend'
$backend = Join-Path $root 'backend'
$static = Join-Path $backend 'src\main\resources\static'

Write-Host '==> Building frontend' -ForegroundColor Cyan
Push-Location $frontend
try {
    npm.cmd install
    npm.cmd run build
} finally {
    Pop-Location
}

Write-Host '==> Copying frontend into backend static resources' -ForegroundColor Cyan
if (Test-Path $static) { Remove-Item -Recurse -Force $static }
New-Item -ItemType Directory -Force $static | Out-Null
Copy-Item -Path (Join-Path $frontend 'dist\*') -Destination $static -Recurse -Force

Write-Host '==> Packaging backend' -ForegroundColor Cyan
Push-Location $backend
try {
    .\mvnw.cmd clean package
} finally {
    Pop-Location
}

Write-Host ''
Write-Host 'Build complete: backend/target/opcua-web.jar' -ForegroundColor Green
Write-Host 'Run: java -jar backend/target/opcua-web.jar' -ForegroundColor Green
