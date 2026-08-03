[CmdletBinding()]
param(
    [switch]$Stop,
    [string]$ClusterId = 'c23ea84b986c446d5b3fa9227962e77f4',
    [string]$KubernetesNamespace = 'biel-life-camp',
    [string]$ServiceName = 'redis',
    [string]$SecretName = 'redis-dev-secret',
    [ValidateRange(1, 65535)]
    [int]$LocalPort = 6379
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$redisEnvironmentVariables = @(
    'REDIS_HOST',
    'REDIS_PORT',
    'REDIS_USERNAME',
    'REDIS_PASSWORD',
    'REDIS_DATABASE',
    'REDISCLI_AUTH'
)
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$stateDirectory = Join-Path $tempRoot "biel-life-camp-redis-tunnel-$LocalPort"
$stateFile = Join-Path $stateDirectory 'state.json'

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

function Test-LocalPortOpen {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $client = New-Object Net.Sockets.TcpClient
    $waitHandle = $null
    try {
        $asyncResult = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        $waitHandle = $asyncResult.AsyncWaitHandle
        if (-not $waitHandle.WaitOne(500, $false)) {
            return $false
        }
        $client.EndConnect($asyncResult)
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $waitHandle) {
            $waitHandle.Dispose()
        }
        $client.Dispose()
    }
}

function Test-SavedTunnelProcess {
    param(
        [Parameter(Mandatory = $true)]
        [PSCustomObject]$State
    )

    try {
        $process = Get-Process -Id ([int]$State.processId) -ErrorAction SilentlyContinue
        if ($null -eq $process -or 'kubectl' -ne $process.ProcessName) {
            return $false
        }

        $savedStartTime = [DateTime]::Parse(
            [string]$State.processStartedAtUtc,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind
        )
        $actualStartTime = $process.StartTime.ToUniversalTime()
        return [Math]::Abs(
            ($actualStartTime - $savedStartTime.ToUniversalTime()).TotalSeconds
        ) -lt 1
    } catch {
        return $false
    }
}

function Remove-StateDirectory {
    if (-not (Test-Path -LiteralPath $stateDirectory)) {
        return
    }

    $resolvedDirectory = [IO.Path]::GetFullPath($stateDirectory)
    $safePrefix = $tempRoot.TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    ) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedDirectory.StartsWith($safePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove an unsafe Redis tunnel state path: $resolvedDirectory"
    }

    Remove-Item -LiteralPath $resolvedDirectory -Recurse -Force
}

function Clear-RedisEnvironment {
    foreach ($variableName in $redisEnvironmentVariables) {
        [Environment]::SetEnvironmentVariable($variableName, $null, 'Process')
    }
}

function Get-SavedTunnelState {
    if (-not (Test-Path -LiteralPath $stateFile)) {
        return $null
    }

    try {
        return Get-Content -Raw -LiteralPath $stateFile -Encoding utf8 |
            ConvertFrom-Json
    } catch {
        return $null
    }
}

function Stop-SavedTunnel {
    $state = Get-SavedTunnelState
    if ($null -eq $state) {
        if (Test-LocalPortOpen -Port $LocalPort) {
            Write-Warning (
                "Port $LocalPort is in use, but it is not a tunnel managed by this script. " +
                'It was not stopped.'
            )
            return
        }

        Remove-StateDirectory
        Write-Host 'ACK Redis tunnel is already stopped.'
        return
    }

    $savedProcessId = [int]$state.processId
    if (Test-SavedTunnelProcess -State $state) {
        Stop-Process -Id $savedProcessId -Force
        for ($attempt = 0; $attempt -lt 20; $attempt++) {
            if ($null -eq (Get-Process -Id $savedProcessId -ErrorAction SilentlyContinue)) {
                break
            }
            Start-Sleep -Milliseconds 100
        }
    } else {
        if (Test-LocalPortOpen -Port $LocalPort) {
            Write-Warning (
                "Port $LocalPort is in use, but the saved Redis tunnel process no longer " +
                'matches. It was not stopped.'
            )
            return
        }
    }

    Remove-StateDirectory
    Write-Host 'ACK Redis tunnel has been stopped.'
}

function New-AckKubeconfig {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AliyunPath,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory
    )

    New-Item -ItemType Directory -Path $WorkingDirectory -Force | Out-Null
    $rawKubeconfig = & $AliyunPath cs DescribeClusterUserKubeconfig --ClusterId $ClusterId
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to obtain the ACK kubeconfig.'
    }

    $kubeconfigContent = [string](($rawKubeconfig | ConvertFrom-Json).config)
    if ([string]::IsNullOrWhiteSpace($kubeconfigContent)) {
        throw 'ACK returned an empty kubeconfig.'
    }

    $kubeconfig = Join-Path $WorkingDirectory 'kubeconfig'
    [IO.File]::WriteAllText(
        $kubeconfig,
        $kubeconfigContent,
        (New-Object Text.UTF8Encoding($false))
    )
    return $kubeconfig
}

