[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $CouponTemplateId,

    [ValidateSet("same", "different")]
    [string] $Mode = "different",

    [int] $Threads = 500,
    [int] $RampUp = 1,
    [string] $HostName = "localhost",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $TokenCsv,
    [string] $OutputRoot = "loadtest-output/runs"
)

if (-not $TokenCsv) {
    if ($Mode -eq "same") {
        $TokenCsv = "loadtest-output/same-user-token.csv"
    } else {
        $TokenCsv = "loadtest-output/coupon-users-token.csv"
    }
}

$plan = if ($Mode -eq "same") {
    "loadtest/jmeter/coupon-claim-same-user.jmx"
} else {
    "loadtest/jmeter/coupon-claim-different-users.jmx"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-$Mode"
$jtlPath = Join-Path $runDir "coupon-claim.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$arguments = @(
    "-n",
    "-t", $plan,
    "-l", $jtlPath,
    "-j", $logPath,
    "-e",
    "-o", $reportDir,
    "-JCOUPON_TEMPLATE_ID=$CouponTemplateId",
    "-JTHREADS=$Threads",
    "-JRAMP_UP=$RampUp",
    "-JHOST=$HostName",
    "-JPORT=$Port",
    "-JPROTOCOL=$Protocol",
    "-JTOKEN_CSV=$TokenCsv"
)

Write-Host "Running JMeter plan: $plan"
Write-Host "Output directory: $runDir"
& jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath
Write-Host "JTL: $jtlPath"
Write-Host "JMeter log: $logPath"
Write-Host "HTML report: $reportDir"
Write-Host "Summary: $summaryPath"
