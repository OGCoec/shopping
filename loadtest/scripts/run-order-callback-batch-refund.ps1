[CmdletBinding()]
param(
    [int] $Threads = 500,
    [int] $RampUp = 1,
    [string] $HostName = "localhost",
    [int] $Port = 6655,
    [string] $Protocol = "https",
    [string] $CallbackCsv = "loadtest-output/order-callback-batch-input.csv",
    [string] $RunId,
    [string] $OutputRoot = "loadtest-output/runs",
    [int] $DispatchSettleSeconds = 15,
    [switch] $Verify,
    [string] $PostgresUrl = "postgresql://postgres:123456@127.0.0.1:5432/shopping"
)

if (-not $RunId) {
    $RunId = Get-Date -Format "yyyyMMddHHmmss"
}

$plan = "loadtest/jmeter/order-callback-batch-refund-flow.jmx"
$verifySql = "loadtest/sql/verify-order-callback-batch-refund.sql"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-order-callback-batch-refund"

$runDir = (New-Item -ItemType Directory -Force -Path $runDir).FullName
$jtlPath = Join-Path $runDir "order-callback-batch-refund.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"
$runIdPath = Join-Path $runDir "run-id.txt"
$callbackResultCsvPath = Join-Path $runDir "order-callback-batch-results.csv"
$configPath = Join-Path $runDir "run-config.json"
$verifyCommandPath = Join-Path $runDir "verify-command.txt"
$verifyOutputPath = Join-Path $runDir "verify-output.txt"
Set-Content -LiteralPath $runIdPath -Value $RunId -Encoding UTF8

if (-not (Test-Path -LiteralPath $CallbackCsv)) {
    throw "Callback CSV does not exist: $CallbackCsv"
}
$CallbackCsv = (Resolve-Path -LiteralPath $CallbackCsv).Path

$config = [ordered]@{
    runId = $RunId
    plan = $plan
    callbackCsv = $CallbackCsv
    threads = $Threads
    rampUp = $RampUp
    dispatchSettleSeconds = $DispatchSettleSeconds
}
$config | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $configPath -Encoding UTF8

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
    "-JCALLBACK_CSV=$CallbackCsv",
    "-JCALLBACK_RESULT_CSV=$callbackResultCsvPath",
    "-Jjmeter.save.saveservice.output_format=csv",
    "-Jjmeter.save.saveservice.print_field_names=true"
)

Write-Host "Running JMeter plan: $plan"
Write-Host "RunId: $RunId"
Write-Host "Callback CSV: $CallbackCsv"
Write-Host "Output directory: $runDir"
& jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}

& (Join-Path $PSScriptRoot "summarize-jmeter.ps1") -JtlPath $jtlPath -OutputPath $summaryPath

$callbackInputSqlPath = $CallbackCsv.Replace("\", "/")
$callbackResultSqlPath = $callbackResultCsvPath.Replace("\", "/")
$verifyCommand = @(
    "psql `"$PostgresUrl`"",
    "-v run_id=$RunId",
    "-v callback_input_csv=`"$callbackInputSqlPath`"",
    "-v callback_result_csv=`"$callbackResultSqlPath`"",
    "-f $verifySql"
) -join " "
Set-Content -LiteralPath $verifyCommandPath -Value $verifyCommand -Encoding UTF8

if ($Verify) {
    if ($DispatchSettleSeconds -gt 0) {
        Write-Host "Waiting $DispatchSettleSeconds seconds for payment callback and refund dispatch schedulers."
        Start-Sleep -Seconds $DispatchSettleSeconds
    }
    $psqlArgs = @(
        $PostgresUrl,
        "-v", "run_id=$RunId",
        "-v", "callback_input_csv=$callbackInputSqlPath",
        "-v", "callback_result_csv=$callbackResultSqlPath",
        "-f", $verifySql
    )
    & psql @psqlArgs | Tee-Object -FilePath $verifyOutputPath
    if ($LASTEXITCODE -ne 0) {
        throw "Verification SQL failed with exit code $LASTEXITCODE. Check $verifyOutputPath"
    }
}

Write-Host "RunId: $RunId"
Write-Host "JTL: $jtlPath"
Write-Host "JMeter log: $logPath"
Write-Host "HTML report: $reportDir"
Write-Host "Summary: $summaryPath"
Write-Host "Callback result CSV: $callbackResultCsvPath"
Write-Host "Run config: $configPath"
Write-Host "Verify command: $verifyCommandPath"
