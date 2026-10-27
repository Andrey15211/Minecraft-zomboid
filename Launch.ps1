$ErrorActionPreference = 'Stop'
$ProjectRoot = $PSScriptRoot
$PzLauncher = Join-Path $ProjectRoot 'bridge-base\zomboid\pz-bridge\Launch-PZ.bat'

# Bridge shared-memory directory (both games resolve it from ERMC_DIR).
$env:ERMC_DIR = Join-Path $ProjectRoot 'runtime'
New-Item -ItemType Directory -Path $env:ERMC_DIR -Force | Out-Null
$SharedFile = Join-Path $env:ERMC_DIR 'bridge.shm'

function Read-BridgeHeader {
    if (!(Test-Path -LiteralPath $SharedFile)) { return $null }
    try {
        $Header = New-Object byte[] 128
        $Stream = [IO.File]::Open($SharedFile, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        try { $Read = $Stream.Read($Header, 0, $Header.Length) } finally { $Stream.Dispose() }
        if ($Read -ne $Header.Length -or [BitConverter]::ToUInt32($Header, 0) -ne 0x434D484D) { return $null }
        return [PSCustomObject]@{
            HostProcessId = [BitConverter]::ToUInt32($Header, 0x20)
            HostHeartbeat = [BitConverter]::ToUInt64($Header, 0x10)
        }
    } catch { return $null }
}

# Project Zomboid via the bridge launcher (Start menu -> load your save as usual).
if (!(Test-Path -LiteralPath $PzLauncher)) { throw "PZ bridge launcher not found: $PzLauncher" }
Write-Output 'Starting Project Zomboid (bridge launcher)...'
Start-Process -FilePath $PzLauncher -WorkingDirectory (Split-Path -Parent $PzLauncher)

# Wait for the host heartbeat to advance: the bridge is live once PZ's JVM is up.
$Watch = [Diagnostics.Stopwatch]::StartNew()
$Ready = $false
$LastHb = [UInt64]::MaxValue
while ($Watch.Elapsed.TotalSeconds -lt 120) {
    $State = Read-BridgeHeader
    if ($State -and $State.HostHeartbeat -gt 0 -and $State.HostHeartbeat -ne [UInt64]::MaxValue) {
        if ($LastHb -ne [UInt64]::MaxValue -and $State.HostHeartbeat -gt $LastHb) { $Ready = $true; break }
        $LastHb = $State.HostHeartbeat
    }
    Start-Sleep -Milliseconds 500
}
if (!$Ready) { throw 'Project Zomboid did not start its bridge (no advancing heartbeat in runtime\bridge.shm). Check the launcher console.' }
Write-Output "PZ bridge ready (host PID $((Read-BridgeHeader).HostProcessId)). Load your save in PZ."

# Minecraft (Fabric dev client): normal window, link-only for Stage 3
# (-Derbridge.disableOverlay=true is set in build.gradle runs.client.vmArgs).
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2'
Write-Output 'Starting Minecraft dev client... It opens the bridge world itself.'
Set-Location "$ProjectRoot\bridge-base\elden-ring\mc-bridge"
& .\gradlew.bat runClient --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Minecraft launch failed; see the console and run\logs\latest.log.' }
