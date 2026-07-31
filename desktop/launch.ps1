# Start the AI Dungeon Master engine (if needed) and open the web client (Windows).
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
if (-not $Root) { $Root = Resolve-Path (Join-Path $PSScriptRoot "..") }

$Port = if ($env:DM_PORT) { $env:DM_PORT } else { "8080" }
$HostName = if ($env:DM_HOST) { $env:DM_HOST } else { "127.0.0.1" }
$Base = "http://${HostName}:${Port}"
$AppUrl = if ($env:DM_APP_URL) { $env:DM_APP_URL } else { "$Base/app/" }
$Jar = if ($env:DM_JAR) { $env:DM_JAR } else {
  Join-Path $Root "service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar"
}
$Log = if ($env:DM_LOG) { $env:DM_LOG } else {
  Join-Path $env:TEMP "ai-dungeon-master-desktop.log"
}

function Test-Healthy {
  try {
    Invoke-WebRequest -Uri "$Base/v2/catalog" -UseBasicParsing -TimeoutSec 2 | Out-Null
    return $true
  } catch {
    try {
      Invoke-WebRequest -Uri "$Base/app/" -UseBasicParsing -TimeoutSec 2 | Out-Null
      return $true
    } catch {
      return $false
    }
  }
}

$startedHere = $false
$proc = $null

try {
  if (Test-Healthy) {
    Write-Host "[desktop] engine already running at $Base"
  } else {
    if (-not (Test-Path $Jar)) {
      Write-Host "[desktop] building fat jar…"
      Push-Location $Root
      try {
        mvn -pl service -am -DskipTests package -q
      } finally {
        Pop-Location
      }
    }
    if (-not (Test-Path $Jar)) {
      throw "Jar not found: $Jar"
    }
    Write-Host "[desktop] starting $Jar"
    Write-Host "[desktop] log → $Log"
    $proc = Start-Process -FilePath "java" -ArgumentList @("-jar", $Jar) `
      -RedirectStandardOutput $Log -RedirectStandardError $Log `
      -PassThru -WindowStyle Hidden
    $startedHere = $true

    Write-Host -NoNewline "[desktop] waiting for engine"
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
      if (Test-Healthy) { $ready = $true; break }
      if ($proc.HasExited) {
        Write-Host ""
        Get-Content $Log -Tail 40
        throw "engine exited early"
      }
      Write-Host -NoNewline "."
      Start-Sleep -Milliseconds 500
    }
    Write-Host ""
    if (-not $ready) { throw "timed out waiting for $Base" }
    Write-Host "[desktop] ready."
  }

  Write-Host "[desktop] opening $AppUrl"
  Start-Process $AppUrl

  if ($startedHere -and $proc) {
    Write-Host "[desktop] engine running (pid $($proc.Id)). Close this window or Ctrl+C to stop."
    Wait-Process -Id $proc.Id
  } else {
    Write-Host "[desktop] left existing engine running."
  }
} finally {
  if ($startedHere -and $proc -and -not $proc.HasExited) {
    Write-Host "[desktop] stopping engine…"
    Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
  }
}
