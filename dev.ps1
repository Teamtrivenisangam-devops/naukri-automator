#Requires -Version 5.1
<#
.SYNOPSIS
    Dev-mode launcher for NaukriAutomator.

    Starts the Java backend on port 8080 (visible in a separate console window)
    and the Vite dev server on http://127.0.0.1:5173 (opens automatically in
    your default browser). No EXE. No install. No electron-builder. Edit React
    files, hit save, browser hot-reloads.

    Created by Adikarthik Gupta C B

.NOTES
    Ctrl+C in this window stops the BE window. Close the browser tab or hit
    Ctrl+C in the Vite terminal to stop the FE. To restart the BE only,
    close the BE window and re-run this script.
#>
$ErrorActionPreference = 'Stop'
$root  = $PSScriptRoot
$be    = Join-Path $root 'backend\target\naukri-be.jar'
$mock  = Join-Path $root 'mock-naukri\target\mock-naukri.jar'
$JAVA_HOME_DEV = 'C:\Users\ADMIN\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot'

Write-Host "==== NaukriAutomator DEV launcher ====" -ForegroundColor Cyan

# 1) Make sure the BE jar exists
if (-not (Test-Path $be)) {
    Write-Host "BE jar not found -- building it now..." -ForegroundColor Yellow
    $env:JAVA_HOME = $JAVA_HOME_DEV
    Push-Location (Join-Path $root 'backend')
    mvn -q clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "BE build failed" }
    Pop-Location
}

# 2) Start BE on port 8080 in a new console window
Write-Host "Starting Java BE on http://127.0.0.1:8080 ..." -ForegroundColor Green
$javaExe = Join-Path $JAVA_HOME_DEV 'bin\java.exe'
$beProc  = Start-Process -FilePath $javaExe `
                         -ArgumentList @('-jar', $be, '--server.port=8080') `
                         -PassThru

Write-Host "BE PID: $($beProc.Id)"
Write-Host "Waiting for BE to bind port 8080..." -ForegroundColor Yellow

$deadline = (Get-Date).AddSeconds(60)
$ready = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 1
    try {
        $r = Invoke-WebRequest -Uri 'http://127.0.0.1:8080/api/health' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
}
if (-not $ready) {
    Write-Host "BE did not come up within 60 s -- stopping." -ForegroundColor Red
    Stop-Process -Id $beProc.Id -Force -ErrorAction SilentlyContinue
    throw "BE did not come up"
}

Write-Host "BE READY at http://127.0.0.1:8080" -ForegroundColor Green
Write-Host ""
Write-Host "Now start the FE in a SEPARATE terminal:" -ForegroundColor Cyan
Write-Host "    cd $root\frontend"
Write-Host "    npm run dev"
Write-Host ""
Write-Host "Then open: http://127.0.0.1:5173" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press Ctrl+C in THIS window to stop the BE (PID $($beProc.Id))." -ForegroundColor Yellow

# Block until user hits Ctrl+C
try {
    while (-not $beProc.HasExited) { Start-Sleep -Seconds 5 }
} finally {
    if (-not $beProc.HasExited) {
        Write-Host "Stopping BE PID $($beProc.Id) ..." -ForegroundColor Yellow
        Stop-Process -Id $beProc.Id -Force -ErrorAction SilentlyContinue
    }
}
