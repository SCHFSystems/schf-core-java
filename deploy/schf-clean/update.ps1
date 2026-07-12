#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Update SCHF Clean to the latest version.
.DESCRIPTION
    Pulls the latest image, rebuilds if needed, applies Flyway migrations,
    and restarts services. Preserves volumes and .env.
#>

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $ScriptDir

Write-Host "=== SCHF Clean Update ===" -ForegroundColor Cyan

if (-not (Test-Path ".env")) {
    Write-Host "ERROR: .env not found. Run install.ps1 first." -ForegroundColor Red
    exit 1
}

Write-Host "  Preserving volumes and .env..." -ForegroundColor Yellow

Write-Host "  Pulling latest images..." -ForegroundColor Cyan
docker compose pull --quiet

Write-Host "  Rebuilding API image..." -ForegroundColor Cyan
docker compose build --quiet api
if ($LASTEXITCODE -ne 0) { throw "Docker build failed" }

Write-Host "  Recreating services..." -ForegroundColor Cyan
docker compose up -d --force-recreate --remove-orphans
if ($LASTEXITCODE -ne 0) { throw "Docker compose up failed" }

Write-Host "  Waiting for API health..." -ForegroundColor Yellow
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 5
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8084/api/health" -TimeoutSec 5 -ErrorAction Stop
        $info = Invoke-RestMethod -Uri "http://localhost:8084/api/system/info" -TimeoutSec 5 -ErrorAction Stop
        if ($response.status -eq "ok") { $healthy = $true; break }
    } catch { }
}
if ($healthy) {
    Write-Host "  Update successful. Running version: $($info.serverVersion)" -ForegroundColor Green
} else {
    Write-Host "  WARNING: Update may not have completed successfully." -ForegroundColor Red
    Write-Host "  Check logs with: docker compose logs api" -ForegroundColor Yellow
}

Write-Host "=== Update complete ===" -ForegroundColor Cyan
Write-Host "  To roll back, restore a previous backup and use the previous image tag." -ForegroundColor Yellow
