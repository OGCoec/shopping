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
    [int] $StreamSettleSeconds = 20,
    [switch] $Verify,
    [string] $PostgresUrl = "postgresql://postgres:123456@127.0.0.1:5432/shopping",
    [switch] $RedisCheck,
    [string] $RedisCliPath = "redis-cli",
    [string] $RedisHost = "127.0.0.1",
    [int] $RedisPort = 6380,
    [string] $RedisPassword = "123456",
    [int] $RedisDatabase = 1,
    [string] $CallbackStreamKey = "shopping:payment:callback:stream",
    [string] $CallbackStreamGroup = "payment-callback-flusher",
    [string] $RefundStreamKey = "shopping:payment:refund:stream",
    [string] $RefundStreamGroup = "payment-refund-flusher"
)

if (-not $RunId) {
    $RunId = "callback-refund-stream-" + (Get-Date -Format "yyyyMMddHHmmss")
}

$plan = "loadtest/jmeter/order-callback-batch-refund-flow.jmx"
$verifySql = "loadtest/sql/verify-order-callback-refund-stream.sql"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$runDir = Join-Path $OutputRoot "$timestamp-order-callback-refund-stream"

$runDir = (New-Item -ItemType Directory -Force -Path $runDir).FullName
$jtlPath = Join-Path $runDir "order-callback-refund-stream.jtl"
$logPath = Join-Path $runDir "jmeter.log"
$reportDir = Join-Path $runDir "html-report"
$summaryPath = Join-Path $runDir "summary.csv"
$runIdPath = Join-Path $runDir "run-id.txt"
$callbackResultCsvPath = Join-Path $runDir "order-callback-refund-stream-results.csv"
$configPath = Join-Path $runDir "run-config.json"
$verifyCommandPath = Join-Path $runDir "verify-command.txt"
$verifyOutputPath = Join-Path $runDir "verify-output.txt"
$redisOutputPath = Join-Path $runDir "redis-stream-state.txt"
Set-Content -LiteralPath $runIdPath -Value $RunId -Encoding UTF8

if (-not (Test-Path -LiteralPath $CallbackCsv)) {
    throw "Callback CSV does not exist: $CallbackCsv"
}
$CallbackCsv = (Resolve-Path -LiteralPath $CallbackCsv).Path

function Invoke-RedisCli {
    param([string[]] $RedisArgs)

    $baseArgs = @("-h", $RedisHost, "-p", "$RedisPort", "-n", "$RedisDatabase")
    if ($RedisPassword) {
        $baseArgs += @("-a", $RedisPassword)
    }
    & $RedisCliPath @baseArgs @RedisArgs 2>&1
}

function Write-RedisStreamState {
    param([string] $Label)

    if (-not $RedisCheck) {
        return
    }
    Add-Content -LiteralPath $redisOutputPath -Value "===== $Label =====" -Encoding UTF8
    Add-Content -LiteralPath $redisOutputPath -Value "Callback XLEN" -Encoding UTF8
    Invoke-RedisCli -RedisArgs @("XLEN", $CallbackStreamKey) | Tee-Object -FilePath $redisOutputPath -Append
    Add-Content -LiteralPath $redisOutputPath -Value "Callback XPENDING" -Encoding UTF8
    Invoke-RedisCli -RedisArgs @("XPENDING", $CallbackStreamKey, $CallbackStreamGroup) | Tee-Object -FilePath $redisOutputPath -Append
    Add-Content -LiteralPath $redisOutputPath -Value "Refund XLEN" -Encoding UTF8
    Invoke-RedisCli -RedisArgs @("XLEN", $RefundStreamKey) | Tee-Object -FilePath $redisOutputPath -Append
    Add-Content -LiteralPath $redisOutputPath -Value "Refund XPENDING" -Encoding UTF8
    Invoke-RedisCli -RedisArgs @("XPENDING", $RefundStreamKey, $RefundStreamGroup) | Tee-Object -FilePath $redisOutputPath -Append
}

$config = [ordered]@{
    runId = $RunId
    plan = $plan
    verifySql = $verifySql
    callbackCsv = $CallbackCsv
    threads = $Threads
    rampUp = $RampUp
    streamSettleSeconds = $StreamSettleSeconds
    redisCheck = [bool] $RedisCheck
    callbackStreamKey = $CallbackStreamKey
    refundStreamKey = $RefundStreamKey
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
Write-RedisStreamState -Label "before-jmeter"
& jmeter @arguments
if ($LASTEXITCODE -ne 0) {
    throw "JMeter failed with exit code $LASTEXITCODE. Check $logPath"
}
Write-RedisStreamState -Label "after-jmeter-before-settle"

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
    if ($StreamSettleSeconds -gt 0) {
        Write-Host "Waiting $StreamSettleSeconds seconds for callback Stream flush and refund Stream flush."
        Start-Sleep -Seconds $StreamSettleSeconds
    }
    Write-RedisStreamState -Label "after-settle-before-sql"
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
if ($RedisCheck) {
    Write-Host "Redis stream state: $redisOutputPath"
}
