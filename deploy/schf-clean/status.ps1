#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $ScriptDir
Write-Host "SCHF Clean Status:" -ForegroundColor Cyan
docker compose ps
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host ""
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8084/api/health" -TimeoutSec 5 -ErrorAction Stop
    Write-Host "API Health: $($response.status)" -ForegroundColor Green
} catch {
    Write-Host "API Health: unreachable" -ForegroundColor Red
}
try {
    $setup = Invoke-RestMethod -Uri "http://localhost:8084/api/setup/status" -TimeoutSec 5 -ErrorAction Stop
    if ($setup.setupRequired) {
        Write-Host "Setup: required" -ForegroundColor Yellow
    } else {
        Write-Host "Setup: completed" -ForegroundColor Green
    }
} catch {
    Write-Host "Setup: unknown" -ForegroundColor Yellow
}
