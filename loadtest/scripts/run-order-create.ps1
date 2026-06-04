[CmdletBinding()]
param(
    [ValidateSet("single-hot", "same-user", "spread-hot")]
    [string] $Mode = "single-hot",

    [int] $Threads = 500,
    [int] $RampUp = 1,
    [string] $HostName = "localhost",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $TokenSkuCsv,
    [string] $RunId,
    [string] $OutputRoot = "loadtest-output/runs"
)

if (-not $TokenSkuCsv) {
    $TokenSkuCsv = "loadtest-output/order-create-$Mode-token-sku.csv"
}
if (-not $RunId) {
    $RunId = Get-Date -Format "yyyyMMddHHmmss"
}

$plan = "loadtest/jmeter/order-create-hot-sku.jmx"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-order-$Mode"
$jtlPath = Join-Path $runDir "order-create.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"
$runIdPath = Join-Path $runDir "run-id.txt"

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Set-Content -LiteralPath $runIdPath -Value $RunId -Encoding UTF8

$arguments = @(
    "-n",
    "-t", $plan,
    "-l", $jtlPath,
    "-j", $logPath,
    "-e",
    "-o", $reportDir,
    "-JRUN_ID=$RunId",
    "-JTHREADS=$Threads",
    "-JRAMP_UP=$RampUp",
    "-JHOST=$HostName",
    "-JPORT=$Port",
    "-JPROTOCOL=$Protocol",
    "-JTOKEN_SKU_CSV=$TokenSkuCsv"
)

Write-Host "Running JMeter plan: $plan"
Write-Host "Mode: $Mode"
Write-Host "RunId: $RunId"
Write-Host "Token/SKU CSV: $TokenSkuCsv"
Write-Host "Output directory: $runDir"
& jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath
Write-Host "RunId: $RunId"
Write-Host "JTL: $jtlPath"
Write-Host "JMeter log: $logPath"
Write-Host "HTML report: $reportDir"
Write-Host "Summary: $summaryPath"