function Get-RedisCredential {
    param(
        [Parameter(Mandatory = $true)]
        [string]$KubectlPath,
        [Parameter(Mandatory = $true)]
        [string]$Kubeconfig
    )

    $secretJson = & $KubectlPath `
        --kubeconfig $Kubeconfig `
        -n $KubernetesNamespace `
        get secret $SecretName `
        -o json
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read Redis credentials from Secret/$SecretName."
    }

    $secret = $secretJson | ConvertFrom-Json
    $username = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String([string]$secret.data.REDIS_USERNAME)
    )
    $password = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String([string]$secret.data.REDIS_PASSWORD)
    )
    if ([string]::IsNullOrWhiteSpace($username) -or [string]::IsNullOrWhiteSpace($password)) {
        throw "Secret/$SecretName does not contain usable Redis ACL credentials."
    }

    return [PSCustomObject]@{
        Username = $username
        Password = $password
    }
}

function Start-KubectlPortForward {
    param(
        [Parameter(Mandatory = $true)]
        [string]$KubectlPath,
        [Parameter(Mandatory = $true)]
        [string]$Kubeconfig
    )

    $arguments = @(
        '--kubeconfig', $Kubeconfig,
        '-n', $KubernetesNamespace,
        'port-forward', "service/$ServiceName",
        "${LocalPort}:6379",
        '--address', '127.0.0.1'
    )
    $quotedArguments = @(
        foreach ($argument in $arguments) {
            if ([string]$argument -match '"') {
                throw 'A kubectl argument contains an unsupported quote character.'
            }
            '"{0}"' -f [string]$argument
        }
    )

    # ShellExecute avoids the Path/PATH merge failure seen in some Windows
    # development shells and keeps the kubectl window hidden.
    $processStartInfo = New-Object -TypeName 'System.Diagnostics.ProcessStartInfo'
    $processStartInfo.FileName = $KubectlPath
    $processStartInfo.Arguments = $quotedArguments -join ' '
    $processStartInfo.UseShellExecute = $true
    $processStartInfo.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden

    $process = New-Object -TypeName 'System.Diagnostics.Process'
    $process.StartInfo = $processStartInfo
    if (-not $process.Start()) {
        throw 'Failed to create the kubectl port-forward process.'
    }
    return $process
}

function Write-RespCommand {
    param(
        [Parameter(Mandatory = $true)]
        [IO.Stream]$Stream,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $encoding = New-Object Text.UTF8Encoding($false)
    $buffer = New-Object IO.MemoryStream
    try {
        $header = $encoding.GetBytes("*$($Arguments.Count)`r`n")
        $buffer.Write($header, 0, $header.Length)
        foreach ($argument in $Arguments) {
            $argumentBytes = $encoding.GetBytes($argument)
            $lengthBytes = $encoding.GetBytes("`$$($argumentBytes.Length)`r`n")
            $buffer.Write($lengthBytes, 0, $lengthBytes.Length)
            $buffer.Write($argumentBytes, 0, $argumentBytes.Length)
            $lineEnd = $encoding.GetBytes("`r`n")
            $buffer.Write($lineEnd, 0, $lineEnd.Length)
        }

        $payload = $buffer.ToArray()
        $Stream.Write($payload, 0, $payload.Length)
        $Stream.Flush()
    } finally {
        $buffer.Dispose()
    }
}

function Test-RedisConnection {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [string]$Password
    )

    $client = New-Object Net.Sockets.TcpClient
    $waitHandle = $null
    $reader = $null
    try {
        $asyncResult = $client.BeginConnect('127.0.0.1', $LocalPort, $null, $null)
        $waitHandle = $asyncResult.AsyncWaitHandle
        if (-not $waitHandle.WaitOne(2000, $false)) {
            return $false
        }
        $client.EndConnect($asyncResult)
        $client.ReceiveTimeout = 2000
        $client.SendTimeout = 2000

        $stream = $client.GetStream()
        $reader = New-Object IO.StreamReader(
            $stream,
            (New-Object Text.UTF8Encoding($false)),
            $false,
            1024,
            $true
        )
        Write-RespCommand -Stream $stream -Arguments @('AUTH', $Username, $Password)
        $authResponse = $reader.ReadLine()
        if ('+OK' -ne $authResponse) {
            return $false
        }

        Write-RespCommand -Stream $stream -Arguments @('PING')
        return '+PONG' -eq $reader.ReadLine()
    } catch {
        return $false
    } finally {
        if ($null -ne $reader) {
            $reader.Dispose()
        }
        if ($null -ne $waitHandle) {
            $waitHandle.Dispose()
        }
        $client.Dispose()
    }
}

