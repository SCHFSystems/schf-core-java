#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Uninstall SCHF Clean - removes containers but preserves data.
.DESCRIPTION
    Stops and removes containers, networks, and images.
    By default, preserves volumes (.env, database, Redis, backups).
    Use -RemoveData to also delete volumes and runtime data.
#>

param(
    [switch]$RemoveData
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RuntimeRoot = "D:\SCHF\_runtime\schf-clean"

Set-Location -LiteralPath $ScriptDir

Write-Host "=== SCHF Clean Uninstall ===" -ForegroundColor Cyan

if ($RemoveData) {
    Write-Host "WARNING: You are about to remove ALL data including volumes and backups." -ForegroundColor Red
    $confirmation = Read-Host "Are you sure? Type 'DELETE EVERYTHING' to confirm"
    if ($confirmation -ne "DELETE EVERYTHING") {
        Write-Host "Uninstall cancelled." -ForegroundColor Yellow
        exit 0
    }
}

Write-Host "  Stopping and removing containers..." -ForegroundColor Yellow
docker compose down --remove-orphans
if ($LASTEXITCODE -ne 0) { Write-Host "WARNING: docker compose down had issues" -ForegroundColor Yellow }

Write-Host "  Removing images..." -ForegroundColor Yellow
docker compose images -q api 2>$null | ForEach-Object { docker rmi $_ 2>$null }

if ($RemoveData) {
    Write-Host "  Removing volumes..." -ForegroundColor Red
    docker volume rm schf-clean-pgdata schf-clean-redis-data schf-clean-caddy-data schf-clean-caddy-config schf-clean-logs 2>$null

    Write-Host "  Removing runtime directories..." -ForegroundColor Red
    if (Test-Path $RuntimeRoot) {
        Remove-Item -Path $RuntimeRoot -Recurse -Force
        Write-Host "  Removed: $RuntimeRoot" -ForegroundColor Red
    }

    Write-Host "  Removing .env..." -ForegroundColor Red
    $envFile = "$ScriptDir\.env"
    if (Test-Path $envFile) {
        Remove-Item -Path $envFile -Force
        Write-Host "  Removed: .env" -ForegroundColor Red
    }

    Write-Host "All data removed." -ForegroundColor Red
} else {
    Write-Host "  PRESERVED: volumes, .env, backups, runtime data" -ForegroundColor Green
    Write-Host "  To also remove data, run: .\uninstall.ps1 -RemoveData" -ForegroundColor Yellow
}

Write-Host "=== Uninstall complete ===" -ForegroundColor Cyan
