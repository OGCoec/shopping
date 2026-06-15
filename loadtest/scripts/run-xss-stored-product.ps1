[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $AdminCookie,

    [Parameter(Mandatory = $true)]
    [string] $CategoryId,

    [int] $Threads = 1,
    [int] $RampUp = 1,
    [string] $HostName = "localhost",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $PayloadTsv = "loadtest/data/xss-payloads.tsv",
    [string] $ImagePayloadTsv = "loadtest/data/xss-image-url-payloads.tsv",
    [string] $SafeImagePayloadTsv = "loadtest/data/xss-safe-image-url-payloads.tsv",
    [string] $TokenCsv = "loadtest-output/xss-users-token.csv",
    [string] $TestImage = "loadtest/data/xss-test-image.svg",
    [string] $TestImageMime = "image/svg+xml",
    [string] $MainImageUrl = "/shopping/images/test.png",
    [string] $SpuId,
    [switch] $AllowExistingSpuUpdate,
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

$plan = "loadtest/jmeter/xss-stored-product.jmx"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-xss-stored-product"
$jtlPath = Join-Path $runDir "xss-stored-product.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"
$resultVarsPath = Join-Path $runDir "xss-product.properties"

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$jmeter = Resolve-JMeterPath -RequestedPath $JMeterPath
$payloadLoops = Get-TsvDataRowCount -Path $PayloadTsv
$imagePayloadLoops = Get-TsvDataRowCount -Path $ImagePayloadTsv
$safeImagePayloadLoops = Get-TsvDataRowCount -Path $SafeImagePayloadTsv
if (-not (Test-Path -LiteralPath $TokenCsv)) {
    throw "TokenCsv was not found: $TokenCsv. Generate it with CouponAccessTokenCsvExportMain before running stored XSS tests."
}
$runId = Get-Date -Format "yyyyMMddHHmmss"
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
    "-JIMAGE_PAYLOAD_TSV=$ImagePayloadTsv",
    "-JIMAGE_PAYLOAD_LOOPS=$imagePayloadLoops",
    "-JSAFE_IMAGE_PAYLOAD_TSV=$SafeImagePayloadTsv",
    "-JSAFE_IMAGE_PAYLOAD_LOOPS=$safeImagePayloadLoops",
    "-JTOKEN_CSV=$TokenCsv",
    "-JADMIN_COOKIE=$AdminCookie",
    "-JCATEGORY_ID=$CategoryId",
    "-JTEST_IMAGE=$TestImage",
    "-JTEST_IMAGE_MIME=$TestImageMime",
    "-JMAIN_IMAGE_URL=$MainImageUrl",
    "-JRUN_ID=$runId",
    "-JXSS_RESULT_FILE=$resultVarsPath",
    "-JALLOW_UPDATE_EXISTING_SPU=$($AllowExistingSpuUpdate.IsPresent.ToString().ToLowerInvariant())",
    "-Jjmeter.save.saveservice.output_format=csv",
    "-Jjmeter.save.saveservice.print_field_names=true"
)
if ($SpuId) {
    $arguments += "-JSPU_ID=$SpuId"
}

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
    throw "Stored XSS JMeter run has $failureCount failed samples. Check $jtlPath and $logPath"
}

Write-Host "JTL: $jtlPath"
Write-Host "JMeter log: $logPath"
Write-Host "HTML report: $reportDir"
Write-Host "Summary: $summaryPath"
if (Test-Path -LiteralPath $resultVarsPath) {
    Write-Host "Result variables: $resultVarsPath"
    Get-Content -LiteralPath $resultVarsPath
}
