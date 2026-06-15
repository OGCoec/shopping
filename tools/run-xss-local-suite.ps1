[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $AdminCookie,

    [Parameter(Mandatory = $true)]
    [string] $CategoryId,

    [string] $HostName = "localhost",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $BaseUrl,
    [string] $SpuId,
    [string] $TokenCsv = "loadtest-output/xss-users-token.csv",
    [switch] $AllowExistingSpuUpdate,
    [switch] $RunDom,
    [switch] $SkipAdminDom,
    [switch] $HeadedDom,
    [string] $JMeterPath
)

$ErrorActionPreference = "Stop"

if (-not $BaseUrl) {
    $BaseUrl = "${Protocol}://${HostName}:${Port}"
}

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
Push-Location $repoRoot
try {
    Write-Host "Step 1/6: static XSS scan"
    powershell -ExecutionPolicy Bypass -File ".\tools\xss-static-scan.ps1"

    Write-Host "Step 2/6: reflected XSS JMeter smoke"
    $reflectedArgs = @{
        AdminCookie = $AdminCookie
        HostName = $HostName
        Port = $Port
        Protocol = $Protocol
        TokenCsv = $TokenCsv
    }
    if ($JMeterPath) {
        $reflectedArgs["JMeterPath"] = $JMeterPath
    }
    & ".\loadtest\scripts\run-xss-reflected-api.ps1" @reflectedArgs

    Write-Host "Step 3/6: stored XSS JMeter smoke"
    $storedArgs = @{
        AdminCookie = $AdminCookie
        CategoryId = $CategoryId
        HostName = $HostName
        Port = $Port
        Protocol = $Protocol
        TokenCsv = $TokenCsv
    }
    if ($SpuId) {
        $storedArgs["SpuId"] = $SpuId
    }
    if ($AllowExistingSpuUpdate) {
        $storedArgs["AllowExistingSpuUpdate"] = $true
    }
    if ($JMeterPath) {
        $storedArgs["JMeterPath"] = $JMeterPath
    }
    & ".\loadtest\scripts\run-xss-stored-product.ps1" @storedArgs

    $latestStoredRun = Get-ChildItem -LiteralPath ".\loadtest-output\runs" -Directory |
        Where-Object { $_.Name -like "*-xss-stored-product" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latestStoredRun) {
        throw "Could not find the latest stored XSS run directory."
    }
    $resultVarsPath = Join-Path $latestStoredRun.FullName "xss-product.properties"
    if (Test-Path -LiteralPath $resultVarsPath) {
        $resultVars = ConvertFrom-StringData -StringData (Get-Content -LiteralPath $resultVarsPath -Raw)
        if (-not $SpuId) {
            $SpuId = $resultVars.spuId
        }
    }
    if (-not $SpuId) {
        throw "Stored XSS run did not produce an SPU id."
    }

    Write-Host "Step 4/6: CSP Report-Only response header check"
    & ".\tools\xss-response-header-check.ps1" -BaseUrl $BaseUrl -SpuId $SpuId -AdminCookie $AdminCookie

    if ($RunDom) {
        Write-Host "Step 5/6: DOM XSS browser smoke"
        $domArgs = @{
            SpuId = $SpuId
            BaseUrl = $BaseUrl
            AdminCookie = $AdminCookie
        }
        if ($SkipAdminDom) {
            $domArgs["SkipAdmin"] = $true
        }
        if ($HeadedDom) {
            $domArgs["Headed"] = $true
        }
        & ".\tools\run-xss-dom-smoke.ps1" @domArgs
    } else {
        Write-Host "Step 5/6: DOM XSS browser smoke skipped. Pass -RunDom to execute it."
    }

    Write-Host "Step 6/6: static XSS scan after dynamic checks"
    powershell -ExecutionPolicy Bypass -File ".\tools\xss-static-scan.ps1"

    Write-Host "XSS local suite completed. Stored test SPU id: $SpuId"
} finally {
    Pop-Location
}
