[CmdletBinding()]
param(
    [string]$ClusterId = 'c23ea84b986c446d5b3fa9227962e77f4',
    [string]$KubernetesNamespace = 'biel-life-camp',
    [string]$SecretName = 'nacos-client-dev-secret',
    [string]$NacosNamespace = 'dev',
    [string]$NacosGroup = 'LIFECAMP'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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

function Get-ListenerProcessId {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) {
        return $null
    }
    if ($listeners.Count -gt 1) {
        throw "Multiple processes are listening on local port $Port."
    }

    return [int]$listeners[0].OwningProcess
}

function Wait-NacosForward {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessId
    )

    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Milliseconds 500
        $httpProcessId = Get-ListenerProcessId -Port 8848
        $grpcProcessId = Get-ListenerProcessId -Port 9848
        if ($httpProcessId -eq $ProcessId -and $grpcProcessId -eq $ProcessId) {
            return
        }

        $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if ($null -eq $process) {
            break
        }
    }

    throw 'Timed out while waiting for the Nacos 8848/9848 port-forward.'
}

$kubectl = Get-RequiredCommandPath -Name 'kubectl'
$aliyun = Get-RequiredCommandPath -Name 'aliyun'
$tunnelScript = Join-Path $PSScriptRoot 'Start-AckNacosTunnel.ps1'
if (-not (Test-Path -LiteralPath $tunnelScript)) {
    throw "Tunnel script was not found: $tunnelScript"
}
& $tunnelScript `
    -ClusterId $ClusterId `
    -KubernetesNamespace $KubernetesNamespace

$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$workDirectory = Join-Path $tempRoot ('biel-life-camp-nacos-' + [Guid]::NewGuid().ToString('N'))
$kubeconfig = Join-Path $workDirectory 'kubeconfig'
$portForwardProcess = $null
$portForwardStarted = $false
$completed = $false
$username = $null
$password = $null
$accessToken = $null

try {
    New-Item -ItemType Directory -Path $workDirectory | Out-Null

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

    $secretJson = & $kubectl `
        --kubeconfig $kubeconfig `
        -n $KubernetesNamespace `
        get secret $SecretName `
        -o json
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read Kubernetes Secret $SecretName."
    }

    $secret = $secretJson | ConvertFrom-Json
    $username = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String([string]$secret.data.NACOS_USERNAME)
    )
    $password = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String([string]$secret.data.NACOS_PASSWORD)
    )
    if ([string]::IsNullOrWhiteSpace($username) -or [string]::IsNullOrWhiteSpace($password)) {
        throw "Kubernetes Secret $SecretName contains an empty Nacos credential."
    }

    $httpProcessId = Get-ListenerProcessId -Port 8848
    $grpcProcessId = Get-ListenerProcessId -Port 9848
    if (($null -eq $httpProcessId) -xor ($null -eq $grpcProcessId)) {
        throw 'Only one Nacos local port is listening. Resolve the 8848/9848 port conflict.'
    }
    if ($null -ne $httpProcessId -and $httpProcessId -ne $grpcProcessId) {
        throw 'The Nacos local ports are owned by different processes.'
    }

    if ($null -eq $httpProcessId) {
        $stdout = Join-Path $workDirectory 'port-forward.stdout.log'
        $stderr = Join-Path $workDirectory 'port-forward.stderr.log'
        $arguments = @(
            '--kubeconfig', $kubeconfig,
            '-n', $KubernetesNamespace,
            'port-forward', 'service/nacos',
            '8848:8848', '9848:9848',
            '--address', '127.0.0.1'
        )
        $portForwardProcess = Start-Process `
            -FilePath $kubectl `
            -ArgumentList $arguments `
            -WindowStyle Hidden `
            -RedirectStandardOutput $stdout `
            -RedirectStandardError $stderr `
            -PassThru
        $portForwardStarted = $true
        Wait-NacosForward -ProcessId $portForwardProcess.Id
        $httpProcessId = $portForwardProcess.Id
    }

    $loginResponse = Invoke-RestMethod `
        -Method Post `
        -Uri 'http://127.0.0.1:8848/nacos/v3/auth/user/login' `
        -Body @{username = $username; password = $password} `
        -ContentType 'application/x-www-form-urlencoded' `
        -TimeoutSec 15
    $accessToken = [string]$loginResponse.accessToken
    if ([string]::IsNullOrWhiteSpace($accessToken) -and $null -ne $loginResponse.data) {
        $accessToken = [string]$loginResponse.data.accessToken
    }
    if ([string]::IsNullOrWhiteSpace($accessToken)) {
        throw 'The Nacos login response does not contain an access token.'
    }

    $configResponse = Invoke-RestMethod `
        -Method Get `
        -Uri (
            'http://127.0.0.1:8848/nacos/v3/client/cs/config' +
            '?dataId=gateway.yaml&groupName=' + [Uri]::EscapeDataString($NacosGroup) +
            '&namespaceId=' + [Uri]::EscapeDataString($NacosNamespace)
        ) `
        -Headers @{accessToken = $accessToken} `
        -TimeoutSec 15
    if ($null -ne $configResponse.code -and [int]$configResponse.code -ne 0) {
        throw "Failed to read the Nacos development configuration: $($configResponse.message)"
    }

    $env:SPRING_PROFILES_ACTIVE = 'nacos'
    $env:NACOS_ENABLED = 'true'
    $env:NACOS_SERVER_ADDR = '127.0.0.1:8848'
    $env:NACOS_NAMESPACE = $NacosNamespace
    $env:NACOS_GROUP = $NacosGroup
    $env:NACOS_USERNAME = $username
    $env:NACOS_PASSWORD = $password
    $env:SPRING_CLOUD_NACOS_DISCOVERY_IP = '127.0.0.1'

    Write-Host 'ACK Nacos local development environment is ready.' -ForegroundColor Green
    Write-Host "Namespace: $NacosNamespace"
    Write-Host "Group: $NacosGroup"
    Write-Host 'Server: 127.0.0.1:8848'
    Write-Host "Port-forward PID: $httpProcessId"
    Write-Host 'Java services launched from this PowerShell will inherit the environment.'
    $completed = $true
} finally {
    $accessToken = $null
    $username = $null
    $password = $null

    if (Test-Path -LiteralPath $kubeconfig) {
        Remove-Item -LiteralPath $kubeconfig -Force
    }

    if (-not $completed -and $portForwardStarted -and $null -ne $portForwardProcess) {
        Stop-Process -Id $portForwardProcess.Id -Force -ErrorAction SilentlyContinue
    }

    if (-not $portForwardStarted -or -not $completed) {
        $resolvedWorkDirectory = [IO.Path]::GetFullPath($workDirectory)
        if (
            (Test-Path -LiteralPath $resolvedWorkDirectory) -and
            $resolvedWorkDirectory.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)
        ) {
            Remove-Item -LiteralPath $resolvedWorkDirectory -Recurse -Force
        }
    }
}
