[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $AdminCookie,

    [int] $Threads = 1,
    [int] $RampUp = 1,
    [string] $HostName = "localhost",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $PayloadTsv = "loadtest/data/xss-payloads.tsv",
    [string] $TokenCsv = "loadtest-output/xss-users-token.csv",
    [string] $OutputRoot = "loadtest-output/runs",
    [string] $JMeterPath
)

$ErrorActionPreference = "Stop"

function Resolve-JMeterPath {
    param([string] $RequestedPath)
    if ($RequestedPath) {
        if (Test-Path -LiteralPath $RequestedPath) {
            return (Resolve-Path -LiteralPath $RequestedPath).Path
        }
        return $RequestedPath
    }
    $command = Get-Command jmeter -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $bundled = "E:\apache-jmeter-5.6.3\bin\jmeter.bat"
    if (Test-Path -LiteralPath $bundled) {
        return $bundled
    }
    throw "JMeter was not found. Pass -JMeterPath or add jmeter to PATH."
}

function Get-TsvDataRowCount {
    param([string] $Path)
    $resolved = Resolve-Path -LiteralPath $Path
    $lines = @(Get-Content -LiteralPath $resolved.Path | Where-Object { $_.Trim() })
    return [Math]::Max(0, $lines.Count - 1)
}

$plan = "loadtest/jmeter/xss-reflected-api.jmx"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-xss-reflected"
$jtlPath = Join-Path $runDir "xss-reflected-api.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$jmeter = Resolve-JMeterPath -RequestedPath $JMeterPath
$payloadLoops = Get-TsvDataRowCount -Path $PayloadTsv
if (-not (Test-Path -LiteralPath $TokenCsv)) {
    throw "TokenCsv was not found: $TokenCsv. Generate it with CouponAccessTokenCsvExportMain before running reflected XSS tests."
}
$arguments = @(
    "-n",
    "-t", $plan,
    "-l", $jtlPath,
    "-j", $logPath,
    "-e",
    "-o", $reportDir,
    "-JTHREADS=$Threads",
    "-JRAMP_UP=$RampUp",
    "-JHOST=$HostName",
    "-JPORT=$Port",
    "-JPROTOCOL=$Protocol",
    "-JPAYLOAD_TSV=$PayloadTsv",
    "-JPAYLOAD_LOOPS=$payloadLoops",
    "-JTOKEN_CSV=$TokenCsv",
    "-JADMIN_COOKIE=$AdminCookie",
    "-Jjmeter.save.saveservice.output_format=csv",
    "-Jjmeter.save.saveservice.print_field_names=true"
)

Write-Host "Running JMeter plan: $plan"
Write-Host "Output directory: $runDir"
& $jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath
$samples = @(Import-Csv -LiteralPath $jtlPath)
$failureCount = @($samples | Where-Object { $_.success -ne "true" }).Count
if ($failureCount -gt 0) {
    throw "Reflected XSS JMeter run has $failureCount failed samples. Check $jtlPath and $logPath"
}

Write-Host "JTL: $jtlPath"
Write-Host "JMeter log: $logPath"
Write-Host "HTML report: $reportDir"
Write-Host "Summary: $summaryPath"
