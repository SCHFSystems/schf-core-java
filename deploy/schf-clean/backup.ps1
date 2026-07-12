#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Create a backup of SCHF Clean PostgreSQL database.
.DESCRIPTION
    Creates a compressed SQL dump with manifest, SHA-256 checksum, and metadata.
    Backups are stored in D:\SCHF\_runtime\schf-clean\backups\.
#>

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackupDir = "D:\SCHF\_runtime\schf-clean\backups"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupFile = "$BackupDir\schf-clean-$Timestamp.sql.gz"
$ManifestFile = "$BackupDir\schf-clean-$Timestamp.manifest.json"

New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null

Write-Host "=== SCHF Clean Backup ===" -ForegroundColor Cyan

# Load .env
$envFile = "$ScriptDir\.env"
if (-not (Test-Path $envFile)) {
    Write-Host "ERROR: .env not found at $envFile" -ForegroundColor Red
    exit 1
}
$envVars = Get-Content $envFile | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object {
    $parts = $_ -split '=', 2
    [PSCustomObject]@{ Name = $parts[0]; Value = $parts[1] }
}

$dbName = ($envVars | Where-Object { $_.Name -eq 'POSTGRES_DB' }).Value
$dbUser = ($envVars | Where-Object { $_.Name -eq 'POSTGRES_USER' }).Value
$dbPass = ($envVars | Where-Object { $_.Name -eq 'SCHF_DATABASE_PASSWORD' }).Value
$instanceId = ($envVars | Where-Object { $_.Name -eq 'SCHF_INSTANCE_ID' }).Value

if (-not $dbPass) {
    Write-Host "ERROR: SCHF_DATABASE_PASSWORD not found in .env" -ForegroundColor Red
    exit 1
}

$containerId = docker compose ps -q db 2>$null
if (-not $containerId) {
    Write-Host "ERROR: Database container is not running. Start the stack first." -ForegroundColor Red
    exit 1
}

Write-Host "  Creating backup: $BackupFile"
docker exec -i $containerId pg_dump -U $dbUser -d $dbName --no-owner --compress=9 > $BackupFile
if ($LASTEXITCODE -ne 0) { Write-Host "ERROR: pg_dump failed" -ForegroundColor Red; exit 1 }

$checksum = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash.ToLower()

$version = docker compose exec -T api curl -fsS http://localhost:8080/api/system/info 2>$null | ConvertFrom-Json 2>$null
$apiVersion = if ($version) { $version.serverVersion } else { "unknown" }

$manifest = @{
    backupFile = "schf-clean-$Timestamp.sql.gz"
    timestamp  = (Get-Date -Format "o")
    instanceId = $instanceId
    schfVersion = $apiVersion
    database   = $dbName
    sha256     = $checksum
    status     = "completed"
} | ConvertTo-Json -Compress

$manifest | Set-Content -Path $ManifestFile -Encoding UTF8

Write-Host "  Backup completed!" -ForegroundColor Green
Write-Host "  File:     $BackupFile" -ForegroundColor White
Write-Host "  Manifest: $ManifestFile" -ForegroundColor White
Write-Host "  SHA-256:  $checksum" -ForegroundColor White
