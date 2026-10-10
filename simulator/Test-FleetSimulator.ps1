$simulatorPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'simulator/fleet-simulator.ps1'

Describe 'Fleet simulator startup checks' {
    It 'has valid PowerShell syntax' {
        $tokens = $null
        $parseErrors = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            $simulatorPath, [ref]$tokens, [ref]$parseErrors) | Out-Null

        $parseErrors | Should BeNullOrEmpty
    }

    It 'fails before starting workers when the API is unreachable' {
        $failure = $null
        try {
            & $simulatorPath -ApiUrl 'http://127.0.0.1:1'
        }
        catch {
            $failure = $_.Exception
        }

        $failure | Should Not BeNullOrEmpty
        $failure.Message | Should Match 'Fleet API is not reachable'
    }

    It 'rejects heartbeat intervals outside the supported range' {
        { & $simulatorPath -IntervalSeconds 0 } | Should Throw
        { & $simulatorPath -IntervalSeconds 31 } | Should Throw
    }

    It 'stops and restarts only the selected device when the API is available' {
        $global:fleetSimulatorCommands = New-Object System.Collections.Queue
        $global:fleetSimulatorRequestCount = 0
        $global:fleetSimulatorJobCount = 0
        $global:fleetSimulatorStopCount = 0
        $global:fleetSimulatorRemoveCount = 0
        @('stop device-03', 'start device-03', 'quit') | ForEach-Object {
            $global:fleetSimulatorCommands.Enqueue($_)
        }

        Mock Invoke-RestMethod {
            $global:fleetSimulatorRequestCount++
            @()
        }
        Mock Start-Job {
            $global:fleetSimulatorJobCount++
            [pscustomobject]@{ Name = $Name; Id = $global:fleetSimulatorJobCount }
        }
        Mock Stop-Job { $global:fleetSimulatorStopCount++ }
        Mock Remove-Job { $global:fleetSimulatorRemoveCount++ }
        Mock Read-Host { $global:fleetSimulatorCommands.Dequeue() }

        & $simulatorPath

        $global:fleetSimulatorRequestCount | Should Be 13
        $global:fleetSimulatorJobCount | Should Be 6
        $global:fleetSimulatorStopCount | Should Be 6
        $global:fleetSimulatorRemoveCount | Should Be 6
        Remove-Variable fleetSimulatorCommands -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorRequestCount -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorJobCount -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorStopCount -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorRemoveCount -Scope Global -ErrorAction SilentlyContinue
    }

    It 'does not start a device worker when the API goes down before restart' {
        $global:fleetSimulatorCommands = New-Object System.Collections.Queue
        $global:fleetSimulatorRequestCount = 0
        $global:fleetSimulatorJobCount = 0
        $global:fleetSimulatorStopCount = 0
        $global:fleetSimulatorRemoveCount = 0
        @('stop device-03', 'start device-03', 'quit') | ForEach-Object {
            $global:fleetSimulatorCommands.Enqueue($_)
        }
        $global:fleetSimulatorAvailable = $true

        Mock Invoke-RestMethod {
            $global:fleetSimulatorRequestCount++
            if (-not $global:fleetSimulatorAvailable) {
                throw 'API unavailable'
            }
            @()
        }
        Mock Start-Job {
            $global:fleetSimulatorJobCount++
            [pscustomobject]@{ Name = $Name; Id = $global:fleetSimulatorJobCount }
        }
        Mock Stop-Job { $global:fleetSimulatorStopCount++ }
        Mock Remove-Job { $global:fleetSimulatorRemoveCount++ }
        Mock Read-Host {
            $nextCommand = $global:fleetSimulatorCommands.Dequeue()
            if ($nextCommand -eq 'start device-03') {
                $global:fleetSimulatorAvailable = $false
            }
            $nextCommand
        }

        & $simulatorPath

        $global:fleetSimulatorRequestCount | Should Be 12
        $global:fleetSimulatorJobCount | Should Be 5
        $global:fleetSimulatorStopCount | Should Be 5
        $global:fleetSimulatorRemoveCount | Should Be 5
        Remove-Variable fleetSimulatorCommands -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorRequestCount -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorJobCount -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorStopCount -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorRemoveCount -Scope Global -ErrorAction SilentlyContinue
        Remove-Variable fleetSimulatorAvailable -Scope Global -ErrorAction SilentlyContinue
    }
}
