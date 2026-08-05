<#
.SYNOPSIS
Generates cryptographically secure 32-byte keys encoded as Base64.

.PARAMETER Count
Number of independent keys to generate. The default is 1.

.EXAMPLE
.\scripts\New-32ByteKey.ps1

.EXAMPLE
.\scripts\New-32ByteKey.ps1 -Count 4
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 100)]
    [int]$Count = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    for ($index = 0; $index -lt $Count; $index++) {
        $keyBytes = New-Object byte[] 32
        try {
            $random.GetBytes($keyBytes)
            [Convert]::ToBase64String($keyBytes)
        } finally {
            [Array]::Clear($keyBytes, 0, $keyBytes.Length)
        }
    }
} finally {
    $random.Dispose()
}
