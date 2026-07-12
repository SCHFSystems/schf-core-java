#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $ScriptDir
Write-Host "Stopping SCHF Clean..." -ForegroundColor Cyan
docker compose stop
if ($LASTEXITCODE -ne 0) { throw "Failed to stop" }
Write-Host "SCHF Clean stopped." -ForegroundColor Green