function Set-RedisEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Username,
        [Parameter(Mandatory = $true)]
        [string]$Password
    )

    [Environment]::SetEnvironmentVariable('REDIS_HOST', '127.0.0.1', 'Process')
    [Environment]::SetEnvironmentVariable('REDIS_PORT', [string]$LocalPort, 'Process')
    [Environment]::SetEnvironmentVariable('REDIS_USERNAME', $Username, 'Process')
    [Environment]::SetEnvironmentVariable('REDIS_PASSWORD', $Password, 'Process')
    [Environment]::SetEnvironmentVariable('REDIS_DATABASE', '0', 'Process')
    [Environment]::SetEnvironmentVariable('REDISCLI_AUTH', $Password, 'Process')

    try {
        Set-Clipboard -Value $Password
        return $true
    } catch {
        Write-Warning 'Redis password could not be copied to the clipboard.'
        return $false
    }
}

if ($Stop) {
    Stop-SavedTunnel
    Clear-RedisEnvironment
    exit 0
}

$kubectl = Get-RequiredCommandPath -Name 'kubectl'
$aliyun = Get-RequiredCommandPath -Name 'aliyun'
$workingDirectory = Join-Path $tempRoot ('biel-life-camp-redis-' + [Guid]::NewGuid().ToString('N'))
$kubeconfig = $null
$credential = $null
$kubectlProcess = $null
$startedNewTunnel = $false

try {
    $kubeconfig = New-AckKubeconfig -AliyunPath $aliyun -WorkingDirectory $workingDirectory
    $credential = Get-RedisCredential -KubectlPath $kubectl -Kubeconfig $kubeconfig

    $state = Get-SavedTunnelState
    $tunnelReady = $false
    if ($null -ne $state) {
        $savedProcessId = [int]$state.processId
        if (
            (Test-SavedTunnelProcess -State $state) -and
            (Test-RedisConnection `
                -Username $credential.Username `
                -Password $credential.Password)
        ) {
            $tunnelReady = $true
            $kubectlProcess = Get-Process -Id $savedProcessId
        } elseif (
            Test-SavedTunnelProcess -State $state
        ) {
            Stop-Process -Id $savedProcessId -Force
        }
    }

    if (-not $tunnelReady) {
        Remove-StateDirectory
        if (Test-LocalPortOpen -Port $LocalPort) {
            throw (
                "Local port $LocalPort is already in use. Use -LocalPort 16379 or stop " +
                'the process that owns the port.'
            )
        }

        New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
        $startedNewTunnel = $true
        $kubectlProcess = Start-KubectlPortForward `
            -KubectlPath $kubectl `
            -Kubeconfig $kubeconfig

        for ($attempt = 0; $attempt -lt 40; $attempt++) {
            Start-Sleep -Milliseconds 500
            if ($null -eq (Get-Process -Id $kubectlProcess.Id -ErrorAction SilentlyContinue)) {
                break
            }
            if (
                (Test-RedisConnection `
                    -Username $credential.Username `
                    -Password $credential.Password)
            ) {
                $tunnelReady = $true
                break
            }
        }

        if (-not $tunnelReady) {
            throw 'Failed to start the ACK Redis tunnel.'
        }

        [PSCustomObject]@{
            processId = $kubectlProcess.Id
            processStartedAtUtc = $kubectlProcess.StartTime.ToUniversalTime().ToString('o')
            localPort = $LocalPort
            kubernetesNamespace = $KubernetesNamespace
            serviceName = $ServiceName
            startedAt = (Get-Date).ToString('o')
        } |
            ConvertTo-Json |
            Set-Content -LiteralPath $stateFile -Encoding utf8
    }

    $passwordCopied = Set-RedisEnvironment `
        -Username $credential.Username `
        -Password $credential.Password

    Write-Host 'ACK Redis is ready for local tools.' -ForegroundColor Green
    Write-Host "Address: 127.0.0.1:$LocalPort"
    Write-Host "Username: $($credential.Username)"
    Write-Host 'Database: 0'
    if ($passwordCopied) {
        Write-Host 'Password: copied to clipboard'
    } else {
        Write-Host 'Password: available in REDIS_PASSWORD and REDISCLI_AUTH'
    }
    Write-Host "Tunnel PID: $($kubectlProcess.Id)"
    Write-Host "redis-cli: redis-cli -h 127.0.0.1 -p $LocalPort --user $($credential.Username)"
    Write-Host "Stop: .\server\scripts\Connect-AckRedisDev.ps1 -Stop -LocalPort $LocalPort"
} catch {
    if (
        $startedNewTunnel -and
        $null -ne $kubectlProcess -and
        $null -ne (Get-Process -Id $kubectlProcess.Id -ErrorAction SilentlyContinue)
    ) {
        Stop-Process -Id $kubectlProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($startedNewTunnel) {
        Remove-StateDirectory
    }
    throw
} finally {
    $credential = $null
    if (Test-Path -LiteralPath $workingDirectory) {
        $resolvedWorkingDirectory = [IO.Path]::GetFullPath($workingDirectory)
        $safePrefix = $tempRoot.TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
        ) + [IO.Path]::DirectorySeparatorChar
        if ($resolvedWorkingDirectory.StartsWith($safePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedWorkingDirectory -Recurse -Force
        }
    }
}
