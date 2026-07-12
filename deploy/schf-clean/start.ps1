#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $ScriptDir
Write-Host "Starting SCHF Clean..." -ForegroundColor Cyan
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw "Failed to start" }
Write-Host "SCHF Clean started." -ForegroundColor Green
Write-Host "  URL: http://localhost:8084" -ForegroundColor White
