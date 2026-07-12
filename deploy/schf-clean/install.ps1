#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Install SCHF Clean - first-time setup of the SCHF v2 clean environment.
.DESCRIPTION
    Creates runtime directories, generates secure secrets, builds the Docker image,
    and starts the stack.
#>

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RuntimeRoot = "D:\SCHF\_runtime\schf-clean"

Write-Host "=== SCHF Clean Install ===" -ForegroundColor Cyan

# 1. Check prerequisites
$missing = @()
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { $missing += "Docker" }
if ($missing.Count -gt 0) {
    Write-Host "Missing prerequisites: $($missing -join ', ')" -ForegroundColor Red
    exit 1
}

# 2. Create runtime directories
$dirs = @(
    "$RuntimeRoot\data\postgres",
    "$RuntimeRoot\data\redis",
    "$RuntimeRoot\backups",
    "$RuntimeRoot\config",
    "$RuntimeRoot\logs",
    "$RuntimeRoot\tmp"
)
foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    Write-Host "  Created: $dir" -ForegroundColor Green
}

# 3. Generate .env if not exists
$envFile = "$ScriptDir\.env"
if (-not (Test-Path $envFile)) {
    Write-Host "  Generating .env with secure secrets..." -ForegroundColor Yellow
    $instanceId = [guid]::NewGuid().ToString()
    $dbPass = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
    $redisPass = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
    $jwtSecret = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 80 | ForEach-Object { [char]$_ })

    @"
SCHF_INSTANCE_ID=$instanceId
SCHF_INSTANCE_NAME=My SCHF Instance
SCHF_ENVIRONMENT=production
SCHF_PUBLIC_URL=http://localhost:8084
SCHF_PORT=8084
POSTGRES_DB=schf_v2
POSTGRES_USER=schf
SCHF_DATABASE_PASSWORD=$dbPass
SCHF_REDIS_PASSWORD=$redisPass
SCHF_JWT_SECRET=$jwtSecret
SCHF_ALLOWED_ORIGINS=http://localhost:8084
SCHF_BACKUP_DIRECTORY=$RuntimeRoot\backups
"@ | Set-Content -Path $envFile -Encoding UTF8

    Write-Host "  .env created with generated secrets." -ForegroundColor Green
    Write-Host "  WARNING: Save these credentials securely. They will not be shown again." -ForegroundColor Yellow
} else {
    Write-Host "  .env already exists, keeping existing configuration." -ForegroundColor Yellow
}

# 4. Build and start
Write-Host "  Building Docker image and starting stack..." -ForegroundColor Cyan
Set-Location -LiteralPath $ScriptDir
docker compose build --quiet
if ($LASTEXITCODE -ne 0) { throw "Docker build failed" }

docker compose up -d
if ($LASTEXITCODE -ne 0) { throw "Docker compose up failed" }

# 5. Wait for health
Write-Host "  Waiting for API to become healthy..." -ForegroundColor Yellow
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 5
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8084/api/health" -TimeoutSec 5 -ErrorAction Stop
        if ($response.status -eq "ok") { $healthy = $true; break }
    } catch { }
}
if (-not $healthy) {
    Write-Host "  WARNING: API did not become healthy within timeout." -ForegroundColor Red
    Write-Host "  Check logs with: docker compose logs api" -ForegroundColor Yellow
} else {
    Write-Host "  API is healthy." -ForegroundColor Green
}

Write-Host "=== Installation complete ===" -ForegroundColor Cyan
Write-Host "  URL:       http://localhost:8084" -ForegroundColor White
Write-Host "  API:       http://localhost:8084/api" -ForegroundColor White
Write-Host "  Setup:     POST http://localhost:8084/api/setup/initialize" -ForegroundColor White
Write-Host "  Status:    GET  http://localhost:8084/api/setup/status" -ForegroundColor White
Write-Host "  Compose:   docker compose ps" -ForegroundColor White
Write-Host "  Logs:      docker compose logs -f" -ForegroundColor White
