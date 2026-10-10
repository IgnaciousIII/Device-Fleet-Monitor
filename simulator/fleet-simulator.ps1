param(
    [string] $ApiUrl = 'http://localhost:8080',
    [ValidateRange(1, 30)]
    [int] $IntervalSeconds = 5
)

$ErrorActionPreference = 'Stop'
$ApiUrl = $ApiUrl.TrimEnd('/')

try {
    $null = Invoke-RestMethod -Uri "$ApiUrl/devices" -TimeoutSec 3
}
catch {
    throw "Fleet API is not reachable at $ApiUrl. Start the API first, then run the simulator."
}

$devices = @{
    'device-01' = 'Lab Device 01'
    'device-02' = 'Lab Device 02'
    'device-03' = 'Lab Device 03'
    'device-04' = 'Lab Device 04'
    'device-05' = 'Lab Device 05'
}

$heartbeatWorker = {
    param($ServerUrl, $DeviceId, $DelaySeconds)

    while ($true) {
        $heartbeatBody = @{
            timestamp = [DateTime]::UtcNow.ToString('o')
            status = 'OK'
        } | ConvertTo-Json -Compress

        try {
            $null = Invoke-RestMethod -Method Post `
                -Uri "$ServerUrl/devices/$DeviceId/heartbeat" `
                -ContentType 'application/json' -Body $heartbeatBody -TimeoutSec 5
        }
        catch {
            # Keep trying on the next interval if the API is briefly unavailable.
        }

        Start-Sleep -Seconds $DelaySeconds
    }
}

$jobs = @{}

function Start-DeviceHeartbeat {
    param([string] $DeviceId)

    $name = $devices[$DeviceId]

    try {
        $registerBody = @{
            id = $DeviceId
            name = $name
        } | ConvertTo-Json -Compress

        try {
            $null = Invoke-RestMethod -Method Post -Uri "$ApiUrl/devices" `
                -ContentType 'application/json' -Body $registerBody -TimeoutSec 5
        }
        catch {
            $statusCode = 0
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $statusCode = [int]$_.Exception.Response.StatusCode
            }
            if ($statusCode -ne 409) {
                throw
            }
        }

        $firstHeartbeat = @{
            timestamp = [DateTime]::UtcNow.ToString('o')
            status = 'OK'
        } | ConvertTo-Json -Compress
        $null = Invoke-RestMethod -Method Post `
            -Uri "$ApiUrl/devices/$DeviceId/heartbeat" `
            -ContentType 'application/json' -Body $firstHeartbeat -TimeoutSec 5
    }
    catch {
        Write-Host "Could not start ${DeviceId}: API request failed at $ApiUrl. $($_.Exception.Message)" `
            -ForegroundColor Red
        return
    }

    $jobs[$DeviceId] = Start-Job -Name $DeviceId -ScriptBlock $heartbeatWorker `
        -ArgumentList $ApiUrl, $DeviceId, $IntervalSeconds
    Write-Host "Started $DeviceId ($name)."
}

function Stop-DeviceHeartbeat {
    param([string] $DeviceId)

    if (-not $jobs.ContainsKey($DeviceId)) {
        Write-Host "$DeviceId is already stopped."
        return
    }

    $jobId = $jobs[$DeviceId].Id
    Stop-Job -Id $jobId -ErrorAction SilentlyContinue
    Remove-Job -Id $jobId -Force -ErrorAction SilentlyContinue
    $jobs.Remove($DeviceId)
    Write-Host "Stopped $DeviceId. It will report OFFLINE after 30 seconds without a heartbeat."
}

try {
    foreach ($deviceId in $devices.Keys) {
        Start-DeviceHeartbeat -DeviceId $deviceId
    }

    Write-Host "Heartbeats are sent every $IntervalSeconds seconds to $ApiUrl."
    Write-Host 'Commands: stop device-03 | start device-03 | status | quit'

    while ($true) {
        $command = (Read-Host 'simulator').Trim()

        if ($command -match '^quit$') {
            break
        }
        elseif ($command -match '^status$') {
            foreach ($deviceId in ($devices.Keys | Sort-Object)) {
                $state = if ($jobs.ContainsKey($deviceId)) { 'running' } else { 'stopped' }
                Write-Host "$deviceId : $state"
            }
        }
        elseif ($command -match '^stop\s+(device-0[1-5])$') {
            Stop-DeviceHeartbeat -DeviceId $Matches[1]
        }
        elseif ($command -match '^start\s+(device-0[1-5])$') {
            $deviceId = $Matches[1]
            if ($jobs.ContainsKey($deviceId)) {
                Write-Host "$deviceId is already running."
            }
            else {
                Start-DeviceHeartbeat -DeviceId $deviceId
            }
        }
        else {
            Write-Host 'Use: stop device-03, start device-03, status, or quit.'
        }
    }
}
finally {
    foreach ($deviceId in @($jobs.Keys)) {
        Stop-DeviceHeartbeat -DeviceId $deviceId
    }
}
