[CmdletBinding()]
param(
    [switch]$Monitor,
    [switch]$Stop,
    [string]$ClusterId = 'c23ea84b986c446d5b3fa9227962e77f4',
    [string]$KubernetesNamespace = 'biel-life-camp',
    [int]$RestartDelaySeconds = 3
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$targetPorts = @(8848, 9848, 18000)
$scriptPath = $MyInvocation.MyCommand.Path
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$stateDirectory = Join-Path $tempRoot 'biel-life-camp-nacos-tunnel'

function Get-RequiredCommandPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Required command was not found: $Name"
    }

    return $command.Source
}

function Get-PortOwnerProcessId {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $listener = Get-NetTCPConnection `
        -LocalPort $Port `
        -State Listen `
        -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $listener) {
        return $null
    }

    return [int]$listener.OwningProcess
}

function Get-TunnelMonitorProcesses {
    $escapedScriptPath = [Regex]::Escape($scriptPath)
    return @(
        Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $_.ProcessId -ne $PID -and
            $_.Name -match '^powershell(?:\.exe)?$|^pwsh(?:\.exe)?$' -and
            $_.CommandLine -match $escapedScriptPath -and
            $_.CommandLine -match '(?:^|\s)-Monitor(?:\s|$)'
        }
    )
}

function Test-NacosHealth {
    try {
        $response = Invoke-WebRequest `
            -UseBasicParsing `
            -Uri 'http://127.0.0.1:8848/nacos/v3/admin/core/state/liveness' `
            -TimeoutSec 3
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Test-AllTunnelPorts {
    foreach ($port in $targetPorts) {
        if ($null -eq (Get-PortOwnerProcessId -Port $port)) {
            return $false
        }
    }

    return $true
}

function Stop-TunnelProcesses {
    foreach ($monitorProcess in Get-TunnelMonitorProcesses) {
        Stop-Process -Id $monitorProcess.ProcessId -Force -ErrorAction SilentlyContinue
    }

    Start-Sleep -Milliseconds 500
    $portProcessIds = @(
        foreach ($port in $targetPorts) {
            Get-PortOwnerProcessId -Port $port
        }
    ) |
        Where-Object { $null -ne $_ } |
        Sort-Object -Unique
    foreach ($portProcessId in $portProcessIds) {
        $process = Get-Process -Id $portProcessId -ErrorAction SilentlyContinue
        if ($null -ne $process -and 'kubectl' -eq $process.ProcessName) {
            Stop-Process -Id $portProcessId -Force -ErrorAction SilentlyContinue
        }
    }
}

if ($Stop) {
    Stop-TunnelProcesses
    Write-Host 'ACK Nacos tunnel monitor has been stopped.'
    exit 0
}

if ($Monitor) {
    $kubectl = Get-RequiredCommandPath -Name 'kubectl'
    $aliyun = Get-RequiredCommandPath -Name 'aliyun'

    while ($true) {
        $iterationDirectory = Join-Path $stateDirectory ([Guid]::NewGuid().ToString('N'))
        $kubeconfig = Join-Path $iterationDirectory 'kubeconfig'
        $kubectlProcess = $null

        try {
            New-Item -ItemType Directory -Path $iterationDirectory -Force | Out-Null
            $rawKubeconfig = & $aliyun cs DescribeClusterUserKubeconfig --ClusterId $ClusterId
            if ($LASTEXITCODE -ne 0) {
                throw 'Failed to obtain the ACK kubeconfig.'
            }

            $kubeconfigContent = [string](($rawKubeconfig | ConvertFrom-Json).config)
            if ([string]::IsNullOrWhiteSpace($kubeconfigContent)) {
                throw 'ACK returned an empty kubeconfig.'
            }
            [IO.File]::WriteAllText(
                $kubeconfig,
                $kubeconfigContent,
                (New-Object Text.UTF8Encoding($false))
            )

            $arguments = @(
                '--kubeconfig', $kubeconfig,
                '-n', $KubernetesNamespace,
                'port-forward', 'statefulset/nacos',
                '8848:8848', '9848:9848', '18000:8080',
                '--address', '127.0.0.1'
            )
            $kubectlProcess = Start-Process `
                -FilePath $kubectl `
                -ArgumentList $arguments `
                -WindowStyle Hidden `
                -PassThru

            $ready = $false
            for ($attempt = 0; $attempt -lt 30; $attempt++) {
                Start-Sleep -Milliseconds 500
                if ($kubectlProcess.HasExited) {
                    break
                }
                if (Test-AllTunnelPorts -and (Test-NacosHealth)) {
                    $ready = $true
                    break
                }
            }
            if (-not $ready) {
                throw 'Timed out while waiting for the ACK Nacos tunnel.'
            }

            Remove-Item -LiteralPath $kubeconfig -Force -ErrorAction SilentlyContinue
            $consecutiveFailures = 0
            while ($null -ne (Get-Process -Id $kubectlProcess.Id -ErrorAction SilentlyContinue)) {
                Start-Sleep -Seconds 5
                if (Test-NacosHealth) {
                    $consecutiveFailures = 0
                } else {
                    $consecutiveFailures++
                }

                if ($consecutiveFailures -ge 3) {
                    Stop-Process -Id $kubectlProcess.Id -Force -ErrorAction SilentlyContinue
                    break
                }
            }
        } catch {
            Write-Warning $_.Exception.Message
        } finally {
            if ($null -ne $kubectlProcess) {
                Stop-Process -Id $kubectlProcess.Id -Force -ErrorAction SilentlyContinue
            }
            if (Test-Path -LiteralPath $iterationDirectory) {
                $resolvedDirectory = [IO.Path]::GetFullPath($iterationDirectory)
                if ($resolvedDirectory.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
                    Remove-Item -LiteralPath $resolvedDirectory -Recurse -Force
                }
            }
        }

        Start-Sleep -Seconds $RestartDelaySeconds
    }
}

$existingMonitors = @(Get-TunnelMonitorProcesses)
if ($existingMonitors.Count -gt 0 -and (Test-AllTunnelPorts) -and (Test-NacosHealth)) {
    Write-Host 'ACK Nacos tunnel monitor is already running.' -ForegroundColor Green
    Write-Host "Monitor PID: $($existingMonitors[0].ProcessId)"
    exit 0
}

Stop-TunnelProcesses
New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
$powershell = Get-RequiredCommandPath -Name 'powershell.exe'
$stdout = Join-Path $stateDirectory 'monitor.stdout.log'
$stderr = Join-Path $stateDirectory 'monitor.stderr.log'
$monitorArguments = @(
    '-NoProfile',
    '-ExecutionPolicy', 'Bypass',
    '-File', $scriptPath,
    '-Monitor',
    '-ClusterId', $ClusterId,
    '-KubernetesNamespace', $KubernetesNamespace,
    '-RestartDelaySeconds', $RestartDelaySeconds
)
$monitorProcess = Start-Process `
    -FilePath $powershell `
    -ArgumentList $monitorArguments `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru

$ready = $false
for ($attempt = 0; $attempt -lt 40; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($monitorProcess.HasExited) {
        break
    }
    if ((Test-AllTunnelPorts) -and (Test-NacosHealth)) {
        $ready = $true
        break
    }
}
if (-not $ready) {
    $errorOutput = if (Test-Path -LiteralPath $stderr) {
        Get-Content -Raw -LiteralPath $stderr
    } else {
        ''
    }
    Stop-TunnelProcesses
    throw "Failed to start the ACK Nacos tunnel monitor. $errorOutput"
}

Write-Host 'ACK Nacos tunnel monitor is ready.' -ForegroundColor Green
Write-Host "Monitor PID: $($monitorProcess.Id)"
Write-Host 'Client: 127.0.0.1:8848 and 127.0.0.1:9848'
Write-Host 'Console: http://127.0.0.1:18000'
