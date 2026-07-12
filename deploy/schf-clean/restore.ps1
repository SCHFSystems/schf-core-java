#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Restore SCHF Clean from a backup file.
.DESCRIPTION
    Validates the backup file and manifest, then restores the PostgreSQL database.
    Requires explicit confirmation before proceeding.
#>

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackupDir = "D:\SCHF\_runtime\schf-clean\backups"

Write-Host "=== SCHF Clean Restore ===" -ForegroundColor Cyan

# Load .env
$envFile = "$ScriptDir\.env"
if (-not (Test-Path $envFile)) { Write-Host "ERROR: .env not found" -ForegroundColor Red; exit 1 }
$envVars = Get-Content $envFile | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [PSCustomObject]@{ Name = $parts[0]; Value = $parts[1] }
}
$dbName = ($envVars | Where-Object { $_.Name -eq 'POSTGRES_DB' }).Value
$dbUser = ($envVars | Where-Object { $_.Name -eq 'POSTGRES_USER' }).Value

# List available backups
$backups = Get-ChildItem -Path $BackupDir -Filter "*.sql.gz" | Sort-Object LastWriteTime -Descending
if ($backups.Count -eq 0) {
    Write-Host "No backups found in $BackupDir" -ForegroundColor Red
    exit 1
}

Write-Host "Available backups:" -ForegroundColor Yellow
for ($i = 0; $i -lt $backups.Count; $i++) {
    $manifest = $backups[$i].BaseName + ".manifest.json"
    $manifestPath = Join-Path $BackupDir $manifest
    $info = if (Test-Path $manifestPath) { " (manifest found)" } else { " (no manifest)" }
    Write-Host "  [$i] $($backups[$i].Name)$info"
}

$selection = Read-Host "Enter backup number to restore"
$backupFile = $backups[[int]$selection]
if (-not $backupFile) { Write-Host "Invalid selection" -ForegroundColor Red; exit 1 }

$backupPath = $backupFile.FullName
$manifestPath = [System.IO.Path]::ChangeExtension($backupPath, "manifest.json")

# Validate checksum if manifest exists
if (Test-Path $manifestPath) {
    $manifest = Get-Content $manifestPath | ConvertFrom-Json
    $expectedHash = $manifest.sha256
    $actualHash = (Get-FileHash -Path $backupPath -Algorithm SHA256).Hash.ToLower()
    if ($expectedHash -ne $actualHash) {
        Write-Host "ERROR: Checksum mismatch! Expected $expectedHash, got $actualHash" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Checksum verified." -ForegroundColor Green
    Write-Host "  Backup timestamp: $($manifest.timestamp)" -ForegroundColor White
    Write-Host "  SCHF version:     $($manifest.schfVersion)" -ForegroundColor White
} else {
    Write-Host "WARNING: No manifest found for this backup." -ForegroundColor Yellow
    Write-Host "  Proceed at your own risk." -ForegroundColor Yellow
}

# Confirmation
$confirmation = Read-Host "This will STOP all services and REPLACE database data. Are you sure? (yes/no)"
if ($confirmation -ne "yes") {
    Write-Host "Restore cancelled." -ForegroundColor Yellow
    exit 0
}

Write-Host "  Stopping services..." -ForegroundColor Cyan
docker compose stop api proxy 2>$null

Write-Host "  Dropping and recreating database..." -ForegroundColor Cyan
$containerId = docker compose ps -q db
docker exec -i $containerId psql -U $dbUser -d postgres -c "DROP DATABASE IF EXISTS $dbName;" 2>$null
docker exec -i $containerId psql -U $dbUser -d postgres -c "CREATE DATABASE $dbName OWNER $dbUser;"

Write-Host "  Restoring from backup..." -ForegroundColor Cyan
Get-Content $backupPath -Raw | docker exec -i $containerId gunzip | docker exec -i $containerId psql -U $dbUser -d $dbName
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: Restore failed" -ForegroundColor Red; exit 1 }

Write-Host "  Restarting services..." -ForegroundColor Cyan
docker compose up -d api proxy

Write-Host "  Waiting for API health..." -ForegroundColor Yellow
Start-Sleep -Seconds 10
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8084/api/health" -TimeoutSec 10 -ErrorAction Stop
    Write-Host "  API health: $($response.status)" -ForegroundColor Green
} catch {
    Write-Host "  WARNING: API did not become healthy after restore." -ForegroundColor Red
}

Write-Host "=== Restore completed ===" -ForegroundColor Green
