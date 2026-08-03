[CmdletBinding()]
param(
    [string]$Url = $env:EHR_URL,
    [string]$EsbAuth = $env:EHR_ESB_AUTH,
    [string]$SourceSystem = $(if ($env:EHR_SOURCE_SYSTEM) {
        $env:EHR_SOURCE_SYSTEM
    } else {
        'WeChat'
    }),
    [string]$TargetSystem = $(if ($env:EHR_TARGET_SYSTEM) {
        $env:EHR_TARGET_SYSTEM
    } else {
        'EHR-Micro'
    }),
    [string]$ServiceName = $(if ($env:EHR_SERVICE_NAME) {
        $env:EHR_SERVICE_NAME
    } else {
        'ehr-micro-getpsninfo'
    }),
    [string]$RouteId = $(if ($env:EHR_ROUTE_ID) {
        $env:EHR_ROUTE_ID
    } else {
        'HZ'
    }),
    [string]$FullSince = $(if ($env:EHR_FULL_SINCE) {
        $env:EHR_FULL_SINCE
    } else {
        '2000-01-01 00:00:00'
    }),
    [ValidateRange(1, 300)]
    [int]$TimeoutSec = 30
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Url)) {
    throw 'EHR_URL is required.'
}
if ([string]::IsNullOrWhiteSpace($EsbAuth)) {
    throw 'EHR_ESB_AUTH is required.'
}

function Invoke-EhrMetadataPage {
    param(
        [Parameter(Mandatory)]
        [int]$PageNo
    )

    $separator = if ($Url.Contains('?')) { '&' } else { '?' }
    $query = 'ts={0}&pageSize=1&pageNo={1}&state=N' -f `
        [Uri]::EscapeDataString($FullSince), $PageNo
    $requestUrl = $Url + $separator + $query
    $headers = @{
        sourceSystem = $SourceSystem
        targetSystem = $TargetSystem
        serviceName = $ServiceName
        requestId = [Guid]::NewGuid().ToString()
        routeId = $RouteId
        EsbAuth = $EsbAuth
        Accept = 'application/json'
    }

    $response = Invoke-RestMethod `
        -Method Get `
        -Uri $requestUrl `
        -Headers $headers `
        -TimeoutSec $TimeoutSec

    if ([string]$response.code -ne '0') {
        throw ('EHR rejected the metadata probe. code={0}' -f [string]$response.code)
    }
    if ($null -eq $response.data) {
        throw 'EHR response does not contain data.'
    }

    $totalRecords = [long]$response.data.totalRecords
    $totalPages = [int]$response.data.totalPages
    if ($totalRecords -lt 0 -or $totalPages -lt 1) {
        throw 'EHR response contains invalid totalRecords or totalPages.'
    }

    $rows = @($response.data.data)
    $fieldNames = @()
    if ($rows.Count -gt 0 -and $null -ne $rows[0]) {
        $fieldNames = @($rows[0].PSObject.Properties.Name | Sort-Object)
    }

    [PSCustomObject]@{
        PageNo = $PageNo
        TotalRecords = $totalRecords
        TotalPages = $totalPages
        ReturnedRows = $rows.Count
        FieldNames = $fieldNames -join ','
    }
}

$firstPage = Invoke-EhrMetadataPage -PageNo 1
$lastPage = if ($firstPage.TotalPages -gt 1) {
    Invoke-EhrMetadataPage -PageNo $firstPage.TotalPages
} else {
    $firstPage
}

if ($lastPage.TotalRecords -ne $firstPage.TotalRecords -or
    $lastPage.TotalPages -ne $firstPage.TotalPages) {
    throw 'EHR paging metadata changed between the first and last page probes.'
}

$firstPage
if ($lastPage.PageNo -ne $firstPage.PageNo) {
    $lastPage
}

Write-Host 'EHR metadata probe passed. No employee field values or EsbAuth were printed.'
