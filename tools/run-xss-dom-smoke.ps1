[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SpuId,

    [string] $BaseUrl = "https://localhost:6655",
    [string] $AdminCookie,
    [string] $UserCookie,
    [string] $AccessToken,
    [string] $TokenCsv = "loadtest-output/xss-users-token.csv",
    [switch] $SkipAdmin,
    [switch] $Headed,
    [int] $WaitMs = 1500
)

$ErrorActionPreference = "Stop"

$resolvedAccessToken = $AccessToken
if (-not $resolvedAccessToken -and $TokenCsv -and (Test-Path -LiteralPath $TokenCsv)) {
    $tokenRow = Import-Csv -LiteralPath $TokenCsv | Select-Object -First 1
    if ($tokenRow -and $tokenRow.accessToken) {
        $resolvedAccessToken = [string] $tokenRow.accessToken
    }
}

$scriptPath = Join-Path $PSScriptRoot "xss-dom-smoke.spec.js"
$arguments = @(
    $scriptPath,
    "--spu-id", $SpuId,
    "--base-url", $BaseUrl,
    "--wait-ms", $WaitMs
)
if ($AdminCookie) {
    $arguments += @("--admin-cookie", $AdminCookie)
}
if ($UserCookie) {
    $arguments += @("--user-cookie", $UserCookie)
}
if ($resolvedAccessToken) {
    $arguments += @("--access-token", $resolvedAccessToken)
}
if ($SkipAdmin) {
    $arguments += "--skip-admin"
}
if ($Headed) {
    $arguments += @("--headless", "false")
}

& node @arguments
if ($LASTEXITCODE -ne 0) {
    throw "DOM XSS smoke failed with exit code $LASTEXITCODE."
}
